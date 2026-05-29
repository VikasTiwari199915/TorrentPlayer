package com.vikas.torrentplayer.tv.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentDataSource;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.tv.R;
import com.vikas.torrentplayer.utils.FormatUtils;

import org.libtorrent4j.TorrentHandle;

import java.io.File;

/**
 * TV player. Same engine as the phone version: TorrentDataSource for active
 * torrents (blocks reads on undownloaded pieces), plain file IO for finished
 * downloads. Leanback's built-in PlayerView controller handles d-pad nicely.
 */
@OptIn(markerClass = UnstableApi.class)
public class TvPlayerActivity extends FragmentActivity {

    private static final String EXTRA_HASH = "hash";
    private static final String EXTRA_FILE = "file";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_GROW_FILE = "grow_file";
    private static final String EXTRA_GROW_SIZE = "grow_size";
    private static final String EXTRA_TITLE = "title";

    public static void start(Context ctx, String infoHash) {
        Intent i = new Intent(ctx, TvPlayerActivity.class);
        i.putExtra(EXTRA_HASH, infoHash);
        ctx.startActivity(i);
    }

    /** Play a fully-downloaded local file directly (e.g. a TorBox download). */
    public static void startFile(Context ctx, String absPath, String title) {
        Intent i = new Intent(ctx, TvPlayerActivity.class);
        i.putExtra(EXTRA_FILE, absPath);
        i.putExtra(EXTRA_TITLE, title);
        ctx.startActivity(i);
    }

    /** Stream a remote HTTPS URL directly (e.g. a cached TorBox file) — ExoPlayer
     *  uses HTTP range requests, so MP4 and MKV both start without downloading. */
    public static void startUrl(Context ctx, String url, String title) {
        Intent i = new Intent(ctx, TvPlayerActivity.class);
        i.putExtra(EXTRA_URL, url);
        i.putExtra(EXTRA_TITLE, title);
        ctx.startActivity(i);
    }

    /** Play a still-downloading local file (grows sequentially). Best for MKV. */
    public static void startGrowingFile(Context ctx, String absPath, long totalSize, String title) {
        Intent i = new Intent(ctx, TvPlayerActivity.class);
        i.putExtra(EXTRA_GROW_FILE, absPath);
        i.putExtra(EXTRA_GROW_SIZE, totalSize);
        i.putExtra(EXTRA_TITLE, title);
        ctx.startActivity(i);
    }

    private PlayerView playerView;
    private LinearLayout loadingOverlay;
    private TextView loadingTitle, loadingProgress;
    private ExoPlayer player;
    private DownloadHandle handle;
    private boolean playbackStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_player);
        playerView = findViewById(R.id.player_view);
        loadingOverlay = findViewById(R.id.loading_overlay);
        loadingTitle = findViewById(R.id.loading_title);
        loadingProgress = findViewById(R.id.loading_progress);

        // Direct playback (TorBox): a local file, a remote URL, or a growing
        // (still-downloading) local file.
        String filePath = getIntent().getStringExtra(EXTRA_FILE);
        String streamUrl = getIntent().getStringExtra(EXTRA_URL);
        String growFile = getIntent().getStringExtra(EXTRA_GROW_FILE);
        if ((filePath != null && !filePath.isEmpty())
                || (streamUrl != null && !streamUrl.isEmpty())
                || (growFile != null && !growFile.isEmpty())) {
            try {
                if (growFile != null && !growFile.isEmpty()) {
                    playGrowingFile(new File(growFile), getIntent().getLongExtra(EXTRA_GROW_SIZE, 0));
                } else if (filePath != null && !filePath.isEmpty()) {
                    playLocalFile(new File(filePath));
                } else {
                    playDirectUri(Uri.parse(streamUrl));
                }
            } catch (Throwable ex) {
                android.util.Log.e("TvPlayer", "direct playback failed", ex);
                android.widget.Toast.makeText(this,
                        "Player error: " + (ex.getMessage() != null ? ex.getMessage()
                                : ex.getClass().getSimpleName()),
                        android.widget.Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }

        String hash = getIntent().getStringExtra(EXTRA_HASH);
        try {
            TorrentManager.get().init(getApplicationContext());
        } catch (Throwable ignored) { /* idempotent */ }
        handle = TorrentManager.get().findByHash(hash);
        if (handle == null) {
            android.widget.Toast.makeText(this,
                    "Download not found", android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            setupPlayer();
            observeHandle();
        } catch (Throwable ex) {
            android.util.Log.e("TvPlayer", "setup failed", ex);
            android.widget.Toast.makeText(this,
                    "Player error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                    android.widget.Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /** Renderers with decoder fallback enabled so a failing primary audio/video
     *  decoder is retried with an alternate one instead of producing silence /
     *  a black frame. */
    private DefaultRenderersFactory renderersFactory() {
        return new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true);
    }

    private void setupPlayer() {
        ExoPlayer.Builder b = new ExoPlayer.Builder(this, renderersFactory());

        DownloadHandle.State s = handle.state.getValue();
        boolean isActive = s == DownloadHandle.State.STARTING
                || s == DownloadHandle.State.BUFFERING
                || s == DownloadHandle.State.READY;
        if (isActive) {
            TorrentManager.VideoFileLayout layout = TorrentManager.get().videoFileLayout(handle.infoHash);
            TorrentHandle th = TorrentManager.get().handleFor(handle.infoHash);
            if (layout != null && th != null) {
                TorrentDataSource.Factory factory = new TorrentDataSource.Factory(
                        layout.file, th, layout.pieceLength, layout.fileOffsetInTorrent);
                b.setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(factory));
            }
        }
        player = b.build();
        playerView.setPlayer(player);
        attachAudioDiagnostics();
        player.setPlayWhenReady(true);
        // Surface embedded MKV subtitles (and any side-loaded SRT/VTT) to the
        // PlayerView's subtitle button.
        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters()
                        .buildUpon()
                        .setPreferredTextLanguage(java.util.Locale.getDefault().getLanguage())
                        .build());
    }

    private void observeHandle() {
        handle.state.observe(this, state -> {
            if (state == null) return;
            switch (state) {
                case READY:
                case FINISHED:
                    File f = handle.videoFile.getValue();
                    if (f != null && !playbackStarted) beginPlayback(f);
                    break;
                case ERROR:
                    loadingTitle.setText("Stream error");
                    String err = handle.errorMessage.getValue();
                    if (err != null) loadingProgress.setText(err);
                    break;
                case BUFFERING:
                    loadingTitle.setText("Buffering…");
                    break;
                default: break;
            }
        });
        handle.videoFile.observe(this, f -> {
            if (f != null && !playbackStarted) beginPlayback(f);
        });
        handle.progress.observe(this, p -> {
            if (p == null) return;
            loadingProgress.setText("Downloaded " + p.percent + "%  ·  "
                    + FormatUtils.humanSpeed(p.downloadSpeed));
        });
    }

    /** Plain ExoPlayer playback of a finished local file (no TorrentDataSource). */
    private void playLocalFile(File file) {
        if (!file.exists()) {
            android.widget.Toast.makeText(this, "File not found",
                    android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        playDirectUri(Uri.fromFile(file));
    }

    /** Play a file that's still downloading: blocks reads at the current EOF
     *  until the downloader appends more (works best for progressive MKV). */
    private void playGrowingFile(File file, long totalSize) {
        com.vikas.torrentplayer.torbox.GrowingFileDataSource.Factory factory =
                new com.vikas.torrentplayer.torbox.GrowingFileDataSource.Factory(file, totalSize);
        player = new ExoPlayer.Builder(this, renderersFactory())
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(factory))
                .build();
        playerView.setPlayer(player);
        attachAudioDiagnostics();
        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters().buildUpon()
                        .setPreferredTextLanguage(java.util.Locale.getDefault().getLanguage())
                        .build());
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.setPlayWhenReady(true);
        player.prepare();
        player.play();
        playbackStarted = true;
        loadingOverlay.setVisibility(View.GONE);
    }

    /** Plain ExoPlayer playback of any directly-readable URI (local or HTTP). */
    /**
     * Surfaces why audio might be silent: inspects the resolved tracks and, if
     * the file has audio but no track the device can decode, toasts the codec
     * (e.g. "audio/eac3"). Always logs the full audio track list to "TvPlayer".
     */
    private void attachAudioDiagnostics() {
        if (player == null) return;
        player.addListener(new Player.Listener() {
            private boolean reported;
            @Override public void onTracksChanged(@androidx.annotation.NonNull Tracks tracks) {
                if (reported) return;
                int audioGroups = 0, supported = 0;
                String firstCodec = null;
                StringBuilder log = new StringBuilder("audio tracks:");
                for (Tracks.Group g : tracks.getGroups()) {
                    if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
                    for (int i = 0; i < g.length; i++) {
                        audioGroups++;
                        Format f = g.getTrackFormat(i);
                        boolean ok = g.isTrackSupported(i);
                        if (ok) supported++;
                        if (firstCodec == null) firstCodec = f.sampleMimeType;
                        log.append("\n  ").append(f.sampleMimeType)
                                .append(" ch=").append(f.channelCount)
                                .append(" supported=").append(ok)
                                .append(" selected=").append(g.isTrackSelected(i));
                    }
                }
                android.util.Log.i("TvPlayer", log.toString());
                if (audioGroups > 0 && supported == 0) {
                    reported = true;
                    android.widget.Toast.makeText(TvPlayerActivity.this,
                            "No audio: this device can't decode " + firstCodec
                                    + " — try an AAC/AC3 release of this title",
                            android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void playDirectUri(Uri uri) {
        player = new ExoPlayer.Builder(this, renderersFactory()).build();
        playerView.setPlayer(player);
        attachAudioDiagnostics();
        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters().buildUpon()
                        .setPreferredTextLanguage(java.util.Locale.getDefault().getLanguage())
                        .build());
        player.setMediaItem(MediaItem.fromUri(uri));
        player.setPlayWhenReady(true);
        player.prepare();
        player.play();
        playbackStarted = true;
        loadingOverlay.setVisibility(View.GONE);
    }

    private void beginPlayback(File file) {
        if (player == null || playbackStarted) return;
        playbackStarted = true;
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.prepare();
        player.play();
        loadingOverlay.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
