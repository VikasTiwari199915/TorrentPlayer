package com.vikas.torrentplayer.torbox;

import java.io.File;
import java.io.IOException;

/** Keeps API paths relative to the torrent's directory, without flattening folders. */
public final class TorBoxPaths {
    private TorBoxPaths() {}

    public static File allocateFolder(File base, String title) throws IOException {
        String name = title == null ? "TorBox download" : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        name = name.replaceAll("[. ]+$", "");
        if (name.isEmpty()) name = "TorBox download";
        // Keep enough room for suffixes, including multi-byte title characters.
        while (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 180)
            name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
        if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Download folder unavailable");
        for (int suffix = 1; suffix < 10000; suffix++) {
            File folder = resolve(base, name + (suffix == 1 ? "" : " (" + suffix + ")"));
            if (folder.mkdir()) return folder;
            if (!folder.exists()) throw new IOException("Could not create torrent folder");
        }
        throw new IOException("Too many folders with this title");
    }

    public static File resolve(File root, String name) throws IOException {
        if (name == null || name.isEmpty()) throw new IOException("Missing file name");
        String path = name.replace('\\', '/');
        if (path.startsWith("/") || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0)
            throw new IOException("Invalid file path");
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IOException("Invalid file path");
        }
        File result = new File(root, path).getCanonicalFile();
        if (!result.getPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("File path escapes download folder");
        return result;
    }
}
