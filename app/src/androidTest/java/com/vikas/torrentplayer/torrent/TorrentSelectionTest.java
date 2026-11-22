package com.vikas.torrentplayer.torrent;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vikas.torrentplayer.api.models.TorrentItem;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libtorrent4j.Priority;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TorrentSelectionTest {
    @Test public void selectionPriorityCompletionAndDeletion() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TorrentManager manager = TorrentManager.get();
        onMain(() -> manager.init(context));
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        String name = "SelectionTest" + System.nanoTime();
        String header = "d4:infod5:filesld6:lengthi16384e4:pathl7:E09.mkveed6:lengthi16384e4:pathl7:E02.mkveed6:lengthi16384e4:pathl7:E01.mkveee4:name" + name.length() + ":" + name + "12:piece lengthi16384e6:pieces60:";
        data.write(header.getBytes(StandardCharsets.US_ASCII));
        byte[] block = new byte[16384];
        Arrays.fill(block, (byte) 42);
        byte[] hash = MessageDigest.getInstance("SHA-1").digest(block);
        for (int i = 0; i < 3; i++) data.write(hash);
        data.write("7:privatei1eee".getBytes(StandardCharsets.US_ASCII));
        TorrentInfo info = new TorrentInfo(data.toByteArray());
        String key = info.infoHash().toHex();
        File metadata = new File(context.getExternalFilesDir(null), "meta/" + key + ".torrent");
        Files.write(metadata.toPath(), data.toByteArray());
        AtomicReference<DownloadHandle> download = new AtomicReference<>();
        TorrentItem item = new TorrentItem(); item.infoHash = key; item.rawTitle = name; item.sizeBytes = 49152;
        try {
            onMain(() -> download.set(manager.startStream(null, item)));
            waitFor(() -> manager.handleFor(key) != null);
            TorrentHandle nativeHandle = manager.handleFor(key);
            waitFor(() -> nativeHandle.filePriority(2) == Priority.TOP_PRIORITY);
            DownloadHandle h = download.get();
            try (androidx.test.core.app.ActivityScenario<com.vikas.torrentplayer.ui.downloads.DownloadDetailsActivity> screen =
                         androidx.test.core.app.ActivityScenario.launch(new android.content.Intent(context,
                                 com.vikas.torrentplayer.ui.downloads.DownloadDetailsActivity.class).putExtra("info_hash", key))) {
                screen.onActivity(a -> ((androidx.viewpager2.widget.ViewPager2) a.findViewById(com.vikas.torrentplayer.R.id.pager)).setCurrentItem(1, false));
                Thread.sleep(800);
                screen.onActivity(a -> {
                    android.view.View button = visiblePriority(a.getWindow().getDecorView());
                    assertNotNull(button); assertTrue(button.isShown());
                    button.performClick();
                });
                Thread.sleep(300);
                android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(context.getCacheDir(), "file-priority.png"))) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                } finally { bitmap.recycle(); }
            }
            onMain(() -> manager.setFilePriority(key, Arrays.asList(0, 1, 2), false));
            waitFor(() -> !h.selectionPending);
            onMain(() -> manager.setFilePriority(key, Arrays.asList(0, 1, 2), true));
            waitFor(() -> !h.selectionPending);
            assertEquals(3, new com.google.gson.Gson().fromJson(h.selectedFilesJson, int[].class).length);
            onMain(() -> manager.downloadFirst(key, 0));
            try { waitFor(() -> !h.selectionPending && nativeHandle.filePriority(0) == Priority.TOP_PRIORITY); }
            catch (AssertionError error) { throw new AssertionError("priority pending=" + h.selectionPending + " checking=" + h.selectionRechecking + " state=" + h.state.getValue() + " native=" + Arrays.toString(nativeHandle.filePriorities()), error); }
            for (int i = 0; i < 3; i++) nativeHandle.addPiece(i, block);
            waitFor(() -> h.state.getValue() == DownloadHandle.State.FINISHED);
            onMain(() -> manager.setFilePriority(key, Collections.singletonList(0), false));
            waitFor(() -> !h.selectionPending);
            File skipped = new File(h.downloadDirectory, name + "/E09.mkv");
            waitFor(() -> !skipped.exists());
            onMain(() -> manager.setFilePriority(key, Collections.singletonList(0), true));
            waitFor(() -> !h.selectionPending && !h.selectionRechecking);
            assertFalse(nativeHandle.havePiece(0));
            assertNotEquals(DownloadHandle.State.FINISHED, h.state.getValue());
            nativeHandle.addPiece(0, block);
            waitFor(() -> h.state.getValue() == DownloadHandle.State.FINISHED);
            AtomicReference<String> deletion = new AtomicReference<>("waiting");
            onMain(() -> manager.deleteDownload(key, deletion::set));
            waitFor(() -> !"waiting".equals(deletion.get()));
            assertNull(deletion.get());
            assertFalse(new File(h.downloadDirectory, name + "/E01.mkv").exists());
        } finally {
            onMain(() -> manager.remove(key, false));
            metadata.delete();
        }
    }
    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
    private static android.view.View visiblePriority(android.view.View view) {
        if (view.getId() == com.vikas.torrentplayer.R.id.priority && view.isShown()) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = visiblePriority(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long end = System.currentTimeMillis() + 20000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(100);
        assertTrue("Timed out waiting for torrent state", condition.getAsBoolean());
    }
}
