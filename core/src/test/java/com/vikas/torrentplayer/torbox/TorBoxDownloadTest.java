package com.vikas.torrentplayer.torbox;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class TorBoxDownloadTest {
    private TorBoxManager.Download file(long torrent, int id, long size, long done, TorBoxManager.State state) {
        TorBoxManager.Download file = new TorBoxManager.Download("tb:" + torrent + ":" + id, "Legacy title");
        file.torrentId = torrent; file.fileId = id; file.size = size; file.downloaded = done;
        file.state = state; file.speed = 50;
        return file;
    }
    @Test public void progressIsByteWeightedNotAverageOfFiles() {
        TorBoxDownload group = TorBoxDownload.group(Arrays.asList(
                file(1, 1, 900, 450, TorBoxManager.State.DOWNLOADING),
                file(1, 2, 100, 100, TorBoxManager.State.DONE))).get(0);
        assertEquals(1, group.id); assertEquals(2, group.files.size());
        assertEquals(55, group.percent); assertEquals(550, group.downloaded);
        assertEquals(1000, group.size); assertEquals(50, group.speed);
    }
    @Test public void separateTorrentsNeverMergeByTitle() {
        assertEquals(2, TorBoxDownload.group(Arrays.asList(
                file(1, 1, 100, 0, TorBoxManager.State.PAUSED),
                file(2, 1, 100, 0, TorBoxManager.State.PAUSED))).size());
    }
    @Test public void pausedAndCompletedFilesAreNotActive() {
        TorBoxDownload group = TorBoxDownload.group(Arrays.asList(
                file(1, 1, 100, 10, TorBoxManager.State.PAUSED),
                file(1, 2, 100, 100, TorBoxManager.State.DONE))).get(0);
        assertEquals(TorBoxManager.State.PAUSED, group.state); assertEquals(0, group.speed);
    }
    @Test public void missingFileKeepsGroupInErrorEvenAtFullProgress() {
        TorBoxDownload group = TorBoxDownload.group(Collections.singletonList(
                file(1, 1, 100, 100, TorBoxManager.State.ERROR))).get(0);
        assertEquals(TorBoxManager.State.ERROR, group.state);
    }
    @Test public void allFilesMustFinishForGroupToFinish() {
        List<TorBoxManager.Download> files = Arrays.asList(
                file(1, 1, 100, 100, TorBoxManager.State.DONE),
                file(1, 2, 100, 100, TorBoxManager.State.DONE));
        files.get(1).posterUrl = "https://example.test/poster.jpg";
        files.get(1).torrentName = "Series.S01";
        files.get(1).infoHash = "test-hash";
        TorBoxDownload group = TorBoxDownload.group(files).get(0);
        assertEquals(TorBoxManager.State.DONE, group.state); assertEquals(100, group.percent);
        assertEquals("Series.S01", group.title); assertEquals("test-hash", group.infoHash);
        assertEquals(files.get(1).posterUrl, group.posterUrl);
    }
}
