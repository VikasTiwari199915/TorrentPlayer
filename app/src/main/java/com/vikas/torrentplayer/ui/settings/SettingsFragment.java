package com.vikas.torrentplayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vikas.torrentplayer.BuildConfig;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.PrefsManager;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private PrefsManager prefs;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        prefs = new PrefsManager(requireContext());

        Preference apiKey = findPreference(PrefsManager.KEY_API_KEY);
        if (apiKey != null) {
            apiKey.setOnPreferenceClickListener(p -> {
                showApiKeyDialog();
                return true;
            });
            refreshApiKeySummary();
        }

        Preference version = findPreference("pref_version");
        if (version != null) {
            version.setSummary(BuildConfig.VERSION_NAME);
        }

        Preference saveDir = findPreference("pref_save_dir");
        if (saveDir != null) {
            java.io.File dir = TorrentManager.get().getSaveDir();
            String path = dir != null ? dir.getAbsolutePath() : "—";
            saveDir.setSummary(getString(R.string.pref_save_dir_summary_fmt, path));
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom + dp(72));
            return insets;
        });
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.dialog_api_key_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSingleLine();
        input.setText(prefs.getApiKey());

        FrameLayout container = new FrameLayout(requireContext());
        int pad = dp(24);
        container.setPadding(pad, dp(8), pad, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_api_key_title)
                .setView(container)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_save, (d, w) -> {
                    prefs.setApiKey(input.getText().toString());
                    refreshApiKeySummary();
                })
                .show();
    }

    private void refreshApiKeySummary() {
        Preference p = findPreference(PrefsManager.KEY_API_KEY);
        if (p == null) return;
        String key = prefs.getApiKey();
        if (key == null || key.isEmpty()) {
            p.setSummary(R.string.pref_api_key_not_set);
        } else {
            // Mask all but first 4 / last 4
            String masked;
            if (key.length() <= 8) {
                masked = "••••••••";
            } else {
                masked = key.substring(0, 4) + "•••••" + key.substring(key.length() - 4);
            }
            p.setSummary(masked);
        }
    }

    private int dp(int dp) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (dp * d);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, @Nullable String key) {
        if (PrefsManager.KEY_API_KEY.equals(key)) refreshApiKeySummary();
    }
}
