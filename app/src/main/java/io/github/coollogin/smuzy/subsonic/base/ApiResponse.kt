package io.github.coollogin.smuzy.subsonic.base

import androidx.annotation.Keep
import io.github.coollogin.smuzy.subsonic.models.ResponseStatus
import io.github.coollogin.smuzy.subsonic.models.SubsonicResponse
import com.google.gson.annotations.SerializedName

@Keep
class ApiResponse {
    @SerializedName("subsonic-response")
    var subsonicResponse: SubsonicResponse = SubsonicResponse().apply {
        status = ResponseStatus.FAILED
    }
}