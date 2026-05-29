package com.vikas.torrentplayer.tv.downloads;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.tv.R;
import com.vikas.torrentplayer.tv.player.TvPlayerActivity;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * D-pad-friendly downloads list. OK plays, Long-press (or menu key) shows a
 * compact action menu (pause/resume/remove). Each row is focusable so the
 * remote can navigate naturally.
 */
public class TvDownloadsActivity extends FragmentActivity {

    private DownloadsAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_downloads);
        RecyclerView list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);

        adapter = new DownloadsAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        TorrentManager.get().downloads().observe(this, items -> {
            List<DownloadHandle> safe = items == null ? new ArrayList<>() : items;
            adapter.submit(safe);
            empty.setVisibility(safe.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.VH> {
        private final List<DownloadHandle> items = new ArrayList<>();

        void submit(List<DownloadHandle> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tv_download, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DownloadHandle handle = items.get(position);
            h.title.setText(handle.title);

            DownloadHandle.Progress p = handle.progress.getValue();
            DownloadHandle.State s = handle.state.getValue();
            int pct = p == null ? 0 : p.percent;
            String state = s == null ? "—" : s.name();
            String speed = p == null ? "" : FormatUtils.humanSpeed(p.downloadSpeed);
            h.meta.setText(state + " · " + pct + "%" + (speed.isEmpty() ? "" : " · " + speed));
            h.progress.setProgress(pct);

            // Single OK click opens the action menu — gives the user explicit
            // Play / Pause / Resume / Details / Remove choices instead of
            // having to discover the hidden long-press. Long-press still works
            // as a power-user shortcut.
            h.itemView.setOnClickListener(v -> showActionMenu(handle));
            h.itemView.setOnLongClickListener(v -> { showActionMenu(handle); return true; });
            h.itemView.setOnKeyListener((view, code, ev) -> {
                if (ev.getAction() == KeyEvent.ACTION_DOWN
                        && (code == KeyEvent.KEYCODE_MENU
                            || code == KeyEvent.KEYCODE_BUTTON_Y)) {
                    showActionMenu(handle);
                    return true;
                }
                return false;
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, meta;
            final ProgressBar progress;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.title);
                meta = v.findViewById(R.id.meta);
                progress = v.findViewById(R.id.progress);
            }
        }
    }

    /** Inline action menu via AlertDialog — keeps the implementation simple
     *  and remains d-pad navigable. */
    private void showActionMenu(DownloadHandle h) {
        DownloadHandle.State s = h.state.getValue();
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // Play is always available — the player itself handles the partial /
        // not-yet-playable case with its loading overlay.
        labels.add("Play");
        actions.add(() -> TvPlayerActivity.start(this, h.infoHash));

        if (s == DownloadHandle.State.PAUSED
                || s == DownloadHandle.State.ERROR
                || s == DownloadHandle.State.FINISHED) {
            // FINISHED gets "Resume" too in case the user wants to seed/recheck;
            // libtorrent will just verify and report 100% again.
            labels.add("Resume");
            actions.add(() -> TorrentManager.get().resume(h.infoHash));
        } else {
            labels.add("Pause");
            actions.add(() -> TorrentManager.get().pause(h.infoHash));
        }

        labels.add("Details");
        actions.add(() -> showDetailsDialog(h));

        labels.add("Remove (delete files)");
        actions.add(() -> {
            TorrentManager.get().remove(h.infoHash, true);
            Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
        });

        new android.app.AlertDialog.Builder(this)
                .setTitle(h.title)
                .setItems(labels.toArray(new CharSequence[0]),
                        (d, which) -> actions.get(which).run())
                .show();
    }

    /** Per-download info dialog — covers the TV's missing details screen
     *  with the essentials: status, progress, speed, size, video path. */
    private void showDetailsDialog(DownloadHandle h) {
        DownloadHandle.State s = h.state.getValue();
        DownloadHandle.Progress p = h.progress.getValue();
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(s == null ? "—" : s.name()).append('\n');
        sb.append("Size: ").append(FormatUtils.humanBytes(h.sizeBytes)).append('\n');
        if (h.quality != null) sb.append("Quality: ").append(h.quality).append('\n');
        sb.append("Progress: ").append(p == null ? 0 : p.percent).append("%\n");
        if (p != null) {
            sb.append("Speed: ").append(FormatUtils.humanSpeed(p.downloadSpeed)).append('\n');
            sb.append("Seeders: ").append(p.seeders).append('\n');
            sb.append("Head+tail buffer: ").append(p.bufferProgress).append("%\n");
        }
        if (h.videoFile.getValue() != null) {
            sb.append("\nFile:\n").append(h.videoFile.getValue().getAbsolutePath());
        }
        if (h.errorMessage.getValue() != null) {
            sb.append("\n\nError: ").append(h.errorMessage.getValue());
        }
        sb.append("\n\nInfoHash: ").append(h.infoHash);

        new android.app.AlertDialog.Builder(this)
                .setTitle(h.title)
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
