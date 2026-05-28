package com.vikas.torrentplayer.tv.discover;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.vikas.torrentplayer.tv.R;

/**
 * Library-row card: gradient background with a single white icon centred on
 * the image area, label below.
 */
public class ActionPresenter extends Presenter {

    private static final int CARD_DP = 220;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ImageCardView card = new ImageCardView(parent.getContext());
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        float density = parent.getResources().getDisplayMetrics().density;
        card.setMainImageDimensions((int) (CARD_DP * density), (int) (CARD_DP * density));
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object item) {
        ActionCard a = (ActionCard) item;
        ImageCardView card = (ImageCardView) viewHolder.view;
        card.setTitleText(a.label);
        card.setContentText("");

        // Render the icon centred on the gradient background.
        Drawable bg = ContextCompat.getDrawable(card.getContext(), R.drawable.tv_action_card);
        ImageView main = card.getMainImageView();
        main.setBackground(bg);
        main.setImageResource(a.iconRes);
        // White tint for the action icon
        main.setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
        main.setScaleType(ImageView.ScaleType.CENTER);
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        ImageCardView card = (ImageCardView) viewHolder.view;
        card.getMainImageView().setImageDrawable(null);
        card.getMainImageView().setBackground(null);
    }
}
