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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.api.models.tmdb.TMDBEpisode;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeasonSummary;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeriesDetails;
import com.vikas.torrentplayer.databinding.ActivityDetailBinding;
import com.vikas.torrentplayer.service.TorrentDownloadService;
import com.vikas.torrentplayer.torbox.TorBoxClient;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.ui.player.PlayerActivity;
import com.vikas.torrentplayer.ui.torbox.TorBoxFileChooser;
import com.vikas.torrentplayer.utils.MagnetUtils;
import com.vikas.torrentplayer.utils.PrefsManager;

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
    private List<TMDBSeasonSummary> seasonChoices = new java.util.ArrayList<>();
    private List<TMDBEpisode> episodeChoices = new java.util.ArrayList<>();
    @Nullable private Integer selectedSeason;
    @Nullable private Integer selectedEpisode;

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
        b.episodeSection.setVisibility(result.isShow() ? View.VISIBLE : View.GONE);
        b.btnSeason.setEnabled(false);
        b.btnEpisode.setEnabled(false);

        adapter = new TorrentAdapter(new TorrentAdapter.OnTorrentAction() {
            @Override
            public void onPlay(TorrentItem item) {
                DownloadHandle h = safeStartStream(item);
                if (h != null) PlayerActivity.start(DetailActivity.this, h.infoHash);
            }
            @Override
            public void onDownload(TorrentItem item) {
                DownloadHandle h = safeStartStream(item);
                if (h != null) {
                    Toast.makeText(DetailActivity.this,
                            "Download started", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onMagnet(TorrentItem item) {
                MagnetUtils.openMagnet(DetailActivity.this, item.magnetUrl);
            }
            @Override
            public void onMagnetLong(TorrentItem item) {
                MagnetUtils.copyMagnet(DetailActivity.this, item.magnetUrl);
            }
            @Override
            public void onDownloadLong(TorrentItem item) {
                startTorBox(item);
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
        vm.resultLive().observe(this, r -> {
            if (r == null) return;
            bindHeader(r);
            b.episodeSection.setVisibility(r.isShow() ? View.VISIBLE : View.GONE);
        });
        vm.seriesDetails().observe(this, this::renderSeriesDetails);
        vm.seasons().observe(this, list -> {
            seasonChoices = nullSafeSeasons(list);
            b.btnSeason.setEnabled(!seasonChoices.isEmpty());
        });
        vm.episodes().observe(this, list -> {
            episodeChoices = nullSafeEpisodes(list);
            b.btnEpisode.setEnabled(selectedSeason != null && !episodeChoices.isEmpty());
        });
        vm.selectedSeason().observe(this, season -> {
            selectedSeason = season;
            updateSeasonButton();
            b.btnEpisode.setEnabled(season != null && !episodeChoices.isEmpty());
        });
        vm.selectedEpisode().observe(this, episode -> {
            selectedEpisode = episode;
            updateEpisodeButton();
        });
        vm.episodeMetadataMessage().observe(this, this::renderEpisodeMessage);
        vm.episodeLoading().observe(this,
                loading -> b.episodeProgress.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));
        b.btnSeason.setOnClickListener(v -> showSeasonPicker());
        b.btnEpisode.setOnClickListener(v -> showEpisodePicker());
        vm.load(result);

        // The AppBarLayout already declares fitsSystemWindows="true", so insets
        // are applied automatically. Adding a manual listener here would
        // double-pad the toolbar.
    }

    /** Long-press Download → get this torrent via TorBox (stream or download). */
    private void startTorBox(TorrentItem item) {
        if (!new PrefsManager(this).hasTorBoxKey()) {
            Toast.makeText(this, "Add your TorBox API key in Settings first",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String magnet = item.magnetUrl;
        if ((magnet == null || magnet.isEmpty()) && item.infoHash != null && !item.infoHash.isEmpty()) {
            magnet = "magnet:?xt=urn:btih:" + item.infoHash;
        }
        if (magnet == null || magnet.isEmpty()) {
            Toast.makeText(this, "This torrent has no magnet/hash for TorBox",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final String title = item.downloadTitle(vm != null ? vm.result() : null);
        TorBoxManager.get().init(getApplicationContext());

        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle("Preparing on TorBox")
                .setMessage("Sending to TorBox…")
                .setCancelable(true)
                .show();

        TorBoxManager.get().addAndPrepare(magnet, new TorBoxManager.PrepareCallback() {
            @Override public void onProgress(int percent, String state) {
                if (!isFinishing()) dlg.setMessage("TorBox " + state + " · " + percent + "%");
            }
            @Override public void onReady(TorBoxClient.TbTorrent torrent) {
                if (isFinishing()) return;
                dlg.dismiss();
                TorBoxFileChooser.show(DetailActivity.this, torrent, title);
            }
            @Override public void onError(String message) {
                if (isFinishing()) return;
                dlg.dismiss();
                Toast.makeText(DetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Wraps {@link TorrentManager#startStream} with a defensive init() (in
     * case the engine hasn't initialised yet) and a try/catch that surfaces
     * any failure as a Toast instead of crashing the activity.
     */
    @Nullable
    private DownloadHandle safeStartStream(TorrentItem item) {
        try {
            TorrentManager.get().init(getApplicationContext());
            TorrentDownloadService.start(this);
            return TorrentManager.get().startStream(vm.result(), item);
        } catch (Throwable ex) {
            android.util.Log.e("DetailActivity", "startStream failed", ex);
            Toast.makeText(DetailActivity.this,
                    "Could not start: "
                            + (ex.getMessage() != null ? ex.getMessage()
                                                       : ex.getClass().getSimpleName()),
                    Toast.LENGTH_LONG).show();
            return null;
        }
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

    private void renderSeriesDetails(@Nullable TMDBSeriesDetails details) {
        if (details == null) return;
        b.episodeSection.setVisibility(View.VISIBLE);
        b.episodeSummary.setText(details.numberOfSeasons + " seasons · "
                + details.numberOfEpisodes + " episodes");
        b.episodeMessage.setText(R.string.detail_episodes_source);
        b.episodeMessage.setVisibility(View.VISIBLE);
    }

    private void renderEpisodeMessage(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            if (b.episodeSummary.getText() == null || b.episodeSummary.getText().length() == 0) {
                b.episodeMessage.setText(R.string.detail_episodes_source);
            }
            return;
        }
        b.episodeSection.setVisibility(View.VISIBLE);
        b.episodeMessage.setText(message);
        b.episodeMessage.setVisibility(View.VISIBLE);
    }

    private void showSeasonPicker() {
        if (seasonChoices.isEmpty()) return;
        CharSequence[] labels = new CharSequence[seasonChoices.size() + 1];
        labels[0] = getString(R.string.detail_all_seasons);
        int checked = 0;
        for (int i = 0; i < seasonChoices.size(); i++) {
            TMDBSeasonSummary s = seasonChoices.get(i);
            labels[i + 1] = s.displayTitle();
            if (selectedSeason != null && selectedSeason == s.seasonNumber) checked = i + 1;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_season)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    if (which == 0) {
                        vm.clearEpisodeFilter();
                    } else {
                        vm.selectSeason(seasonChoices.get(which - 1).seasonNumber);
                    }
                })
                .show();
    }

    private void showEpisodePicker() {
        if (selectedSeason == null || episodeChoices.isEmpty()) return;
        CharSequence[] labels = new CharSequence[episodeChoices.size() + 1];
        labels[0] = getString(R.string.detail_all_episodes);
        int checked = 0;
        for (int i = 0; i < episodeChoices.size(); i++) {
            TMDBEpisode e = episodeChoices.get(i);
            labels[i + 1] = e.displayTitle();
            if (selectedEpisode != null && selectedEpisode == e.episodeNumber) checked = i + 1;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_episode)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    vm.selectEpisode(which == 0 ? null : episodeChoices.get(which - 1).episodeNumber);
                })
                .show();
    }

    private void updateSeasonButton() {
        if (selectedSeason == null) {
            b.btnSeason.setText(R.string.detail_all_seasons);
            return;
        }
        for (TMDBSeasonSummary s : seasonChoices) {
            if (s.seasonNumber == selectedSeason) {
                b.btnSeason.setText(s.displayTitle());
                return;
            }
        }
        b.btnSeason.setText("Season " + selectedSeason);
    }

    private void updateEpisodeButton() {
        if (selectedEpisode == null) {
            b.btnEpisode.setText(R.string.detail_all_episodes);
            return;
        }
        for (TMDBEpisode e : episodeChoices) {
            if (e.episodeNumber == selectedEpisode) {
                b.btnEpisode.setText(e.displayTitle());
                return;
            }
        }
        b.btnEpisode.setText("Episode " + selectedEpisode);
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

    private static List<TMDBSeasonSummary> nullSafeSeasons(List<TMDBSeasonSummary> in) {
        return in == null ? new java.util.ArrayList<>() : in;
    }

    private static List<TMDBEpisode> nullSafeEpisodes(List<TMDBEpisode> in) {
        return in == null ? new java.util.ArrayList<>() : in;
    }
}
