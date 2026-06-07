package com.vikas.torrentplayer.tv;

// Platform AlertDialog — Leanback themes don't extend Theme.AppCompat so the
// androidx.appcompat version isn't available here.
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.AppAutoUpdater;
import com.vikas.torrentplayer.utils.AppUpdateListener;
import com.vikas.torrentplayer.utils.CacheCleaner;
import com.vikas.torrentplayer.utils.FormatUtils;
import com.vikas.torrentplayer.utils.PrefsManager;
import com.vikas.torrentplayer.utils.StoragePermissions;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * D-pad-friendly settings list: API key, current save folder, clear-cache.
 */
public class SettingsActivity extends FragmentActivity {

    private static final int ITEM_API_KEY = 0;
    private static final int ITEM_TMDB_KEY = 1;
    private static final int ITEM_TORBOX_KEY = 2;
    private static final int ITEM_TORBOX_LIBRARY = 3;
    private static final int ITEM_SAVE_DIR = 4;
    private static final int ITEM_STORAGE_ACCESS = 5;
    private static final int ITEM_DIAGNOSTICS = 6;
    private static final int ITEM_BACKDROP = 7;
    private static final int ITEM_CLEAR_CACHE = 8;
    private static final int ITEM_CHECK_UPDATES = 9;

    private PrefsManager prefs;
    private RowsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PrefsManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0820);
        root.setPadding(dp(48), dp(48), dp(48), dp(48));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        root.addView(title);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowsAdapter();
        list.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(24);
        list.setLayoutParams(lp);
        root.addView(list);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Permission may have been granted in the system settings screen.
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setHint("tc_…");
        input.setText(prefs.getApiKey());
        new AlertDialog.Builder(this)
                .setTitle("TorrentClaw API key")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.setApiKey(input.getText().toString());
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    private void showTorBoxKeyDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setHint("TorBox API key");
        input.setText(prefs.getTorBoxKey());
        new AlertDialog.Builder(this)
                .setTitle("TorBox API key")
                .setMessage("Find it at torbox.app → Settings → API. Enables the "
                        + "\"Download via TorBox\" option on each torrent.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.setTorBoxKey(input.getText().toString());
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    private void showTmdbCredentialDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setHint("TMDB read token or API key");
        input.setText(prefs.getTmdbCredential());
        new AlertDialog.Builder(this)
                .setTitle("TMDB API credential")
                .setMessage("Use either a TMDB v4 read token or a v3 API key. This powers show season and episode pickers.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.setTmdbCredential(input.getText().toString());
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    private void showSaveDirDialog() {
        List<TorrentManager.VolumeInfo> vols = TorrentManager.get().getAvailableVolumes();
        if (vols.size() > 1) {
            showVolumePicker(vols);
        } else {
            // Single volume — just show info.
            String path = TorrentManager.get().getSaveDir() != null
                    ? TorrentManager.get().getSaveDir().getAbsolutePath() : "—";
            String msg = path;
            if (!vols.isEmpty()) {
                TorrentManager.VolumeInfo v = vols.get(0);
                msg += "\n\n" + FormatUtils.humanBytes(v.free) + " free / "
                        + FormatUtils.humanBytes(v.total) + " total";
            }
            new AlertDialog.Builder(this)
                    .setTitle("Save folder")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showVolumePicker(List<TorrentManager.VolumeInfo> vols) {
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
        new AlertDialog.Builder(this)
                .setTitle("Choose save volume")
                .setSingleChoiceItems(labels, checkedIdx, (d, which) -> selected[0] = which)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    TorrentManager.VolumeInfo chosen = vols.get(selected[0]);
                    if (!chosen.isAppDir && !StoragePermissions.hasAllFilesAccess()) {
                        promptAllFilesAccess();
                        return;
                    }
                    TorrentManager.get().switchVolume(chosen.root);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Saving to " + chosen.label,
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showDiagnostics() {
        String dump = TorrentManager.get().getStorageDiagnostics();
        TextView tv = new TextView(this);
        tv.setText(dump);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setPadding(dp(24), dp(16), dp(24), dp(16));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Storage diagnostics")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    /** USB / SD writes need All-files access — send the user to the system toggle. */
    private void promptAllFilesAccess() {
        new AlertDialog.Builder(this)
                .setTitle("Storage permission needed")
                .setMessage("To download onto a USB drive or SD card, open this app's "
                        + "info screen, tap \"Files and media\" (or \"All files access\"), "
                        + "and choose \"Allow management of files\". Then come back and "
                        + "pick the volume again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open settings", (d, w) -> {
                    if (!StoragePermissions.openBestSettings(this)) {
                        Toast.makeText(this, "Couldn't open storage settings",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showClearCacheDialog() {
        long bytes = CacheCleaner.getCacheSize(this);
        new AlertDialog.Builder(this)
                .setTitle("Clear downloads & cache")
                .setMessage("This will delete every downloaded torrent and clear ~"
                        + FormatUtils.humanBytes(bytes)
                        + " of cached data. Settings and the API key will be kept. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> runClear())
                .show();
    }

    private void runClear() {
        Toast.makeText(this, "Clearing…", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            long freed = CacheCleaner.clearAll(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                adapter.notifyDataSetChanged();
                Toast.makeText(SettingsActivity.this,
                        "Freed " + FormatUtils.humanBytes(freed),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void runManualUpdateCheck() {
        Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show();
        AppAutoUpdater.checkForUpdates(this, new AppUpdateListener() {
            @Override
            public void onUpdateAvailable(String currentVersion, String latestVersion, String downloadUrl) {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Update available")
                        .setMessage("New: " + latestVersion + "\nYou: " + currentVersion
                                + "\n\nDownload and install?")
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Install", (d, w) ->
                                AppAutoUpdater.downloadAndInstall(SettingsActivity.this,
                                        downloadUrl, latestVersion, new AppUpdateListener() {
                                            @Override
                                            public void onUpdateAvailable(String c, String l, String u) {}
                                            @Override
                                            public void onError(Throwable t) {
                                                if (isFinishing() || isDestroyed()) return;
                                                Toast.makeText(SettingsActivity.this,
                                                        "Download failed: " + t.getMessage(),
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        }))
                        .show();
            }
            @Override
            public void onUpToDate(String currentVersion) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(SettingsActivity.this,
                        "You're on the latest version (" + currentVersion + ")",
                        Toast.LENGTH_LONG).show();
            }
            @Override
            public void onError(Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(SettingsActivity.this,
                        "Update check failed: " + msg,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.VH> {
        private final List<Integer> rows = Arrays.asList(
                ITEM_API_KEY, ITEM_TMDB_KEY, ITEM_TORBOX_KEY, ITEM_TORBOX_LIBRARY, ITEM_SAVE_DIR,
                ITEM_STORAGE_ACCESS, ITEM_DIAGNOSTICS, ITEM_BACKDROP,
                ITEM_CLEAR_CACHE, ITEM_CHECK_UPDATES);

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            v.setBackgroundResource(R.drawable.tv_focus_row);
            v.setFocusable(true);
            v.setPadding(dp(16), dp(16), dp(16), dp(16));
            ((TextView) v.findViewById(android.R.id.text1)).setTextColor(0xFFFFFFFF);
            ((TextView) v.findViewById(android.R.id.text2)).setTextColor(0xFFCCCCCC);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            int id = rows.get(position);
            TextView t1 = h.itemView.findViewById(android.R.id.text1);
            TextView t2 = h.itemView.findViewById(android.R.id.text2);
            switch (id) {
                case ITEM_API_KEY:
                    t1.setText("API key");
                    String key = prefs.getApiKey();
                    t2.setText(key == null || key.isEmpty() ? "Not set" : maskKey(key));
                    h.itemView.setOnClickListener(v -> showApiKeyDialog());
                    break;
                case ITEM_TMDB_KEY:
                    t1.setText("TMDB API credential");
                    String tmdb = prefs.getTmdbCredential();
                    t2.setText(tmdb == null || tmdb.isEmpty()
                            ? "Not set — enables season and episode pickers"
                            : maskKey(tmdb));
                    h.itemView.setOnClickListener(v -> showTmdbCredentialDialog());
                    break;
                case ITEM_TORBOX_KEY:
                    t1.setText("TorBox API key");
                    String tb = prefs.getTorBoxKey();
                    t2.setText(tb == null || tb.isEmpty()
                            ? "Not set — enables full-speed \"Download via TorBox\""
                            : maskKey(tb));
                    h.itemView.setOnClickListener(v -> showTorBoxKeyDialog());
                    break;
                case ITEM_TORBOX_LIBRARY:
                    t1.setText("TorBox library");
                    t2.setText("Browse, stream, download or delete torrents in your account");
                    h.itemView.setOnClickListener(v -> {
                        if (!prefs.hasTorBoxKey()) {
                            Toast.makeText(SettingsActivity.this,
                                    "Set your TorBox API key first", Toast.LENGTH_LONG).show();
                            return;
                        }
                        startActivity(new android.content.Intent(SettingsActivity.this,
                                com.vikas.torrentplayer.tv.torbox.TvTorBoxLibraryActivity.class));
                    });
                    break;
                case ITEM_SAVE_DIR:
                    t1.setText("Save folder");
                    String path = TorrentManager.get().getSaveDir() != null
                            ? TorrentManager.get().getSaveDir().getAbsolutePath()
                            : "—";
                    List<TorrentManager.VolumeInfo> vols = TorrentManager.get().getAvailableVolumes();
                    TorrentManager.VolumeInfo cur = null;
                    for (TorrentManager.VolumeInfo v : vols) { if (v.isCurrent) { cur = v; break; } }
                    if (cur != null) {
                        t2.setText(path + "  ·  "
                                + FormatUtils.humanBytes(cur.free) + " free / "
                                + FormatUtils.humanBytes(cur.total));
                    } else {
                        t2.setText(path);
                    }
                    h.itemView.setOnClickListener(v -> showSaveDirDialog());
                    break;
                case ITEM_STORAGE_ACCESS:
                    t1.setText("Storage access (USB / SD)");
                    t2.setText(StoragePermissions.hasAllFilesAccess()
                            ? "Granted — external drives are usable"
                            : "Not granted — tap to enable for USB drives");
                    h.itemView.setOnClickListener(v -> promptAllFilesAccess());
                    break;
                case ITEM_DIAGNOSTICS:
                    t1.setText("Storage diagnostics");
                    t2.setText("Show what the system reports about drives");
                    h.itemView.setOnClickListener(v -> showDiagnostics());
                    break;
                case ITEM_BACKDROP:
                    t1.setText("Show backdrop art");
                    t2.setText(prefs.isTvBackdropEnabled()
                            ? "On — full-screen art on the details screen"
                            : "Off — plain background (lighter on weak boxes)");
                    h.itemView.setOnClickListener(v -> {
                        prefs.setTvBackdropEnabled(!prefs.isTvBackdropEnabled());
                        notifyDataSetChanged();
                    });
                    break;
                case ITEM_CLEAR_CACHE:
                    t1.setText("Clear downloads & cache");
                    long bytes = CacheCleaner.getCacheSize(h.itemView.getContext());
                    t2.setText("Free " + FormatUtils.humanBytes(bytes)
                            + " — settings will be kept");
                    h.itemView.setOnClickListener(v -> showClearCacheDialog());
                    break;
                case ITEM_CHECK_UPDATES:
                    t1.setText("Check for updates");
                    t2.setText("You're on " + AppAutoUpdater.getAppVersionName(h.itemView.getContext()));
                    h.itemView.setOnClickListener(v -> runManualUpdateCheck());
                    break;
            }
        }

        private String maskKey(String k) {
            if (k.length() <= 8) return "••••••••";
            return k.substring(0, 4) + "•••••" + k.substring(k.length() - 4);
        }

        @Override public int getItemCount() { return rows.size(); }

        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
