package com.vikas.torrentplayer.ui.downloads;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.ItemTorrentFileBinding;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.utils.FormatUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Folder expansion is local UI state; progress updates only rebind changed rows. */
final class TorBoxFilesAdapter extends ListAdapter<TorBoxFilesAdapter.Row, TorBoxFilesAdapter.VH> {
    private final Set<String> collapsed = new HashSet<>();
    private List<TorBoxManager.Download> source = new ArrayList<>();
    TorBoxFilesAdapter() { super(DIFF); }
    ArrayList<String> collapsedPaths() { return new ArrayList<>(collapsed); }
    void restoreCollapsed(List<String> paths) { if (paths != null) collapsed.addAll(paths); }

    void update(List<TorBoxManager.Download> files) {
        source = files;
        Node root = new Node("", "");
        for (TorBoxManager.Download file : files) {
            String path = file.fileName == null ? "File " + file.fileId : file.fileName.replace('\\', '/');
            Node node = root;
            StringBuilder key = new StringBuilder();
            for (String part : path.split("/")) {
                key.append('/').append(part);
                String nodeKey = key.toString();
                node = node.children.computeIfAbsent(part, ignored -> new Node(nodeKey, part));
                node.size += file.size;
                node.downloaded += Math.min(file.size, file.downloaded);
                node.count++;
            }
            node.file = file;
        }
        List<Row> rows = new ArrayList<>();
        flatten(root, 0, rows);
        submitList(rows);
    }
    private void flatten(Node parent, int depth, List<Row> rows) {
        List<Node> children = new ArrayList<>(parent.children.values());
        children.sort((a, b) -> {
            if ((a.file == null) != (b.file == null)) return a.file == null ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        for (Node node : children) {
            boolean folder = node.file == null;
            boolean expanded = !collapsed.contains(node.key);
            String status = node.count + " files";
            if (!folder) {
                switch (node.file.state) {
                    case DONE: status = "Finished"; break;
                    case PAUSED: status = "Paused"; break;
                    case ERROR: status = node.file.error == null ? "Error" : node.file.error; break;
                    default: status = node.file.speed > 0 ? FormatUtils.humanSpeed(node.file.speed) : "Queued / connecting";
                }
            }
            int percent = node.size > 0 ? (int) Math.min(100, node.downloaded * 100 / node.size) : 0;
            String detail = FormatUtils.humanBytes(node.downloaded) + " / " + FormatUtils.humanBytes(node.size)
                    + " · " + percent + "% · " + status;
            rows.add(new Row(node.key, node.name, detail, depth, folder, expanded, node.file));
            if (folder && expanded) flatten(node, depth + 1, rows);
        }
    }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new VH(ItemTorrentFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull VH holder, int position) { holder.bind(getItem(position)); }
    @Override public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) holder.bind(getItem(position));
        else { holder.row = getItem(position); holder.b.size.setText(holder.row.detail); }
    }
    final class VH extends RecyclerView.ViewHolder {
        final ItemTorrentFileBinding b;
        Row row;
        VH(ItemTorrentFileBinding binding) {
            super(binding.getRoot()); b = binding;
            b.path.setMaxLines(Integer.MAX_VALUE); b.path.setEllipsize(null);
            b.icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(b.icon, com.google.android.material.R.attr.colorOnSurfaceVariant)));
            b.getRoot().setOnClickListener(v -> {
                if (row.folder) {
                    if (!collapsed.add(row.key)) collapsed.remove(row.key);
                    update(source);
                } else if (row.file.state == TorBoxManager.State.DONE) {
                    TorBoxDownloadDetailsActivity.open(v.getContext(), row.file);
                } else {
                    TorBoxManager.Download file = row.file;
                    boolean active = file.state == TorBoxManager.State.DOWNLOADING;
                    new MaterialAlertDialogBuilder(v.getContext()).setTitle(row.name)
                            .setMessage(row.detail + (file.file == null ? "" : "\n\n" + file.file.getAbsolutePath()))
                            .setNegativeButton(R.string.dialog_cancel, null)
                            .setPositiveButton(active ? "Pause" : "Resume / retry", (dialog, which) -> {
                                if (active) TorBoxManager.get().pause(file.key); else TorBoxManager.get().resume(file.key);
                            }).show();
                }
            });
        }
        void bind(Row item) {
            row = item;
            float density = b.getRoot().getResources().getDisplayMetrics().density;
            int base = (int) (14 * density);
            b.getRoot().setPaddingRelative(base + (int) (Math.min(6, row.depth) * 16 * density), base, base, base);
            b.icon.setImageResource(row.folder ? R.drawable.rounded_folder_24 : R.drawable.rounded_docs_24);
            b.path.setText((row.folder ? (row.expanded ? "▾ " : "▸ ") : "") + row.name);
            b.size.setText(row.detail);
            b.getRoot().setContentDescription((row.folder ? (row.expanded ? "Collapse " : "Expand ") : "Open ") + row.name + ", " + row.detail);
        }
    }
    private static final class Node {
        final String key, name;
        final Map<String, Node> children = new TreeMap<>();
        long size, downloaded;
        int count;
        TorBoxManager.Download file;
        Node(String key, String name) { this.key = key; this.name = name; }
    }
    static final class Row {
        final String key, name, detail;
        final int depth;
        final boolean folder, expanded;
        final TorBoxManager.Download file;
        Row(String key, String name, String detail, int depth, boolean folder, boolean expanded, TorBoxManager.Download file) {
            this.key = key; this.name = name; this.detail = detail; this.depth = depth;
            this.folder = folder; this.expanded = expanded; this.file = file;
        }
    }
    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) { return a.key.equals(b.key); }
        @Override public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.expanded == b.expanded && a.folder == b.folder && a.depth == b.depth
                    && Objects.equals(a.name, b.name) && Objects.equals(a.detail, b.detail);
        }
        @Override public Object getChangePayload(@NonNull Row a, @NonNull Row b) {
            return a.expanded == b.expanded && a.folder == b.folder && a.depth == b.depth
                    && Objects.equals(a.name, b.name) ? Boolean.TRUE : null;
        }
    };
}
