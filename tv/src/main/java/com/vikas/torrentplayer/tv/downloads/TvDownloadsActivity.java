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

            h.itemView.setOnClickListener(v -> {
                if (s == DownloadHandle.State.READY
                        || s == DownloadHandle.State.FINISHED) {
                    TvPlayerActivity.start(TvDownloadsActivity.this, handle.infoHash);
                } else {
                    TvPlayerActivity.start(TvDownloadsActivity.this, handle.infoHash);
                }
            });

            // D-pad menu shortcut: long-press OR press the 'menu' key on the
            // remote shows an inline action picker.
            h.itemView.setOnLongClickListener(v -> {
                showActionMenu(handle);
                return true;
            });
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

        labels.add("Play");
        actions.add(() -> TvPlayerActivity.start(this, h.infoHash));

        if (s == DownloadHandle.State.PAUSED) {
            labels.add("Resume");
            actions.add(() -> TorrentManager.get().resume(h.infoHash));
        } else if (s != DownloadHandle.State.FINISHED && s != DownloadHandle.State.ERROR) {
            labels.add("Pause");
            actions.add(() -> TorrentManager.get().pause(h.infoHash));
        }
        labels.add("Remove");
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
}
