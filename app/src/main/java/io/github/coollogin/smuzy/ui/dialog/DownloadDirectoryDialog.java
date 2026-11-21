package io.github.coollogin.smuzy.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.fragment.app.DialogFragment;
import androidx.media3.common.util.UnstableApi;

import io.github.coollogin.smuzy.R;
import io.github.coollogin.smuzy.databinding.DialogDownloadDirectoryBinding;
import io.github.coollogin.smuzy.interfaces.DialogClickCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

@OptIn(markerClass = UnstableApi.class)
public class DownloadDirectoryDialog extends DialogFragment {
    public static final String REQUEST_KEY = "DownloadDirectoryDialog";
    public static final String KEY_DOWNLOAD_CONFIRMED = "download_confirmed";

    private DialogClickCallback dialogClickCallback;

    public DownloadDirectoryDialog() {
        this.dialogClickCallback = null;
    }

    public DownloadDirectoryDialog(DialogClickCallback dialogClickCallback) {
        this.dialogClickCallback = dialogClickCallback;
    }

    public void setDialogClickCallback(DialogClickCallback dialogClickCallback) {
        this.dialogClickCallback = dialogClickCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogDownloadDirectoryBinding bind = DialogDownloadDirectoryBinding.inflate(getLayoutInflater());

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(bind.getRoot())
                .setTitle(R.string.download_directory_dialog_title)
                .setPositiveButton(R.string.download_directory_dialog_positive_button, null)
                .setNegativeButton(R.string.download_directory_dialog_negative_button, null)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();
        setButtonAction();
    }

    private void setButtonAction() {
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) getDialog();

        if (dialog != null) {
            Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                Bundle result = new Bundle();
                result.putBoolean(KEY_DOWNLOAD_CONFIRMED, true);
                getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                if (dialogClickCallback != null) {
                    dialogClickCallback.onPositiveClick();
                }
                dialog.dismiss();
            });

            Button negativeButton = dialog.getButton(Dialog.BUTTON_NEGATIVE);
            negativeButton.setOnClickListener(v -> {
                if (dialogClickCallback != null) {
                    dialogClickCallback.onNegativeClick();
                }
                dialog.dismiss();
            });
        }
    }
}
