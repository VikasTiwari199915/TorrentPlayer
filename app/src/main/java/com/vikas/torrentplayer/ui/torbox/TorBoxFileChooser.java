package com.vikas.torrentplayer.ui.torbox;

import android.app.Activity;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.torbox.TorBoxClient;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.ui.player.PlayerActivity;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Material dialog for picking a file inside a TorBox torrent and choosing to
 * stream it (instant) or download it to the device. Shared by the detail screen
 * and the TorBox library.
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
        if (files.size() == 1) { showFileActions(ctx, torrent, files.get(0), title); return; }

        CharSequence[] labels = new CharSequence[files.size()];
        for (int i = 0; i < files.size(); i++) {
            TorBoxClient.TbFile f = files.get(i);
            String name = f.shortName != null ? f.shortName : f.name;
            labels[i] = (f.isVideo() ? "🎬 " : "📄 ") + name + "\n" + FormatUtils.humanBytes(f.size);
        }
        new MaterialAlertDialogBuilder(ctx)
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
            actions.add(() -> stream(ctx, torrent.id, file.id));
        }
        labels.add("Download to device");
        actions.add(() -> {
            TorBoxManager.get().downloadFile(torrent.id, file.id,
                    file.shortName != null ? file.shortName : file.name, file.size, title);
            Toast.makeText(ctx, "Downloading — track it in Downloads", Toast.LENGTH_LONG).show();
        });
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(file.shortName != null ? file.shortName : file.name)
                .setItems(labels.toArray(new CharSequence[0]),
                        (d, which) -> actions.get(which).run())
                .show();
    }

    private static void stream(Activity ctx, long torrentId, int fileId) {
        Toast.makeText(ctx, "Opening stream…", Toast.LENGTH_SHORT).show();
        TorBoxManager.get().resolveStreamUrl(torrentId, fileId,
                new TorBoxManager.Callback<String>() {
                    @Override public void onResult(String url) {
                        if (!ctx.isFinishing()) PlayerActivity.startUrl(ctx, url);
                    }
                    @Override public void onError(String message) {
                        if (!ctx.isFinishing()) Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
