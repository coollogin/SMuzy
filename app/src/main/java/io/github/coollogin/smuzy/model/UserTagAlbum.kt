package io.github.coollogin.smuzy.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Display metadata for an album that carries at least one USER_TAG song. */
@Entity(tableName = "user_tag_album", primaryKeys = ["server_id", "album_id"])
data class UserTagAlbum(
    @ColumnInfo(name = "server_id")
    val serverId: String,

    @ColumnInfo(name = "album_id")
    val albumId: String,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "artist")
    val artist: String?,

    @ColumnInfo(name = "cover_art_id")
    val coverArtId: String?,

    @ColumnInfo(name = "year")
    val year: Int?,
)
