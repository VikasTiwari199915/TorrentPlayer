package com.vikas.torrentplayer.ui.downloads;

import org.libtorrent4j.FileStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree representation of a torrent's file list. Built from the flat path list
 * libtorrent gives us; each non-leaf node represents a folder, each leaf is a
 * concrete file (with its torrent file index for priority operations).
 */
public class FileTreeNode {

    public String name;
    /** Depth from root: 0 for first-level entries. Root is -1, not displayed. */
    public int level;
    /** True if this is an intermediate folder, false if it's a real file. */
    public boolean isFolder;
    /** -1 for folders; for files, the libtorrent file index. */
    public int fileIndex = -1;
    /** For files: the file size in bytes. For folders: sum of descendant sizes. */
    public long size;

    public FileTreeNode parent;
    public final List<FileTreeNode> children = new ArrayList<>();

    /** Convenience: total number of leaf-file descendants (1 for a file). */
    public int fileCount;

    public static FileTreeNode build(FileStorage files) {
        FileTreeNode root = new FileTreeNode();
        root.name = "";
        root.isFolder = true;
        root.level = -1;

        int n = files.numFiles();
        for (int i = 0; i < n; i++) {
            String path = files.filePath(i);
            long size = files.fileSize(i);
            insert(root, splitPath(path), 0, i, size);
        }
        computeAggregates(root);
        return root;
    }

    private static String[] splitPath(String path) {
        // libtorrent always uses '/' as separator, even on Windows torrents
        return path.split("/");
    }

    private static void insert(FileTreeNode current, String[] parts, int idx,
                               int fileIndex, long size) {
        if (idx >= parts.length) return;
        String segment = parts[idx];
        boolean isLast = (idx == parts.length - 1);

        FileTreeNode child = null;
        for (FileTreeNode c : current.children) {
            if (c.name.equals(segment) && c.isFolder != isLast) {
                child = c;
                break;
            }
        }
        if (child == null) {
            child = new FileTreeNode();
            child.name = segment;
            child.level = current.level + 1;
            child.isFolder = !isLast;
            child.parent = current;
            current.children.add(child);
        }
        if (isLast) {
            child.fileIndex = fileIndex;
            child.size = size;
        } else {
            insert(child, parts, idx + 1, fileIndex, size);
        }
    }

    private static void computeAggregates(FileTreeNode node) {
        if (!node.isFolder) {
            node.fileCount = 1;
            return;
        }
        long total = 0;
        int count = 0;
        for (FileTreeNode c : node.children) {
            computeAggregates(c);
            total += c.size;
            count += c.fileCount;
        }
        node.size = total;
        node.fileCount = count;
    }

    /** DFS-flatten into a UI-friendly list (root excluded). */
    public List<FileTreeNode> flatten() {
        List<FileTreeNode> out = new ArrayList<>();
        flattenInto(this, out);
        return out;
    }

    private static void flattenInto(FileTreeNode node, List<FileTreeNode> out) {
        if (node.level >= 0) out.add(node);
        for (FileTreeNode c : node.children) flattenInto(c, out);
    }

    /** Apply a "selected" change to this subtree by collecting affected file indices. */
    public void collectFileIndices(List<Integer> out) {
        if (!isFolder) { out.add(fileIndex); return; }
        for (FileTreeNode c : children) c.collectFileIndices(out);
    }
}
