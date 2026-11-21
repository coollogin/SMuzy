package io.github.coollogin.smuzy.interfaces;

import androidx.annotation.Keep;

@Keep
public interface PlaylistCallback {
    default void onRenamed(String name) {}
}
