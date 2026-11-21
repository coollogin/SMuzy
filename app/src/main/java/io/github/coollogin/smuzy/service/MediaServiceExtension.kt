package io.github.coollogin.smuzy.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

interface MediaServiceExtension {
    fun handle(
        player: Player,
        item: MediaItem,
        queueTarget: MediaManager.QueueTarget
    ): Boolean
}

object MediaServiceExtensionRegistry {
    var handler: MediaServiceExtension? = null
}
