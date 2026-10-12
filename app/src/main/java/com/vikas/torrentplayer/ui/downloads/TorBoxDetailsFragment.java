package com.vikas.torrentplayer.ui.downloads;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.FragmentTorboxInfoBinding;
import com.vikas.torrentplayer.torbox.TorBoxDownload;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.utils.FormatUtils;
import java.util.Objects;

public class TorBoxDetailsFragment extends Fragment {
    private FragmentTorboxInfoBinding info;
    private TorBoxFilesAdapter files;
    private TorBoxDownload current;
    private String poster;
    private boolean posterBound;

    static TorBoxDetailsFragment create(long id, boolean files) {
        TorBoxDetailsFragment fragment = new TorBoxDetailsFragment();
        Bundle args = new Bundle(); args.putLong("id", id); args.putBoolean("files", files);
        fragment.setArguments(args); return fragment;
    }
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        if (requireArguments().getBoolean("files")) {
            RecyclerView list = new RecyclerView(requireContext());
            list.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            list.setLayoutManager(new LinearLayoutManager(requireContext()));
            files = new TorBoxFilesAdapter();
            if (state != null) files.restoreCollapsed(state.getStringArrayList("collapsed"));
            list.setAdapter(files);
            ((androidx.recyclerview.widget.SimpleItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);
            return list;
        }
        info = FragmentTorboxInfoBinding.inflate(inflater, parent, false);
        info.pause.setOnClickListener(v -> {
            if (current == null) return;
            if (current.state == TorBoxManager.State.DOWNLOADING) TorBoxManager.get().pauseTorrent(current.id);
            else TorBoxManager.get().resumeTorrent(current.id);
        });
        return info.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        TorBoxManager.get().torrentDownloads().observe(getViewLifecycleOwner(), groups -> {
            for (TorBoxDownload group : groups) {
                if (group.id == requireArguments().getLong("id")) { bind(group); return; }
            }
            if (info != null) { info.status.setText("Download unavailable"); info.pause.setVisibility(View.GONE); }
        });
    }
    private void bind(TorBoxDownload d) {
        current = d;
        if (files != null) { files.update(d.files); return; }
        if (info == null) return;
        if (!posterBound || !Objects.equals(poster, d.posterUrl)) {
            posterBound = true; poster = d.posterUrl;
            Glide.with(this).load(poster).placeholder(R.drawable.placeholder_poster).error(R.drawable.placeholder_poster).into(info.poster);
        }
        setText(info.title, d.title);
        setText(info.status, TorBoxDownloadsAdapter.status(d));
        info.progress.setProgressCompat(d.percent, false);
        setText(info.bytes, FormatUtils.humanBytes(d.downloaded) + " / " + FormatUtils.humanBytes(d.size) + "  -  " + d.files.size() + " files");
        info.pause.setVisibility(d.state == TorBoxManager.State.DONE ? View.GONE : View.VISIBLE);
        boolean active = d.state == TorBoxManager.State.DOWNLOADING;
        info.pause.setIconResource(active ? R.drawable.rounded_pause_24 : R.drawable.rounded_play_arrow_24);
        info.pause.setContentDescription(active ? "Pause download" : "Resume or retry download");
        info.description.setVisibility(d.description == null ? View.GONE : View.VISIBLE);
        setText(info.description, d.description == null ? "" : d.description);
        StringBuilder location = new StringBuilder("TorBox ID: " + d.id);
        if (d.infoHash != null) location.append("\nInfo hash: ").append(d.infoHash);
        java.io.File directory = null;
        for (TorBoxManager.Download file : d.files) {
            if (file.file == null) continue;
            if (directory == null) directory = file.file.getParentFile();
            while (directory != null && !file.file.getAbsolutePath().startsWith(directory.getAbsolutePath() + java.io.File.separator))
                directory = directory.getParentFile();
        }
        if (directory != null) location.append("\nFolder: ").append(directory.getAbsolutePath());
        for (TorBoxManager.Download f : d.files) if (f.error != null) location.append("\n").append(f.fileName).append(": ").append(f.error);
        setText(info.location, location.toString());
    }
    private void setText(android.widget.TextView view, String text) {
        if (!view.getText().toString().equals(text)) view.setText(text);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (files != null) state.putStringArrayList("collapsed", files.collapsedPaths());
    }
    @Override public void onDestroyView() {
        super.onDestroyView(); info = null; files = null; current = null; posterBound = false;
    }
}
