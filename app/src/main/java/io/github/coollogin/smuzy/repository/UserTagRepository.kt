package io.github.coollogin.smuzy.repository

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.github.coollogin.smuzy.App
import io.github.coollogin.smuzy.database.AppDatabase
import io.github.coollogin.smuzy.model.UserTagAlbum
import io.github.coollogin.smuzy.model.UserTagCloudUiState
import io.github.coollogin.smuzy.model.UserTagSong
import io.github.coollogin.smuzy.model.UserTagSync
import io.github.coollogin.smuzy.repository.UserTagAggregator.TaggedSong
import io.github.coollogin.smuzy.subsonic.api.navidrome.NavidromeAuthException
import io.github.coollogin.smuzy.subsonic.api.navidrome.NavidromeClient
import io.github.coollogin.smuzy.subsonic.api.navidrome.NavidromeUnsupportedException
import io.github.coollogin.smuzy.subsonic.models.AlbumID3
import io.github.coollogin.smuzy.subsonic.models.NavidromeSong
import io.github.coollogin.smuzy.util.Preferences
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds and serves the USER_TAG cloud. A full scan of the library is done through Navidrome's
 * native API (the only place USER_TAG exists), stored in Room per server and replaced atomically
 * once every page has been fetched successfully. A failed or partial scan never commits, so a
 * partial library is never presented as complete.
 */
@OptIn(UnstableApi::class)
class UserTagRepository {

    fun load(
        serverId: String?,
        libraryId: String?,
        force: Boolean,
        onState: (UserTagCloudUiState) -> Unit,
    ) {
        if (serverId.isNullOrBlank()) {
            onState(UserTagCloudUiState.unsupported())
            return
        }

        val generation = generationCounter.incrementAndGet()

        fun publish(state: UserTagCloudUiState) {
            if (generation == generationCounter.get()) onState(state)
        }

        IO.execute {
            val dao = AppDatabase.getInstance().userTagDao()
            val sync = dao.getSync(serverId)
            val sameLibrary = sync != null && sync.libraryId == libraryId
            val cachedTags = if (sameLibrary) dao.getTagCounts(serverId) else emptyList()
            val age = System.currentTimeMillis() - (sync?.completedAt ?: 0L)
            val fresh = sameLibrary && age in 0..CACHE_TTL_MS

            if (!force && fresh) {
                publish(UserTagCloudUiState.loaded(cachedTags))
                return@execute
            }

            publish(UserTagCloudUiState.loading(cachedTags))

            try {
                val client = NavidromeClient(Preferences.getInUseServerAddress())
                val songs = client.fetchSongs(Preferences.getUser(), Preferences.getPassword(), libraryId)

                if (generation != generationCounter.get()) return@execute

                val entries = ArrayList<TaggedSong>()
                val nativeAlbums = HashMap<String, NavidromeSong>()
                for (song in songs) {
                    val songId = song.id ?: continue
                    val albumId = song.albumId ?: continue
                    val tags = UserTagAggregator.extractUserTags(song.tags)
                    if (tags.isEmpty()) continue

                    entries.add(TaggedSong(songId, albumId, tags))
                    nativeAlbums.putIfAbsent(albumId, song)
                }

                val tagAlbumIds = entries.mapTo(LinkedHashSet()) { it.albumId }
                val metadata = fetchAlbumMetadata(tagAlbumIds)

                val albumRows = tagAlbumIds.mapNotNull { albumId ->
                    val meta = metadata[albumId]
                    val native = nativeAlbums[albumId]
                    UserTagAlbum(
                        serverId = serverId,
                        albumId = albumId,
                        name = meta?.name ?: native?.album,
                        artist = meta?.artist
                            ?: meta?.displayArtist
                            ?: native?.albumArtist
                            ?: native?.artist,
                        coverArtId = meta?.coverArtId,
                        year = meta?.year?.takeIf { it > 0 } ?: native?.year,
                    )
                }.filter { !it.name.isNullOrBlank() }

                val tagRows = entries.flatMap { entry ->
                    entry.tags.distinct().map { tag ->
                        UserTagSong(serverId, entry.songId, entry.albumId, tag)
                    }
                }
                Log.d(TAG, "Scanned ${songs.size} songs, tagged albums: ${tagAlbumIds.size}")

                if (generation != generationCounter.get()) return@execute

                AppDatabase.getInstance().runInTransaction {
                    dao.deleteSongs(serverId)
                    dao.deleteAlbums(serverId)
                    dao.deleteSync(serverId)
                    if (tagRows.isNotEmpty()) dao.insertSongs(tagRows)
                    if (albumRows.isNotEmpty()) dao.insertAlbums(albumRows)
                    dao.insertSync(
                        UserTagSync(
                            serverId = serverId,
                            libraryId = libraryId,
                            completedAt = System.currentTimeMillis(),
                            songCount = songs.size,
                        )
                    )
                }

                publish(UserTagCloudUiState.loaded(dao.getTagCounts(serverId)))
            } catch (e: NavidromeUnsupportedException) {
                Log.w(TAG, "Native tag API unavailable: ${e.message}")
                publish(if (cachedTags.isEmpty()) UserTagCloudUiState.unsupported() else UserTagCloudUiState.error(cachedTags))
            } catch (e: NavidromeAuthException) {
                Log.w(TAG, "Native tag API rejected credentials: ${e.message}")
                publish(UserTagCloudUiState.error(cachedTags))
            } catch (e: Exception) {
                Log.w(TAG, "Tag scan failed: ${e.javaClass.simpleName}: ${e.message}")
                publish(UserTagCloudUiState.error(cachedTags))
            }
        }
    }

    fun albumsForTag(serverId: String?, tag: String, callback: (List<AlbumID3>) -> Unit) {
        if (serverId.isNullOrBlank() || tag.isBlank()) {
            callback(emptyList())
            return
        }

        IO.execute {
            val dao = AppDatabase.getInstance().userTagDao()
            val albumIds = dao.getAlbumIdsForTag(serverId, tag)
            val rows = if (albumIds.isEmpty()) emptyList() else dao.getAlbums(serverId, albumIds)

            val albums = rows
                .map { row ->
                    AlbumID3(
                        id = row.albumId,
                        name = row.name ?: "",
                        artist = row.artist,
                        coverArtId = row.coverArtId,
                        year = row.year ?: 0,
                    )
                }
                .sortedWith { first, second ->
                    (first.name ?: "").compareTo(second.name ?: "", ignoreCase = true)
                }

            callback(albums)
        }
    }

    /** Drops the freshness marker so the next Library visit rescans, e.g. after a server-side scan. */
    fun invalidate(serverId: String?) {
        if (serverId.isNullOrBlank()) return
        IO.execute {
            AppDatabase.getInstance().userTagDao().deleteSync(serverId)
        }
    }

    /**
     * Display metadata (names, real cover art ids) comes from the regular Subsonic album listing.
     * Failures are tolerated: the caller falls back to the fields the native songs carry.
     */
    private fun fetchAlbumMetadata(albumIds: Set<String>): Map<String, AlbumID3> {
        if (albumIds.isEmpty()) return emptyMap()

        val result = HashMap<String, AlbumID3>()
        val client = App.getSubsonicClientInstance(false).getAlbumSongListClient()
        var offset = 0

        while (offset <= MAX_ALBUM_OFFSET) {
            val albums = try {
                val response = client
                    .getAlbumList2("alphabeticalByName", ALBUM_PAGE_SIZE, offset, null, null)
                    .execute()
                if (!response.isSuccessful) null
                else response.body()?.subsonicResponse?.albumList2?.albums
            } catch (e: Exception) {
                null
            } ?: break

            for (album in albums) {
                val id = album.id ?: continue
                if (albumIds.contains(id)) result[id] = album
            }

            if (albums.size < ALBUM_PAGE_SIZE) break
            offset += albums.size
        }

        return result
    }

    private companion object {
        const val TAG = "UserTagRepository"
        val IO = Executors.newSingleThreadExecutor()
        val generationCounter = AtomicInteger()
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L
        const val ALBUM_PAGE_SIZE = 500
        const val MAX_ALBUM_OFFSET = 100_000
    }
}
