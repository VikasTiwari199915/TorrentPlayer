package com.vikas.torrentplayer.torrent;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import org.libtorrent4j.TorrentHandle;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * ExoPlayer {@link DataSource} that reads from a torrent-backed file, blocking
 * reads on pieces that haven't yet been downloaded.
 *
 * <p>Why we need this: libtorrent writes to a sparse pre-allocated file —
 * undownloaded regions read back as zeros, not as EOF. Tolerant extractors
 * (MKV) often survive this; strict ones (MP4 / mp4-fragmented) blow up with
 * "atom not found" or similar errors as soon as the parser walks into the
 * zero-fill region. By making the read call block until the relevant piece is
 * actually on disk, we present ExoPlayer with a normal sequentially-readable
 * file no matter what the underlying download order is.
 *
 * <p>Subtitle / non-video files (which are tiny and usually fully downloaded
 * before playback starts) bypass the piece-wait fast-path.
 */
@OptIn(markerClass = UnstableApi.class)
public class TorrentDataSource extends BaseDataSource {

    private static final String TAG = "TorrentDataSource";
    private static final long PIECE_WAIT_TIMEOUT_MS = 60_000L;
    private static final long POLL_INTERVAL_MS = 100L;
    /** How many pieces ahead to deadline-prioritise on demand. */
    private static final int READ_AHEAD_PIECES = 4;

    private final String videoFileAbsPath;
    private final TorrentHandle handle;
    private final int pieceLength;
    private final long videoFileOffsetInTorrent;

    @Nullable private RandomAccessFile raf;
    @Nullable private Uri uri;
    private boolean isVideoFile;
    private long currentPos;        // current read position WITHIN the file
    private long bytesRemaining;
    private boolean opened;

    public TorrentDataSource(String videoFileAbsPath, TorrentHandle handle,
                             int pieceLength, long videoFileOffsetInTorrent) {
        super(/* isNetwork = */ true);
        this.videoFileAbsPath = videoFileAbsPath;
        this.handle = handle;
        this.pieceLength = pieceLength;
        this.videoFileOffsetInTorrent = videoFileOffsetInTorrent;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        transferInitializing(dataSpec);
        uri = dataSpec.uri;
        if (uri == null || (uri.getScheme() != null && !"file".equals(uri.getScheme()))) {
            throw new IOException("Unsupported URI scheme: " + uri);
        }
        String path = uri.getPath();
        if (path == null) throw new IOException("Missing path in " + uri);

        File file = new File(path);
        if (!file.exists()) throw new IOException("File not found: " + path);

        isVideoFile = path.equals(videoFileAbsPath);
        raf = new RandomAccessFile(file, "r");
        currentPos = dataSpec.position;
        if (currentPos > 0) raf.seek(currentPos);

        long fileLength = file.length();
        if (dataSpec.length != C.LENGTH_UNSET) {
            bytesRemaining = dataSpec.length;
        } else {
            bytesRemaining = fileLength - currentPos;
        }
        if (bytesRemaining < 0) throw new EOFException();

        opened = true;
        transferStarted(dataSpec);
        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (bytesRemaining == 0) return C.RESULT_END_OF_INPUT;
        if (raf == null) throw new IOException("DataSource not open");

        int toRead;
        if (isVideoFile && handle != null && handle.isValid() && pieceLength > 0) {
            // Limit each read to the bytes that live in a single piece so we
            // know exactly which piece we need to be ready.
            long absInTorrent = videoFileOffsetInTorrent + currentPos;
            int pieceIndex = (int) (absInTorrent / pieceLength);
            int bytesIntoPiece = (int) (absInTorrent % pieceLength);
            int bytesLeftInPiece = pieceLength - bytesIntoPiece;
            toRead = (int) Math.min(length, Math.min(bytesLeftInPiece, bytesRemaining));

            if (!waitForPiece(pieceIndex)) {
                throw new IOException("Timed out waiting for piece " + pieceIndex);
            }
        } else {
            toRead = (int) Math.min(length, bytesRemaining);
        }

        int actuallyRead = raf.read(buffer, offset, toRead);
        if (actuallyRead < 0) return C.RESULT_END_OF_INPUT;

        currentPos += actuallyRead;
        bytesRemaining -= actuallyRead;
        bytesTransferred(actuallyRead);
        return actuallyRead;
    }

    @Nullable
    @Override
    public Uri getUri() { return uri; }

    @Override
    public void close() throws IOException {
        if (raf != null) {
            try { raf.close(); } catch (IOException ignored) {}
            raf = null;
        }
        uri = null;
        if (opened) {
            opened = false;
            transferEnded();
        }
    }

    /** Block until the piece arrives, prioritising it (+a small read-ahead). */
    private boolean waitForPiece(int pieceIndex) {
        if (handle == null || !handle.isValid()) return false;
        if (handle.havePiece(pieceIndex)) return true;

        try {
            handle.setPieceDeadline(pieceIndex, 0);
            for (int i = 1; i <= READ_AHEAD_PIECES; i++) {
                handle.setPieceDeadline(pieceIndex + i, i * 200);
            }
        } catch (Throwable t) {
            Log.w(TAG, "setPieceDeadline failed", t);
        }

        long deadline = System.currentTimeMillis() + PIECE_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (handle.havePiece(pieceIndex)) return true;
            try { Thread.sleep(POLL_INTERVAL_MS); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return handle.havePiece(pieceIndex);
    }

    /** Factory ExoPlayer uses to mint per-request DataSource instances. */
    @OptIn(markerClass = UnstableApi.class)
    public static class Factory implements DataSource.Factory {

        private final String videoFileAbsPath;
        private final TorrentHandle handle;
        private final int pieceLength;
        private final long videoFileOffsetInTorrent;

        public Factory(File videoFile, TorrentHandle handle, int pieceLength,
                       long videoFileOffsetInTorrent) {
            this.videoFileAbsPath = videoFile.getAbsolutePath();
            this.handle = handle;
            this.pieceLength = pieceLength;
            this.videoFileOffsetInTorrent = videoFileOffsetInTorrent;
        }

        @Override
        public DataSource createDataSource() {
            return new TorrentDataSource(videoFileAbsPath, handle, pieceLength,
                    videoFileOffsetInTorrent);
        }
    }
}
