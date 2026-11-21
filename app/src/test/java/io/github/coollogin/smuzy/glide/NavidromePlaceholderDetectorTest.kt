package io.github.coollogin.smuzy.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromePlaceholderDetectorTest {

    @Test
    fun placeholderHeadersAreDetectedForCoverArt() {
        assertTrue(
            NavidromePlaceholderDetector.isPlaceholderResponse(
                "/rest/getCoverArt",
                "no-store",
                null,
                null,
                "image/webp"
            )
        )
        assertTrue(
            NavidromePlaceholderDetector.isPlaceholderResponse(
                "/rest/getCoverArt.view",
                "No-Store",
                "",
                "",
                "image/webp; charset=binary"
            )
        )
    }

    @Test
    fun responsesWithValidatorsAreNotPlaceholders() {
        assertFalse(
            NavidromePlaceholderDetector.isPlaceholderResponse(
                "/rest/getCoverArt",
                "no-store",
                "\"abc\"",
                null,
                "image/webp"
            )
        )
        assertFalse(
            NavidromePlaceholderDetector.isPlaceholderResponse(
                "/rest/getCoverArt",
                "no-store",
                null,
                "Wed, 21 Oct 2015 07:28:00 GMT",
                "image/webp"
            )
        )
    }

    @Test
    fun ordinaryArtworkIsNotMistakenForPlaceholder() {
        assertFalse(
            NavidromePlaceholderDetector.isPlaceholderResponse(
                "/rest/getCoverArt",
                "public, max-age=315360000",
                null,
                null,
                "image/jpeg"
            )
        )
        assertFalse(
            NavidromePlaceholderDetector.isPlaceholderResponse(
                "/rest/getCoverArt",
                "no-store",
                null,
                null,
                "image/jpeg"
            )
        )
        assertFalse(
            NavidromePlaceholderDetector.isPlaceholderResponse(
                "/rest/stream",
                "no-store",
                null,
                null,
                "image/webp"
            )
        )
    }

    @Test
    fun onlyKnownPlaceholderSizesAreBuffered() {
        assertTrue(NavidromePlaceholderDetector.shouldBuffer(546))
        assertTrue(NavidromePlaceholderDetector.shouldBuffer(69_228))
        assertTrue(NavidromePlaceholderDetector.shouldBuffer(17_464))
        assertFalse(NavidromePlaceholderDetector.shouldBuffer(0))
        assertFalse(NavidromePlaceholderDetector.shouldBuffer(-1))
        assertFalse(NavidromePlaceholderDetector.shouldBuffer(150_000))
    }

    @Test
    fun knownPlaceholderDigestsAreRecognized() {
        assertTrue(NavidromePlaceholderDetector.isKnownDigest("521259c652af6d43cb124876106258d93b6b6f7fbe7ad2c73e8345875afe4dcf"))
        assertTrue(NavidromePlaceholderDetector.isKnownDigest("273a4dbd61dfbb0a12d5d8ffe780eb7a3d4d000bc9771c5411cc70ae4dfa8a1f"))
        assertTrue(NavidromePlaceholderDetector.isKnownDigest("b502979c464ab7a61f79bfd820e23b8dc3eeb36034b0722f7588972fd9fa7392"))
        assertFalse(NavidromePlaceholderDetector.isKnownDigest("0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun unknownPayloadsAreNotPlaceholders() {
        assertFalse(NavidromePlaceholderDetector.isKnownPlaceholder(ByteArray(0)))
        assertFalse(NavidromePlaceholderDetector.isKnownPlaceholder(ByteArray(17_464)))
        assertFalse(NavidromePlaceholderDetector.isKnownPlaceholder(null))
    }

    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            NavidromePlaceholderDetector.sha256Hex("abc".toByteArray())
        )
    }
}
