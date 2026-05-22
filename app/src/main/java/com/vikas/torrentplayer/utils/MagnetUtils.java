package com.vikas.torrentplayer.utils;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.vikas.torrentplayer.R;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for handing off magnet URIs to an external torrent client.
 *
 * <p>Magnet handling is standardised — any installed BitTorrent client registers
 * an intent filter for {@code magnet:}, so we just fire an ACTION_VIEW. If no
 * handler is present we fall back to copying the link to the clipboard so the
 * user can paste it elsewhere.
 */
public final class MagnetUtils {

    private MagnetUtils() {}

    public static void openMagnet(Context ctx, String magnetUrl) {
        if (magnetUrl == null || magnetUrl.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(magnetUrl));
        // Allow the user to pick their client every time (useful when multiple are installed)
        Intent chooser = Intent.createChooser(intent, ctx.getString(R.string.action_open_external));
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(chooser);
        } catch (ActivityNotFoundException e) {
            // No magnet handler — copy to clipboard so the user can paste it
            copyMagnet(ctx, magnetUrl);
            Toast.makeText(ctx, R.string.magnet_no_handler, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Pulls every {@code tr=…} tracker URL out of a magnet URI. We use this as
     * a fallback when libtorrent's live tracker list is still empty (e.g. when
     * a torrent has just been added and hasn't announced yet).
     */
    public static List<String> parseTrackers(String magnetUrl) {
        List<String> out = new ArrayList<>();
        if (magnetUrl == null || magnetUrl.isEmpty()) return out;
        int q = magnetUrl.indexOf('?');
        if (q < 0) return out;
        String query = magnetUrl.substring(q + 1);
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq);
            if (!"tr".equalsIgnoreCase(key)) continue;
            try {
                String value = URLDecoder.decode(
                        part.substring(eq + 1), StandardCharsets.UTF_8.name());
                if (!value.isEmpty()) out.add(value);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static void copyMagnet(Context ctx, String magnetUrl) {
        if (magnetUrl == null || magnetUrl.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("magnet", magnetUrl));
            Toast.makeText(ctx, R.string.magnet_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
