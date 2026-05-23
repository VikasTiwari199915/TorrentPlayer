package com.vikas.torrentplayer.ui.downloads;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.ItemDownloadBinding;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.HashMap;
import java.util.Map;

public class DownloadsAdapter extends ListAdapter<DownloadHandle, DownloadsAdapter.VH> {

    public interface Listener {
        void onPlay(DownloadHandle h);
        void onRemove(DownloadHandle h);
        void onPauseToggle(DownloadHandle h);
    }

    private final LifecycleOwner lifecycleOwner;
    private final Listener listener;

    /** Per-handle observers so we can detach when a row is rebound to another handle. */
    private final Map<VH, DownloadHandle> bound = new HashMap<>();

    public DownloadsAdapter(LifecycleOwner owner, Listener listener) {
        super(DIFF);
        this.lifecycleOwner = owner;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDownloadBinding b = ItemDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DownloadHandle h = getItem(position);
        holder.bind(h);
        bound.put(holder, h);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        DownloadHandle prev = bound.remove(holder);
        if (prev != null) {
            prev.state.removeObservers(lifecycleOwner);
            prev.progress.removeObservers(lifecycleOwner);
        }
    }

    public class VH extends RecyclerView.ViewHolder {
        private final ItemDownloadBinding b;

        VH(ItemDownloadBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(DownloadHandle h) {
            // Tap on the row (anywhere outside the action buttons) opens the
            // download details screen.
            b.getRoot().setOnClickListener(v -> DownloadDetailsActivity.start(b.getRoot().getContext(), h.infoHash));

            b.title.setText(h.title);
            String meta = (h.quality != null ? h.quality + " · " : "") + FormatUtils.humanBytes(h.sizeBytes);
            b.meta.setText(meta);

            Glide.with(b.poster)
                    .load(h.posterUrl)
                    .placeholder(R.drawable.placeholder_poster)
                    .error(R.drawable.placeholder_poster)
                    .into(b.poster);

            // Detach previous observers (safety) then attach for this handle
            h.state.removeObservers(lifecycleOwner);
            h.progress.removeObservers(lifecycleOwner);

            h.state.observe(lifecycleOwner, state -> updateText(h));
            h.progress.observe(lifecycleOwner, p -> {
                b.progress.setProgressCompat(p == null ? 0 : p.percent, true);
                updateText(h);
            });

            b.btnPlay.setOnClickListener(v -> {
                if (listener != null) listener.onPlay(h);
            });
            b.btnRemove.setOnClickListener(v -> {
                if (listener != null) listener.onRemove(h);
            });
            b.btnPause.setOnClickListener(v -> {
                if (listener != null) listener.onPauseToggle(h);
            });
        }

        private void updateText(DownloadHandle h) {
            DownloadHandle.Progress p = h.progress.getValue();
            DownloadHandle.State s = h.state.getValue();
            String statusLabel;
            switch (s == null ? DownloadHandle.State.STARTING : s) {
                case READY:
                case BUFFERING: statusLabel = b.getRoot().getContext().getString(R.string.download_status_downloading); break;
                case FINISHED:  statusLabel = b.getRoot().getContext().getString(R.string.download_status_finished); break;
                case PAUSED:    statusLabel = b.getRoot().getContext().getString(R.string.download_status_paused); break;
                case ERROR:     statusLabel = h.errorMessage.getValue() != null
                                ? h.errorMessage.getValue()
                                : b.getRoot().getContext().getString(R.string.search_error); break;
                case STARTING:
                default:        statusLabel = b.getRoot().getContext().getString(R.string.download_status_starting); break;
            }
            int pct = p == null ? 0 : p.percent;
            String speed = p == null ? "" : FormatUtils.humanSpeed(p.downloadSpeed);
            String full = statusLabel + " · " + pct + "%" + (speed.isEmpty() ? "" : " · " + speed);
            b.progressText.setText(full);
            b.progress.setProgressCompat(pct, false);

            // Pause button reflects current state — show "play" icon when paused,
            // "pause" icon while actively downloading. Hide when finished/errored.
            boolean canPause = s == DownloadHandle.State.STARTING
                    || s == DownloadHandle.State.BUFFERING
                    || s == DownloadHandle.State.READY;
            boolean canResume = s == DownloadHandle.State.PAUSED || s == DownloadHandle.State.ERROR;
            if (canPause) {
                b.btnPause.setIconResource(R.drawable.rounded_pause_24);
                b.btnPause.setVisibility(android.view.View.VISIBLE);
            } else if (canResume) {
                b.btnPause.setIconResource(R.drawable.rounded_play_arrow_24);
                b.btnPause.setVisibility(android.view.View.VISIBLE);
            } else {
                b.btnPause.setVisibility(View.INVISIBLE);
            }
        }
    }

    private static final DiffUtil.ItemCallback<DownloadHandle> DIFF = new DiffUtil.ItemCallback<DownloadHandle>() {
        @Override
        public boolean areItemsTheSame(@NonNull DownloadHandle a, @NonNull DownloadHandle b) {
            return a.infoHash.equals(b.infoHash);
        }
        @Override
        public boolean areContentsTheSame(@NonNull DownloadHandle a, @NonNull DownloadHandle b) {
            return a.infoHash.equals(b.infoHash); // live state is observed separately
        }
    };
}
