package io.github.coollogin.smuzy.model

/** Projection of `user_tag_song` grouped by tag. */
data class UserTagCount(
    val tag: String,
    val songCount: Int,
)
