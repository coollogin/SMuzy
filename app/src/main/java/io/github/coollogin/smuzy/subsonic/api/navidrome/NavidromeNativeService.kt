package io.github.coollogin.smuzy.subsonic.api.navidrome

import io.github.coollogin.smuzy.subsonic.models.NavidromeLoginRequest
import io.github.coollogin.smuzy.subsonic.models.NavidromeLoginResponse
import io.github.coollogin.smuzy.subsonic.models.NavidromeSong
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface NavidromeNativeService {
    @POST("auth/login")
    fun login(@Body body: NavidromeLoginRequest): Call<NavidromeLoginResponse>

    @GET("api/song")
    fun songs(
        @Header("x-nd-authorization") authorization: String,
        @Query("_start") start: Int,
        @Query("_end") end: Int,
        @Query("_sort") sort: String,
    ): Call<List<NavidromeSong>>
}
