package com.vikas.torrentplayer.tv;

// Platform AlertDialog — Leanback themes don't extend Theme.AppCompat so the
// androidx.appcompat version isn't available here.
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.Arrays;
import java.util.List;

/**
 * Minimal TV settings: API key + the (read-only) save folder. Keeps things
 * d-pad friendly with focusable rows; no preference fragment magic.
 */
public class SettingsActivity extends FragmentActivity {

    private static final int ITEM_API_KEY = 0;
    private static final int ITEM_SAVE_DIR = 1;

    private PrefsManager prefs;
    private RowsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PrefsManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0820);
        root.setPadding(dp(48), dp(48), dp(48), dp(48));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        root.addView(title);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowsAdapter();
        list.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(24);
        list.setLayoutParams(lp);
        root.addView(list);

        setContentView(root);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setHint("tc_…");
        input.setText(prefs.getApiKey());
        new AlertDialog.Builder(this)
                .setTitle("TorrentClaw API key")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.setApiKey(input.getText().toString());
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    private class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.VH> {
        private final List<Integer> rows = Arrays.asList(ITEM_API_KEY, ITEM_SAVE_DIR);

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            v.setBackgroundResource(R.drawable.tv_focus_row);
            v.setFocusable(true);
            v.setPadding(dp(16), dp(16), dp(16), dp(16));
            ((TextView) v.findViewById(android.R.id.text1)).setTextColor(0xFFFFFFFF);
            ((TextView) v.findViewById(android.R.id.text2)).setTextColor(0xFFCCCCCC);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            int id = rows.get(position);
            TextView t1 = h.itemView.findViewById(android.R.id.text1);
            TextView t2 = h.itemView.findViewById(android.R.id.text2);
            switch (id) {
                case ITEM_API_KEY:
                    t1.setText("API key");
                    String key = prefs.getApiKey();
                    t2.setText(key == null || key.isEmpty()
                            ? "Not set"
                            : maskKey(key));
                    h.itemView.setOnClickListener(v -> showApiKeyDialog());
                    break;
                case ITEM_SAVE_DIR:
                    t1.setText("Save folder");
                    Context ctx = h.itemView.getContext();
                    t2.setText(TorrentManager.get().getSaveDir() != null
                            ? TorrentManager.get().getSaveDir().getAbsolutePath()
                            : "—");
                    h.itemView.setOnClickListener(null);
                    break;
            }
        }

        private String maskKey(String k) {
            if (k.length() <= 8) return "••••••••";
            return k.substring(0, 4) + "•••••" + k.substring(k.length() - 4);
        }

        @Override public int getItemCount() { return rows.size(); }

        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
