package com.vikas.torrentplayer.ui.downloads;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayoutMediator;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.databinding.ActivityDownloadDetailsBinding;
import com.vikas.torrentplayer.service.TorrentDownloadService;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;

public class DownloadDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_INFO_HASH = "info_hash";

    public static void start(Context ctx, String infoHash) {
        Intent i = new Intent(ctx, DownloadDetailsActivity.class);
        i.putExtra(EXTRA_INFO_HASH, infoHash);
        ctx.startActivity(i);
    }

    private ActivityDownloadDetailsBinding b;
    private String infoHash;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDownloadDetailsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        infoHash = getIntent().getStringExtra(EXTRA_INFO_HASH);
        DownloadHandle handle = TorrentManager.get().findByHash(infoHash);
        if (infoHash == null || handle == null) {
            finish();
            return;
        }
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(handle.title);
        }
        b.toolbar.setNavigationOnClickListener(v -> finish());

        b.pager.setAdapter(new TabsAdapter(this, infoHash));
        new TabLayoutMediator(b.tabs, b.pager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(R.string.tab_info); break;
                case 1: tab.setText(R.string.tab_files); break;
                case 2: tab.setText(R.string.tab_pieces); break;
                case 3: tab.setText(R.string.tab_trackers); break;
                case 4: tab.setText(R.string.tab_peers); break;
            }
        }).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.download_details_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_verify_pieces) {
            TorrentDownloadService.start(this);
            TorrentManager.get().forceRecheck(infoHash);
            Toast.makeText(this, R.string.action_verify_pieces, Toast.LENGTH_SHORT).show();
            return true;
        } else if (item.getItemId() == R.id.action_restart_download) {
            TorrentDownloadService.start(this);
            TorrentManager.get().restartDownload(infoHash);
            Toast.makeText(this, R.string.action_restart_download, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Holds the four tabs. Each fragment reads its data lazily from
     *  TorrentManager keyed by the infoHash argument. */
    private static class TabsAdapter extends FragmentStateAdapter {
        private final String infoHash;
        TabsAdapter(@NonNull DownloadDetailsActivity a, String hash) {
            super(a);
            this.infoHash = hash;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 1: return DetailsFilesFragment.newInstance(infoHash);
                case 2: return DetailsPiecesFragment.newInstance(infoHash);
                case 3: return DetailsTrackersFragment.newInstance(infoHash);
                case 4: return DetailsPeersFragment.newInstance(infoHash);
                case 0:
                default: return DetailsInfoFragment.newInstance(infoHash);
            }
        }

        @Override public int getItemCount() { return 5; }
    }
}
