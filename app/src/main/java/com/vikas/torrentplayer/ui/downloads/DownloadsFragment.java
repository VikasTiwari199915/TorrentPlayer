package com.vikas.torrentplayer.ui.downloads;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.FragmentDownloadsBinding;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.ui.player.PlayerActivity;
import com.vikas.torrentplayer.utils.PrefsManager;

public class DownloadsFragment extends Fragment {

    private FragmentDownloadsBinding b;
    private DownloadsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDownloadsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new DownloadsAdapter(getViewLifecycleOwner(), new DownloadsAdapter.Listener() {
            @Override
            public void onPlay(DownloadHandle h) {
                PlayerActivity.start(requireContext(), h.infoHash);
            }
            @Override
            public void onRemove(DownloadHandle h) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_remove_download_title)
                        .setMessage(R.string.dialog_remove_download_message)
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.action_remove, (d, w) -> {
                            boolean deleteFiles = new PrefsManager(requireContext()).isDeleteOnRemove();
                            TorrentManager.get().remove(h.infoHash, deleteFiles);
                        })
                        .show();
            }
            @Override
            public void onPauseToggle(DownloadHandle h) {
                if (h.state.getValue() == DownloadHandle.State.PAUSED) {
                    TorrentManager.get().resume(h.infoHash);
                } else {
                    TorrentManager.get().pause(h.infoHash);
                }
            }
        });

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        TorrentManager.get().downloads().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            boolean empty = list == null || list.isEmpty();
            b.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            b.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        // Insets handled declaratively by android:fitsSystemWindows on the
        // AppBarLayout — no manual padding here, otherwise the toolbar gets
        // double-padded and the title clips into the status bar.
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
