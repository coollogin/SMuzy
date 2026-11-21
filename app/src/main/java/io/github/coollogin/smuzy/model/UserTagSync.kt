package io.github.coollogin.smuzy.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Marks a completed USER_TAG scan. The tag rows are only trusted when this row exists for the
 * same server and the same active library; partial scans never write here.
 */
@Entity(tableName = "user_tag_sync")
data class UserTagSync(
    @PrimaryKey
    @ColumnInfo(name = "server_id")
    val serverId: String,

    @ColumnInfo(name = "library_id")
    val libraryId: String?,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long,

    @ColumnInfo(name = "song_count")
    val songCount: Int,
)
