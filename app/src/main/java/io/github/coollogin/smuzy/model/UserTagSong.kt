package io.github.coollogin.smuzy.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One USER_TAG value of one song. Only tagged songs get rows, so the table stays small even for
 * large libraries. The cache is scoped by server and replaced atomically after a full scan.
 */
@Entity(
    tableName = "user_tag_song",
    primaryKeys = ["server_id", "song_id", "tag"],
    indices = [
        Index(value = ["server_id", "tag"]),
        Index(value = ["server_id", "album_id"]),
    ],
)
data class UserTagSong(
    @ColumnInfo(name = "server_id")
    val serverId: String,

    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "album_id")
    val albumId: String,

    @ColumnInfo(name = "tag")
    val tag: String,
)
