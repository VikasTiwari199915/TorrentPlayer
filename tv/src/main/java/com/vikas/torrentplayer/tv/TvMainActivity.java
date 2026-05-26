package com.vikas.torrentplayer.tv;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.vikas.torrentplayer.tv.discover.DiscoverBrowseFragment;

/**
 * Single-activity host for the leanback {@link DiscoverBrowseFragment}. The
 * fragment is the actual TV home screen — rows of posters, side header items.
 */
public class TvMainActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_browse_fragment, new DiscoverBrowseFragment())
                    .commit();
        }
    }
}
