package io.github.coollogin.smuzy.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_playlist")
data class PinnedPlaylist(
    @PrimaryKey val playlistId: String
)
