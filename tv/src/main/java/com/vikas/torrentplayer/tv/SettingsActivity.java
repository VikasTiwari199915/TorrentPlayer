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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * D-pad-friendly settings list: API key, current save folder, clear-cache.
 */
public class SettingsActivity extends FragmentActivity {

    private static final int ITEM_API_KEY = 0;
    private static final int ITEM_SAVE_DIR = 1;
    private static final int ITEM_CLEAR_CACHE = 2;
    private static final int ITEM_CHECK_UPDATES = 3;

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
        String[] labels = new String[vols.size()];
        int checkedIdx = 0;
        for (int i = 0; i < vols.size(); i++) {
            TorrentManager.VolumeInfo v = vols.get(i);
            labels[i] = v.label + "\n"
                    + FormatUtils.humanBytes(v.free) + " free / "
                    + FormatUtils.humanBytes(v.total) + " total";
            if (v.isCurrent) checkedIdx = i;
        }
        final int[] selected = {checkedIdx};
        new AlertDialog.Builder(this)
                .setTitle("Choose save volume")
                .setSingleChoiceItems(labels, checkedIdx, (d, which) -> selected[0] = which)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    TorrentManager.get().switchVolume(vols.get(selected[0]).root);
                    adapter.notifyDataSetChanged();
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
                ITEM_API_KEY, ITEM_SAVE_DIR, ITEM_CLEAR_CACHE, ITEM_CHECK_UPDATES);

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
