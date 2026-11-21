package io.github.coollogin.smuzy.model

/**
 * Everything the Library USER_TAG sector needs to render. [tags] may accompany an error state:
 * that is stale cache kept visible while the retry affordance is shown.
 */
data class UserTagCloudUiState(
    val status: Status,
    val tags: List<UserTagCount> = emptyList(),
) {
    enum class Status {
        /** First load, nothing cached yet. */
        LOADING,
        LOADED,
        /** Scan completed and the library has no USER_TAG values. */
        EMPTY,
        /** Network/API/auth failure; [tags] may hold the previous successful result. */
        ERROR,
        /** Server has no Navidrome native API, so USER_TAG cannot be read. */
        UNSUPPORTED,
    }

    companion object {
        @JvmStatic
        fun loading(tags: List<UserTagCount> = emptyList()): UserTagCloudUiState =
            UserTagCloudUiState(Status.LOADING, tags)

        @JvmStatic
        fun loaded(tags: List<UserTagCount>): UserTagCloudUiState =
            UserTagCloudUiState(if (tags.isEmpty()) Status.EMPTY else Status.LOADED, tags)

        @JvmStatic
        fun error(tags: List<UserTagCount>): UserTagCloudUiState =
            UserTagCloudUiState(Status.ERROR, tags)

        @JvmStatic
        fun unsupported(): UserTagCloudUiState =
            UserTagCloudUiState(Status.UNSUPPORTED)
    }
}
