package io.github.coollogin.smuzy.repository

/**
 * Pure USER_TAG logic, kept independent of Android and of the network so the cloud rules in the
 * specification can be unit tested. Only the `user_tag` custom tag is read; genres, artists and
 * other metadata that Navidrome returns alongside it must never leak into the cloud.
 */
object UserTagAggregator {
    const val USER_TAG_KEY = "user_tag"

    data class TaggedSong(
        val songId: String,
        val albumId: String,
        val tags: List<String>,
    )

    data class TagCount(
        val tag: String,
        val songCount: Int,
    )

    /** Navidrome normalizes custom tag names to snake_case; tolerate spacing and casing anyway. */
    fun isUserTagKey(key: String): Boolean =
        key.filter { it.isLetterOrDigit() }.lowercase() == "usertag"

    fun extractUserTags(tags: Map<String, List<String>>?): List<String> {
        if (tags == null) return emptyList()

        val result = LinkedHashSet<String>()
        for ((key, values) in tags) {
            if (!isUserTagKey(key)) continue
            for (value in values) {
                val trimmed = value?.trim().orEmpty()
                if (trimmed.isNotEmpty()) result.add(trimmed)
            }
        }
        return result.toList()
    }

    /** One tag counts once per song even if the tag repeats inside that song. */
    fun buildTagCounts(songs: List<TaggedSong>): List<TagCount> {
        val counts = HashMap<String, Int>()
        for (song in songs) {
            for (tag in song.tags.toSet()) {
                if (tag.isBlank()) continue
                counts[tag] = (counts[tag] ?: 0) + 1
            }
        }

        return counts
            .map { TagCount(it.key, it.value) }
            .sortedWith(
                compareByDescending<TagCount> { it.songCount }
                    .thenBy { it.tag.lowercase() }
                    .thenBy { it.tag }
            )
    }

    /** Distinct albums that contain at least one song carrying [tag]. */
    fun albumIdsForTag(songs: List<TaggedSong>, tag: String): List<String> {
        val albumIds = LinkedHashSet<String>()
        for (song in songs) {
            if (song.tags.contains(tag) && song.albumId.isNotEmpty()) albumIds.add(song.albumId)
        }
        return albumIds.toList()
    }
}
