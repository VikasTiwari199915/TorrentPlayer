package com.vikas.torrentplayer.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = { DownloadEntity.class, TorBoxEntity.class, FavoriteEntity.class }, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DownloadDao downloadDao();

    public abstract TorBoxDao torBoxDao();
    public abstract FavoriteDao favoriteDao();

    private static final androidx.room.migration.Migration MIGRATION_1_2 =
            new androidx.room.migration.Migration(1, 2) {
                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS torbox_downloads (`key` TEXT NOT NULL PRIMARY KEY, title TEXT, torrentId INTEGER NOT NULL, fileId INTEGER NOT NULL, fileName TEXT, filePath TEXT, sizeBytes INTEGER NOT NULL, lastState INTEGER NOT NULL, lastProgress INTEGER NOT NULL, addedAt INTEGER NOT NULL)");
                }
            };
    private static final androidx.room.migration.Migration MIGRATION_2_3 =
            new androidx.room.migration.Migration(2, 3) {
                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS favorites (`key` TEXT NOT NULL PRIMARY KEY, metadata TEXT, addedAt INTEGER NOT NULL, lastEpisode TEXT, updated INTEGER NOT NULL, ended INTEGER NOT NULL)");
                    db.execSQL("ALTER TABLE downloads ADD COLUMN downloadDirectory TEXT");
                    db.execSQL("ALTER TABLE downloads ADD COLUMN selectedFilesJson TEXT");
                    db.execSQL("ALTER TABLE downloads ADD COLUMN completedFilesJson TEXT");
                }
            };

    private static volatile AppDatabase INSTANCE;
    static final androidx.room.migration.Migration MIGRATION_3_4 =
            new androidx.room.migration.Migration(3, 4) {
                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                    for (String column : new String[]{"torrentName", "infoHash", "posterUrl", "description"}) {
                        db.execSQL("ALTER TABLE torbox_downloads ADD COLUMN " + column + " TEXT");
                    }
                }
            };

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "torrentplayer.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
