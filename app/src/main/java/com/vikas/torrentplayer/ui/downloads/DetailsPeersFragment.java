package com.vikas.torrentplayer.ui.downloads;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.databinding.FragmentDetailsListBinding;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;

import org.libtorrent4j.PeerInfo;
import org.libtorrent4j.TorrentHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DetailsPeersFragment extends Fragment {

    private static final String ARG_HASH = "hash";
    private static final long REFRESH_MS = 1500;

    public static DetailsPeersFragment newInstance(String hash) {
        DetailsPeersFragment f = new DetailsPeersFragment();
        Bundle b = new Bundle();
        b.putString(ARG_HASH, hash);
        f.setArguments(b);
        return f;
    }

    private FragmentDetailsListBinding b;
    private PeerAdapter adapter;
    private final Handler poller = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refresh();
            poller.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentDetailsListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new PeerAdapter();
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);
        refresh();
    }

    @Override public void onResume() { super.onResume(); poller.postDelayed(refresh, REFRESH_MS); }
    @Override public void onPause() { super.onPause(); poller.removeCallbacks(refresh); }

    private void refresh() {
        if (b == null) return;
        String hash = requireArguments().getString(ARG_HASH);
        TorrentHandle th = TorrentManager.get().handleFor(hash);
        if (th == null) {
            showEmpty("Waiting for torrent…");
            return;
        }
        List<PeerInfo> peers;
        try {
            peers = th.peerInfo();
        } catch (Throwable t) {
            showEmpty("Peer details unavailable");
            return;
        }
        if (peers == null || peers.isEmpty()) {
            showEmpty("No connected peers");
            return;
        }
        List<PeerRow> rows = new ArrayList<>();
        for (PeerInfo p : peers) {
            if (p == null) continue;
            rows.add(new PeerRow(p));
        }
        Collections.sort(rows, (a, c) ->
                Integer.compare(c.downSpeed + c.upSpeed, a.downSpeed + a.upSpeed));
        b.empty.setVisibility(View.GONE);
        b.recycler.setVisibility(View.VISIBLE);
        adapter.submit(rows);
    }

    private void showEmpty(String text) {
        b.recycler.setVisibility(View.GONE);
        b.empty.setText(text);
        b.empty.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poller.removeCallbacks(refresh);
        b = null;
    }

    static class PeerRow {
        final String ip;
        final String client;
        final int downSpeed;
        final int upSpeed;
        final long totalDownload;
        final long totalUpload;
        final int progressPpm;
        final String connectionType;

        PeerRow(PeerInfo p) {
            ip = p.ip();
            client = p.client();
            downSpeed = p.downSpeed();
            upSpeed = p.upSpeed();
            totalDownload = p.totalDownload();
            totalUpload = p.totalUpload();
            progressPpm = p.progressPpm();
            connectionType = p.connectionType() == null ? "" : p.connectionType().name();
        }
    }

    private static class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.VH> {
        private List<PeerRow> items = Collections.emptyList();

        void submit(List<PeerRow> list) {
            items = list == null ? Collections.emptyList() : list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            v.setPadding(48, 16, 48, 16);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            PeerRow row = items.get(position);
            TextView t1 = holder.itemView.findViewById(android.R.id.text1);
            TextView t2 = holder.itemView.findViewById(android.R.id.text2);
            t1.setText((row.ip == null || row.ip.isEmpty() ? "Unknown peer" : row.ip)
                    + (row.client == null || row.client.isEmpty() ? "" : "  ·  " + row.client));
            t1.setSingleLine(true);
            t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
            String progress = String.format(Locale.US, "%.1f%%", row.progressPpm / 10000f);
            t2.setText("Down " + FormatUtils.humanSpeed(row.downSpeed)
                    + "  ·  Up " + FormatUtils.humanSpeed(row.upSpeed)
                    + "  ·  " + progress
                    + "  ·  Total " + FormatUtils.humanBytes(row.totalDownload)
                    + " / " + FormatUtils.humanBytes(row.totalUpload)
                    + (row.connectionType.isEmpty() ? "" : "  ·  " + row.connectionType));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }
}
