package com.vikas.torrentplayer.tv.details;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.SearchResponse;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.tv.R;
import com.vikas.torrentplayer.tv.player.TvPlayerActivity;
import com.vikas.torrentplayer.utils.FormatUtils;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TvDetailsActivity extends FragmentActivity {

    private static final String EXTRA_ITEM = "discover_item";

    public static void start(Context ctx, DiscoverItem item) {
        Intent i = new Intent(ctx, TvDetailsActivity.class);
        i.putExtra(EXTRA_ITEM, (Serializable) item);
        ctx.startActivity(i);
    }

    private ImageView backdrop;
    private TextView title, meta, overview, empty;
    private RecyclerView torrentsList;

    private DiscoverItem item;
    private SearchResult result;

    private final TorrentClawApi api = ApiClient.get();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_details);

        backdrop = findViewById(R.id.backdrop);
        title = findViewById(R.id.title);
        meta = findViewById(R.id.meta);
        overview = findViewById(R.id.overview);
        empty = findViewById(R.id.empty);
        torrentsList = findViewById(R.id.torrents);
        torrentsList.setLayoutManager(new LinearLayoutManager(this));

        item = (DiscoverItem) getIntent().getSerializableExtra(EXTRA_ITEM);
        if (item == null) { finish(); return; }
        bindHeader(item);
        searchByTitle(item);
    }

    private void bindHeader(DiscoverItem d) {
        title.setText(d.title != null ? d.title : "—");
        StringBuilder m = new StringBuilder();
        m.append(d.isShow() ? "Show" : "Movie");
        if (d.year != null && d.year > 0) m.append(" · ").append(d.year);
        if (d.genres != null && !d.genres.isEmpty()) {
            m.append(" · ").append(TextUtils.join(", ",
                    d.genres.subList(0, Math.min(3, d.genres.size()))));
        }
        String rating = d.displayRating();
        if (rating != null) m.append(" · ").append(rating).append("★");
        meta.setText(m.toString());
        overview.setText(d.overview != null ? d.overview : "");
        String img = d.backdropUrl != null ? d.backdropUrl : d.effectivePoster();
        Glide.with(this).load(img).into(backdrop);
    }

    /** Like the phone DetailViewModel: hit /search to find torrents. */
    private void searchByTitle(DiscoverItem d) {
        PrefsManager prefs = new PrefsManager(this);
        if (!prefs.hasApiKey() || d.title == null) {
            showEmpty("Add an API key in Settings");
            return;
        }
        String key = prefs.getApiKey();
        api.search(
                ApiClient.bearer(key),
                d.title,
                /* type */ null,
                "available", "seeders",
                1, 25, false,
                /* genre */ null, /* season */ null, /* episode */ null,
                key
        ).enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SearchResponse> call,
                                   @NonNull Response<SearchResponse> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().results == null) {
                    showEmpty("Couldn't load torrents");
                    return;
                }
                SearchResult best = pickBest(response.body().results, d);
                if (best == null || best.torrents == null || best.torrents.isEmpty()) {
                    showEmpty("No torrents found");
                    return;
                }
                result = best;
                List<TorrentItem> items = new ArrayList<>(best.torrents);
                Collections.sort(items, new Comparator<TorrentItem>() {
                    @Override public int compare(TorrentItem a, TorrentItem b) {
                        return Integer.compare(b.seeders, a.seeders);
                    }
                });
                torrentsList.setAdapter(new TorrentAdapter(items));
            }

            @Override
            public void onFailure(@NonNull Call<SearchResponse> call, @NonNull Throwable t) {
                showEmpty(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    @Nullable
    private static SearchResult pickBest(List<SearchResult> results, DiscoverItem initial) {
        long wantId = initial.effectiveId();
        for (SearchResult r : results) if (r.id == wantId) return r;
        if (initial.title != null) {
            for (SearchResult r : results) {
                if (initial.title.equalsIgnoreCase(r.title)
                        && (initial.year == null || initial.year.equals(r.year))) {
                    return r;
                }
            }
        }
        return results.isEmpty() ? null : results.get(0);
    }

    private void showEmpty(String msg) {
        empty.setText(msg);
        empty.setVisibility(View.VISIBLE);
        torrentsList.setVisibility(View.GONE);
    }

    private class TorrentAdapter extends RecyclerView.Adapter<TorrentAdapter.VH> {
        private final List<TorrentItem> items;
        TorrentAdapter(List<TorrentItem> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tv_torrent, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            TorrentItem t = items.get(pos);
            h.quality.setText(t.quality != null ? t.quality.toUpperCase() : "—");
            h.size.setText(FormatUtils.humanBytes(t.sizeBytes));
            h.seeders.setText("S: " + t.seeders);
            h.title.setText(t.rawTitle != null ? t.rawTitle : "—");
            h.itemView.setOnClickListener(v -> {
                // The engine usually finishes init before any UI surfaces, but
                // on a fresh launch the foreground service can still be coming
                // up when the user makes a fast click. Re-running init() is
                // idempotent so it's safe as a guard.
                try {
                    TorrentManager.get().init(getApplicationContext());
                    DownloadHandle hd = TorrentManager.get().startStream(result, t);
                    if (hd == null) {
                        android.widget.Toast.makeText(TvDetailsActivity.this,
                                "Could not start torrent (no handle)",
                                android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    TvPlayerActivity.start(TvDetailsActivity.this, hd.infoHash);
                } catch (Throwable ex) {
                    android.util.Log.e("TvDetails", "startStream failed", ex);
                    android.widget.Toast.makeText(TvDetailsActivity.this,
                            "Could not start: "
                                    + (ex.getMessage() != null ? ex.getMessage()
                                                                : ex.getClass().getSimpleName()),
                            android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView quality, size, seeders, title;
            VH(@NonNull View v) {
                super(v);
                quality = v.findViewById(R.id.quality);
                size = v.findViewById(R.id.size);
                seeders = v.findViewById(R.id.seeders);
                title = v.findViewById(R.id.raw_title);
            }
        }
    }
}
