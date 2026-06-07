package com.vikas.torrentplayer.ui.discover;

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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.databinding.FragmentDiscoverBinding;
import com.vikas.torrentplayer.ui.detail.DetailActivity;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.List;

public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding b;
    private DiscoverViewModel vm;

    private PosterAdapter trendingAdapter;
    private PosterAdapter popularAdapter;
    private PosterAdapter recentAdapter;
    private PosterAdapter streamingMoviesAdapter;
    private PosterAdapter streamingShowsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDiscoverBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(DiscoverViewModel.class);

        PosterAdapter.OnClick onClick = item -> DetailActivity.startFromDiscover(requireContext(), item);

        trendingAdapter = new PosterAdapter(onClick, /*showRank=*/false);
        popularAdapter = new PosterAdapter(onClick, false);
        recentAdapter = new PosterAdapter(onClick, false);
        streamingMoviesAdapter = new PosterAdapter(onClick, /*showRank=*/true);
        streamingShowsAdapter = new PosterAdapter(onClick, /*showRank=*/true);

        wireCarousel(b.trendingRecycler, trendingAdapter);
        wireCarousel(b.popularRecycler, popularAdapter);
        wireCarousel(b.recentRecycler, recentAdapter);
        wireCarousel(b.streamingMoviesRecycler, streamingMoviesAdapter);
        wireCarousel(b.streamingShowsRecycler, streamingShowsAdapter);

        // Streaming service chip selection — read android:tag from the chip
        b.serviceChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            View chip = group.findViewById(checkedIds.get(0));
            if (chip instanceof Chip && chip.getTag() != null) {
                vm.selectService(chip.getTag().toString());
            }
        });

        // Banner visibility
        boolean hasKey = new PrefsManager(requireContext()).hasApiKey();
        b.bannerNoApiKey.setVisibility(hasKey ? View.GONE : View.VISIBLE);

        b.swipe.setOnRefreshListener(() -> {
            vm.loadIfNeeded(true);
            // Hide the refresh spinner — section progress bars will take over.
            b.swipe.setRefreshing(false);
        });

        // Observe each section
        observe(vm.trending(), trendingAdapter, b.trendingProgress);
        observe(vm.popular(), popularAdapter, b.popularProgress);
        observe(vm.recent(), recentAdapter, b.recentProgress);
        observe(vm.streamingTopMovies(), streamingMoviesAdapter, b.streamingMoviesProgress);
        observe(vm.streamingTopShows(), streamingShowsAdapter, b.streamingShowsProgress);

        // Wire spinner visibility to state
        vm.trendingState().observe(getViewLifecycleOwner(), s ->
                b.trendingProgress.setVisibility(s == DiscoverViewModel.SectionState.LOADING ? View.VISIBLE : View.GONE));
        vm.popularState().observe(getViewLifecycleOwner(), s ->
                b.popularProgress.setVisibility(s == DiscoverViewModel.SectionState.LOADING ? View.VISIBLE : View.GONE));
        vm.recentState().observe(getViewLifecycleOwner(), s ->
                b.recentProgress.setVisibility(s == DiscoverViewModel.SectionState.LOADING ? View.VISIBLE : View.GONE));
        vm.streamingTopMoviesState().observe(getViewLifecycleOwner(), s ->
                b.streamingMoviesProgress.setVisibility(s == DiscoverViewModel.SectionState.LOADING ? View.VISIBLE : View.GONE));
        vm.streamingTopShowsState().observe(getViewLifecycleOwner(), s ->
                b.streamingShowsProgress.setVisibility(s == DiscoverViewModel.SectionState.LOADING ? View.VISIBLE : View.GONE));

        // Insets handled by android:fitsSystemWindows on the AppBarLayout —
        // manually padding the toolbar here would double up and clip the title.

        vm.loadIfNeeded(false);
    }

    private void wireCarousel(RecyclerView rv, PosterAdapter adapter) {
        rv.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        rv.setAdapter(adapter);
        rv.setHasFixedSize(true);
        rv.setNestedScrollingEnabled(false);
    }

    private void observe(LiveData<List<DiscoverItem>> data, PosterAdapter adapter, View progress) {
        data.observe(getViewLifecycleOwner(), adapter::submitList);
    }

    @Override
    public void onResume() {
        super.onResume();
        // If the user just added their API key, allow the next load to fire
        boolean hasKey = new PrefsManager(requireContext()).hasApiKey();
        b.bannerNoApiKey.setVisibility(hasKey ? View.GONE : View.VISIBLE);
        if (hasKey) vm.loadIfNeeded(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    // Silence unused warning for MutableLiveData import
    @SuppressWarnings("unused")
    private static <T> MutableLiveData<T> noop() { return new MutableLiveData<>(); }
}
