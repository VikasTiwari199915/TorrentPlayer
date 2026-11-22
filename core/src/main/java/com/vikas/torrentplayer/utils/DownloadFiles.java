package com.vikas.torrentplayer.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;

/** Deletes only explicitly owned files, never recursively deletes a shared download root. */
public final class DownloadFiles {
    private DownloadFiles() {}
    public static void delete(File root, Collection<File> files) throws IOException {
        File boundary = root.getCanonicalFile();
        for (File file : files) {
            File canonical = file.getCanonicalFile();
            if (!canonical.getPath().startsWith(boundary.getPath() + File.separator))
                throw new IOException("File outside download folder: " + file.getName());
            if (Files.isSymbolicLink(file.toPath())) throw new IOException("Refusing linked file: " + file.getName());
            if (file.exists() && (!file.isFile() || !file.delete()))
                throw new IOException("Could not delete " + file.getName());
            File parent = file.getParentFile();
            while (parent != null && !parent.getCanonicalFile().equals(boundary)) {
                String[] remaining = parent.list();
                if (remaining == null || remaining.length != 0 || !parent.delete()) break;
                parent = parent.getParentFile();
            }
        }
    }
}
