package com.vikas.torrentplayer.torbox;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * ExoPlayer {@link DataSource} for a file that is still being written
 * sequentially (a TorBox download in progress). When a read reaches the current
 * end of the file, it blocks until the downloader appends more bytes — or until
 * the file reaches its known final size, at which point it behaves like a normal
 * complete file.
 *
 * <p>Best-effort: it supports linear playback and seeking <em>within</em> the
 * already-downloaded region. Seeking past it (or a container that needs the tail
 * before playback, e.g. some MP4 moov layouts) will wait, so this is meant for
 * progressive MKV. For guaranteed instant seek, stream from TorBox instead.
 */
@OptIn(markerClass = UnstableApi.class)
public class GrowingFileDataSource extends BaseDataSource {

    private static final long WAIT_TIMEOUT_MS = 60_000L;
    private static final long POLL_MS = 150L;

    private final File file;
    private final long totalSize;   // expected final size, <=0 if unknown

    @Nullable private RandomAccessFile raf;
    @Nullable private Uri uri;
    private long position;
    private long bytesRemaining;
    private boolean opened;

    public GrowingFileDataSource(File file, long totalSize) {
        super(/* isNetwork = */ false);
        this.file = file;
        this.totalSize = totalSize;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        transferInitializing(dataSpec);
        uri = dataSpec.uri;
        position = dataSpec.position;

        // Wait until the requested start offset is actually on disk.
        if (!waitForLength(position + 1)) {
            throw new EOFException("offset " + position + " never reached");
        }
        raf = new RandomAccessFile(file, "r");
        raf.seek(position);

        if (dataSpec.length != C.LENGTH_UNSET) {
            bytesRemaining = dataSpec.length;
        } else if (totalSize > 0) {
            bytesRemaining = totalSize - position;
        } else {
            bytesRemaining = C.LENGTH_UNSET;
        }
        opened = true;
        transferStarted(dataSpec);
        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (bytesRemaining == 0) return C.RESULT_END_OF_INPUT;
        if (raf == null) throw new IOException("not open");

        // Block until at least one more byte is available at the current position.
        long avail = waitForAvailable();
        if (avail <= 0) return C.RESULT_END_OF_INPUT;   // complete and truly at EOF

        int toRead = (int) Math.min(length, avail);
        if (bytesRemaining != C.LENGTH_UNSET) {
            toRead = (int) Math.min(toRead, bytesRemaining);
        }
        int read = raf.read(buffer, offset, toRead);
        if (read < 0) return C.RESULT_END_OF_INPUT;

        position += read;
        if (bytesRemaining != C.LENGTH_UNSET) bytesRemaining -= read;
        bytesTransferred(read);
        return read;
    }

    /** Bytes available to read at the current position, waiting for growth. */
    private long waitForAvailable() throws IOException {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (true) {
            long len = file.length();
            if (len > position) return len - position;
            if (isComplete(len)) return 0;            // genuinely finished at EOF
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("timed out waiting for more data at " + position);
            }
            sleep();
        }
    }

    /** Wait until the file is at least {@code target} bytes long. */
    private boolean waitForLength(long target) throws IOException {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (file.length() < target) {
            if (isComplete(file.length())) return file.length() >= target;
            if (System.currentTimeMillis() > deadline) return false;
            sleep();
        }
        return true;
    }

    private boolean isComplete(long currentLen) {
        return totalSize > 0 && currentLen >= totalSize;
    }

    private void sleep() throws IOException {
        try { Thread.sleep(POLL_MS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    @Nullable @Override public Uri getUri() { return uri; }

    @Override
    public void close() throws IOException {
        if (raf != null) { try { raf.close(); } catch (IOException ignored) {} raf = null; }
        uri = null;
        if (opened) { opened = false; transferEnded(); }
    }

    /** ExoPlayer factory minting per-request instances for one growing file. */
    @OptIn(markerClass = UnstableApi.class)
    public static class Factory implements DataSource.Factory {
        private final File file;
        private final long totalSize;
        public Factory(File file, long totalSize) {
            this.file = file; this.totalSize = totalSize;
        }
        @Override public DataSource createDataSource() {
            return new GrowingFileDataSource(file, totalSize);
        }
    }
}
