package com.vikas.torrentplayer.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TorBoxMigrationTest {
    @Test public void versionThreePreservesPathsAndPausedState() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "torbox-migration-test.db";
        context.deleteDatabase(name);
        try {
            try (SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null)) {
                old.execSQL("CREATE TABLE downloads (infoHash TEXT NOT NULL PRIMARY KEY, title TEXT, posterUrl TEXT, quality TEXT, sizeBytes INTEGER NOT NULL, magnetUrl TEXT, torrentFilePath TEXT, videoFilePath TEXT, addedAt INTEGER NOT NULL, lastState INTEGER NOT NULL, lastProgress INTEGER NOT NULL, downloadDirectory TEXT, selectedFilesJson TEXT, completedFilesJson TEXT)");
                old.execSQL("CREATE TABLE favorites (`key` TEXT NOT NULL PRIMARY KEY, metadata TEXT, addedAt INTEGER NOT NULL, lastEpisode TEXT, updated INTEGER NOT NULL, ended INTEGER NOT NULL)");
                old.execSQL("CREATE TABLE torbox_downloads (`key` TEXT NOT NULL PRIMARY KEY, title TEXT, torrentId INTEGER NOT NULL, fileId INTEGER NOT NULL, fileName TEXT, filePath TEXT, sizeBytes INTEGER NOT NULL, lastState INTEGER NOT NULL, lastProgress INTEGER NOT NULL, addedAt INTEGER NOT NULL)");
                old.execSQL("INSERT INTO torbox_downloads VALUES ('tb:42:1', 'Example', 42, 1, 'episode.mkv', '/old/media/TorBox/42/1/episode.mkv', 1000, 1, 45, 123)");
                old.setVersion(3);
            }
            AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name)
                    .addMigrations(AppDatabase.MIGRATION_3_4).build();
            try {
                TorBoxEntity file = db.torBoxDao().getAll().get(0);
                assertEquals("/old/media/TorBox/42/1/episode.mkv", file.filePath);
                assertEquals(1, file.lastState); assertEquals(45, file.lastProgress);
                assertNull(file.posterUrl);
                file.posterUrl = "https://example.test/poster.jpg"; file.torrentName = "Example season";
                db.torBoxDao().upsert(file);
                assertEquals(file.posterUrl, db.torBoxDao().getAll().get(0).posterUrl);
            } finally { db.close(); }
        } finally { context.deleteDatabase(name); }
    }
}
