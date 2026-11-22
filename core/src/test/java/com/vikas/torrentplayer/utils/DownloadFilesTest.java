package com.vikas.torrentplayer.utils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import static org.junit.Assert.*;
public class DownloadFilesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void deletesOwnedFilesButKeepsSiblingAndSharedRoot() throws Exception {
        File root = temp.newFolder();
        File nested = new File(root, "Show/Season 1"); assertTrue(nested.mkdirs());
        File video = new File(nested, "Episode.mkv"); assertTrue(video.createNewFile());
        File sibling = new File(root, "other.mkv"); assertTrue(sibling.createNewFile());
        DownloadFiles.delete(root, Collections.singletonList(video));
        assertFalse(video.exists()); assertFalse(nested.exists()); assertTrue(sibling.exists()); assertTrue(root.exists());
        DownloadFiles.delete(root, Collections.singletonList(video));
    }
    @Test public void rejectsEscapesDirectoriesAndSymlinks() throws Exception {
        File root = temp.newFolder(), outside = temp.newFile();
        assertThrows(IOException.class, () -> DownloadFiles.delete(root, Collections.singletonList(outside)));
        File directory = new File(root, "directory"); assertTrue(directory.mkdir());
        assertThrows(IOException.class, () -> DownloadFiles.delete(root, Collections.singletonList(directory)));
        File link = new File(root, "link"); Files.createSymbolicLink(link.toPath(), outside.toPath());
        assertThrows(IOException.class, () -> DownloadFiles.delete(root, Collections.singletonList(link)));
        assertTrue(outside.exists());
    }
}
