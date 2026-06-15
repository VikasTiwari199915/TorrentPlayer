package com.vikas.torrentplayer.ui.detail;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.api.models.tmdb.TMDBEpisode;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeasonSummary;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeriesDetails;
import com.vikas.torrentplayer.api.models.tmdb.TMDBVideo;
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
    private static final String TRAILER_PAGE_ORIGIN =
            "https://com.vikas.torrentplayer";
    private static final String YOUTUBE_EMBED_ORIGIN =
            "https://www.youtube-nocookie.com";

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
    private DetailMediaPagerAdapter mediaAdapter;
    private List<TMDBSeasonSummary> seasonChoices = new java.util.ArrayList<>();
    private List<TMDBEpisode> episodeChoices = new java.util.ArrayList<>();
    @Nullable private Integer selectedSeason;
    @Nullable private Integer selectedEpisode;
    @Nullable private SearchResult currentResult;
    @Nullable private TMDBVideo featuredVideo;
    private final Handler trailerHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoplayTrailer = this::startInlineTrailer;
    private boolean trailerPlaying;
    private boolean trailerEmbedFailed;
    private boolean pendingTrailerStart;
    private boolean userSeeking;
    private boolean trailerMuted = true;
    private double trailerDuration;
    @Nullable private WebView trailerWebView;
    @Nullable private ViewGroup trailerSurface;
    @Nullable private MaterialButton trailerMute;
    @Nullable private MaterialButton trailerFullscreen;
    @Nullable private SeekBar trailerProgress;
    @Nullable private FrameLayout fullscreenOverlay;
    @Nullable private ViewGroup trailerNormalParent;
    @Nullable private ViewGroup.LayoutParams trailerNormalLayoutParams;
    private int trailerNormalIndex;
    private float trailerTouchDownX;

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
        setupMediaPager();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenOverlay != null) exitTrailerFullscreen();
                else finish();
            }
        });

        SearchResult result = (SearchResult) getIntent().getSerializableExtra(EXTRA_RESULT);
        if (result == null) {
            finish();
            return;
        }

        currentResult = result;
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
            currentResult = r;
            bindHeader(r);
            b.episodeSection.setVisibility(r.isShow() ? View.VISIBLE : View.GONE);
        });
        vm.featuredVideo().observe(this, video -> {
            trailerHandler.removeCallbacks(autoplayTrailer);
            stopInlineTrailer();
            trailerEmbedFailed = false;
            featuredVideo = video;
            renderBackdrop();
            if (video != null && video.isYouTube()
                    && new PrefsManager(this).isTrailerAutoplayEnabled()) {
                trailerHandler.postDelayed(autoplayTrailer, 2500L);
            }
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

        renderBackdrop();
        Glide.with(this).load(r.posterUrl)
                .placeholder(R.drawable.placeholder_poster)
                .error(R.drawable.placeholder_poster)
                .into(b.poster);
    }

    private void renderBackdrop() {
        if (b == null || mediaAdapter == null) return;
        String backdropUrl = currentResult == null ? null : currentResult.backdropUrl;
        boolean hasTrailer = featuredVideo != null && featuredVideo.isYouTube();
        mediaAdapter.setBackdropUrl(backdropUrl);
        mediaAdapter.setHasTrailer(hasTrailer);
        b.mediaPageIndicator.setVisibility(hasTrailer ? View.VISIBLE : View.GONE);
        updateMediaPageIndicator(b.mediaPager.getCurrentItem());
    }

    private void setupMediaPager() {
        mediaAdapter = new DetailMediaPagerAdapter(new DetailMediaPagerAdapter.Listener() {
            @Override
            public void onPlayTrailer() {
                if (trailerEmbedFailed) openFeaturedVideo();
                else startInlineTrailer();
            }

            @Override
            public void onTrailerViewsReady(WebView webView, ViewGroup surface,
                                            MaterialButton mute, SeekBar progress,
                                            MaterialButton fullscreen) {
                configureTrailerPlayer(webView, surface, mute, progress, fullscreen);
            }
        });
        b.mediaPager.setAdapter(mediaAdapter);
        b.mediaPager.setOffscreenPageLimit(1);
        b.mediaPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateMediaPageIndicator(position);
                int titleBottomMarginDp = position == 0 ? 16 : 64;
                b.collapsingToolbar.setExpandedTitleMarginBottom(
                        Math.round(titleBottomMarginDp
                                * getResources().getDisplayMetrics().density));
                if (position == 0) {
                    evaluateTrailerJavascript("if(window.player){player.pauseVideo();}");
                } else if (trailerPlaying) {
                    evaluateTrailerJavascript("if(window.player){player.playVideo();}");
                }
            }
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void configureTrailerPlayer(WebView webView, ViewGroup surface,
                                        MaterialButton mute, SeekBar progress,
                                        MaterialButton fullscreen) {
        trailerWebView = webView;
        trailerSurface = surface;
        trailerMute = mute;
        trailerProgress = progress;
        trailerFullscreen = fullscreen;

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(
                new TrailerJavascriptBridge(), "TorrentPlayer");

        mute.setOnClickListener(v -> evaluateTrailerJavascript(
                "if(window.player){player.isMuted()?player.unMute():player.mute();}"));
        fullscreen.setOnClickListener(v -> toggleTrailerFullscreen());
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (trailerDuration <= 0) return;
                double target = trailerDuration * seekBar.getProgress() / seekBar.getMax();
                evaluateTrailerJavascript("if(window.player){player.seekTo(" + target + ",true);}");
            }
        });
        webView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                trailerTouchDownX = event.getX();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && event.getX() - trailerTouchDownX > 80f
                    && fullscreenOverlay == null) {
                b.mediaPager.setCurrentItem(0, true);
            }
            return true;
        });

        if (pendingTrailerStart) {
            pendingTrailerStart = false;
            loadTrailerIntoWebView();
        }
    }

    private void startInlineTrailer() {
        TMDBVideo video = featuredVideo;
        if (b == null || video == null || !video.isYouTube() || trailerPlaying) return;
        trailerHandler.removeCallbacks(autoplayTrailer);
        trailerPlaying = true;
        b.mediaPager.setCurrentItem(1, true);
        b.backdropScrim.setAlpha(0.35f);
        if (trailerWebView == null) {
            pendingTrailerStart = true;
            return;
        }
        loadTrailerIntoWebView();
    }

    private void loadTrailerIntoWebView() {
        TMDBVideo video = featuredVideo;
        WebView webView = trailerWebView;
        if (video == null || webView == null) return;
        String html = "<!doctype html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"referrer\" content=\"strict-origin-when-cross-origin\">"
                + "<style>html,body,iframe{margin:0;width:100%;height:100%;border:0;"
                + "background:#000;overflow:hidden}</style></head><body>"
                + "<div id=\"player\"></div>"
                + "<script src=\"https://www.youtube.com/iframe_api\"></script>"
                + "<script>var player;function report(){if(player&&player.getDuration){"
                + "TorrentPlayer.onProgress(player.getCurrentTime(),player.getDuration(),player.isMuted());}}"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('player',{"
                + "videoId:'" + video.key + "',width:'100%',height:'100%',"
                + "host:'" + YOUTUBE_EMBED_ORIGIN + "',"
                + "playerVars:{autoplay:1,mute:1,controls:0,disablekb:1,fs:0,playsinline:1,rel:0,"
                + "origin:'" + YOUTUBE_EMBED_ORIGIN + "'},"
                + "events:{onReady:function(e){e.target.mute();e.target.playVideo();"
                + "TorrentPlayer.onReady(e.target.getDuration(),e.target.isMuted());"
                + "setInterval(report,500);},"
                + "onStateChange:function(e){if(e.data===0){TorrentPlayer.onEnded();}},"
                + "onError:function(e){TorrentPlayer.onPlayerError(String(e.data));}}"
                + "});}</script></body></html>";
        webView.loadDataWithBaseURL(
                TRAILER_PAGE_ORIGIN + "/trailer.html",
                html, "text/html", "UTF-8", null);
    }

    private final class TrailerJavascriptBridge {
        @JavascriptInterface
        public void onPlayerError(String code) {
            runOnUiThread(() -> {
                android.util.Log.w("DetailActivity",
                        "YouTube embedded player error: " + code);
                trailerEmbedFailed = true;
                stopInlineTrailer();
                renderBackdrop();
                Toast.makeText(DetailActivity.this,
                        R.string.detail_video_embed_error, Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void onReady(double duration, boolean muted) {
            runOnUiThread(() -> updateTrailerProgress(0, duration, muted));
        }

        @JavascriptInterface
        public void onProgress(double current, double duration, boolean muted) {
            runOnUiThread(() -> updateTrailerProgress(current, duration, muted));
        }

        @JavascriptInterface
        public void onEnded() {
            runOnUiThread(() -> {
                trailerPlaying = false;
                if (fullscreenOverlay != null) exitTrailerFullscreen();
                if (b != null) b.mediaPager.setCurrentItem(0, true);
            });
        }
    }

    private void openFeaturedVideo() {
        TMDBVideo video = featuredVideo;
        if (video == null || !video.isYouTube()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(video.watchUrl())));
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.detail_video_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopInlineTrailer() {
        trailerPlaying = false;
        pendingTrailerStart = false;
        WebView webView = trailerWebView;
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
        }
        if (b != null) {
            b.backdropScrim.setAlpha(1f);
            b.mediaPager.setCurrentItem(0, false);
        }
    }

    private void updateMediaPageIndicator(int position) {
        if (b == null || featuredVideo == null || !featuredVideo.isYouTube()) return;
        b.mediaPageIndicator.setText(position == 0
                ? R.string.detail_media_page_backdrop
                : R.string.detail_media_page_trailer);
    }

    private void evaluateTrailerJavascript(String javascript) {
        WebView webView = trailerWebView;
        if (webView != null) webView.evaluateJavascript(javascript, null);
    }

    private void updateTrailerProgress(double current, double duration, boolean muted) {
        trailerDuration = Math.max(0, duration);
        trailerMuted = muted;
        MaterialButton mute = trailerMute;
        if (mute != null) {
            mute.setIconResource(muted
                    ? R.drawable.rounded_volume_off_24
                    : R.drawable.rounded_volume_up_24);
            mute.setContentDescription(getString(muted
                    ? R.string.detail_trailer_unmute
                    : R.string.detail_trailer_mute));
        }
        SeekBar progress = trailerProgress;
        if (progress != null && !userSeeking && trailerDuration > 0) {
            int value = (int) Math.round(
                    progress.getMax() * Math.max(0, current) / trailerDuration);
            progress.setProgress(Math.min(progress.getMax(), value));
        }
    }

    private void toggleTrailerFullscreen() {
        if (fullscreenOverlay == null) enterTrailerFullscreen();
        else exitTrailerFullscreen();
    }

    private void enterTrailerFullscreen() {
        ViewGroup surface = trailerSurface;
        MaterialButton fullscreen = trailerFullscreen;
        if (surface == null || fullscreen == null || surface.getParent() == null) return;

        trailerNormalParent = (ViewGroup) surface.getParent();
        trailerNormalIndex = trailerNormalParent.indexOfChild(surface);
        trailerNormalLayoutParams = surface.getLayoutParams();
        trailerNormalParent.removeView(surface);

        FrameLayout content = findViewById(android.R.id.content);
        fullscreenOverlay = new FrameLayout(this);
        fullscreenOverlay.setBackgroundColor(android.graphics.Color.BLACK);
        content.addView(fullscreenOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenOverlay.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreen.setIconResource(R.drawable.rounded_fullscreen_exit_24);
        fullscreen.setContentDescription(getString(R.string.detail_trailer_exit_fullscreen));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .hide(WindowInsetsCompat.Type.systemBars());
    }

    private void exitTrailerFullscreen() {
        FrameLayout overlay = fullscreenOverlay;
        ViewGroup surface = trailerSurface;
        ViewGroup parent = trailerNormalParent;
        if (overlay == null || surface == null || parent == null) return;

        overlay.removeView(surface);
        ViewGroup overlayParent = (ViewGroup) overlay.getParent();
        if (overlayParent != null) overlayParent.removeView(overlay);
        parent.addView(surface, Math.min(trailerNormalIndex, parent.getChildCount()),
                trailerNormalLayoutParams);
        fullscreenOverlay = null;
        trailerNormalParent = null;
        trailerNormalLayoutParams = null;
        if (trailerFullscreen != null) {
            trailerFullscreen.setIconResource(R.drawable.rounded_fullscreen_24);
            trailerFullscreen.setContentDescription(
                    getString(R.string.detail_trailer_fullscreen));
        }
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .show(WindowInsetsCompat.Type.systemBars());
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
    protected void onPause() {
        trailerHandler.removeCallbacks(autoplayTrailer);
        if (trailerWebView != null) trailerWebView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (b != null) {
            if (trailerWebView != null) trailerWebView.onResume();
            if (!trailerPlaying && featuredVideo != null && featuredVideo.isYouTube()
                    && new PrefsManager(this).isTrailerAutoplayEnabled()) {
                trailerHandler.removeCallbacks(autoplayTrailer);
                trailerHandler.postDelayed(autoplayTrailer, 2500L);
            }
        }
    }

    @Override
    protected void onDestroy() {
        trailerHandler.removeCallbacksAndMessages(null);
        if (fullscreenOverlay != null) exitTrailerFullscreen();
        WebView trailer = trailerWebView;
        if (trailer != null) {
            trailer.stopLoading();
            trailer.loadUrl("about:blank");
            trailer.removeAllViews();
            trailer.destroy();
        }
        trailerWebView = null;
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
