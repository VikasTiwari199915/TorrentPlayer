package com.vikas.torrentplayer.tv.details;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.SearchResponse;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.tv.torbox.TorBoxFileChooser;
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

    private ImageView poster, backdrop;
    private View scrim;
    private TextView title, meta, overview, empty;
    private android.widget.Button filterSe;
    private RecyclerView torrentsList;

    private DiscoverItem item;
    private SearchResult result;
    private boolean backdropEnabled;
    private Integer filterSeason, filterEpisode;

    private final TorrentClawApi api = ApiClient.get();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_details);

        poster = findViewById(R.id.poster);
        backdrop = findViewById(R.id.backdrop);
        scrim = findViewById(R.id.scrim);
        title = findViewById(R.id.title);
        meta = findViewById(R.id.meta);
        overview = findViewById(R.id.overview);
        empty = findViewById(R.id.empty);
        filterSe = findViewById(R.id.filter_se);
        torrentsList = findViewById(R.id.torrents);
        torrentsList.setLayoutManager(new LinearLayoutManager(this));

        backdropEnabled = new PrefsManager(this).isTvBackdropEnabled();

        item = (DiscoverItem) getIntent().getSerializableExtra(EXTRA_ITEM);
        if (item == null) { finish(); return; }
        // Season/episode filter only makes sense for shows.
        if (item.isShow()) {
            filterSe.setVisibility(View.VISIBLE);
            filterSe.setOnClickListener(v -> showSeasonEpisodeDialog());
            updateFilterLabel();
        }
        bindHeader(item);
        searchByTitle(item);
    }

    private void updateFilterLabel() {
        String label;
        if (filterSeason != null && filterEpisode != null) {
            label = String.format(java.util.Locale.US, "S%02dE%02d", filterSeason, filterEpisode);
        } else if (filterSeason != null) {
            label = "Season " + filterSeason;
        } else {
            label = "All episodes";
        }
        filterSe.setText("Filter: " + label);
    }

    /** D-pad dialog with season + episode inputs (episode requires a season). */
    private void showSeasonEpisodeDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        final android.widget.EditText season = new android.widget.EditText(this);
        season.setHint("Season (e.g. 1)");
        season.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        season.setSingleLine();
        if (filterSeason != null) season.setText(String.valueOf(filterSeason));
        box.addView(season);

        final android.widget.EditText episode = new android.widget.EditText(this);
        episode.setHint("Episode (optional)");
        episode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        episode.setSingleLine();
        if (filterEpisode != null) episode.setText(String.valueOf(filterEpisode));
        box.addView(episode);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Filter by season / episode")
                .setView(box)
                .setNeutralButton("Clear", (d, w) -> {
                    filterSeason = null; filterEpisode = null;
                    updateFilterLabel();
                    searchByTitle(item);
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> {
                    Integer s = parseOrNull(season.getText().toString());
                    Integer e = parseOrNull(episode.getText().toString());
                    if (e != null && s == null) {
                        android.widget.Toast.makeText(this,
                                "Pick a season for that episode", android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    filterSeason = s; filterEpisode = e;
                    updateFilterLabel();
                    searchByTitle(item);
                })
                .show();
    }

    private static Integer parseOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { int v = Integer.parseInt(s); return v > 0 ? v : null; }
        catch (NumberFormatException e) { return null; }
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

        Glide.with(this).load(d.effectivePoster())
                .override(280, 420).into(poster);
        loadBackdrop(d.backdropUrl);
    }

    /** Loads the full-screen backdrop when enabled and a URL is present;
     *  otherwise leaves the plain dark background. Bitmap is capped to 1280×720
     *  to keep memory low on weak TV boxes. */
    private void loadBackdrop(@Nullable String url) {
        if (!backdropEnabled || url == null || url.isEmpty()) {
            backdrop.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            return;
        }
        backdrop.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        Glide.with(this).load(url)
                .override(1280, 720)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(400))
                .into(backdrop);
    }

    /** Like the phone DetailViewModel: hit /search to find torrents. */
    private void searchByTitle(DiscoverItem d) {
        PrefsManager prefs = new PrefsManager(this);
        if (!prefs.hasApiKey() || d.title == null) {
            showEmpty("Add an API key in Settings");
            return;
        }
        String key = prefs.getApiKey();
        // Show a loading state and clear stale results when re-querying.
        empty.setText("Loading…");
        empty.setVisibility(View.VISIBLE);
        torrentsList.setVisibility(View.GONE);
        api.search(
                ApiClient.bearer(key),
                d.title,
                /* type */ null,
                "available", "seeders",
                1, 25, false,
                /* genre */ null, filterSeason, filterEpisode,
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
                // The search result often has richer art than the discover card —
                // upgrade the backdrop / poster if the initial one was missing.
                if (item == null || item.backdropUrl == null) loadBackdrop(best.backdropUrl);
                if ((item == null || item.effectivePoster() == null) && best.posterUrl != null) {
                    Glide.with(TvDetailsActivity.this).load(best.posterUrl)
                            .override(280, 420).into(poster);
                }
                List<TorrentItem> items = new ArrayList<>(best.torrents);
                Collections.sort(items, new Comparator<TorrentItem>() {
                    @Override public int compare(TorrentItem a, TorrentItem b) {
                        return Integer.compare(b.seeders, a.seeders);
                    }
                });
                empty.setVisibility(View.GONE);
                torrentsList.setVisibility(View.VISIBLE);
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

    /** Lets the user pick how to get this torrent: P2P stream (libtorrent) or a
     *  full-speed download via TorBox (when a TorBox key is configured). */
    private void showSourceChooser(TorrentItem t) {
        boolean hasTorBox = new PrefsManager(this).hasTorBoxKey();
        java.util.List<CharSequence> labels = new ArrayList<>();
        java.util.List<Runnable> actions = new ArrayList<>();

        labels.add("Stream now (P2P)");
        actions.add(() -> playViaP2p(t));

        if (hasTorBox) {
            labels.add("Via TorBox (stream / download)");
            actions.add(() -> startTorBox(t));
        } else {
            labels.add("Via TorBox — set API key in Settings");
            actions.add(() -> android.widget.Toast.makeText(this,
                    "Add your TorBox API key in Settings first",
                    android.widget.Toast.LENGTH_LONG).show());
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(t.rawTitle != null ? t.rawTitle : "Torrent")
                .setItems(labels.toArray(new CharSequence[0]),
                        (d, which) -> actions.get(which).run())
                .show();
    }

    private void playViaP2p(TorrentItem t) {
        // init() is idempotent — guards against a fast click before the
        // foreground service has finished coming up on a cold launch.
        try {
            TorrentManager.get().init(getApplicationContext());
            DownloadHandle hd = TorrentManager.get().startStream(result, t);
            if (hd == null) {
                android.widget.Toast.makeText(this,
                        "Could not start torrent (no handle)",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            TvPlayerActivity.start(this, hd.infoHash);
        } catch (Throwable ex) {
            android.util.Log.e("TvDetails", "startStream failed", ex);
            android.widget.Toast.makeText(this,
                    "Could not start: "
                            + (ex.getMessage() != null ? ex.getMessage()
                                                        : ex.getClass().getSimpleName()),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void startTorBox(TorrentItem t) {
        String magnet = t.magnetUrl;
        if ((magnet == null || magnet.isEmpty())
                && t.infoHash != null && !t.infoHash.isEmpty()) {
            magnet = "magnet:?xt=urn:btih:" + t.infoHash;
        }
        if (magnet == null || magnet.isEmpty()) {
            android.widget.Toast.makeText(this,
                    "This torrent has no magnet/hash for TorBox",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final String title = item != null && item.title != null ? item.title
                : (t.rawTitle != null ? t.rawTitle : "Download");
        TorBoxManager.get().init(getApplicationContext());

        TextView msg = new TextView(this);
        msg.setPadding(48, 40, 48, 24);
        msg.setTextColor(0xFFFFFFFF);
        msg.setText("Sending to TorBox…");
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("Preparing on TorBox")
                .setView(msg)
                .setCancelable(true)
                .show();

        TorBoxManager.get().addAndPrepare(magnet, new TorBoxManager.PrepareCallback() {
            @Override public void onProgress(int percent, String state) {
                if (!isFinishing()) msg.setText("TorBox " + state + " · " + percent + "%");
            }
            @Override public void onReady(com.vikas.torrentplayer.torbox.TorBoxClient.TbTorrent torrent) {
                if (isFinishing()) return;
                dlg.dismiss();
                TorBoxFileChooser.show(TvDetailsActivity.this, torrent, title);
            }
            @Override public void onError(String message) {
                if (isFinishing()) return;
                dlg.dismiss();
                android.widget.Toast.makeText(TvDetailsActivity.this,
                        message, android.widget.Toast.LENGTH_LONG).show();
            }
        });
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
            h.itemView.setOnClickListener(v -> showSourceChooser(t));
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
