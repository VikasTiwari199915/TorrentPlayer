package com.vikas.torrentplayer.ui.downloads;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.ItemDownloadBinding;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.ui.player.PlayerActivity;
import com.vikas.torrentplayer.utils.FormatUtils;

/**
 * Renders TorBox device-downloads in the Downloads list (concatenated after the
 * libtorrent downloads). Reuses {@code item_download} for a consistent look.
 */
public class TorBoxDownloadsAdapter
        extends ListAdapter<TorBoxManager.Download, TorBoxDownloadsAdapter.VH> {

    public interface Listener {
        void onPlay(TorBoxManager.Download d);
        void onPauseToggle(TorBoxManager.Download d);
        void onRemove(TorBoxManager.Download d);
    }

    private final Listener listener;

    public TorBoxDownloadsAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemDownloadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemDownloadBinding b;
        VH(ItemDownloadBinding b) { super(b.getRoot()); this.b = b; }

        void bind(TorBoxManager.Download d) {
            b.poster.setImageResource(R.drawable.placeholder_poster);
            b.title.setText(d.title);
            b.meta.setText("TorBox · " + FormatUtils.humanBytes(d.size));

            String status;
            switch (d.state) {
                case DOWNLOADING: status = "Downloading · " + d.percent + "%  ·  "
                        + FormatUtils.humanSpeed(d.speed); break;
                case PAUSED:      status = "Paused · " + d.percent + "%"; break;
                case DONE:        status = "Done"; break;
                case ERROR:       status = d.error != null ? d.error : "Error"; break;
                default:          status = ""; break;
            }
            b.progressText.setText(status);
            b.progress.setProgressCompat(d.percent, false);

            boolean fileReady = d.file != null;
            b.btnPlay.setVisibility(fileReady ? View.VISIBLE : View.INVISIBLE);
            b.btnPlay.setOnClickListener(v -> { if (listener != null) listener.onPlay(d); });

            if (d.state == TorBoxManager.State.DOWNLOADING) {
                b.btnPause.setIconResource(R.drawable.rounded_pause_24);
                b.btnPause.setVisibility(View.VISIBLE);
            } else if (d.state == TorBoxManager.State.PAUSED) {
                b.btnPause.setIconResource(R.drawable.rounded_play_arrow_24);
                b.btnPause.setVisibility(View.VISIBLE);
            } else {
                b.btnPause.setVisibility(View.INVISIBLE);
            }
            b.btnPause.setOnClickListener(v -> { if (listener != null) listener.onPauseToggle(d); });

            b.btnRemove.setOnClickListener(v -> { if (listener != null) listener.onRemove(d); });
            b.getRoot().setOnClickListener(v -> { if (listener != null) listener.onPlay(d); });
        }
    }

    private static final DiffUtil.ItemCallback<TorBoxManager.Download> DIFF =
            new DiffUtil.ItemCallback<TorBoxManager.Download>() {
                @Override public boolean areItemsTheSame(
                        @NonNull TorBoxManager.Download a, @NonNull TorBoxManager.Download b) {
                    return a.key.equals(b.key);
                }
                @Override public boolean areContentsTheSame(
                        @NonNull TorBoxManager.Download a, @NonNull TorBoxManager.Download b) {
                    return a.state == b.state && a.percent == b.percent
                            && a.speed == b.speed && (a.file != null) == (b.file != null);
                }
            };
}
