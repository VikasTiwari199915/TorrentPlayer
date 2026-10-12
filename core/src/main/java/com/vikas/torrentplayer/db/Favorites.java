package com.vikas.torrentplayer.db;

import android.content.Context;
import com.google.gson.Gson;
import com.vikas.torrentplayer.api.models.SearchResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Favorites {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Gson JSON = new Gson();
    private Favorites() {}

    public static String key(SearchResult result) {
        return result.contentType + ":" + (result.tmdbId != null && result.tmdbId > 0
                ? "tmdb:" + result.tmdbId : "catalog:" + result.id);
    }

    public static SearchResult result(FavoriteEntity entity) {
        try { return JSON.fromJson(entity.metadata, SearchResult.class); }
        catch (RuntimeException ignored) { return null; }
    }

    public static boolean matches(FavoriteEntity favorite, SearchResult item) {
        SearchResult saved = result(favorite);
        if (saved == null || saved.isShow() != item.isShow()) return false;
        return (saved.tmdbId != null && saved.tmdbId.equals(item.tmdbId))
                || (saved.imdbId != null && saved.imdbId.equals(item.imdbId))
                || (saved.id != 0 && saved.id == item.id);
    }

    public static void set(Context context, SearchResult result, boolean favorite) {
        FavoriteDao dao = AppDatabase.get(context).favoriteDao();
        FavoriteEntity entity = new FavoriteEntity();
        entity.key = key(result);
        // Keep catalogue metadata, not transient torrent lists or stream URLs.
        SearchResult copy = JSON.fromJson(JSON.toJson(result), SearchResult.class);
        copy.torrents = null;
        copy.streaming = null;
        entity.metadata = JSON.toJson(copy);
        entity.addedAt = System.currentTimeMillis();
        IO.execute(() -> {
            for (FavoriteEntity saved : dao.getAll()) {
                if (matches(saved, result)) {
                    if (!favorite) dao.delete(saved.key);
                    else { saved.metadata = entity.metadata; dao.put(saved); }
                    return;
                }
            }
            if (favorite) dao.put(entity);
        });
    }
}
