package com.vikas.torrentplayer.tv.discover;

import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.tv.R;

/**
 * Renders a {@link DiscoverItem} as a 200×300 poster card. Leanback's
 * {@link ImageCardView} handles focus highlighting, title/content lines, and
 * loading shimmer for free.
 */
public class PosterPresenter extends Presenter {

    private static final int CARD_WIDTH_DP = 200;
    private static final int CARD_HEIGHT_DP = 300;

    private Drawable defaultPoster;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ImageCardView card = new ImageCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                super.setSelected(selected);
                int color = ContextCompat.getColor(
                        getContext(),
                        selected ? android.R.color.holo_purple : android.R.color.darker_gray);
                setBackgroundColor(color);
                findViewById(androidx.leanback.R.id.info_field).setBackgroundColor(color);
            }
        };
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);

        float density = parent.getResources().getDisplayMetrics().density;
        card.setMainImageDimensions(
                (int) (CARD_WIDTH_DP * density),
                (int) (CARD_HEIGHT_DP * density));
        defaultPoster = ContextCompat.getDrawable(parent.getContext(),
                R.drawable.tv_placeholder_poster);
        card.setMainImage(defaultPoster);
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object item) {
        DiscoverItem d = (DiscoverItem) item;
        ImageCardView card = (ImageCardView) viewHolder.view;
        card.setTitleText(d.title != null ? d.title : "—");
        StringBuilder content = new StringBuilder();
        content.append(d.isShow() ? "Show" : "Movie");
        if (d.year != null && d.year > 0) content.append(" · ").append(d.year);
        String rating = d.displayRating();
        if (rating != null) content.append(" · ").append(rating).append("★");
        card.setContentText(content.toString());

        Glide.with(card.getContext())
                .load(d.effectivePoster())
                .placeholder(defaultPoster)
                .error(defaultPoster)
                .into(card.getMainImageView());
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        ImageCardView card = (ImageCardView) viewHolder.view;
        card.setMainImage(null);
    }
}
