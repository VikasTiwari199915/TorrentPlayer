package com.vikas.torrentplayer.ui.downloads;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.ActivityDownloadDetailsBinding;
import com.vikas.torrentplayer.torbox.TorBoxDownload;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.ui.player.PlayerActivity;
import java.util.ArrayList;
import java.util.List;

public class TorBoxDownloadDetailsActivity extends AppCompatActivity {
    static final String EXTRA_ID = "torbox_id";
    public static void start(Context context, long id) {
        context.startActivity(new Intent(context, TorBoxDownloadDetailsActivity.class).putExtra(EXTRA_ID, id));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ActivityDownloadDetailsBinding b = ActivityDownloadDetailsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (id < 0) { finish(); return; }
        b.toolbar.setTitle(R.string.torbox_details);
        b.toolbar.setNavigationOnClickListener(v -> finish());
        b.toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(
                b.toolbar, com.google.android.material.R.attr.colorOnSurface));
        b.pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override public Fragment createFragment(int position) {
                return TorBoxDetailsFragment.create(id, position == 1);
            }
            @Override public int getItemCount() { return 2; }
        });
        new TabLayoutMediator(b.tabs, b.pager, (tab, position) ->
                tab.setText(position == 0 ? R.string.tab_info : R.string.tab_files)).attach();
        TorBoxManager.get().init(this);
    }

    public static void chooseVideo(Context context, TorBoxDownload download) {
        List<TorBoxManager.Download> videos = new ArrayList<>();
        for (TorBoxManager.Download file : download.files)
            if (file.state == TorBoxManager.State.DONE && TorBoxDownloadsAdapter.isVideo(file.fileName)) videos.add(file);
        if (videos.isEmpty()) { Toast.makeText(context, "No completed videos", Toast.LENGTH_SHORT).show(); return; }
        String[] names = videos.stream().map(f -> f.fileName).toArray(String[]::new);
        new MaterialAlertDialogBuilder(context).setTitle("Play video")
                .setItems(names, (dialog, index) -> open(context, videos.get(index))).show();
    }

    static void open(Context context, TorBoxManager.Download file) {
        if (file.file == null || !file.file.isFile()) {
            Toast.makeText(context, "File missing", Toast.LENGTH_LONG).show(); return;
        }
        if (file.state != TorBoxManager.State.DONE) {
            Toast.makeText(context, "File is not fully downloaded", Toast.LENGTH_SHORT).show(); return;
        }
        if (TorBoxDownloadsAdapter.isVideo(file.fileName)) PlayerActivity.startFile(context, file.file.getAbsolutePath());
        else DetailsFilesFragment.openFile(context, file.file);
    }
}
