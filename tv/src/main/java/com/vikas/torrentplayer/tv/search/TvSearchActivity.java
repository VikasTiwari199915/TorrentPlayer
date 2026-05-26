package com.vikas.torrentplayer.tv.search;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.vikas.torrentplayer.tv.R;

public class TvSearchActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_search);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.search_fragment, new TvSearchFragment())
                    .commit();
        }
    }
}
