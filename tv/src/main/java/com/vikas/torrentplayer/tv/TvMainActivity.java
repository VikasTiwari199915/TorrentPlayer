package com.vikas.torrentplayer.tv;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.vikas.torrentplayer.tv.discover.DiscoverBrowseFragment;
import com.vikas.torrentplayer.utils.AppAutoUpdater;
import com.vikas.torrentplayer.utils.AppUpdateListener;

import java.io.File;

/**
 * Single-activity host for the leanback {@link DiscoverBrowseFragment}.
 * Also kicks off the GitHub update check on startup.
 */
public class TvMainActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_browse_fragment, new DiscoverBrowseFragment())
                    .commit();
        }
        checkForUpdate();
    }

    private void checkForUpdate() {
        AppAutoUpdater.checkForUpdates(this, new AppUpdateListener() {
            @Override
            public void onUpdateAvailable(String currentVersion, String latestVersion, String downloadUrl) {
                if (isFinishing() || isDestroyed()) return;
                showUpdateDialog(currentVersion, latestVersion, downloadUrl);
            }
        });
    }

    private void showUpdateDialog(String currentVersion, String latestVersion, String downloadUrl) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_dialog_title)
                .setMessage(getString(R.string.update_dialog_message, latestVersion, currentVersion))
                .setNegativeButton(R.string.update_dialog_later, null)
                .setPositiveButton(R.string.update_dialog_install, (d, w) ->
                        downloadWithProgress(downloadUrl, latestVersion))
                .show();
    }

    private void downloadWithProgress(String url, String version) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_downloading, 0))
                .setView(bar)
                .setCancelable(false)
                .show();

        AppAutoUpdater.downloadAndInstall(this, url, version, new AppUpdateListener() {
            @Override
            public void onUpdateAvailable(String c, String l, String u) { /* unused */ }

            @Override
            public void onDownloadProgress(int percent) {
                bar.setProgress(percent);
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
                Toast.makeText(TvMainActivity.this,
                        getString(R.string.update_download_failed, msg),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
