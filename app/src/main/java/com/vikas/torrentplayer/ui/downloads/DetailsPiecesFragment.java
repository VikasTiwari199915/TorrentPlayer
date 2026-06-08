package com.vikas.torrentplayer.ui.downloads;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vikas.torrentplayer.databinding.FragmentDetailsPiecesBinding;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;

import org.libtorrent4j.PartialPieceInfo;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
            TorrentManager.get().forceRecheck(hash);
            Toast.makeText(requireContext(), "Verifying torrent pieces…", Toast.LENGTH_SHORT).show();
        });
        refresh();
    }

    @Override public void onResume() { super.onResume(); poller.postDelayed(refresh, REFRESH_MS); }
    @Override public void onPause() { super.onPause(); poller.removeCallbacks(refresh); }

    private void refresh() {
        TorrentInfo ti = TorrentManager.get().torrentInfoFor(hash);
        TorrentHandle th = TorrentManager.get().handleFor(hash);
        if (ti == null || !ti.isValid() || th == null) {
            b.piecesSummary.setText("Waiting for metadata…");
            return;
        }
        int total = ti.numPieces();
        int[] states = new int[total];
        int have = 0;
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < total; i++) {
            boolean h = th.havePiece(i);
            if (h) {
                states[i] = 1;
                have++;
            } else if (missing.length() < 180) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(i);
            }
        }

        int active = 0;
        try {
            List<PartialPieceInfo> queue = th.getDownloadQueue();
            Set<Integer> activePieces = new HashSet<>();
            if (queue != null) {
                for (PartialPieceInfo p : queue) {
                    int idx = p.pieceIndex();
                    if (idx >= 0 && idx < total && states[idx] == 0) {
                        states[idx] = 2;
                        activePieces.add(idx);
                    }
                }
            }
            active = activePieces.size();
        } catch (Throwable ignored) {}

        int available = -1;
        int rareMissing = 0;
        try {
            int[] availability = th.pieceAvailability();
            if (availability != null) {
                available = 0;
                for (int i = 0; i < Math.min(total, availability.length); i++) {
                    if (availability[i] > 0) available++;
                    else if (states[i] == 0) rareMissing++;
                }
            }
        } catch (Throwable ignored) {}

        int missingCount = Math.max(0, total - have);
        b.pieceMap.setPieceStates(states);
        b.piecesSummary.setText(have + " / " + total + " pieces  ·  "
                + FormatUtils.humanBytes(ti.pieceLength()) + " each");
        String availabilityText = available >= 0 ? String.valueOf(available) : "—";
        b.piecesDetail.setText(String.format(Locale.US,
                "Missing: %d  ·  Active: %d  ·  Available from peers: %s  ·  Unavailable missing: %d",
                missingCount, active, availabilityText, rareMissing));
        b.missingPieces.setText(missingCount == 0
                ? "All pieces are complete."
                : "Missing indexes: " + missing
                        + (missing.length() >= 180 ? "…" : ""));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poller.removeCallbacks(refresh);
        b = null;
    }
}
