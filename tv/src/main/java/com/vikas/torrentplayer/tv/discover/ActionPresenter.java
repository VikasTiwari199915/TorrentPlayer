package com.vikas.torrentplayer.tv.discover;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.vikas.torrentplayer.tv.R;

/** Wide square card for the library row actions. */
public class ActionPresenter extends Presenter {

    private static final int CARD_DP = 200;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ImageCardView card = new ImageCardView(parent.getContext());
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        float density = parent.getResources().getDisplayMetrics().density;
        card.setMainImageDimensions((int) (CARD_DP * density), (int) (CARD_DP * density));
        card.setMainImage(ContextCompat.getDrawable(
                parent.getContext(), R.drawable.tv_action_card));
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object item) {
        ActionCard a = (ActionCard) item;
        ImageCardView card = (ImageCardView) viewHolder.view;
        card.setTitleText(a.label);
        card.setContentText("");
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) { }
}
