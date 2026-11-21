package io.github.coollogin.smuzy.subsonic.api.navidrome

import io.github.coollogin.smuzy.subsonic.models.NavidromeLoginRequest
import io.github.coollogin.smuzy.subsonic.models.NavidromeSong
import io.github.coollogin.smuzy.util.ClientCertManager
import io.github.coollogin.smuzy.util.ServerHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server does not offer Navidrome's native API (not Navidrome, or too old). */
class NavidromeUnsupportedException(message: String) : Exception(message)

/** The server rejected the stored credentials for the native API. */
class NavidromeAuthException(message: String) : Exception(message)

/**
 * Read-only client for Navidrome's native API. It is the only API that exposes custom tags:
 * Subsonic `Child` carries genres/moods/groupings, never USER_TAG (navidrome#5868).
 *
 * The JWT is obtained per scan and kept in memory only; it is never stored or logged. The
 * OkHttp client deliberately has no logging interceptor so the authorization header cannot leak
 * into logcat.
 */
class NavidromeClient(serverAddress: String?) {
    private val service: NavidromeNativeService

    init {
        val baseUrl = sanitizeBaseUrl(serverAddress)
            ?: throw NavidromeUnsupportedException("No usable server address")

        service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient())
            .build()
            .create(NavidromeNativeService::class.java)
    }

    /**
     * Logs in and walks the whole library page by page. Returns every song with its raw tag map;
     * callers pick USER_TAG out of it. Songs are deduplicated by id in case the library changes
     * between pages, and [libraryId] keeps the result inside the selected Navidrome library.
     */
    fun fetchSongs(username: String?, password: String?, libraryId: String?): List<NavidromeSong> {
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            throw NavidromeUnsupportedException("No credentials stored for the native API")
        }

        var token = login(username, password)
        val songs = LinkedHashMap<String, NavidromeSong>()
        var offset = 0

        while (true) {
            var response = requestPage(token, offset)
            if (response.code() == HTTP_UNAUTHORIZED) {
                token = login(username, password)
                response = requestPage(token, offset)
            }

            val page = readPage(response)
            if (page.isEmpty()) break

            for (song in page) {
                val id = song.id ?: continue
                if (!belongsToLibrary(song, libraryId)) continue
                songs[id] = song
            }

            if (page.size > PAGE_SIZE) break // server ignored pagination; the page is the library
            offset += page.size
            if (page.size < PAGE_SIZE) break
            if (offset >= MAX_SONGS) break
        }

        return songs.values.toList()
    }

    private fun requestPage(token: String, offset: Int): Response<List<NavidromeSong>> =
        service.songs("Bearer $token", offset, offset + PAGE_SIZE, SORT).execute()

    private fun login(username: String, password: String): String {
        val response = try {
            service.login(NavidromeLoginRequest(username, password)).execute()
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw NavidromeUnsupportedException("Native API login is not available")
        }

        when {
            response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN ->
                throw NavidromeAuthException("Native API rejected the stored credentials")
            !response.isSuccessful ->
                throw NavidromeUnsupportedException("Native API login returned HTTP ${response.code()}")
        }

        return response.body()?.token?.takeIf { it.isNotBlank() }
            ?: throw NavidromeUnsupportedException("Native API login returned no token")
    }

    private fun readPage(response: Response<List<NavidromeSong>>): List<NavidromeSong> {
        if (response.code() == HTTP_UNAUTHORIZED) {
            throw NavidromeAuthException("Native API rejected the token")
        }
        if (!response.isSuccessful) {
            throw if (response.code() in 400..499) {
                NavidromeUnsupportedException("Native API songs returned HTTP ${response.code()}")
            } else {
                IOException("Native API songs returned HTTP ${response.code()}")
            }
        }

        return try {
            response.body().orEmpty()
        } catch (e: RuntimeException) {
            throw NavidromeUnsupportedException("Native API songs response is not a song list")
        }
    }

    private fun belongsToLibrary(song: NavidromeSong, libraryId: String?): Boolean {
        if (libraryId == null) return true
        return song.libraryId == libraryId
    }

    private fun sanitizeBaseUrl(address: String?): String? {
        val trimmed = address?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val parsed = trimmed.toHttpUrlOrNull() ?: return null
        if (parsed.host.isEmpty()) return null

        val text = parsed.toString()
        return if (text.endsWith("/")) text else "$text/"
    }

    private fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(2, TimeUnit.MINUTES)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(ServerHeaders.createInterceptor())
        .apply {
            ClientCertManager.sslSocketFactory?.let { sslSocketFactory(it, ClientCertManager.trustManager) }
        }
        .build()

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_SONGS = 100_000
        const val SORT = "title"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
