package io.github.coollogin.smuzy.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.github.coollogin.smuzy.interfaces.ScanCallback;
import io.github.coollogin.smuzy.repository.DirectoryRepository;
import io.github.coollogin.smuzy.repository.ScanRepository;
import io.github.coollogin.smuzy.repository.UserTagRepository;
import io.github.coollogin.smuzy.subsonic.models.MusicFolder;
import io.github.coollogin.smuzy.util.Preferences;

import java.util.List;

public class SettingViewModel extends AndroidViewModel {
    private static final String TAG = "SettingViewModel";

    private final ScanRepository scanRepository;
    private final DirectoryRepository directoryRepository;
    private final UserTagRepository userTagRepository;

    private final MutableLiveData<List<MusicFolder>> musicFolders = new MutableLiveData<>(null);

    public SettingViewModel(@NonNull Application application) {
        super(application);

        scanRepository = new ScanRepository();
        directoryRepository = new DirectoryRepository();
        userTagRepository = new UserTagRepository();
    }

    public LiveData<List<MusicFolder>> getMusicFolders(LifecycleOwner owner) {
        if (musicFolders.getValue() == null) {
            directoryRepository.getMusicFolders().observe(owner, musicFolders::postValue);
        }

        return musicFolders;
    }

    public void launchScan(ScanCallback callback) {
        // Tags are rescanned on the next Library visit; the server-side scan may change them.
        userTagRepository.invalidate(Preferences.getServerId());
        scanRepository.startScan(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                callback.onSuccess(isScanning, count);
            }
        });
    }

    public void getScanStatus(ScanCallback callback) {
        scanRepository.getScanStatus(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                callback.onSuccess(isScanning, count);
            }
        });
    }
}
