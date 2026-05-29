package com.vikas.torrentplayer.tv.torbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import com.vikas.torrentplayer.torbox.TorBoxClient;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.tv.player.TvPlayerActivity;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared D-pad dialog for picking a file inside a TorBox torrent and choosing to
 * stream it (instant, HTTP range requests — works for MP4 and MKV) or download
 * it to the device. Used by both the details screen and the TorBox library.
 */
public final class TorBoxFileChooser {

    private TorBoxFileChooser() {}

    public static void show(Activity ctx, TorBoxClient.TbTorrent torrent, String title) {
        if (torrent == null || torrent.files == null || torrent.files.isEmpty()) {
            Toast.makeText(ctx, "No files in this torrent", Toast.LENGTH_LONG).show();
            return;
        }
        TorBoxManager.get().init(ctx.getApplicationContext());

        final List<TorBoxClient.TbFile> files = torrent.files;
        CharSequence[] labels = new CharSequence[files.size()];
        for (int i = 0; i < files.size(); i++) {
            TorBoxClient.TbFile f = files.get(i);
            String name = f.shortName != null ? f.shortName : f.name;
            labels[i] = (f.isVideo() ? "🎬 " : "📄 ") + name
                    + "\n" + FormatUtils.humanBytes(f.size);
        }

        // Single file → skip straight to the actions.
        if (files.size() == 1) {
            showFileActions(ctx, torrent, files.get(0), title);
            return;
        }
        new AlertDialog.Builder(ctx)
                .setTitle(torrent.name != null && !torrent.name.isEmpty() ? torrent.name : title)
                .setItems(labels, (d, which) -> showFileActions(ctx, torrent, files.get(which), title))
                .show();
    }

    private static void showFileActions(Activity ctx, TorBoxClient.TbTorrent torrent,
                                        TorBoxClient.TbFile file, String title) {
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (file.isVideo()) {
            labels.add("Stream now (full speed)");
            actions.add(() -> stream(ctx, torrent.id, file.id, title));
        }
        labels.add("Download to device");
        actions.add(() -> {
            TorBoxManager.get().downloadFile(torrent.id, file.id,
                    file.shortName != null ? file.shortName : file.name, title);
            Toast.makeText(ctx, "Downloading — track it in Downloads", Toast.LENGTH_LONG).show();
        });

        new AlertDialog.Builder(ctx)
                .setTitle(file.shortName != null ? file.shortName : file.name)
                .setItems(labels.toArray(new CharSequence[0]),
                        (d, which) -> actions.get(which).run())
                .show();
    }

    private static void stream(Activity ctx, long torrentId, int fileId, String title) {
        Toast.makeText(ctx, "Opening stream…", Toast.LENGTH_SHORT).show();
        TorBoxManager.get().resolveStreamUrl(torrentId, fileId,
                new TorBoxManager.Callback<String>() {
                    @Override public void onResult(String url) {
                        if (ctx.isFinishing()) return;
                        TvPlayerActivity.startUrl(ctx, url, title);
                    }
                    @Override public void onError(String message) {
                        if (ctx.isFinishing()) return;
                        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
