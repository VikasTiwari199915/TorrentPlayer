package com.vikas.torrentplayer.tv.discover;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.tv.R;

/**
 * Standard TV poster card. 160×240 dp lets a full row of 6 fit on a 1080p
 * screen with margins; the title TextView inside ImageCardView is forced to a
 * single line so long titles ellipsize instead of pushing card content
 * outside the row bounds.
 */
public class PosterPresenter extends Presenter {

    private static final int CARD_WIDTH_DP = 160;
    private static final int CARD_HEIGHT_DP = 240;

    private Drawable defaultPoster;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ImageCardView card = new ImageCardView(parent.getContext());
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);

        float density = parent.getResources().getDisplayMetrics().density;
        card.setMainImageDimensions(
                (int) (CARD_WIDTH_DP * density),
                (int) (CARD_HEIGHT_DP * density));
        defaultPoster = ContextCompat.getDrawable(parent.getContext(),
                R.drawable.tv_placeholder_poster);
        card.setMainImage(defaultPoster);

        // Force single-line ellipsize on the built-in title TextView — by
        // default it would expand and shove the content/subtitle outside the
        // card boundary for long titles.
        View titleView = card.findViewById(androidx.leanback.R.id.title_text);
        if (titleView instanceof TextView) {
            TextView t = (TextView) titleView;
            t.setMaxLines(1);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        View contentView = card.findViewById(androidx.leanback.R.id.content_text);
        if (contentView instanceof TextView) {
            TextView c = (TextView) contentView;
            c.setMaxLines(1);
            c.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }

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
