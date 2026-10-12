package com.vikas.torrentplayer.ui.torbox;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.torbox.*;
import com.vikas.torrentplayer.utils.FormatUtils;
import java.util.*;

public class TorBoxLibraryFragment extends Fragment {
    private TextView status;
    private Adapter adapter;
    private int requestGeneration;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        MaterialToolbar toolbar = new MaterialToolbar(requireContext());
        toolbar.setTitle("TorBox");
        toolbar.getMenu().add("Refresh").setOnMenuItemClickListener(item -> { refresh(); return true; });
        root.addView(toolbar);
        status = new TextView(requireContext());
        status.setPadding(24, 16, 24, 16);
        root.addView(status);
        RecyclerView list = new RecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setPadding(0, 0, 0, Math.round(100 * getResources().getDisplayMetrics().density));
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TorBoxManager.get().init(requireContext());
        refresh();
        return root;
    }

    private void refresh() {
        int generation = ++requestGeneration;
        status.setText("Loading...");
        TorBoxManager.get().listAccount(new TorBoxManager.Callback<List<TorBoxClient.TbTorrent>>() {
            public void onResult(List<TorBoxClient.TbTorrent> items) {
                if (status == null || generation != requestGeneration) return;
                adapter.items = items;
                adapter.notifyDataSetChanged();
                status.setText(items.isEmpty() ? "No torrents in your TorBox library" : items.size() + " torrents");
            }
            public void onError(String message) {
                if (status != null && generation == requestGeneration) status.setText(message);
            }
        });
    }

    private void actions(TorBoxClient.TbTorrent torrent) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(torrent.name)
                .setItems(new String[]{"Play / select file", "Download full torrent", "Delete from TorBox"}, (dialog, index) -> {
                    if (index == 0) TorBoxFileChooser.show(requireActivity(), torrent, torrent.name);
                    else if (index == 1) TorBoxFileChooser.downloadAll(requireActivity(), torrent, torrent.name);
                    else new MaterialAlertDialogBuilder(requireContext()).setTitle("Delete from TorBox?")
                            .setMessage(torrent.name).setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, w) -> TorBoxManager.get().deleteFromAccount(torrent.id,
                                    new TorBoxManager.Callback<Void>() {
                                        public void onResult(Void result) { if (status != null) refresh(); }
                                        public void onError(String message) { if (status != null) status.setText(message); }
                                    })).show();
                }).show();
    }

    @Override public void onDestroyView() { ++requestGeneration; status = null; adapter = null; super.onDestroyView(); }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<TorBoxClient.TbTorrent> items = Collections.emptyList();
        @NonNull public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false)) {};
        }
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TorBoxClient.TbTorrent t = items.get(position);
            ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText(t.name);
            ((TextView) holder.itemView.findViewById(android.R.id.text2)).setText(
                    FormatUtils.humanBytes(t.size) + " | " + (t.cached ? "Cached" : t.downloadState)
                            + " | " + Math.round(t.progress * 100) + "% | " + t.files.size() + " files");
            holder.itemView.setOnClickListener(v -> actions(t));
        }
        public int getItemCount() { return items.size(); }
    }
}
