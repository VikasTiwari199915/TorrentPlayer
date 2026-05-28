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
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
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

    public static void start(Context ctx, String infoHash) {
        Intent i = new Intent(ctx, TvPlayerActivity.class);
        i.putExtra(EXTRA_HASH, infoHash);
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

    private void setupPlayer() {
        ExoPlayer.Builder b = new ExoPlayer.Builder(this);

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
        player.setPlayWhenReady(true);
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
