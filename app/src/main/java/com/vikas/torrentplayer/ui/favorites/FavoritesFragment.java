package com.vikas.torrentplayer.ui.favorites;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.db.*;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.ui.detail.DetailActivity;
import java.util.*;

public class FavoritesFragment extends Fragment {
    private FavoriteAdapter adapter;
    private TextView empty;
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        MaterialToolbar toolbar = new MaterialToolbar(requireContext());
        toolbar.setTitle("Favorites");
        root.addView(toolbar);
        empty = new TextView(requireContext());
        empty.setText("No favorites yet");
        empty.setGravity(Gravity.CENTER);
        root.addView(empty);
        RecyclerView list = new RecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setPadding(0, 0, 0, dp(100));
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new FavoriteAdapter();
        list.setAdapter(adapter);
        return root;
    }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        AppDatabase.get(requireContext()).favoriteDao().observeAll().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override public void onDestroyView() { adapter = null; empty = null; super.onDestroyView(); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private class FavoriteAdapter extends androidx.recyclerview.widget.ListAdapter<FavoriteEntity, Holder> {
        FavoriteAdapter() { super(new DiffUtil.ItemCallback<FavoriteEntity>() {
            public boolean areItemsTheSame(@NonNull FavoriteEntity a, @NonNull FavoriteEntity b) { return a.key.equals(b.key); }
            public boolean areContentsTheSame(@NonNull FavoriteEntity a, @NonNull FavoriteEntity b) {
                return Objects.equals(a.metadata, b.metadata) && a.updated == b.updated;
            }
        }); }
        @NonNull public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(8), dp(16), dp(8));
            ImageView poster = new ImageView(parent.getContext());
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(poster, new LinearLayout.LayoutParams(dp(60), dp(90)));
            TextView title = new TextView(parent.getContext());
            title.setPadding(dp(12), 0, dp(8), 0);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            com.google.android.material.button.MaterialButton remove = new com.google.android.material.button.MaterialButton(
                    parent.getContext(), null, com.google.android.material.R.attr.materialIconButtonStyle);
            remove.setIconResource(R.drawable.rounded_delete_24);
            remove.setContentDescription("Remove favorite");
            row.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
            return new Holder(row, poster, title, remove);
        }
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FavoriteEntity item = getItem(position);
            SearchResult result = Favorites.result(item);
            holder.title.setText(result == null ? "Unavailable" : result.title
                    + (result.year == null ? "" : " (" + result.year + ")")
                    + (item.updated ? "\nNew episode available" : ""));
            Glide.with(holder.poster).load(result == null ? null : result.posterUrl)
                    .placeholder(R.drawable.placeholder_poster).into(holder.poster);
            holder.itemView.setOnClickListener(v -> {
                if (result == null) return;
                FavoriteDao dao = AppDatabase.get(requireContext()).favoriteDao();
                Favorites.IO.execute(() -> dao.markSeen(item.key));
                DetailActivity.start(requireContext(), result);
            });
            holder.remove.setOnClickListener(v -> {
                FavoriteDao dao = AppDatabase.get(requireContext()).favoriteDao();
                Favorites.IO.execute(() -> dao.delete(item.key));
            });
        }
    }
    private static class Holder extends RecyclerView.ViewHolder {
        final ImageView poster; final TextView title; final View remove;
        Holder(View root, ImageView poster, TextView title, View remove) {
            super(root); this.poster = poster; this.title = title; this.remove = remove;
        }
    }
}
