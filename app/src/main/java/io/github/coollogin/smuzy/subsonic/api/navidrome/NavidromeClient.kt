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
import java.util.concurrent.ConcurrentHashMap
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
    private val baseUrl: String
    private val service: NavidromeNativeService

    init {
        baseUrl = sanitizeBaseUrl(serverAddress)
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

        // Reuse the JWT across refreshes: Navidrome rate-limits /auth/login (HTTP 429), so
        // logging in on every scan makes retries fail exactly when the user needs them most.
        var token = cachedToken(username)
            ?: login(username, password).also { cacheToken(username, it) }
        val songs = LinkedHashMap<String, NavidromeSong>()
        var offset = 0

        while (true) {
            var response = requestPage(token, offset)
            if (response.code() == HTTP_UNAUTHORIZED) {
                invalidateToken(username)
                token = login(username, password).also { cacheToken(username, it) }
                response = requestPage(token, offset)
            }
            if (response.isSuccessful) token = rotateToken(username, token, response)

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
            isMissingEndpoint(response.code()) ->
                throw NavidromeUnsupportedException("Native API login returned HTTP ${response.code()}")
            // 429 from the login rate limiter and 5xx are transient: retry, do not hide the
            // whole tags section as if the server did not support it.
            !response.isSuccessful ->
                throw IOException("Native API login returned HTTP ${response.code()}")
        }

        return response.body()?.token?.takeIf { it.isNotBlank() }
            ?: throw NavidromeUnsupportedException("Native API login returned no token")
    }

    private fun readPage(response: Response<List<NavidromeSong>>): List<NavidromeSong> {
        if (response.code() == HTTP_UNAUTHORIZED) {
            throw NavidromeAuthException("Native API rejected the token")
        }
        if (!response.isSuccessful) {
            throw when {
                response.code() == HTTP_FORBIDDEN ->
                    NavidromeAuthException("Native API rejected the token")
                isMissingEndpoint(response.code()) ->
                    NavidromeUnsupportedException("Native API songs returned HTTP ${response.code()}")
                else ->
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

    private fun isMissingEndpoint(code: Int): Boolean =
        code == HTTP_NOT_FOUND || code == HTTP_METHOD_NOT_ALLOWED || code == HTTP_NOT_IMPLEMENTED

    private fun cachedToken(username: String): String? {
        val key = TokenKey(baseUrl, username)
        val cached = tokenCache[key] ?: return null
        if (cached.expiresAt > System.currentTimeMillis()) return cached.token

        tokenCache.remove(key, cached)
        return null
    }

    private fun cacheToken(username: String, token: String) {
        tokenCache[TokenKey(baseUrl, username)] =
            CachedToken(token, System.currentTimeMillis() + TOKEN_TTL_MS)
    }

    private fun invalidateToken(username: String) {
        tokenCache.remove(TokenKey(baseUrl, username))
    }

    /**
     * Navidrome rotates the JWT on every successful request and returns the replacement in
     * [AUTHORIZATION_HEADER]. Keep the newest one so the cached token does not go stale.
     */
    private fun rotateToken(
        username: String,
        current: String,
        response: Response<*>,
    ): String {
        val header = response.headers()[AUTHORIZATION_HEADER]?.trim().orEmpty()
        if (header.isEmpty()) return current

        val rotated = if (header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            header.substring(BEARER_PREFIX.length).trim()
        } else {
            header
        }
        if (rotated.isEmpty()) return current

        cacheToken(username, rotated)
        return rotated
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

    private data class TokenKey(val baseUrl: String, val username: String)

    private data class CachedToken(val token: String, val expiresAt: Long)

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_SONGS = 100_000
        const val SORT = "title"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_NOT_IMPLEMENTED = 501
        const val TOKEN_TTL_MS = 12 * 60 * 60 * 1000L
        const val AUTHORIZATION_HEADER = "x-nd-authorization"
        const val BEARER_PREFIX = "Bearer "

        /** In-memory only; JWTs are never written to disk or logged. */
        val tokenCache = ConcurrentHashMap<TokenKey, CachedToken>()
    }
}
