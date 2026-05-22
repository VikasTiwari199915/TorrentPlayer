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
import com.vikas.torrentplayer.ui.detail.DetailActivity;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding b;
    private SearchViewModel vm;
    private SearchAdapter adapter;

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
            String q = b.searchBar.getText() == null ? "" : b.searchBar.getText().toString();
            if (!q.isEmpty()) vm.search(q);
            else b.swipe.setRefreshing(false);
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
    }

    private void renderState(SearchViewModel.UiState state) {
        b.swipe.setRefreshing(state == SearchViewModel.UiState.LOADING
                && (adapter.getItemCount() > 0));
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
                b.swipe.setRefreshing(false);
                b.emptyTitle.setText(R.string.search_empty_title);
                b.emptySubtitle.setText(R.string.search_empty_subtitle);
                b.emptyState.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
