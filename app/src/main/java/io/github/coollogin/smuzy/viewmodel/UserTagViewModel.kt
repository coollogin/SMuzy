package io.github.coollogin.smuzy.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import io.github.coollogin.smuzy.model.UserTagCloudUiState
import io.github.coollogin.smuzy.repository.UserTagRepository
import io.github.coollogin.smuzy.subsonic.models.AlbumID3
import io.github.coollogin.smuzy.util.Preferences
import java.util.function.Consumer

class UserTagViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserTagRepository()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableLiveData<UserTagCloudUiState>()
    val state: LiveData<UserTagCloudUiState> = _state

    fun load(force: Boolean = false) {
        repository.load(
            serverId = Preferences.getServerId(),
            libraryId = Preferences.getActiveMusicFolderId(),
            force = force,
        ) { state -> mainHandler.post { _state.value = state } }
    }

    fun albumsForTag(tag: String, callback: Consumer<List<AlbumID3>>) {
        repository.albumsForTag(Preferences.getServerId(), tag) { albums ->
            mainHandler.post { callback.accept(albums) }
        }
    }
}
