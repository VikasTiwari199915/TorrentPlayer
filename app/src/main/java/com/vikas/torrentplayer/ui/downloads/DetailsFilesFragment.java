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
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.tree.FileTreeNode;
import com.vikas.torrentplayer.utils.FormatUtils;

import org.libtorrent4j.FileStorage;
import org.libtorrent4j.Priority;
import org.libtorrent4j.TorrentHandle;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDetailsListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String hash = requireArguments().getString(ARG_HASH);
        TorrentInfo ti = TorrentManager.get().torrentInfoFor(hash);

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
        TorrentHandle handle = TorrentManager.get().handleFor(hash);

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(new TreeAdapter(root.flatten(), baseDir, handle, requireContext()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
        private final TorrentHandle handle;
        private final Context ctx;

        TreeAdapter(List<FileTreeNode> rows, File baseDir,
                    @Nullable TorrentHandle handle, Context ctx) {
            this.rows = rows;
            this.baseDir = baseDir;
            this.handle = handle;
            this.ctx = ctx;
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
            String meta = FormatUtils.humanBytes(node.size);
            if (node.isFolder) meta = node.fileCount + " files · " + meta;
            b.meta.setText(meta);

            // Initial switch state
            boolean wanted = isWanted(node);
            // Set without firing the listener
            b.toggle.setOnCheckedChangeListener(null);
            b.toggle.setChecked(wanted);
            b.toggle.setOnCheckedChangeListener((btn, isChecked) -> applyPriority(node, isChecked));

            // Tap on the row body to open the file (folders just toggle).
            b.getRoot().setOnClickListener(v -> {
                if (node.isFolder) {
                    boolean newState = !b.toggle.isChecked();
                    b.toggle.setChecked(newState);
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

        private boolean isWanted(FileTreeNode node) {
            if (handle == null || !handle.isValid()) return true;
            List<Integer> indices = new ArrayList<>();
            node.collectFileIndices(indices);
            if (indices.isEmpty()) return true;
            int wanted = 0;
            for (int idx : indices) {
                try {
                    Priority p = handle.filePriority(idx);
                    if (p != Priority.IGNORE) wanted++;
                } catch (Throwable ignored) {}
            }
            // For folders, "wanted" = any descendant included
            return wanted > 0;
        }

        private void applyPriority(FileTreeNode node, boolean enable) {
            if (handle == null || !handle.isValid()) return;
            List<Integer> indices = new ArrayList<>();
            node.collectFileIndices(indices);
            Priority target = enable ? Priority.DEFAULT : Priority.IGNORE;
            for (int idx : indices) {
                try { handle.filePriority(idx, target); }
                catch (Throwable t) { /* best effort */ }
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
