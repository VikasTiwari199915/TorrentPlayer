package com.vikas.torrentplayer.ui.discover;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.databinding.ItemDiscoverPosterBinding;

/**
 * Generic adapter for a horizontally-scrolling poster carousel. Reused for
 * every section on the Discover screen — the optional rank badge is what
 * differentiates "Top on Netflix" from the rest.
 */
public class PosterAdapter extends ListAdapter<DiscoverItem, PosterAdapter.VH> {

    public interface OnClick {
        void onClick(DiscoverItem item);
    }

    private final OnClick onClick;
    private final boolean showRank;

    public PosterAdapter(OnClick onClick, boolean showRank) {
        super(DIFF);
        this.onClick = onClick;
        this.showRank = showRank;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDiscoverPosterBinding b = ItemDiscoverPosterBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemDiscoverPosterBinding b;

        VH(ItemDiscoverPosterBinding b) {
            super(b.getRoot());
            this.b = b;
            b.getRoot().setOnClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && onClick != null) {
                    onClick.onClick(getItem(p));
                }
            });
        }

        void bind(DiscoverItem item) {
            b.title.setText(item.title != null ? item.title : "—");

            StringBuilder meta = new StringBuilder();
            meta.append(item.isShow() ? "Show" : "Movie");
            if (item.year != null && item.year > 0) meta.append(" · ").append(item.year);
            b.meta.setText(meta.toString());

            String rating = item.displayRating();
            if (rating != null) {
                b.ratingText.setText(rating);
                b.ratingChip.setVisibility(View.VISIBLE);
            } else {
                b.ratingChip.setVisibility(View.GONE);
            }

            if (showRank && item.rank != null && item.rank > 0) {
                b.rankBadge.setVisibility(View.VISIBLE);
                b.rankBadge.setText(String.valueOf(item.rank));
            } else {
                b.rankBadge.setVisibility(View.GONE);
            }

            Glide.with(b.poster)
                    .load(item.effectivePoster())
                    .placeholder(R.drawable.placeholder_poster)
                    .error(R.drawable.placeholder_poster)
                    .into(b.poster);
        }
    }

    private static final DiffUtil.ItemCallback<DiscoverItem> DIFF = new DiffUtil.ItemCallback<DiscoverItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull DiscoverItem a, @NonNull DiscoverItem b) {
            return a.effectiveId() == b.effectiveId();
        }
        @Override
        public boolean areContentsTheSame(@NonNull DiscoverItem a, @NonNull DiscoverItem b) {
            return a.effectiveId() == b.effectiveId();
        }
    };
}
