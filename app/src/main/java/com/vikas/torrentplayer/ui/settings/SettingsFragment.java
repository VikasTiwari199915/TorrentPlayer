package com.vikas.torrentplayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.BuildConfig;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.AppAutoUpdater;
import com.vikas.torrentplayer.utils.AppUpdateListener;
import com.vikas.torrentplayer.utils.CacheCleaner;
import com.vikas.torrentplayer.utils.FormatUtils;
import com.vikas.torrentplayer.utils.PrefsManager;
import com.vikas.torrentplayer.utils.StoragePermissions;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private PrefsManager prefs;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        prefs = new PrefsManager(requireContext());

        Preference apiKey = findPreference(PrefsManager.KEY_API_KEY);
        if (apiKey != null) {
            apiKey.setOnPreferenceClickListener(p -> {
                showApiKeyDialog();
                return true;
            });
            refreshApiKeySummary();
        }

        Preference torboxKey = findPreference("pref_torbox_key");
        if (torboxKey != null) {
            refreshTorBoxSummary(torboxKey);
            torboxKey.setOnPreferenceClickListener(p -> { showTorBoxKeyDialog(torboxKey); return true; });
        }

        Preference torboxLib = findPreference("pref_torbox_library");
        if (torboxLib != null) {
            torboxLib.setOnPreferenceClickListener(p -> {
                if (!prefs.hasTorBoxKey()) {
                    Toast.makeText(requireContext(),
                            "Set your TorBox API key first", Toast.LENGTH_LONG).show();
                } else {
                    startActivity(new Intent(requireContext(),
                            com.vikas.torrentplayer.ui.torbox.TorBoxLibraryActivity.class));
                }
                return true;
            });
        }

        Preference version = findPreference("pref_version");
        if (version != null) {
            version.setSummary(BuildConfig.VERSION_NAME);
        }

        Preference saveDir = findPreference("pref_save_dir");
        if (saveDir != null) {
            refreshSaveDirSummary(saveDir);
            saveDir.setOnPreferenceClickListener(p -> {
                List<TorrentManager.VolumeInfo> vols = TorrentManager.get().getAvailableVolumes();
                if (vols.size() > 1) {
                    showVolumePicker(saveDir, vols);
                } else {
                    openStorageInFiles();
                }
                return true;
            });
        }

        Preference clearCache = findPreference("pref_clear_cache");
        if (clearCache != null) {
            clearCache.setOnPreferenceClickListener(p -> {
                showClearCacheDialog();
                return true;
            });
        }

        Preference checkUpdates = findPreference("pref_check_updates");
        if (checkUpdates != null) {
            checkUpdates.setSummary(getString(
                    R.string.pref_check_updates_summary_fmt, BuildConfig.VERSION_NAME));
            checkUpdates.setOnPreferenceClickListener(p -> {
                runManualUpdateCheck();
                return true;
            });
        }
    }

    /** Manual update check from settings: shows feedback for every outcome
     *  (the auto-check on launch is silent on up-to-date / error to avoid
     *  pestering the user). */
    private void runManualUpdateCheck() {
        Toast.makeText(requireContext(), "Checking…", Toast.LENGTH_SHORT).show();
        AppAutoUpdater.checkForUpdates(requireContext(), new AppUpdateListener() {
            @Override
            public void onUpdateAvailable(String currentVersion, String latestVersion, String downloadUrl) {
                if (!isAdded()) return;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.update_dialog_title)
                        .setMessage(getString(R.string.update_dialog_message, latestVersion, currentVersion))
                        .setNegativeButton(R.string.update_dialog_later, null)
                        .setPositiveButton(R.string.update_dialog_install, (d, w) -> {
                            // Delegate to MainActivity's existing download flow.
                            // Simpler: just kick off the download here.
                            AppAutoUpdater.downloadAndInstall(requireContext(),
                                    downloadUrl, latestVersion, new AppUpdateListener() {
                                        @Override
                                        public void onUpdateAvailable(String c, String l, String u) {}
                                        @Override
                                        public void onError(Throwable t) {
                                            if (!isAdded()) return;
                                            Toast.makeText(requireContext(),
                                                    getString(R.string.update_download_failed,
                                                            String.valueOf(t.getMessage())),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        })
                        .show();
            }
            @Override
            public void onUpToDate(String currentVersion) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        getString(R.string.update_up_to_date_fmt, currentVersion),
                        Toast.LENGTH_LONG).show();
            }
            @Override
            public void onError(Throwable t) {
                if (!isAdded()) return;
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(requireContext(),
                        getString(R.string.update_check_failed_fmt, msg),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshSaveDirSummary(Preference pref) {
        File dir = TorrentManager.get().getSaveDir();
        if (dir == null) { pref.setSummary("—"); return; }
        List<TorrentManager.VolumeInfo> vols = TorrentManager.get().getAvailableVolumes();
        TorrentManager.VolumeInfo current = null;
        for (TorrentManager.VolumeInfo v : vols) { if (v.isCurrent) { current = v; break; } }
        String path = dir.getAbsolutePath();
        if (current != null) {
            pref.setSummary(path + "\n"
                    + FormatUtils.humanBytes(current.free) + " free / "
                    + FormatUtils.humanBytes(current.total) + " total");
        } else {
            pref.setSummary(path);
        }
    }

    private void showVolumePicker(Preference saveDirPref, List<TorrentManager.VolumeInfo> vols) {
        boolean granted = StoragePermissions.hasAllFilesAccess();
        String[] labels = new String[vols.size()];
        int checkedIdx = 0;
        for (int i = 0; i < vols.size(); i++) {
            TorrentManager.VolumeInfo v = vols.get(i);
            String space = (!v.isAppDir && !granted)
                    ? "Needs permission — select to grant"
                    : FormatUtils.humanBytes(v.free) + " free / "
                            + FormatUtils.humanBytes(v.total) + " total";
            labels[i] = v.label + "\n" + space;
            if (v.isCurrent) checkedIdx = i;
        }
        final int[] selected = {checkedIdx};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_save_dir_title)
                .setSingleChoiceItems(labels, checkedIdx, (d, which) -> selected[0] = which)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_save, (d, w) -> {
                    TorrentManager.VolumeInfo chosen = vols.get(selected[0]);
                    if (!chosen.isAppDir && !StoragePermissions.hasAllFilesAccess()) {
                        promptAllFilesAccess();
                        return;
                    }
                    TorrentManager.get().switchVolume(chosen.root);
                    refreshSaveDirSummary(saveDirPref);
                })
                .show();
    }

    /** USB / SD writes need All-files access — send the user to the system toggle. */
    private void promptAllFilesAccess() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Storage permission needed")
                .setMessage("To download onto a USB drive or SD card, open this app's "
                        + "info screen, tap \"Files and media\" (or \"All files access\"), "
                        + "and choose \"Allow management of files\". Then come back and "
                        + "pick the volume again.")
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton("Open settings", (d, w) -> {
                    if (!StoragePermissions.openBestSettings(requireContext())) {
                        Toast.makeText(requireContext(),
                                "Couldn't open storage settings", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void openStorageInFiles() {
        File dir = TorrentManager.get().getSaveDir();
        if (dir == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    BuildConfig.APPLICATION_ID + ".fileprovider", dir);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "resource/folder");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Open folder"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), dir.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showClearCacheDialog() {
        long bytes = CacheCleaner.getCacheSize(requireContext());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_clear_cache_title)
                .setMessage(getString(R.string.clear_cache_dialog_message_fmt,
                        FormatUtils.humanBytes(bytes)))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.action_remove, (d, w) -> runClear())
                .show();
    }

    private void runClear() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long freed = CacheCleaner.clearAll(requireContext().getApplicationContext());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(),
                            getString(R.string.clear_cache_done_fmt,
                                    FormatUtils.humanBytes(freed)),
                            Toast.LENGTH_LONG).show());
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom + dp(72));
            return insets;
        });
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.dialog_api_key_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSingleLine();
        input.setText(prefs.getApiKey());

        FrameLayout container = new FrameLayout(requireContext());
        int pad = dp(24);
        container.setPadding(pad, dp(8), pad, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_api_key_title)
                .setView(container)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_save, (d, w) -> {
                    prefs.setApiKey(input.getText().toString());
                    refreshApiKeySummary();
                })
                .show();
    }

    private void refreshTorBoxSummary(Preference p) {
        String k = prefs.getTorBoxKey();
        if (k == null || k.isEmpty()) {
            p.setSummary("Not set — tap to add for full-speed downloads & streaming");
        } else {
            p.setSummary(k.length() <= 8 ? "••••••••"
                    : k.substring(0, 4) + "•••••" + k.substring(k.length() - 4));
        }
    }

    private void showTorBoxKeyDialog(Preference pref) {
        EditText input = new EditText(requireContext());
        input.setHint("TorBox API key");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSingleLine();
        input.setText(prefs.getTorBoxKey());
        FrameLayout container = new FrameLayout(requireContext());
        int pad = dp(24);
        container.setPadding(pad, dp(8), pad, 0);
        container.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("TorBox API key")
                .setMessage("Find it at torbox.app → Settings → API.")
                .setView(container)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_save, (d, w) -> {
                    prefs.setTorBoxKey(input.getText().toString());
                    refreshTorBoxSummary(pref);
                })
                .show();
    }

    private void refreshApiKeySummary() {
        Preference p = findPreference(PrefsManager.KEY_API_KEY);
        if (p == null) return;
        String key = prefs.getApiKey();
        if (key == null || key.isEmpty()) {
            p.setSummary(R.string.pref_api_key_not_set);
        } else {
            // Mask all but first 4 / last 4
            String masked;
            if (key.length() <= 8) {
                masked = "••••••••";
            } else {
                masked = key.substring(0, 4) + "•••••" + key.substring(key.length() - 4);
            }
            p.setSummary(masked);
        }
    }

    private int dp(int dp) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (dp * d);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, @Nullable String key) {
        if (PrefsManager.KEY_API_KEY.equals(key)) refreshApiKeySummary();
    }
}
