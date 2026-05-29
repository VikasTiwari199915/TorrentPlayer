package com.vikas.torrentplayer.tv.discover;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.DiscoverListResponse;
import com.vikas.torrentplayer.tv.R;
import com.vikas.torrentplayer.tv.SettingsActivity;
import com.vikas.torrentplayer.tv.details.TvDetailsActivity;
import com.vikas.torrentplayer.tv.downloads.TvDownloadsActivity;
import com.vikas.torrentplayer.tv.search.TvSearchActivity;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DiscoverBrowseFragment extends BrowseSupportFragment {

    private static final String TAG = "TvBrowse";

    private static final int ROW_TRENDING = 0;
    private static final int ROW_POPULAR = 1;
    private static final int ROW_STREAMING_TOP = 2;
    private static final int ROW_RECENT = 3;
    private static final int ROW_LIBRARY = 4;

    private ArrayObjectAdapter rowsAdapter;
    private final TorrentClawApi api = ApiClient.get();
    private final List<Call<?>> inflight = new ArrayList<>();

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Wrap leanback setup so a layout/theme glitch on a specific TV box
        // surfaces as a logcat line we can read remotely instead of an opaque
        // hang.
        try { setupUiHeader(); }
        catch (Throwable t) { Log.e(TAG, "setupUiHeader failed", t); }
        try { setupRows(); }
        catch (Throwable t) { Log.e(TAG, "setupRows failed", t); }
        try { loadDiscover(); }
        catch (Throwable t) { Log.e(TAG, "loadDiscover failed", t); }
    }

    private void setupUiHeader() {
        setTitle(getString(R.string.browse_title));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setBrandColor(Color.parseColor("#1A0F33"));
        setSearchAffordanceColor(Color.parseColor("#7B5BFF"));
        setOnSearchClickedListener(v ->
                startActivity(new Intent(requireContext(), TvSearchActivity.class)));
    }

    private void setupRows() {
        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        rowsAdapter.add(emptyRow(ROW_TRENDING, getString(R.string.row_trending)));
        rowsAdapter.add(emptyRow(ROW_POPULAR, getString(R.string.row_popular)));
        rowsAdapter.add(emptyRow(ROW_STREAMING_TOP, getString(R.string.row_streaming_top)));
        rowsAdapter.add(emptyRow(ROW_RECENT, getString(R.string.row_recent)));
        rowsAdapter.add(buildLibraryRow());

        setAdapter(rowsAdapter);
        setOnItemViewClickedListener(itemClickedListener);
    }

    private ListRow emptyRow(int id, String title) {
        ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(new PosterPresenter());
        return new ListRow(id, new HeaderItem(id, title), rowAdapter);
    }

    private ListRow buildLibraryRow() {
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new ActionPresenter());
        adapter.add(new ActionCard(ActionCard.SEARCH,
                getString(R.string.action_search), R.drawable.rounded_search_24));
        adapter.add(new ActionCard(ActionCard.DOWNLOADS,
                getString(R.string.action_downloads), R.drawable.rounded_download_24));
        adapter.add(new ActionCard(ActionCard.TORBOX,
                getString(R.string.action_torbox), R.drawable.rounded_download_24));
        adapter.add(new ActionCard(ActionCard.SETTINGS,
                getString(R.string.action_settings), R.drawable.rounded_settings_24));
        return new ListRow(ROW_LIBRARY,
                new HeaderItem(ROW_LIBRARY, getString(R.string.row_quick_actions)),
                adapter);
    }

    private void loadDiscover() {
        if (!new PrefsManager(requireContext()).hasApiKey()) {
            return;
        }
        String key = new PrefsManager(requireContext()).getApiKey();
        String bearer = ApiClient.bearer(key);

        Call<DiscoverListResponse> trending = api.getTrending(bearer, "daily", 20, 1, key);
        inflight.add(trending);
        trending.enqueue(rowFiller(ROW_TRENDING));

        Call<DiscoverListResponse> popular = api.getPopular(bearer, 20, 1, key);
        inflight.add(popular);
        popular.enqueue(rowFiller(ROW_POPULAR));

        // "Top on streaming" — default to Netflix movies. Could expose a chip
        // selector later if needed.
        Call<List<DiscoverItem>> streamingTop = api.getStreamingTop(
                bearer, "netflix", "US", "movie", key);
        inflight.add(streamingTop);
        streamingTop.enqueue(new Callback<List<DiscoverItem>>() {
            @Override
            public void onResponse(@NonNull Call<List<DiscoverItem>> call,
                                   @NonNull Response<List<DiscoverItem>> response) {
                if (!isAdded() || rowsAdapter == null) return;
                if (!response.isSuccessful() || response.body() == null) return;
                fillRow(ROW_STREAMING_TOP, response.body());
            }
            @Override
            public void onFailure(@NonNull Call<List<DiscoverItem>> call, @NonNull Throwable t) {
                Log.w(TAG, "streaming-top failed", t);
            }
        });

        Call<DiscoverListResponse> recent = api.getRecent(bearer, 20, 1, key);
        inflight.add(recent);
        recent.enqueue(rowFiller(ROW_RECENT));
    }

    private Callback<DiscoverListResponse> rowFiller(int rowId) {
        return new Callback<DiscoverListResponse>() {
            @Override
            public void onResponse(@NonNull Call<DiscoverListResponse> call,
                                   @NonNull Response<DiscoverListResponse> response) {
                if (!isAdded() || rowsAdapter == null) return;
                if (!response.isSuccessful() || response.body() == null) return;
                fillRow(rowId, response.body().items);
            }
            @Override
            public void onFailure(@NonNull Call<DiscoverListResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "row " + rowId + " failed", t);
            }
        };
    }

    private void fillRow(int rowId, List<DiscoverItem> items) {
        if (items == null) return;
        for (int i = 0; i < rowsAdapter.size(); i++) {
            Object row = rowsAdapter.get(i);
            if (row instanceof ListRow && ((ListRow) row).getId() == rowId) {
                ArrayObjectAdapter rowAdapter = (ArrayObjectAdapter) ((ListRow) row).getAdapter();
                rowAdapter.clear();
                for (DiscoverItem d : items) {
                    if (d != null && d.effectiveId() > 0) rowAdapter.add(d);
                }
                return;
            }
        }
    }

    @Override
    public void onDestroy() {
        for (Call<?> c : inflight) {
            try { c.cancel(); } catch (Exception ignored) {}
        }
        inflight.clear();
        super.onDestroy();
    }

    private final OnItemViewClickedListener itemClickedListener = new OnItemViewClickedListener() {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof DiscoverItem) {
                TvDetailsActivity.start(requireContext(), (DiscoverItem) item);
            } else if (item instanceof ActionCard) {
                switch (((ActionCard) item).id) {
                    case ActionCard.SEARCH:
                        startActivity(new Intent(requireContext(), TvSearchActivity.class));
                        break;
                    case ActionCard.DOWNLOADS:
                        startActivity(new Intent(requireContext(), TvDownloadsActivity.class));
                        break;
                    case ActionCard.TORBOX:
                        if (new com.vikas.torrentplayer.utils.PrefsManager(requireContext()).hasTorBoxKey()) {
                            startActivity(new Intent(requireContext(),
                                    com.vikas.torrentplayer.tv.torbox.TvTorBoxLibraryActivity.class));
                        } else {
                            android.widget.Toast.makeText(requireContext(),
                                    "Set your TorBox API key in Settings first",
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                        break;
                    case ActionCard.SETTINGS:
                        startActivity(new Intent(requireContext(), SettingsActivity.class));
                        break;
                }
            }
        }
    };
}
