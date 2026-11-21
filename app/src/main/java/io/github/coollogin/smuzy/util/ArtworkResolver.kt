package io.github.coollogin.smuzy.util

import io.github.coollogin.smuzy.subsonic.models.Child

object ArtworkResolver {
    @JvmStatic
    fun songCoverArtId(song: Child?): String? =
        song?.let { songCoverArtId(it.coverArtId, it.albumId) }

    @JvmStatic
    fun songCoverArtId(coverArtId: String?, albumId: String?): String? =
        coverArtId?.takeIf { it.isNotBlank() }
            ?: albumId?.takeIf { it.isNotBlank() }

    @JvmStatic
    fun albumCoverArtId(coverArtId: String?): String? =
        coverArtId?.takeIf { it.isNotBlank() }
}
