package io.github.coollogin.smuzy.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import io.github.coollogin.smuzy.App;
import io.github.coollogin.smuzy.github.models.LatestRelease;
import io.github.coollogin.smuzy.interfaces.SystemCallback;
import io.github.coollogin.smuzy.subsonic.Subsonic;
import io.github.coollogin.smuzy.subsonic.base.ApiResponse;
import io.github.coollogin.smuzy.subsonic.models.OpenSubsonicExtension;
import io.github.coollogin.smuzy.subsonic.models.ResponseStatus;
import io.github.coollogin.smuzy.subsonic.models.SubsonicResponse;
import io.github.coollogin.smuzy.util.Preferences;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SystemRepository {
    private static final String TAG = "SystemRepository";

    public void checkUserCredential(SystemCallback callback) {
        checkUserCredential(App.getSubsonicClientInstance(false), callback);
    }

    // Takes the client so a server can be reached before anything about it is saved.
    public void checkUserCredential(Subsonic client, SystemCallback callback) {
        client
                .getSystemClient()
                .ping()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull retrofit2.Response<ApiResponse> response) {
                        if (response.body() != null) {
                            if (response.body().getSubsonicResponse().getStatus().equals(ResponseStatus.FAILED)) {
                                io.github.coollogin.smuzy.subsonic.models.Error apiError = response.body().getSubsonicResponse().getError();
                                callback.onError(new Exception(apiError != null ? apiError.getCode() + " - " + apiError.getMessage() : "Unknown server error"));
                            } else if (response.body().getSubsonicResponse().getStatus().equals(ResponseStatus.OK)) {
                                String password = response.raw().request().url().queryParameter("p");
                                String token = response.raw().request().url().queryParameter("t");
                                String salt = response.raw().request().url().queryParameter("s");
                                callback.onSuccess(password, token, salt);
                            } else {
                                callback.onError(new Exception("Empty response"));
                            }
                        } else {
                            callback.onError(new Exception(String.valueOf(response.code())));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        // Retrofit sends a body it cannot read here too, so a server answering
                        // something that is not Subsonic would otherwise read as a network fault.
                        Exception failure = new Exception(t.getMessage());
                        if (t instanceof IOException) {
                            callback.onNetworkFailure(failure);
                        } else {
                            callback.onError(failure);
                        }
                    }
                });
    }

    public MutableLiveData<SubsonicResponse> ping() {
        return ping(App.getSubsonicClientInstance(false));
    }

    public MutableLiveData<SubsonicResponse> pingLocalAddress() {
        return ping(App.getSubsonicClientInstance(Preferences.getLocalAddress()));
    }

    private MutableLiveData<SubsonicResponse> ping(Subsonic client) {
        MutableLiveData<SubsonicResponse> pingResult = new MutableLiveData<>();

        client
                .getSystemClient()
                .ping()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            pingResult.postValue(response.body().getSubsonicResponse());
                        } else {
                            pingResult.postValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        pingResult.postValue(null);
                    }
                });

        return pingResult;
    }

    public MutableLiveData<List<OpenSubsonicExtension>> getOpenSubsonicExtensions() {
        MutableLiveData<List<OpenSubsonicExtension>> extensionsResult = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSystemClient()
                .getOpenSubsonicExtensions()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            extensionsResult.postValue(response.body().getSubsonicResponse().getOpenSubsonicExtensions());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        extensionsResult.postValue(null);
                    }
                });

        return extensionsResult;
    }

    public MutableLiveData<LatestRelease> checkUpdate() {
        MutableLiveData<LatestRelease> latestRelease = new MutableLiveData<>();

        App.getGithubClientInstance()
                .getReleaseClient()
                .getLatestRelease()
                .enqueue(new Callback<LatestRelease>() {
                    @Override
                    public void onResponse(@NonNull Call<LatestRelease> call, @NonNull Response<LatestRelease> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            latestRelease.postValue(response.body());
                        } else {
                            Log.w(TAG, "Update check failed: HTTP " + response.code());
                            latestRelease.postValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LatestRelease> call, @NonNull Throwable t) {
                        Log.w(TAG, "Update check failed", t);
                        latestRelease.postValue(null);
                    }
                });

        return latestRelease;
    }
}
