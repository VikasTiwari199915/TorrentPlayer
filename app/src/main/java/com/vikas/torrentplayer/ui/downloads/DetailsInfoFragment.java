package com.vikas.torrentplayer.ui.downloads;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vikas.torrentplayer.databinding.FragmentDetailsInfoBinding;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;

public class DetailsInfoFragment extends Fragment {

    private static final String ARG_HASH = "hash";

    public static DetailsInfoFragment newInstance(String hash) {
        DetailsInfoFragment f = new DetailsInfoFragment();
        Bundle b = new Bundle();
        b.putString(ARG_HASH, hash);
        f.setArguments(b);
        return f;
    }

    private FragmentDetailsInfoBinding b;
    private DownloadHandle handle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDetailsInfoBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String hash = requireArguments().getString(ARG_HASH);
        handle = TorrentManager.get().findByHash(hash);
        if (handle == null) return;

        b.title.setText(handle.title);
        b.infoHash.setText(handle.infoHash);

        handle.progress.observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            b.progressBar.setProgressCompat(p.percent, true);
            b.progressText.setText(p.percent + "%  ·  "
                    + FormatUtils.humanSpeed(p.downloadSpeed)
                    + "  ·  " + p.seeders + " seeders");
            rebuildStats();
        });
        handle.state.observe(getViewLifecycleOwner(), s -> rebuildStats());
        handle.videoFile.observe(getViewLifecycleOwner(), f -> rebuildStats());
        handle.errorMessage.observe(getViewLifecycleOwner(), m -> rebuildStats());
        rebuildStats();
    }

    /** Each "stat" is a label + value stacked vertically. Stable and never clips. */
    private void rebuildStats() {
        if (b == null || handle == null) return;
        LinearLayout container = b.statsContainer;
        container.removeAllViews();
        Context ctx = requireContext();
        DownloadHandle.Progress p = handle.progress.getValue();
        DownloadHandle.State s = handle.state.getValue();

        addStat(container, ctx, "Status", s == null ? "—" : prettyState(s));
        addStat(container, ctx, "Size", FormatUtils.humanBytes(handle.sizeBytes));
        if (handle.quality != null) addStat(container, ctx, "Quality", handle.quality);
        addStat(container, ctx, "Progress", (p == null ? 0 : p.percent) + "%");
        addStat(container, ctx, "Head + tail buffer", (p == null ? 0 : p.bufferProgress) + "%");
        addStat(container, ctx, "Download speed",
                p == null ? "—" : FormatUtils.humanSpeed(p.downloadSpeed));
        addStat(container, ctx, "Seeders", p == null ? "—" : String.valueOf(p.seeders));
        addStat(container, ctx, "Save folder",
                TorrentManager.get().getSaveDir().getAbsolutePath());
        if (handle.videoFile.getValue() != null) {
            addStat(container, ctx, "Video file", handle.videoFile.getValue().getAbsolutePath());
        }
        if (handle.errorMessage.getValue() != null) {
            addStat(container, ctx, "Error", handle.errorMessage.getValue());
        }
    }

    private static String prettyState(DownloadHandle.State s) {
        switch (s) {
            case STARTING:  return "Connecting…";
            case BUFFERING: return "Downloading";
            case READY:     return "Playable, still downloading";
            case PAUSED:    return "Paused";
            case FINISHED:  return "Finished";
            case ERROR:     return "Error";
            default:        return s.name();
        }
    }

    private static void addStat(LinearLayout container, Context ctx, String key, String value) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 12);
        row.setLayoutParams(lp);

        TextView k = new TextView(ctx);
        k.setText(key);
        k.setTextSize(12);
        k.setTextColor(resolveColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant));

        TextView v = new TextView(ctx);
        v.setText(value);
        v.setTextSize(15);
        v.setTextIsSelectable(true);
        v.setTextColor(resolveColor(ctx, com.google.android.material.R.attr.colorOnSurface));

        row.addView(k);
        row.addView(v);
        container.addView(row);
    }

    private static int dp(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static int resolveColor(Context ctx, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        ctx.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
