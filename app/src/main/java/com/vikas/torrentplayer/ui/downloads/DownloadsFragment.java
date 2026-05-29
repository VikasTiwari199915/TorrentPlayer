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
    private TorBoxDownloadsAdapter torBoxAdapter;

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

        torBoxAdapter = new TorBoxDownloadsAdapter(new TorBoxDownloadsAdapter.Listener() {
            @Override public void onPlay(com.vikas.torrentplayer.torbox.TorBoxManager.Download d) {
                if (d.file == null) return;
                if (d.state == com.vikas.torrentplayer.torbox.TorBoxManager.State.DONE) {
                    PlayerActivity.startFile(requireContext(), d.file.getAbsolutePath());
                } else {
                    // Partial playback while downloading (best for progressive MKV).
                    PlayerActivity.startGrowingFile(requireContext(), d.file.getAbsolutePath(), d.size);
                }
            }
            @Override public void onPauseToggle(com.vikas.torrentplayer.torbox.TorBoxManager.Download d) {
                if (d.state == com.vikas.torrentplayer.torbox.TorBoxManager.State.PAUSED) {
                    com.vikas.torrentplayer.torbox.TorBoxManager.get().resume(d.key);
                } else {
                    com.vikas.torrentplayer.torbox.TorBoxManager.get().pause(d.key);
                }
            }
            @Override public void onRemove(com.vikas.torrentplayer.torbox.TorBoxManager.Download d) {
                com.vikas.torrentplayer.torbox.TorBoxManager.get().remove(d.key);
            }
        });

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(adapter, torBoxAdapter));

        com.vikas.torrentplayer.torbox.TorBoxManager.get().init(requireContext().getApplicationContext());
        TorrentManager.get().downloads().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            updateEmpty();
        });
        com.vikas.torrentplayer.torbox.TorBoxManager.get().downloads()
                .observe(getViewLifecycleOwner(), list -> {
                    torBoxAdapter.submitList(list == null ? new java.util.ArrayList<>()
                            : new java.util.ArrayList<>(list));
                    updateEmpty();
                });

        // Insets handled declaratively by android:fitsSystemWindows on the
        // AppBarLayout — no manual padding here, otherwise the toolbar gets
        // double-padded and the title clips into the status bar.
    }

    private void updateEmpty() {
        if (b == null) return;
        int count = (adapter == null ? 0 : adapter.getItemCount())
                + (torBoxAdapter == null ? 0 : torBoxAdapter.getItemCount());
        boolean empty = count == 0;
        b.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
