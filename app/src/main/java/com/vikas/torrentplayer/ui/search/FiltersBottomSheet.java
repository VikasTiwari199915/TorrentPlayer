package com.vikas.torrentplayer.ui.search;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.BottomSheetFiltersBinding;

/**
 * Bottom sheet with genre / season / episode filters for the search endpoint.
 * Type (movie / show) stays in the inline chip group on the search view so
 * it's one tap away.
 *
 * <p>The host fragment can either implement {@link Listener} or supply a
 * callback via {@link #setListener(Listener)} after instantiation.
 */
public class FiltersBottomSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onFiltersApplied(@Nullable String genre,
                              @Nullable Integer season,
                              @Nullable Integer episode);
    }

    private static final String ARG_GENRE = "genre";
    private static final String ARG_SEASON = "season";
    private static final String ARG_EPISODE = "episode";

    public static FiltersBottomSheet newInstance(@Nullable String genre,
                                                 @Nullable Integer season,
                                                 @Nullable Integer episode) {
        FiltersBottomSheet f = new FiltersBottomSheet();
        Bundle b = new Bundle();
        if (genre != null) b.putString(ARG_GENRE, genre);
        if (season != null) b.putInt(ARG_SEASON, season);
        if (episode != null) b.putInt(ARG_EPISODE, episode);
        f.setArguments(b);
        return f;
    }

    private BottomSheetFiltersBinding b;
    @Nullable private Listener listener;

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), getTheme());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = BottomSheetFiltersBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String currentGenre = args.getString(ARG_GENRE);
        Integer currentSeason = args.containsKey(ARG_SEASON) ? args.getInt(ARG_SEASON) : null;
        Integer currentEpisode = args.containsKey(ARG_EPISODE) ? args.getInt(ARG_EPISODE) : null;

        String[] genres = getResources().getStringArray(R.array.genres);
        ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, genres);
        b.genre.setAdapter(genreAdapter);
        b.genre.setText(currentGenre != null ? currentGenre : genres[0], false);

        if (currentSeason != null) b.season.setText(String.valueOf(currentSeason));
        if (currentEpisode != null) b.episode.setText(String.valueOf(currentEpisode));

        b.btnClear.setOnClickListener(v -> {
            b.genre.setText(genres[0], false);
            b.season.setText("");
            b.episode.setText("");
            b.seasonLayout.setError(null);
            b.episodeLayout.setError(null);
        });

        b.btnApply.setOnClickListener(v -> {
            String genreText = b.genre.getText().toString().trim();
            String seasonText = b.season.getText() == null ? "" : b.season.getText().toString().trim();
            String episodeText = b.episode.getText() == null ? "" : b.episode.getText().toString().trim();

            String genre = genreText.isEmpty() || genreText.equalsIgnoreCase("Any") ? null : genreText;
            Integer season = parseOrNull(seasonText);
            Integer episode = parseOrNull(episodeText);

            // Validation: episode without season is invalid (server ignores it
            // anyway, but flag it to the user explicitly).
            if (episode != null && season == null) {
                b.seasonLayout.setError(getString(R.string.filter_episode_needs_season));
                return;
            }
            b.seasonLayout.setError(null);
            b.episodeLayout.setError(null);

            if (listener != null) listener.onFiltersApplied(genre, season, episode);
            // Try the host fragment too — convenient when caller did not call setListener()
            Fragment parent = getParentFragment();
            if (parent instanceof Listener) {
                ((Listener) parent).onFiltersApplied(genre, season, episode);
            }
            dismiss();
        });
    }

    @Nullable
    private static Integer parseOrNull(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            int v = Integer.parseInt(text);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Convenience for hosts: show the sheet with current values. */
    public static void show(FragmentManager fm,
                            @Nullable String genre,
                            @Nullable Integer season,
                            @Nullable Integer episode,
                            @NonNull Listener listener) {
        FiltersBottomSheet f = newInstance(genre, season, episode);
        f.setListener(listener);
        f.show(fm, "filters");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
