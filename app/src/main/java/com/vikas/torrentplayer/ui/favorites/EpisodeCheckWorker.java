package com.vikas.torrentplayer.ui.favorites;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeriesDetails;
import com.vikas.torrentplayer.db.*;
import com.vikas.torrentplayer.utils.PrefsManager;
import java.util.concurrent.TimeUnit;

/** Checks metadata only; never starts a torrent or downloads media. */
public class EpisodeCheckWorker extends Worker {
    public EpisodeCheckWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    public static void schedule(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("favorite-episodes",
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(EpisodeCheckWorker.class, 24, TimeUnit.HOURS)
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                                .setRequiresBatteryNotLow(true).build()).build());
    }

    @NonNull @Override public Result doWork() {
        String credential = new PrefsManager(getApplicationContext()).getTmdbCredential();
        if (credential == null || credential.trim().isEmpty()) return Result.success();
        boolean token = credential.contains(".");
        FavoriteDao dao = AppDatabase.get(getApplicationContext()).favoriteDao();
        boolean transientFailure = false;
        for (FavoriteEntity favorite : dao.getAll()) {
            if (isStopped()) return Result.retry();
            SearchResult result = Favorites.result(favorite);
            if (favorite.ended || result == null || !result.isShow() || result.tmdbId == null || result.tmdbId <= 0) continue;
            try {
                retrofit2.Response<TMDBSeriesDetails> response = ApiClient.tmdb().seriesDetails(
                        token ? ApiClient.bearer(credential) : null, result.tmdbId,
                        token ? null : credential, "en-US").execute();
                if (!response.isSuccessful()) {
                    transientFailure |= response.code() == 429 || response.code() >= 500;
                    continue;
                }
                TMDBSeriesDetails details = response.body();
                if (details == null) continue;
                String episode = details.lastEpisode == null ? "none"
                        : details.lastEpisode.season + ":" + details.lastEpisode.episode;
                boolean changed = favorite.lastEpisode != null && !favorite.lastEpisode.equals(episode) && !episode.equals("none");
                dao.updateEpisode(favorite.key, episode, changed,
                        "Ended".equalsIgnoreCase(details.status) || "Canceled".equalsIgnoreCase(details.status));
            } catch (java.io.IOException exception) { transientFailure = true; }
        }
        return transientFailure ? Result.retry() : Result.success();
    }
}
