package com.vikas.torrentplayer.ui.downloads;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.ItemDownloadBinding;
import com.vikas.torrentplayer.torbox.TorBoxDownload;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.utils.FormatUtils;
import java.util.Objects;

public class TorBoxDownloadsAdapter extends ListAdapter<TorBoxDownload, TorBoxDownloadsAdapter.VH> {
    public interface Listener {
        void onDetails(TorBoxDownload download);
        void onPlay(TorBoxDownload download);
        void onPauseToggle(TorBoxDownload download);
        void onRemove(TorBoxDownload download);
    }
    private final Listener listener;
    public TorBoxDownloadsAdapter(Listener listener) { super(DIFF); this.listener = listener; }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new VH(ItemDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull VH holder, int position) { holder.bind(getItem(position)); }
    @Override public void onBindViewHolder(@NonNull VH holder, int position, @NonNull java.util.List<Object> payloads) {
        if (payloads.isEmpty()) holder.bind(getItem(position)); else holder.progress(getItem(position));
    }
    class VH extends RecyclerView.ViewHolder {
        final ItemDownloadBinding b;
        TorBoxDownload download;
        VH(ItemDownloadBinding b) {
            super(b.getRoot()); this.b = b;
            b.getRoot().setOnClickListener(v -> listener.onDetails(download));
            b.btnPlay.setOnClickListener(v -> listener.onPlay(download));
            b.btnPause.setOnClickListener(v -> listener.onPauseToggle(download));
            b.btnRemove.setOnClickListener(v -> listener.onRemove(download));
        }
        void bind(TorBoxDownload d) {
            Glide.with(b.poster).load(d.posterUrl).placeholder(R.drawable.placeholder_poster)
                    .error(R.drawable.placeholder_poster).into(b.poster);
            b.title.setText(d.title);
            progress(d);
        }
        void progress(TorBoxDownload d) {
            download = d;
            b.meta.setText("TorBox · " + d.files.size() + " files · "
                    + FormatUtils.humanBytes(d.downloaded) + " / " + FormatUtils.humanBytes(d.size));
            b.progressText.setText(status(d));
            b.progress.setProgressCompat(d.percent, false);
            boolean ready = d.files.stream().anyMatch(f -> f.state == TorBoxManager.State.DONE && isVideo(f.fileName));
            b.btnPlay.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
            b.btnPause.setVisibility(d.state == TorBoxManager.State.DONE ? View.INVISIBLE : View.VISIBLE);
            boolean active = d.state == TorBoxManager.State.DOWNLOADING;
            b.btnPause.setIconResource(active ? R.drawable.rounded_pause_24 : R.drawable.rounded_play_arrow_24);
            b.btnPause.setContentDescription(active ? "Pause download" : "Resume or retry download");
        }
    }
    public static boolean isVideo(String name) {
        return name != null && name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mkv|mp4|m4v|avi|webm|mov|ts|m2ts|mpeg|mpg)");
    }
    public static String status(TorBoxDownload d) {
        switch (d.state) {
            case DOWNLOADING: return "Downloading · " + d.percent + "% · " + FormatUtils.humanSpeed(d.speed);
            case PAUSED: return "Paused · " + d.percent + "%";
            case DONE: return "Finished";
            default: return "Some files need attention";
        }
    }
    private static final DiffUtil.ItemCallback<TorBoxDownload> DIFF = new DiffUtil.ItemCallback<TorBoxDownload>() {
        @Override public boolean areItemsTheSame(@NonNull TorBoxDownload a, @NonNull TorBoxDownload b) { return a.id == b.id; }
        @Override public boolean areContentsTheSame(@NonNull TorBoxDownload a, @NonNull TorBoxDownload b) {
            return Objects.equals(a.state, b.state) && a.downloaded == b.downloaded && a.size == b.size
                    && a.speed == b.speed && a.files.size() == b.files.size()
                    && Objects.equals(a.title, b.title) && Objects.equals(a.posterUrl, b.posterUrl);
        }
        @Override public Object getChangePayload(@NonNull TorBoxDownload a, @NonNull TorBoxDownload b) {
            return Objects.equals(a.title, b.title) && Objects.equals(a.posterUrl, b.posterUrl) ? Boolean.TRUE : null;
        }
    };
}
