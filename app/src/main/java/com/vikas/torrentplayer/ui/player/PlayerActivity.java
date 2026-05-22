package com.vikas.torrentplayer.ui.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.google.common.collect.ImmutableList;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.ActivityPlayerBinding;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentDataSource;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;
import com.vikas.torrentplayer.utils.MagnetUtils;

import org.libtorrent4j.TorrentHandle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Video player. Receives an infoHash and observes the {@link DownloadHandle} from
 * {@link TorrentManager}. Starts playback as soon as the torrent provides a video file.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private static final String EXTRA_INFO_HASH = "extra_info_hash";

    public static void start(Context ctx, String infoHash) {
        Intent i = new Intent(ctx, PlayerActivity.class);
        i.putExtra(EXTRA_INFO_HASH, infoHash);
        ctx.startActivity(i);
    }

    private ActivityPlayerBinding b;
    private ExoPlayer player;
    private DownloadHandle handle;
    private boolean playbackStarted = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        b = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        // Fullscreen, immersive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insets.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insets.hide(WindowInsetsCompat.Type.systemBars());

        String hash = getIntent().getStringExtra(EXTRA_INFO_HASH);
        handle = TorrentManager.get().findByHash(hash);
        if (handle == null) {
            finish();
            return;
        }

        setupPlayer();
        observeHandle();

        b.btnOpenExternal.setOnClickListener(v -> {
            if (handle != null) MagnetUtils.openMagnet(this, handle.magnetUrl);
        });
    }

    private void setupPlayer() {
        ExoPlayer.Builder builder = new ExoPlayer.Builder(this);

        // If we have everything we need to back ExoPlayer with our torrent-aware
        // DataSource, wire it up. This is what makes MP4 / non-fast-start
        // streaming actually work — reads block until the relevant piece is on
        // disk, never returning the file's sparse-zero region as if it were
        // valid bytes.
        TorrentManager.VideoFileLayout layout = handle != null
                ? TorrentManager.get().videoFileLayout(handle.infoHash)
                : null;
        TorrentHandle th = handle != null
                ? TorrentManager.get().handleFor(handle.infoHash)
                : null;
        if (layout != null && th != null) {
            TorrentDataSource.Factory factory = new TorrentDataSource.Factory(
                    layout.file, th, layout.pieceLength, layout.fileOffsetInTorrent);
            DefaultMediaSourceFactory msf = new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(factory);
            builder.setMediaSourceFactory(msf);
        }

        player = builder.build();
        b.playerView.setPlayer(player);
        player.setPlayWhenReady(true);
    }

    private void observeHandle() {
        handle.state.observe(this, state -> {
            if (state == null) return;
            switch (state) {
                case READY:
                    File f = handle.videoFile.getValue();
                    if (f != null && !playbackStarted) {
                        beginPlayback(f);
                    }
                    break;
                case ERROR:
                    b.loadingTitle.setText(R.string.search_error);
                    String err = handle.errorMessage.getValue();
                    if (err != null) b.loadingProgress.setText(err);
                    b.loadingSpinner.setVisibility(View.GONE);
                    break;
                case STARTING:
                    b.loadingTitle.setText(R.string.player_starting_stream);
                    break;
                case BUFFERING:
                    b.loadingTitle.setText(R.string.player_buffering);
                    break;
                default:
                    break;
            }
        });

        handle.videoFile.observe(this, file -> {
            if (file != null && !playbackStarted) beginPlayback(file);
        });

        handle.progress.observe(this, p -> {
            if (p == null) return;
            b.loadingProgress.setText(getString(
                    R.string.player_download_progress,
                    p.percent,
                    FormatUtils.humanSpeed(p.downloadSpeed)));
        });
    }

    private void beginPlayback(File file) {
        if (player == null) return;
        playbackStarted = true;

        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.fromFile(file));

        // Side-load any external subtitle files we found inside the torrent
        List<File> subs = handle.subtitleFiles.getValue();
        if (subs != null && !subs.isEmpty()) {
            List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
            for (File s : subs) {
                if (!s.exists() || s.length() == 0) continue;
                String mime = mimeForSubtitle(s.getName());
                if (mime == null) continue;
                configs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(s))
                        .setMimeType(mime)
                        .setLanguage(languageGuess(s.getName()))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build());
            }
            if (!configs.isEmpty()) {
                builder.setSubtitleConfigurations(ImmutableList.copyOf(configs));
            }
        }

        player.setMediaItem(builder.build());
        player.prepare();
        player.play();
        b.loadingOverlay.setVisibility(View.GONE);
    }

    @Nullable
    private static String mimeForSubtitle(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".srt")) return MimeTypes.APPLICATION_SUBRIP;
        if (lower.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (lower.endsWith(".ass") || lower.endsWith(".ssa")) return MimeTypes.TEXT_SSA;
        return null;
    }

    /** Best-effort language guess from a filename like "movie.en.srt". */
    @Nullable
    private static String languageGuess(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        String stem = name.substring(0, dot);
        int dot2 = stem.lastIndexOf('.');
        if (dot2 <= 0) return null;
        String code = stem.substring(dot2 + 1);
        // 2- or 3-letter ISO code in the second-to-last segment
        if (code.length() == 2 || code.length() == 3) return code.toLowerCase(Locale.US);
        return null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && playbackStarted) player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        // Note: we deliberately do NOT stop the torrent here — the user may want it
        // to keep downloading in the background. Stop via the Downloads tab.
        b = null;
    }
}
