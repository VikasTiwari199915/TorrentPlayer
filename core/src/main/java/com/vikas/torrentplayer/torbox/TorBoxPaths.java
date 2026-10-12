package com.vikas.torrentplayer.torbox;

import java.io.File;
import java.io.IOException;

/** Keeps API paths relative to the torrent's directory, without flattening folders. */
public final class TorBoxPaths {
    private TorBoxPaths() {}

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
