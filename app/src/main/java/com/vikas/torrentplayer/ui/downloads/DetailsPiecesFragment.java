package com.vikas.torrentplayer.ui.downloads;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vikas.torrentplayer.databinding.FragmentDetailsPiecesBinding;
import com.vikas.torrentplayer.service.TorrentDownloadService;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.Locale;

public class DetailsPiecesFragment extends Fragment {

    private static final String ARG_HASH = "hash";
    private static final long REFRESH_MS = 1000;

    public static DetailsPiecesFragment newInstance(String hash) {
        DetailsPiecesFragment f = new DetailsPiecesFragment();
        Bundle b = new Bundle();
        b.putString(ARG_HASH, hash);
        f.setArguments(b);
        return f;
    }

    private FragmentDetailsPiecesBinding b;
    private String hash;
    private boolean statsRequestPending;
    private final Handler poller = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (b == null) return;
            refresh();
            poller.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDetailsPiecesBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        hash = requireArguments().getString(ARG_HASH);
        b.verifyPieces.setOnClickListener(v -> {
            TorrentDownloadService.start(requireContext());
            TorrentManager.get().forceRecheck(hash);
            Toast.makeText(requireContext(), "Verifying torrent pieces…", Toast.LENGTH_SHORT).show();
        });
        b.restartDownload.setOnClickListener(v -> {
            TorrentDownloadService.start(requireContext());
            TorrentManager.get().restartDownload(hash);
            Toast.makeText(requireContext(), "Restarting download…", Toast.LENGTH_SHORT).show();
        });
        refresh();
    }

    @Override public void onResume() { super.onResume(); poller.postDelayed(refresh, REFRESH_MS); }
    @Override public void onPause() { super.onPause(); poller.removeCallbacks(refresh); }

    private void refresh() {
        if (statsRequestPending || b == null) return;
        statsRequestPending = true;
        TorrentManager.get().loadPieceStats(hash, stats -> {
            statsRequestPending = false;
            if (b == null) return;
            if (stats == null) {
                setTextIfChanged(b.piecesSummary, "Waiting for metadata…");
                return;
            }
            int total = stats.states.length;
            b.pieceMap.setPieceStates(stats.states);
            setTextIfChanged(b.piecesSummary,
                    stats.have + " / " + total + " pieces  ·  "
                            + FormatUtils.humanBytes(stats.pieceLength) + " each");
            String availabilityText =
                    stats.available >= 0 ? String.valueOf(stats.available) : "—";
            setTextIfChanged(b.piecesDetail, String.format(Locale.US,
                    "Missing: %d  ·  Active: %d  ·  Skipped: %d  ·  Available from peers: %s  ·  Unavailable missing: %d",
                    stats.missing, stats.active, stats.skipped, availabilityText,
                    stats.unavailableMissing));
            setTextIfChanged(b.missingPieces, stats.missing == 0
                    ? "All wanted pieces are complete."
                    : "Missing indexes: " + stats.missingIndexes
                            + (stats.missingIndexesTruncated ? "…" : ""));
        });
    }

    private static void setTextIfChanged(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poller.removeCallbacks(refresh);
        b = null;
    }
}
