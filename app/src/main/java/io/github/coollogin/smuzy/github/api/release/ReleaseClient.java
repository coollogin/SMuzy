package io.github.coollogin.smuzy.github.api.release;

import android.util.Log;

import io.github.coollogin.smuzy.github.Github;
import io.github.coollogin.smuzy.github.GithubRetrofitClient;
import io.github.coollogin.smuzy.github.models.LatestRelease;

import retrofit2.Call;

public class ReleaseClient {
    private static final String TAG = "ReleaseClient";

    private final ReleaseService releaseService;

    public ReleaseClient(Github github) {
        this.releaseService = new GithubRetrofitClient(github).getRetrofit().create(ReleaseService.class);
    }

    public Call<LatestRelease> getLatestRelease() {
        Log.d(TAG, "getLatestRelease()");
        return releaseService.getLatestRelease(Github.getOwner(), Github.getRepo());
    }
}
