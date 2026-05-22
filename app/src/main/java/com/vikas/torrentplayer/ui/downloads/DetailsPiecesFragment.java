package com.vikas.torrentplayer.ui.downloads;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vikas.torrentplayer.databinding.FragmentDetailsPiecesBinding;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;

import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;

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
        boolean[] map = new boolean[total];
        int have = 0;
        for (int i = 0; i < total; i++) {
            boolean h = th.havePiece(i);
            map[i] = h;
            if (h) have++;
        }
        b.pieceMap.setPieces(map);
        b.piecesSummary.setText(have + " / " + total + " pieces  ·  "
                + FormatUtils.humanBytes(ti.pieceLength()) + " each");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poller.removeCallbacks(refresh);
        b = null;
    }
}
