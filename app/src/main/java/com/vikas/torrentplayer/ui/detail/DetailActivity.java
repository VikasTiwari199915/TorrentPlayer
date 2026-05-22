package com.vikas.torrentplayer.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.databinding.ActivityDetailBinding;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.ui.player.PlayerActivity;
import com.vikas.torrentplayer.utils.MagnetUtils;

import java.util.List;

public class DetailActivity extends AppCompatActivity {

    private static final String EXTRA_RESULT = "extra_result";

    public static void start(Context ctx, SearchResult item) {
        Intent i = new Intent(ctx, DetailActivity.class);
        i.putExtra(EXTRA_RESULT, item);
        ctx.startActivity(i);
    }

    /**
     * Discover entry-point: wraps a {@link DiscoverItem} as a partial
     * {@link SearchResult} so the detail VM can fetch torrents via its search
     * fallback. Discover items never carry torrents themselves.
     */
    public static void startFromDiscover(Context ctx, DiscoverItem d) {
        SearchResult sr = new SearchResult();
        sr.id = d.effectiveId();
        sr.title = d.title;
        sr.year = d.year;
        sr.contentType = d.contentType;
        sr.overview = d.overview;
        sr.posterUrl = d.effectivePoster();
        sr.backdropUrl = d.backdropUrl;
        sr.ratingImdb = d.ratingImdb;
        sr.ratingTmdb = d.ratingTmdb;
        sr.genres = d.genres;
        sr.imdbId = d.imdbId;
        sr.tmdbId = d.tmdbId;
        start(ctx, sr);
    }

    private ActivityDetailBinding b;
    private DetailViewModel vm;
    private TorrentAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        b = ActivityDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        b.toolbar.setNavigationOnClickListener(v -> finish());

        SearchResult result = (SearchResult) getIntent().getSerializableExtra(EXTRA_RESULT);
        if (result == null) {
            finish();
            return;
        }

        bindHeader(result);

        adapter = new TorrentAdapter(new TorrentAdapter.OnTorrentAction() {
            @Override
            public void onPlay(TorrentItem item) {
                DownloadHandle h = TorrentManager.get().startStream(vm.result(), item);
                PlayerActivity.start(DetailActivity.this, h.infoHash);
            }
            @Override
            public void onDownload(TorrentItem item) {
                TorrentManager.get().startStream(vm.result(), item);
                Toast.makeText(DetailActivity.this,
                        "Download started", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onMagnet(TorrentItem item) {
                MagnetUtils.openMagnet(DetailActivity.this, item.magnetUrl);
            }
            @Override
            public void onMagnetLong(TorrentItem item) {
                MagnetUtils.copyMagnet(DetailActivity.this, item.magnetUrl);
            }
        });
        b.torrentsRecycler.setLayoutManager(new LinearLayoutManager(this));
        b.torrentsRecycler.setAdapter(adapter);

        vm = new ViewModelProvider(this).get(DetailViewModel.class);
        vm.torrents().observe(this, list -> adapter.submitList(list));
        vm.state().observe(this, this::renderState);
        // Re-render the header whenever the VM swaps in a richer SearchResult —
        // this is how Discover→Detail picks up the backdrop after the search
        // fallback resolves.
        vm.resultLive().observe(this, r -> { if (r != null) bindHeader(r); });
        vm.load(result);

        // The AppBarLayout already declares fitsSystemWindows="true", so insets
        // are applied automatically. Adding a manual listener here would
        // double-pad the toolbar.
    }

    private void bindHeader(SearchResult r) {
        b.collapsingToolbar.setTitle(r.title);
        b.title.setText(r.title != null ? r.title : "—");

        StringBuilder meta = new StringBuilder();
        meta.append(r.isShow() ? "Show" : "Movie");
        if (r.year != null && r.year > 0) meta.append(" · ").append(r.year);
        if (r.genres != null && !r.genres.isEmpty()) {
            meta.append(" · ").append(TextUtils.join(", ",
                    r.genres.subList(0, Math.min(3, r.genres.size()))));
        }
        b.meta.setText(meta.toString());

        String rating = r.getDisplayRating();
        if (rating != null) {
            b.rating.setText(rating);
            b.rating.setVisibility(View.VISIBLE);
            b.ratingIcon.setVisibility(View.VISIBLE);
        } else {
            b.rating.setVisibility(View.GONE);
            b.ratingIcon.setVisibility(View.GONE);
        }

        b.overview.setText(r.overview != null ? r.overview : "—");

        Glide.with(this).load(r.backdropUrl).into(b.backdrop);
        Glide.with(this).load(r.posterUrl)
                .placeholder(R.drawable.placeholder_poster)
                .error(R.drawable.placeholder_poster)
                .into(b.poster);
    }

    private void renderState(DetailViewModel.UiState state) {
        boolean loading = state == DetailViewModel.UiState.LOADING;
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.torrentsEmpty.setVisibility(state == DetailViewModel.UiState.EMPTY ? View.VISIBLE : View.GONE);
        if (state == DetailViewModel.UiState.ERROR) {
            b.torrentsEmpty.setText(R.string.search_error);
            b.torrentsEmpty.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        b = null;
    }

    private static List<TorrentItem> nullSafe(List<TorrentItem> in) {
        return in == null ? new java.util.ArrayList<>() : in;
    }
}
