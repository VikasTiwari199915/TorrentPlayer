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
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.MagnetUtils;

import org.libtorrent4j.AnnounceEntry;
import org.libtorrent4j.TorrentHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DetailsTrackersFragment extends Fragment {

    private static final String ARG_HASH = "hash";
    private static final long REFRESH_MS = 2000;

    public static DetailsTrackersFragment newInstance(String hash) {
        DetailsTrackersFragment f = new DetailsTrackersFragment();
        Bundle b = new Bundle();
        b.putString(ARG_HASH, hash);
        f.setArguments(b);
        return f;
    }

    private FragmentDetailsListBinding b;
    private TrackerAdapter adapter;
    private final Handler poller = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refresh();
            poller.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDetailsListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new TrackerAdapter();
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);
        refresh();
    }

    @Override public void onResume() { super.onResume(); poller.postDelayed(refresh, REFRESH_MS); }
    @Override public void onPause()  { super.onPause();  poller.removeCallbacks(refresh); }

    private void refresh() {
        if (b == null) return;
        String hash = requireArguments().getString(ARG_HASH);

        // Try the live handle first — it knows announce state.
        List<TrackerRow> rows = new ArrayList<>();
        TorrentHandle th = TorrentManager.get().handleFor(hash);
        if (th != null) {
            try {
                List<AnnounceEntry> live = th.trackers();
                if (live != null) {
                    for (AnnounceEntry e : live) {
                        rows.add(new TrackerRow(e.url(), "tier " + e.tier(), true));
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Fallback: parse trackers out of the magnet URL. libtorrent doesn't
        // expose the .torrent file's static announce-list cleanly in 2.x, but
        // the magnet URI carries them in tr= parameters.
        if (rows.isEmpty()) {
            DownloadHandle dh = TorrentManager.get().findByHash(hash);
            if (dh != null && dh.magnetUrl != null) {
                for (String url : MagnetUtils.parseTrackers(dh.magnetUrl)) {
                    rows.add(new TrackerRow(url, "from magnet", false));
                }
            }
        }

        if (rows.isEmpty()) {
            b.recycler.setVisibility(View.GONE);
            b.empty.setText("No trackers");
            b.empty.setVisibility(View.VISIBLE);
            return;
        }
        b.empty.setVisibility(View.GONE);
        b.recycler.setVisibility(View.VISIBLE);
        adapter.submit(rows);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poller.removeCallbacks(refresh);
        b = null;
    }

    static class TrackerRow {
        final String url;
        final String detail;
        final boolean live;
        TrackerRow(String url, String detail, boolean live) {
            this.url = url;
            this.detail = detail;
            this.live = live;
        }
    }

    private static class TrackerAdapter extends RecyclerView.Adapter<TrackerAdapter.VH> {
        private List<TrackerRow> items = Collections.emptyList();

        void submit(List<TrackerRow> list) {
            items = list;
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
            TrackerRow row = items.get(position);
            TextView t1 = holder.itemView.findViewById(android.R.id.text1);
            TextView t2 = holder.itemView.findViewById(android.R.id.text2);
            t1.setText(row.url);
            t1.setMaxLines(1);
            t1.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            t2.setText(row.detail);
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }
}
