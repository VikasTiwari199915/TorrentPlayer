package com.vikas.torrentplayer.tv.search;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.SearchResponse;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.tv.details.TvDetailsActivity;
import com.vikas.torrentplayer.tv.discover.PosterPresenter;
import com.vikas.torrentplayer.utils.PrefsManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Leanback search fragment — input row at the top, results row below.
 * Conversion: {@link SearchResult} from the API gets mapped onto
 * {@link DiscoverItem} so we can reuse {@link PosterPresenter}.
 */
public class TvSearchFragment extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider {

    private ArrayObjectAdapter rowsAdapter;
    private final TorrentClawApi api = ApiClient.get();
    @Nullable private Call<SearchResponse> inflight;
    private String currentQuery = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setSearchResultProvider(this);
        setOnItemViewClickedListener(itemClickedListener);
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return rowsAdapter;
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {
        return false; // wait for submit — torrent search isn't cheap
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (TextUtils.isEmpty(query) || query.equals(currentQuery)) return true;
        currentQuery = query;
        runSearch(query);
        return true;
    }

    private void runSearch(String query) {
        if (inflight != null) inflight.cancel();
        rowsAdapter.clear();

        PrefsManager prefs = new PrefsManager(requireContext());
        if (!prefs.hasApiKey()) return;
        String key = prefs.getApiKey();

        ArrayObjectAdapter resultsRow = new ArrayObjectAdapter(new PosterPresenter());
        rowsAdapter.add(new ListRow(new HeaderItem(0, "Results"), resultsRow));

        inflight = api.search(
                ApiClient.bearer(key),
                query,
                /* type */ null,
                "available",
                "seeders",
                1,
                30,
                prefs.isVerifiedOnly(),
                /* genre */ null,
                /* season */ null,
                /* episode */ null,
                key);
        inflight.enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SearchResponse> call,
                                   @NonNull Response<SearchResponse> response) {
                if (!isAdded() || call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) return;
                for (SearchResult r : response.body().results) {
                    if (r == null) continue;
                    resultsRow.add(toDiscoverItem(r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<SearchResponse> call, @NonNull Throwable t) {
                // ignored — results row simply stays empty
            }
        });
    }

    /** Map a SearchResult onto DiscoverItem so the poster presenter can render it. */
    private static DiscoverItem toDiscoverItem(SearchResult r) {
        DiscoverItem d = new DiscoverItem();
        d.id = r.id;
        d.title = r.title;
        d.year = r.year;
        d.contentType = r.contentType;
        d.posterUrl = r.posterUrl;
        d.backdropUrl = r.backdropUrl;
        d.overview = r.overview;
        d.ratingImdb = r.ratingImdb;
        d.ratingTmdb = r.ratingTmdb;
        d.genres = r.genres;
        d.imdbId = r.imdbId;
        d.tmdbId = r.tmdbId;
        return d;
    }

    private final OnItemViewClickedListener itemClickedListener =
            new OnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                          RowPresenter.ViewHolder rowViewHolder, Row row) {
                    if (item instanceof DiscoverItem) {
                        TvDetailsActivity.start(requireContext(), (DiscoverItem) item);
                    }
                }
            };

    @Override
    public void onDestroy() {
        if (inflight != null) inflight.cancel();
        super.onDestroy();
    }
}
