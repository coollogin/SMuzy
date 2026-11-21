package io.github.coollogin.smuzy.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import io.github.coollogin.smuzy.model.UserTagAlbum
import io.github.coollogin.smuzy.model.UserTagCount
import io.github.coollogin.smuzy.model.UserTagSong
import io.github.coollogin.smuzy.model.UserTagSync

@Dao
interface UserTagDao {
    @Query(
        "SELECT tag AS tag, COUNT(*) AS songCount FROM user_tag_song " +
            "WHERE server_id = :serverId GROUP BY tag " +
            "ORDER BY songCount DESC, tag COLLATE NOCASE ASC"
    )
    fun getTagCounts(serverId: String): List<UserTagCount>

    @Query("SELECT DISTINCT album_id FROM user_tag_song WHERE server_id = :serverId AND tag = :tag")
    fun getAlbumIdsForTag(serverId: String, tag: String): List<String>

    @Query("SELECT * FROM user_tag_album WHERE server_id = :serverId AND album_id IN (:albumIds)")
    fun getAlbums(serverId: String, albumIds: List<String>): List<UserTagAlbum>

    @Query("SELECT * FROM user_tag_sync WHERE server_id = :serverId")
    fun getSync(serverId: String): UserTagSync?

    @Query("DELETE FROM user_tag_song WHERE server_id = :serverId")
    fun deleteSongs(serverId: String)

    @Query("DELETE FROM user_tag_album WHERE server_id = :serverId")
    fun deleteAlbums(serverId: String)

    @Query("DELETE FROM user_tag_sync WHERE server_id = :serverId")
    fun deleteSync(serverId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSongs(songs: List<UserTagSong>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAlbums(albums: List<UserTagAlbum>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(sync: UserTagSync)
}
