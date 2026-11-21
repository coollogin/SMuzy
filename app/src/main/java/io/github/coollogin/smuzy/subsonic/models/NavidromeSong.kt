package io.github.coollogin.smuzy.subsonic.models

import androidx.annotation.Keep

/**
 * Song object as returned by Navidrome's native `/api/song` endpoint. Only the fields Stage 4
 * needs are declared; Gson ignores the rest. Custom tags live in [tags] keyed by their normalized
 * name (`user_tag` for a USER_TAG frame), which is the only place Navidrome exposes USER_TAG.
 */
@Keep
data class NavidromeSong(
    val id: String? = null,
    val albumId: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val artist: String? = null,
    val year: Int? = null,
    val libraryId: String? = null,
    val tags: Map<String, List<String>>? = null,
)

@Keep
data class NavidromeLoginRequest(
    val username: String,
    val password: String,
)

@Keep
data class NavidromeLoginResponse(
    val token: String? = null,
    val username: String? = null,
    val isAdmin: Boolean? = null,
)
