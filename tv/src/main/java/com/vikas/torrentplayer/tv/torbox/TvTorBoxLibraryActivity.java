package com.vikas.torrentplayer.tv.torbox;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.torbox.TorBoxClient;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.tv.R;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the torrents already in the user's TorBox account so they can be
 * streamed/downloaded on demand, or deleted from the account. OK opens the file
 * chooser; long-press (or the menu) offers Delete + Refresh.
 */
public class TvTorBoxLibraryActivity extends FragmentActivity {

    private Adapter adapter;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TorBoxManager.get().init(getApplicationContext());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0820);
        root.setPadding(dp(48), dp(48), dp(48), dp(48));

        TextView title = new TextView(this);
        title.setText("TorBox library");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(0xFFCCCCCC);
        status.setTextSize(15);
        status.setPadding(0, dp(16), 0, dp(16));
        status.setText("Loading…");
        root.addView(status);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        list.setLayoutParams(lp);
        root.addView(list);

        setContentView(root);
        refresh();
    }

    private void refresh() {
        status.setText("Loading…");
        TorBoxManager.get().listAccount(new TorBoxManager.Callback<List<TorBoxClient.TbTorrent>>() {
            @Override public void onResult(List<TorBoxClient.TbTorrent> result) {
                if (isFinishing()) return;
                adapter.submit(result);
                status.setText(result.isEmpty()
                        ? "No torrents in your TorBox account yet."
                        : result.size() + " torrent(s) in your account");
            }
            @Override public void onError(String message) {
                if (isFinishing()) return;
                status.setText("Error: " + message);
            }
        });
    }

    private void showActions(TorBoxClient.TbTorrent t) {
        new AlertDialog.Builder(this)
                .setTitle(t.name)
                .setItems(new CharSequence[]{"Stream / Download", "Delete from TorBox"},
                        (d, which) -> {
                            if (which == 0) {
                                TorBoxFileChooser.show(this, t, t.name);
                            } else {
                                confirmDelete(t);
                            }
                        })
                .show();
    }

    private void confirmDelete(TorBoxClient.TbTorrent t) {
        new AlertDialog.Builder(this)
                .setTitle("Delete from TorBox?")
                .setMessage(t.name + "\n\nThis removes it from your TorBox account.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        TorBoxManager.get().deleteFromAccount(t.id,
                                new TorBoxManager.Callback<Void>() {
                                    @Override public void onResult(Void r) {
                                        if (isFinishing()) return;
                                        Toast.makeText(TvTorBoxLibraryActivity.this,
                                                "Deleted", Toast.LENGTH_SHORT).show();
                                        refresh();
                                    }
                                    @Override public void onError(String message) {
                                        if (isFinishing()) return;
                                        Toast.makeText(TvTorBoxLibraryActivity.this,
                                                message, Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<TorBoxClient.TbTorrent> items = new ArrayList<>();

        void submit(List<TorBoxClient.TbTorrent> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

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
            TorBoxClient.TbTorrent t = items.get(position);
            TextView t1 = h.itemView.findViewById(android.R.id.text1);
            TextView t2 = h.itemView.findViewById(android.R.id.text2);
            t1.setText(t.name);
            String state = t.cached ? "cached" : t.downloadState;
            t2.setText(FormatUtils.humanBytes(t.size) + "  ·  " + state
                    + "  ·  " + t.files.size() + " file(s)");
            h.itemView.setOnClickListener(v -> showActions(t));
            h.itemView.setOnLongClickListener(v -> { confirmDelete(t); return true; });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
