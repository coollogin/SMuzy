package io.github.coollogin.smuzy.interfaces;

import androidx.annotation.Keep;

@Keep
public interface StarCallback {
    default void onError() {}
    default void onSuccess() {}

    default void onRefused() {}
}
