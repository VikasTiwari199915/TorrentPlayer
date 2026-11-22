package com.vikas.torrentplayer.torrent;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
public class EpisodeOrderTest {
    @Test public void sortsEpisodesNumerically() {
        List<String> names = Arrays.asList("Show.S01E9.mkv", "Show.S01E10.mkv", "Show.S01E2.mkv", "Show.S01E1.mkv");
        names.sort(EpisodeOrder.INSTANCE);
        assertEquals(Arrays.asList("Show.S01E1.mkv", "Show.S01E2.mkv", "Show.S01E9.mkv", "Show.S01E10.mkv"), names);
    }
    @Test public void handlesSeasonFoldersAndPadding() {
        assertTrue(EpisodeOrder.INSTANCE.compare("Season 2/E01.mkv", "Season 10/E1.mkv") < 0);
        assertEquals(0, EpisodeOrder.INSTANCE.compare("E01.mkv", "e1.mkv"));
    }
    @Test public void supportsVeryLongNumbersWithoutOverflow() {
        assertTrue(EpisodeOrder.INSTANCE.compare("E999999999999999999999999.mkv", "E1000000000000000000000000.mkv") < 0);
    }
}
