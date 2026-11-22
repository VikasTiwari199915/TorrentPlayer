package com.vikas.torrentplayer.torbox;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

public class TorBoxPathsTest {
    @Test public void allocatesReadableFoldersWithoutOverwriting() throws Exception {
        File root = temp.newFolder();
        File first = TorBoxPaths.allocateFolder(root, "Spider-Noir Season 1");
        File next = TorBoxPaths.allocateFolder(root, "Spider-Noir Season 1");
        assertEquals("Spider-Noir Season 1", first.getName());
        assertEquals("Spider-Noir Season 1 (2)", next.getName());
        assertTrue(first.isDirectory()); assertTrue(next.isDirectory());
    }
    @Test public void sanitizesFolderNamesAndBoundsLength() throws Exception {
        File root = temp.newFolder();
        File folder = TorBoxPaths.allocateFolder(root, "../../bad:name");
        assertEquals(root.getCanonicalFile(), folder.getParentFile());
        assertFalse(folder.getName().contains(":"));
        assertTrue(TorBoxPaths.allocateFolder(root, String.join("", java.util.Collections.nCopies(300, "a"))).getName().length() <= 180);
    }
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void preservesNestedNamesAndExtensions() throws Exception {
        File root = temp.newFolder();
        assertEquals(new File(root, "Season 1/Subtitles/Episode 01.en.srt").getCanonicalFile(),
                TorBoxPaths.resolve(root, "Season 1/Subtitles/Episode 01.en.srt"));
    }
    @Test public void normalizesWindowsSeparatorsWithoutFlattening() throws Exception {
        File root = temp.newFolder();
        assertEquals(new File(root, "Show/Episode.mkv").getCanonicalFile(), TorBoxPaths.resolve(root, "Show\\Episode.mkv"));
    }
    @Test public void sameLeafInDifferentFoldersRemainsDistinct() throws Exception {
        File root = temp.newFolder();
        assertNotEquals(TorBoxPaths.resolve(root, "A/video.mkv"), TorBoxPaths.resolve(root, "B/video.mkv"));
    }
    @Test public void rejectsAbsoluteTraversalAndInvalidPaths() throws Exception {
        File root = temp.newFolder();
        for (String name : new String[]{"../escape", "a/../../escape", "/escape", "C:\\escape", "a/./b", "a//b", "a/", "", null}) {
            assertThrows(IOException.class, () -> TorBoxPaths.resolve(root, name));
        }
    }
    @Test public void rejectsSymlinkEscape() throws Exception {
        File root = temp.newFolder("root"), outside = temp.newFolder("outside");
        java.nio.file.Files.createSymbolicLink(new File(root, "linked").toPath(), outside.toPath());
        assertThrows(IOException.class, () -> TorBoxPaths.resolve(root, "linked/video.mkv"));
    }
}
