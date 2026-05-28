package com.vikas.torrentplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.vikas.torrentplayer.databinding.ActivityMainBinding;
import com.vikas.torrentplayer.utils.AppAutoUpdater;
import com.vikas.torrentplayer.utils.AppUpdateListener;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // No-op — service has already started; the notification just
                // won't show without permission, which is fine.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Ask for notification permission so the foreground service notification
        // (which shows download progress) is actually visible. Android 13+ only.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host != null) {
            NavController nav = host.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, nav);
        }

        // Apply system bar insets to the bottom nav so it isn't behind gesture bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bars.bottom);
            return insets;
        });

        checkForUpdate();
    }

    /** Fire-and-forget GitHub update check, then prompt the user if needed. */
    private void checkForUpdate() {
        AppAutoUpdater.checkForUpdates(this, new AppUpdateListener() {
            @Override
            public void onUpdateAvailable(String currentVersion, String latestVersion, String downloadUrl) {
                if (isFinishing() || isDestroyed()) return;
                showUpdateDialog(currentVersion, latestVersion, downloadUrl);
            }
            // onUpToDate / onError: silent — no need to interrupt the user.
        });
    }

    private void showUpdateDialog(String currentVersion, String latestVersion, String downloadUrl) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_dialog_title)
                .setMessage(getString(R.string.update_dialog_message, latestVersion, currentVersion))
                .setNegativeButton(R.string.update_dialog_later, null)
                .setPositiveButton(R.string.update_dialog_install, (d, w) ->
                        downloadWithProgress(downloadUrl, latestVersion))
                .show();
    }

    private void downloadWithProgress(String url, String version) {
        // Inline progress dialog with an indeterminate-then-determinate bar.
        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setIndeterminate(true);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad, pad, pad);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.update_downloading, 0))
                .setView(bar)
                .setCancelable(false)
                .show();

        AppAutoUpdater.downloadAndInstall(this, url, version, new AppUpdateListener() {
            @Override
            public void onUpdateAvailable(String c, String l, String u) { /* unused */ }

            @Override
            public void onDownloadProgress(int percent) {
                if (bar.isIndeterminate()) bar.setIndeterminate(false);
                bar.setProgressCompat(percent, true);
                dialog.setTitle(getString(R.string.update_downloading, percent));
            }
            @Override
            public void onDownloadComplete(File apk) {
                if (dialog.isShowing()) dialog.dismiss();
            }
            @Override
            public void onError(Throwable t) {
                if (dialog.isShowing()) dialog.dismiss();
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(MainActivity.this,
                        getString(R.string.update_download_failed, msg),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
