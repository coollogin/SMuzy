package io.github.coollogin.smuzy.model

/**
 * The server a request is going to, as passed to a
 * [HeaderSource][io.github.coollogin.smuzy.util.HeaderSource].
 */
data class ServerContext(
    /** Addresses of the server (public, local, in use). Headers are only sent to these origins. */
    val addresses: List<String?>,
    /** The user's raw custom headers text, see [CustomHeaders][io.github.coollogin.smuzy.util.CustomHeaders]. */
    val customHeaders: String?,
)
