package com.vikas.torrentplayer.ui.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.search.SearchView;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.FragmentSearchBinding;
import com.vikas.torrentplayer.databinding.ViewSearchDiscoverySectionBinding;
import com.vikas.torrentplayer.ui.detail.DetailActivity;
import com.vikas.torrentplayer.ui.discover.PosterAdapter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding b;
    private SearchViewModel vm;
    private SearchDiscoveryViewModel discoveryVm;
    private SearchAdapter adapter;
    private SearchViewModel.UiState searchState = SearchViewModel.UiState.IDLE;
    private final Map<SearchDiscoveryViewModel.Category, PosterAdapter> discoveryAdapters =
            new EnumMap<>(SearchDiscoveryViewModel.Category.class);
    private final Map<SearchDiscoveryViewModel.Category, ViewSearchDiscoverySectionBinding>
            discoverySections = new EnumMap<>(SearchDiscoveryViewModel.Category.class);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentSearchBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchViewModel.class);
        discoveryVm = new ViewModelProvider(this).get(SearchDiscoveryViewModel.class);

        adapter = new SearchAdapter(item ->
                DetailActivity.start(requireContext(), item));
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);
        b.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int last = lm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 4) vm.loadMore();
            }
        });

        b.swipe.setOnRefreshListener(() -> {
            String q = b.searchBar.getText().toString();
            if (!q.isEmpty()) vm.search(q);
            else {
                discoveryVm.load(true);
                b.swipe.setRefreshing(false);
            }
        });

        // Wire up SearchBar -> SearchView (Material 3 search UX)
        b.searchView.setupWithSearchBar(b.searchBar);
        b.searchView.getEditText().setOnEditorActionListener((tv, actionId, event) -> {
            String q = tv.getText().toString().trim();
            b.searchBar.setText(q);
            b.searchView.hide();
            vm.search(q);
            return true;
        });

        b.filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_movies) vm.setFilter(SearchViewModel.Filter.MOVIES);
            else if (id == R.id.chip_shows) vm.setFilter(SearchViewModel.Filter.SHOWS);
            else vm.setFilter(SearchViewModel.Filter.ALL);
        });

        // Filter icon in the SearchBar's right action opens the bottom sheet.
        b.searchBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_filters) {
                FiltersBottomSheet.show(
                        getChildFragmentManager(),
                        vm.currentGenre(),
                        vm.currentSeason(),
                        vm.currentEpisode(),
                        (genre, season, episode) -> vm.applyFilters(genre, season, episode));
                return true;
            }
            return false;
        });

        // Edge-to-edge: lift list above the bottom nav
        ViewCompat.setOnApplyWindowInsetsListener(b.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Bottom padding is already baked into recycler; add status bar to appbar
            b.appbar.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        // Observe state
        vm.results().observe(getViewLifecycleOwner(), list -> adapter.submitList(list));
        vm.state().observe(getViewLifecycleOwner(), this::renderState);

        setupDiscoverySections();
        discoveryVm.state().observe(getViewLifecycleOwner(), ignored -> renderIdleState());
        discoveryVm.load(false);
    }

    private void setupDiscoverySections() {
        for (SearchDiscoveryViewModel.Category category
                : SearchDiscoveryViewModel.Category.values()) {
            ViewSearchDiscoverySectionBinding section =
                    ViewSearchDiscoverySectionBinding.inflate(
                            getLayoutInflater(), b.discoveryContainer, true);
            section.title.setText(titleFor(category));

            PosterAdapter posterAdapter = new PosterAdapter(
                    item -> DetailActivity.startFromDiscover(requireContext(), item), false);
            section.recycler.setLayoutManager(new LinearLayoutManager(
                    requireContext(), LinearLayoutManager.HORIZONTAL, false));
            section.recycler.setAdapter(posterAdapter);
            section.recycler.setHasFixedSize(true);

            discoveryAdapters.put(category, posterAdapter);
            discoverySections.put(category, section);

            discoveryVm.items(category).observe(getViewLifecycleOwner(), list -> {
                posterAdapter.submitList(list);
                updateSectionVisibility(category);
            });
            discoveryVm.loading(category).observe(getViewLifecycleOwner(), loading -> {
                section.progress.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE);
                updateSectionVisibility(category);
            });
        }
    }

    private int titleFor(SearchDiscoveryViewModel.Category category) {
        switch (category) {
            case POPULAR_MOVIES: return R.string.section_popular_movies;
            case POPULAR_SHOWS: return R.string.section_popular_shows;
            case NOW_PLAYING: return R.string.section_now_playing;
            case UPCOMING: return R.string.section_upcoming_movies;
            case ON_THE_AIR: return R.string.section_on_the_air;
            case TRENDING:
            default: return R.string.section_trending_today;
        }
    }

    private void updateSectionVisibility(SearchDiscoveryViewModel.Category category) {
        ViewSearchDiscoverySectionBinding section = discoverySections.get(category);
        if (section == null) return;
        Boolean loading = discoveryVm.loading(category).getValue();
        List<?> items = discoveryVm.items(category).getValue();
        section.getRoot().setVisibility(
                Boolean.TRUE.equals(loading) || (items != null && !items.isEmpty())
                        ? View.VISIBLE : View.GONE);
    }

    private void renderState(SearchViewModel.UiState state) {
        searchState = state;
        b.swipe.setRefreshing(state == SearchViewModel.UiState.LOADING
                && (adapter.getItemCount() > 0));
        if (state == SearchViewModel.UiState.IDLE) {
            b.recycler.setVisibility(View.GONE);
            renderIdleState();
            return;
        }

        b.searchDiscovery.setVisibility(View.GONE);
        b.recycler.setVisibility(View.VISIBLE);
        switch (state) {
            case LOADING:
                if (adapter.getItemCount() == 0) {
                    b.swipe.setRefreshing(true);
                }
                b.emptyState.setVisibility(View.GONE);
                break;
            case SUCCESS:
                b.emptyState.setVisibility(View.GONE);
                break;
            case EMPTY:
                b.swipe.setRefreshing(false);
                b.emptyTitle.setText(R.string.search_no_results);
                b.emptySubtitle.setText("");
                b.emptyState.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                b.swipe.setRefreshing(false);
                b.emptyTitle.setText(R.string.search_error);
                String err = vm.errorMessage().getValue();
                b.emptySubtitle.setText(err == null ? "" : err);
                b.emptyState.setVisibility(View.VISIBLE);
                break;
            case NO_API_KEY:
                b.swipe.setRefreshing(false);
                b.emptyTitle.setText(R.string.search_api_key_missing);
                b.emptySubtitle.setText("");
                b.emptyState.setVisibility(View.VISIBLE);
                break;
            case IDLE:
            default:
                break;
        }
    }

    private void renderIdleState() {
        if (b == null || searchState != SearchViewModel.UiState.IDLE) return;
        b.swipe.setRefreshing(false);
        SearchDiscoveryViewModel.State state = discoveryVm.state().getValue();
        if (state == null) state = SearchDiscoveryViewModel.State.IDLE;
        switch (state) {
            case NO_CREDENTIAL:
                b.searchDiscovery.setVisibility(View.GONE);
                b.emptyTitle.setText(R.string.search_empty_title);
                b.emptySubtitle.setText(R.string.search_tmdb_key_missing);
                b.emptyState.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                b.searchDiscovery.setVisibility(View.GONE);
                b.emptyTitle.setText(R.string.search_error);
                b.emptySubtitle.setText(R.string.search_discovery_error);
                b.emptyState.setVisibility(View.VISIBLE);
                break;
            case EMPTY:
                b.searchDiscovery.setVisibility(View.GONE);
                b.emptyTitle.setText(R.string.search_empty_title);
                b.emptySubtitle.setText(R.string.search_discovery_empty);
                b.emptyState.setVisibility(View.VISIBLE);
                break;
            case IDLE:
            case LOADING:
            case CONTENT:
            default:
                b.emptyState.setVisibility(View.GONE);
                b.searchDiscovery.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (discoveryVm != null) discoveryVm.load(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        discoveryAdapters.clear();
        discoverySections.clear();
        b = null;
    }
}
