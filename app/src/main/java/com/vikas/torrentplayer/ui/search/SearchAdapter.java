package com.vikas.torrentplayer.ui.search;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.databinding.ItemSearchResultBinding;

import java.util.List;

public class SearchAdapter extends ListAdapter<SearchResult, SearchAdapter.VH> {

    public interface OnClick {
        void onClick(SearchResult item);
    }

    private final OnClick onClick;

    public SearchAdapter(OnClick onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchResultBinding b = ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding b;

        VH(ItemSearchResultBinding b) {
            super(b.getRoot());
            this.b = b;
            b.getRoot().setOnClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && onClick != null) {
                    onClick.onClick(getItem(p));
                }
            });
        }

        void bind(SearchResult item) {
            b.title.setText(item.title != null ? item.title : "—");

            StringBuilder meta = new StringBuilder();
            meta.append(item.isShow() ? "Show" : "Movie");
            if (item.year != null && item.year > 0) meta.append(" · ").append(item.year);
            List<String> g = item.genres;
            if (g != null && !g.isEmpty()) {
                meta.append(" · ");
                meta.append(TextUtils.join(", ", g.subList(0, Math.min(2, g.size()))));
            }
            b.meta.setText(meta.toString());

            String rating = item.getDisplayRating();
            if (rating != null) {
                b.rating.setText(rating);
                b.rating.setVisibility(android.view.View.VISIBLE);
                b.ratingIcon.setVisibility(android.view.View.VISIBLE);
            } else {
                b.rating.setVisibility(android.view.View.GONE);
                b.ratingIcon.setVisibility(android.view.View.GONE);
            }

            if (item.overview != null && !item.overview.isEmpty()) {
                b.overview.setText(item.overview);
                b.overview.setVisibility(android.view.View.VISIBLE);
            } else {
                b.overview.setVisibility(android.view.View.GONE);
            }

            Glide.with(b.poster)
                    .load(item.posterUrl)
                    .placeholder(R.drawable.placeholder_poster)
                    .error(R.drawable.placeholder_poster)
                    .into(b.poster);
        }
    }

    private static final DiffUtil.ItemCallback<SearchResult> DIFF = new DiffUtil.ItemCallback<SearchResult>() {
        @Override
        public boolean areItemsTheSame(@NonNull SearchResult a, @NonNull SearchResult b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull SearchResult a, @NonNull SearchResult b) {
            return a.id == b.id
                    && TextUtils.equals(a.title, b.title)
                    && TextUtils.equals(a.posterUrl, b.posterUrl);
        }
    };
}
