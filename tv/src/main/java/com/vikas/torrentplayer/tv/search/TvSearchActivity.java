package com.vikas.torrentplayer.tv.search;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.SearchResponse;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.tv.R;
import com.vikas.torrentplayer.tv.details.TvDetailsActivity;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Custom TV search screen — instead of fighting with Leanback's
 * SearchSupportFragment (which expects voice + d-pad keyboard integration
 * that doesn't work cleanly when running on a phone), this is a plain
 * EditText with {@code imeOptions=actionSearch} plus a focusable Submit
 * button. Results are shown in a focusable poster grid.
 */
public class TvSearchActivity extends FragmentActivity {

    private EditText input;
    private RecyclerView results;
    private TextView empty;

    private final TorrentClawApi api = ApiClient.get();
    @Nullable private Call<SearchResponse> inflight;
    private final ResultAdapter adapter = new ResultAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_search);

        input = findViewById(R.id.input);
        Button submit = findViewById(R.id.submit);
        results = findViewById(R.id.results);
        empty = findViewById(R.id.empty);

        results.setLayoutManager(new GridLayoutManager(this, 5));
        results.setAdapter(adapter);

        // IME action: pressing the soft-keyboard "Search" / "Done" key fires
        // the query. Works on phone keyboards and on TV soft-keyboards.
        input.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO) {
                runSearch();
                return true;
            }
            return false;
        });
        submit.setOnClickListener(v -> runSearch());

        input.requestFocus();
    }

    private void runSearch() {
        String query = input.getText() != null ? input.getText().toString().trim() : "";
        if (TextUtils.isEmpty(query)) return;
        if (inflight != null) inflight.cancel();

        PrefsManager prefs = new PrefsManager(this);
        if (!prefs.hasApiKey()) {
            showMessage(getString(R.string.empty_no_api_key));
            return;
        }

        String key = prefs.getApiKey();
        showMessage("Searching…");
        inflight = api.search(
                ApiClient.bearer(key),
                query,
                /* type */ null,
                "available", "seeders",
                1, 30, prefs.isVerifiedOnly(),
                /* genre */ null, /* season */ null, /* episode */ null,
                key
        );
        inflight.enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SearchResponse> call,
                                   @NonNull Response<SearchResponse> response) {
                if (call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null
                        || response.body().results == null
                        || response.body().results.isEmpty()) {
                    showMessage(getString(R.string.empty_no_results));
                    return;
                }
                List<DiscoverItem> items = new ArrayList<>();
                for (SearchResult r : response.body().results) {
                    if (r != null) items.add(toDiscoverItem(r));
                }
                adapter.submit(items);
                empty.setVisibility(View.GONE);
                results.setVisibility(View.VISIBLE);
            }
            @Override
            public void onFailure(@NonNull Call<SearchResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                showMessage(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    private void showMessage(String msg) {
        empty.setText(msg);
        empty.setVisibility(View.VISIBLE);
        results.setVisibility(View.GONE);
    }

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

    @Override
    protected void onDestroy() {
        if (inflight != null) inflight.cancel();
        super.onDestroy();
    }

    /** Result grid: focusable poster cards, tap opens details. */
    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<DiscoverItem> items = new ArrayList<>();

        void submit(List<DiscoverItem> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tv_search_result, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            DiscoverItem d = items.get(pos);
            h.title.setText(d.title != null ? d.title : "—");
            StringBuilder m = new StringBuilder();
            m.append(d.isShow() ? "Show" : "Movie");
            if (d.year != null && d.year > 0) m.append(" · ").append(d.year);
            h.meta.setText(m.toString());
            Glide.with(h.itemView.getContext())
                    .load(d.effectivePoster())
                    .placeholder(R.drawable.tv_placeholder_poster)
                    .into(h.poster);
            h.itemView.setOnClickListener(v ->
                    TvDetailsActivity.start(TvSearchActivity.this, d));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final android.widget.ImageView poster;
            final TextView title, meta;
            VH(View v) {
                super(v);
                poster = v.findViewById(R.id.poster);
                title = v.findViewById(R.id.title);
                meta = v.findViewById(R.id.meta);
            }
        }
    }
}
