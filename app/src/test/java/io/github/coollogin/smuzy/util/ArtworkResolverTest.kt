package io.github.coollogin.smuzy.util

import io.github.coollogin.smuzy.subsonic.models.Child
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkResolverTest {
    @Test
    fun songUsesOwnCoverArtFirst() {
        assertEquals("track-cover", ArtworkResolver.songCoverArtId("track-cover", "album-cover"))
    }

    @Test
    fun songFallsBackToAlbumCover() {
        assertEquals("album-cover", ArtworkResolver.songCoverArtId(null, "album-cover"))
        assertEquals("album-cover", ArtworkResolver.songCoverArtId("", "album-cover"))
        assertEquals("album-cover", ArtworkResolver.songCoverArtId("   ", "album-cover"))
    }

    @Test
    fun songWithoutArtworkHasNoCover() {
        assertNull(ArtworkResolver.songCoverArtId(null, null))
        assertNull(ArtworkResolver.songCoverArtId("", ""))
    }

    @Test
    fun songModelUsesSamePriority() {
        val song = Child(
            id = "song",
            coverArtId = null,
            albumId = "album-cover"
        )

        assertEquals("album-cover", ArtworkResolver.songCoverArtId(song))
        assertNull(ArtworkResolver.songCoverArtId(null as Child?))
    }

    @Test
    fun albumCoverIgnoresBlankId() {
        assertEquals("album-cover", ArtworkResolver.albumCoverArtId("album-cover"))
        assertNull(ArtworkResolver.albumCoverArtId(null))
        assertNull(ArtworkResolver.albumCoverArtId("   "))
    }
}
