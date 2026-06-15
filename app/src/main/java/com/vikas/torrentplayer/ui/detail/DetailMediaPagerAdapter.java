package com.vikas.torrentplayer.ui.detail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.vikas.torrentplayer.R;

final class DetailMediaPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface Listener {
        void onPlayTrailer();
        void onTrailerViewsReady(@NonNull WebView webView,
                                 @NonNull ViewGroup surface,
                                 @NonNull MaterialButton mute,
                                 @NonNull SeekBar progress,
                                 @NonNull MaterialButton fullscreen);
    }

    private static final int TYPE_BACKDROP = 0;
    private static final int TYPE_TRAILER = 1;

    private final Listener listener;
    private String backdropUrl;
    private boolean hasTrailer;

    DetailMediaPagerAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    void setBackdropUrl(String backdropUrl) {
        this.backdropUrl = backdropUrl;
        notifyItemChanged(0);
    }

    void setHasTrailer(boolean hasTrailer) {
        if (this.hasTrailer == hasTrailer) {
            notifyItemChanged(0);
            return;
        }
        this.hasTrailer = hasTrailer;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_BACKDROP : TYPE_TRAILER;
    }

    @Override
    public int getItemCount() {
        return hasTrailer ? 2 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TRAILER) {
            return new TrailerHolder(inflater.inflate(
                    R.layout.item_detail_trailer, parent, false));
        }
        return new BackdropHolder(inflater.inflate(
                R.layout.item_detail_backdrop, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof BackdropHolder) {
            BackdropHolder backdrop = (BackdropHolder) holder;
            Glide.with(backdrop.image)
                    .load(backdropUrl)
                    .into(backdrop.image);
            backdrop.play.setVisibility(hasTrailer ? View.VISIBLE : View.GONE);
            backdrop.play.setOnClickListener(v -> listener.onPlayTrailer());
        } else if (holder instanceof TrailerHolder) {
            TrailerHolder trailer = (TrailerHolder) holder;
            listener.onTrailerViewsReady(
                    trailer.webView,
                    trailer.surface,
                    trailer.mute,
                    trailer.progress,
                    trailer.fullscreen);
        }
    }

    private static final class BackdropHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final MaterialButton play;

        BackdropHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.backdrop);
            play = itemView.findViewById(R.id.play_trailer);
        }
    }

    private static final class TrailerHolder extends RecyclerView.ViewHolder {
        final ViewGroup surface;
        final WebView webView;
        final MaterialButton mute;
        final SeekBar progress;
        final MaterialButton fullscreen;

        TrailerHolder(@NonNull View itemView) {
            super(itemView);
            surface = itemView.findViewById(R.id.trailer_surface);
            webView = itemView.findViewById(R.id.trailer_webview);
            mute = itemView.findViewById(R.id.trailer_mute);
            progress = itemView.findViewById(R.id.trailer_progress);
            fullscreen = itemView.findViewById(R.id.trailer_fullscreen);
        }
    }
}
