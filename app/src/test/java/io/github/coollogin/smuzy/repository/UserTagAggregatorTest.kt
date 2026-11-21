package io.github.coollogin.smuzy.repository

import io.github.coollogin.smuzy.repository.UserTagAggregator.TaggedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserTagAggregatorTest {

    @Test
    fun extractionReadsOnlyUserTagValues() {
        val tags = mapOf(
            "user_tag" to listOf("Italy", "Disco"),
            "genre" to listOf("Pop"),
            "artist" to listOf("Tiziano Ferro"),
            "grouping" to listOf("Dance"),
        )

        assertEquals(listOf("Italy", "Disco"), UserTagAggregator.extractUserTags(tags))
    }

    @Test
    fun extractionIsTolerantAboutKeyShape() {
        assertTrue(UserTagAggregator.isUserTagKey("USER_TAG"))
        assertTrue(UserTagAggregator.isUserTagKey("usertag"))
        assertTrue(UserTagAggregator.isUserTagKey("user tag"))
        assertFalse(UserTagAggregator.isUserTagKey("user_tag_extra"))
        assertFalse(UserTagAggregator.isUserTagKey("genre"))
    }

    @Test
    fun extractionHandlesMissingDuplicateAndBlankValues() {
        assertEquals(emptyList<String>(), UserTagAggregator.extractUserTags(null))
        assertEquals(emptyList<String>(), UserTagAggregator.extractUserTags(emptyMap()))

        val tags = mapOf("user_tag" to listOf(" Italy ", "Italy", "", "  ", "Disco"))
        assertEquals(listOf("Italy", "Disco"), UserTagAggregator.extractUserTags(tags))
    }

    @Test
    fun countsFollowTheSpecificationExample() {
        val songs = specificationExample()

        val counts = UserTagAggregator.buildTagCounts(songs)
        assertEquals(
            listOf(
                UserTagAggregator.TagCount("Italy", 3),
                UserTagAggregator.TagCount("Disco", 2),
                UserTagAggregator.TagCount("Soundtrack", 2),
            ),
            counts,
        )
    }

    @Test
    fun repeatedTagInsideOneSongCountsOnce() {
        val songs = listOf(
            TaggedSong("t1", "A", listOf("Italy", "Italy")),
            TaggedSong("t2", "A", listOf("Italy")),
        )

        assertEquals(listOf(UserTagAggregator.TagCount("Italy", 2)), UserTagAggregator.buildTagCounts(songs))
    }

    @Test
    fun albumsAreReturnedOncePerAlbum() {
        val songs = specificationExample()

        assertEquals(listOf("A", "B"), UserTagAggregator.albumIdsForTag(songs, "Italy"))
        assertEquals(listOf("A", "C"), UserTagAggregator.albumIdsForTag(songs, "Soundtrack"))
        assertEquals(listOf("B", "D"), UserTagAggregator.albumIdsForTag(songs, "Disco"))
        assertEquals(emptyList<String>(), UserTagAggregator.albumIdsForTag(songs, "Children"))
    }

    private fun specificationExample(): List<TaggedSong> = listOf(
        TaggedSong("A1", "A", listOf("Italy")),
        TaggedSong("A2", "A", listOf("Italy")),
        TaggedSong("A3", "A", listOf("Soundtrack")),
        TaggedSong("B1", "B", listOf("Italy")),
        TaggedSong("B2", "B", listOf("Disco")),
        TaggedSong("C1", "C", listOf("Soundtrack")),
        TaggedSong("D1", "D", listOf("Disco")),
    )
}
