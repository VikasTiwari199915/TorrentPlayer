package com.vikas.torrentplayer.ui.downloads;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.BuildConfig;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.FragmentDetailsListBinding;
import com.vikas.torrentplayer.databinding.ItemFileTreeBinding;
import com.vikas.torrentplayer.service.TorrentDownloadService;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.tree.FileTreeNode;
import com.vikas.torrentplayer.utils.FormatUtils;
import com.google.android.material.checkbox.MaterialCheckBox;

import org.libtorrent4j.FileStorage;
import org.libtorrent4j.TorrentInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetailsFilesFragment extends Fragment {

    private static final String ARG_HASH = "hash";

    public static DetailsFilesFragment newInstance(String hash) {
        DetailsFilesFragment f = new DetailsFilesFragment();
        Bundle b = new Bundle();
        b.putString(ARG_HASH, hash);
        f.setArguments(b);
        return f;
    }

    private FragmentDetailsListBinding b;
    private TreeAdapter adapter;
    private boolean statsRequestPending;
    private String infoHash;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDetailsListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        infoHash = requireArguments().getString(ARG_HASH);
        TorrentInfo ti = TorrentManager.get().torrentInfoFor(infoHash);

        if (ti == null || !ti.isValid()) {
            b.empty.setText("Torrent metadata not yet available");
            b.empty.setVisibility(View.VISIBLE);
            return;
        }
        FileStorage fs = ti.files();
        if (fs.numFiles() == 0) {
            b.empty.setText("No files");
            b.empty.setVisibility(View.VISIBLE);
            return;
        }

        FileTreeNode root = FileTreeNode.build(fs);
        File baseDir = TorrentManager.get().getSaveDir();

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TreeAdapter(root.flatten(), baseDir, infoHash, requireContext(),
                this::refreshFileStats);
        b.recycler.setAdapter(adapter);

        DownloadHandle download = TorrentManager.get().findByHash(infoHash);
        if (download != null) {
            download.progress.observe(getViewLifecycleOwner(), ignored -> refreshFileStats());
        }
        refreshFileStats();
    }

    private void refreshFileStats() {
        if (statsRequestPending || adapter == null || infoHash == null) return;
        statsRequestPending = true;
        TorrentManager.get().loadFileStats(infoHash, stats -> {
            statsRequestPending = false;
            if (adapter != null && stats != null) adapter.updateStats(stats);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter = null;
        b = null;
    }

    /** Open a file via FileProvider + ACTION_VIEW chooser. */
    static void openFile(Context ctx, File file) {
        if (!file.exists() || file.length() == 0) {
            Toast.makeText(ctx, "File not downloaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(ctx,
                    BuildConfig.APPLICATION_ID + ".fileprovider", file);
        } catch (IllegalArgumentException e) {
            Toast.makeText(ctx, "Couldn't expose file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String mime = mimeForFile(file.getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        if (mime != null) i.setDataAndType(uri, mime);
        else i.setData(uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(Intent.createChooser(i, "Open with"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, "No app to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private static String mimeForFile(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime != null) return mime;
        switch (ext) {
            case "mkv":  return "video/x-matroska";
            case "ts":
            case "m2ts": return "video/mp2t";
            case "flv":  return "video/x-flv";
            case "srt":  return "application/x-subrip";
            case "ass":
            case "ssa":  return "text/plain";
            default:     return null;
        }
    }

    /** Tree-flattened RecyclerView adapter. Each row toggles a libtorrent
     *  file priority and (for files) opens the file on tap. */
    private static class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.VH> {
        private final List<FileTreeNode> rows;
        private final File baseDir;
        private final String infoHash;
        private final Context ctx;
        private final Runnable refreshStats;
        private long[] downloadedBytes = new long[0];
        private boolean[] wanted = new boolean[0];

        TreeAdapter(List<FileTreeNode> rows, File baseDir, String infoHash,
                    Context ctx, Runnable refreshStats) {
            this.rows = rows;
            this.baseDir = baseDir;
            this.infoHash = infoHash;
            this.ctx = ctx;
            this.refreshStats = refreshStats;
        }

        void updateStats(TorrentManager.FileStats stats) {
            downloadedBytes = stats.downloadedBytes;
            wanted = stats.wanted;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemFileTreeBinding b = ItemFileTreeBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            FileTreeNode node = rows.get(position);
            ItemFileTreeBinding b = holder.b;

            int indentPx = (int) (node.level * 18 * ctx.getResources().getDisplayMetrics().density);
            ViewGroup.LayoutParams lp = b.indent.getLayoutParams();
            lp.width = indentPx;
            b.indent.setLayoutParams(lp);

            b.icon.setImageResource(node.isFolder ? R.drawable.rounded_folder_24 : R.drawable.rounded_docs_24);
            b.name.setText(node.name);
            NodeStats stats = statsFor(node);
            b.meta.setText(formatMeta(node, stats));
            b.progress.setProgressCompat(progressPercent(node, stats), false);

            b.toggle.clearOnCheckedStateChangedListeners();
            b.toggle.setCheckedState(stats.checkedState);
            b.toggle.addOnCheckedStateChangedListener((button, state) -> {
                if (state == MaterialCheckBox.STATE_INDETERMINATE) return;
                applyPriority(node, state == MaterialCheckBox.STATE_CHECKED);
            });

            // Tap on the row body to open the file (folders just toggle).
            b.getRoot().setOnClickListener(v -> {
                if (node.isFolder) {
                    boolean enable = b.toggle.getCheckedState() != MaterialCheckBox.STATE_CHECKED;
                    applyPriority(node, enable);
                } else {
                    File f = new File(baseDir, fullPath(node));
                    openFile(ctx, f);
                }
            });
        }

        @Override public int getItemCount() { return rows.size(); }

        /** Reconstruct the libtorrent file path by walking parent pointers. */
        private static String fullPath(FileTreeNode node) {
            StringBuilder sb = new StringBuilder(node.name);
            for (FileTreeNode p = node.parent; p != null && p.level >= 0; p = p.parent) {
                sb.insert(0, '/').insert(0, p.name);
            }
            return sb.toString();
        }

        private NodeStats statsFor(FileTreeNode node) {
            List<Integer> indices = new ArrayList<>();
            node.collectFileIndices(indices);
            int wantedCount = 0;
            long downloaded = 0;
            for (int idx : indices) {
                if (idx >= 0 && idx < wanted.length && wanted[idx]) wantedCount++;
                if (idx >= 0 && idx < downloadedBytes.length) {
                    long fileSize = fileSizeForIndex(idx);
                    downloaded += Math.min(fileSize, Math.max(0, downloadedBytes[idx]));
                }
            }
            int state;
            if (wantedCount == 0) state = MaterialCheckBox.STATE_UNCHECKED;
            else if (wantedCount == indices.size()) state = MaterialCheckBox.STATE_CHECKED;
            else state = MaterialCheckBox.STATE_INDETERMINATE;
            return new NodeStats(state, downloaded);
        }

        private long fileSizeForIndex(int fileIndex) {
            for (FileTreeNode row : rows) {
                if (!row.isFolder && row.fileIndex == fileIndex) return row.size;
            }
            return 0;
        }

        private String formatMeta(FileTreeNode node, NodeStats stats) {
            int pct = progressPercent(node, stats);
            String selection;
            if (stats.checkedState == MaterialCheckBox.STATE_UNCHECKED) selection = "Skipped";
            else if (pct >= 100) selection = "Complete";
            else if (stats.downloadedBytes > 0) selection = "Downloading";
            else selection = "Queued";
            String prefix = node.isFolder ? node.fileCount + " files · " : "";
            return prefix + FormatUtils.humanBytes(stats.downloadedBytes)
                    + " of " + FormatUtils.humanBytes(node.size)
                    + " · " + pct + "% · " + selection;
        }

        private static int progressPercent(FileTreeNode node, NodeStats stats) {
            return node.size <= 0 ? 0
                    : (int) Math.min(100, (stats.downloadedBytes * 100) / node.size);
        }

        private void applyPriority(FileTreeNode node, boolean enable) {
            List<Integer> indices = new ArrayList<>();
            node.collectFileIndices(indices);
            if (indices.isEmpty()) return;
            ensureStatsCapacity(indices);
            for (int idx : indices) wanted[idx] = enable;
            notifyDataSetChanged();
            if (enable) TorrentDownloadService.start(ctx);
            TorrentManager.get().setFilePriority(infoHash, indices, enable);
            refreshStats.run();
        }

        private void ensureStatsCapacity(List<Integer> indices) {
            int required = wanted.length;
            for (int idx : indices) required = Math.max(required, idx + 1);
            if (required > wanted.length) {
                boolean[] expanded = new boolean[required];
                System.arraycopy(wanted, 0, expanded, 0, wanted.length);
                wanted = expanded;
            }
        }

        private static class NodeStats {
            final int checkedState;
            final long downloadedBytes;

            NodeStats(int checkedState, long downloadedBytes) {
                this.checkedState = checkedState;
                this.downloadedBytes = downloadedBytes;
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemFileTreeBinding b;
            VH(@NonNull ItemFileTreeBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
