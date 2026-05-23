package com.vikas.torrentplayer.ui.detail;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.databinding.ItemTorrentBinding;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TorrentAdapter extends ListAdapter<TorrentItem, TorrentAdapter.VH> {

    public interface OnTorrentAction {
        void onPlay(TorrentItem item);
        void onDownload(TorrentItem item);
        void onMagnet(TorrentItem item);
        /** Long-press on the magnet button — used for "Copy". */
        void onMagnetLong(TorrentItem item);
    }

    private final OnTorrentAction listener;

    public TorrentAdapter(OnTorrentAction listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTorrentBinding b = ItemTorrentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemTorrentBinding b;

        VH(ItemTorrentBinding b) {
            super(b.getRoot());
            this.b = b;
            b.btnPlay.setOnClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlay(getItem(p));
                }
            });
            b.btnDownload.setOnClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && listener != null) {
                    listener.onDownload(getItem(p));
                }
            });
            b.btnMagnet.setOnClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMagnet(getItem(p));
                }
            });
            b.btnMagnet.setOnLongClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMagnetLong(getItem(p));
                    return true;
                }
                return false;
            });
        }

        void bind(TorrentItem item) {
            // Quality chip with HDR suffix when applicable
            String q = item.quality == null ? "—" : item.quality.toUpperCase(Locale.US);
            String hdr = item.hdrLabel();
            if (hdr != null) q = q + " " + hdr.toUpperCase(Locale.US);
            b.qualityChip.setText(q);
            b.qualityChip.setChipBackgroundColor(ColorStateList.valueOf(qualityColor(item.quality)));
            b.qualityChip.setTextColor(Color.WHITE);

            b.size.setText(FormatUtils.humanBytes(item.sizeBytes));
            b.rawTitle.setText(item.rawTitle != null ? item.rawTitle : "—");
            b.seeders.setText(b.getRoot().getContext().getString(R.string.seeders_label, item.seeders));
            b.leechers.setText(b.getRoot().getContext().getString(R.string.leechers_label, item.leechers));

            // Source line: e.g. "WEB-DL · x265 · 1tamilmv"
            StringBuilder src = new StringBuilder();
            if (item.sourceType != null && !item.sourceType.isEmpty()) src.append(item.sourceType.toUpperCase(Locale.US));
            if (item.codec != null && !item.codec.isEmpty()) {
                if (src.length() > 0) src.append(" · ");
                src.append(item.codec.toLowerCase(Locale.US));
            }
            if (item.source != null && !item.source.isEmpty()) {
                if (src.length() > 0) src.append(" · ");
                // shorten messy source strings
                String s = item.source;
                int colon = s.indexOf(':');
                if (colon > 0) s = s.substring(0, colon);
                src.append(s);
            }
            b.source.setText(src.toString());

            // Detail line: S/E · languages · resolution · audio · subs
            StringBuilder spec = new StringBuilder();
            String se = FormatUtils.seasonEpisodeLabel(item.season, item.episode);
            if (se != null) spec.append(se);
            String langs = languageLabel(item.languages);
            if (langs != null) {
                if (spec.length() > 0) spec.append(" · ");
                spec.append(langs);
            }
            String res = item.resolutionLabel();
            if (res != null) {
                if (spec.length() > 0) spec.append(" · ");
                spec.append(res);
            }
            String audio = item.audioLabel();
            if (audio != null) {
                if (spec.length() > 0) spec.append(" · ");
                spec.append(audio);
            }
            String subs = subtitleLabel(item);
            if (subs != null) {
                if (spec.length() > 0) spec.append(" · ");
                spec.append(subs);
            }
            if (spec.length() == 0) {
                b.specs.setVisibility(View.GONE);
            } else {
                b.specs.setVisibility(View.VISIBLE);
                b.specs.setText(spec.toString());
            }

            b.verifiedIcon.setVisibility(item.isTrusted() ? View.VISIBLE : View.GONE);
        }
    }

    /** "HI · TA · TE" capped to ~3 entries. */
    private static String languageLabel(List<String> langs) {
        if (langs == null || langs.isEmpty()) return null;
        Set<String> uniq = new LinkedHashSet<>();
        for (String l : langs) {
            if (l == null || l.isEmpty()) continue;
            // Normalise three-letter codes like "tam"/"tel" to two-letter where obvious
            uniq.add(normaliseLang(l));
            if (uniq.size() >= 3) break;
        }
        return String.join(" · ", uniq);
    }

    private static String normaliseLang(String l) {
        String s = l.toLowerCase(Locale.US);
        switch (s) {
            case "tam": return "TA";
            case "tel": return "TE";
            case "hin": return "HI";
            case "eng": return "EN";
            case "multi": return "MULTI";
            default: return s.length() <= 3 ? s.toUpperCase(Locale.US) : s.toUpperCase(Locale.US);
        }
    }

    private static String subtitleLabel(TorrentItem item) {
        Set<String> langs = new LinkedHashSet<>();
        if (item.subtitleLanguages != null) {
            for (String l : item.subtitleLanguages) {
                if (l != null && !l.isEmpty()) langs.add(l.toUpperCase(Locale.US));
            }
        } else if (item.subtitleTracks != null) {
            for (TorrentItem.SubtitleTrack t : item.subtitleTracks) {
                if (t != null && t.lang != null && !t.lang.isEmpty()) langs.add(t.lang.toUpperCase(Locale.US));
            }
        }
        if (langs.isEmpty()) return null;
        return "Subs: " + String.join(", ", langs);
    }

    private static int qualityColor(String q) {
        if (q == null) return 0xFFA5A5A5;
        switch (q.toLowerCase(Locale.US)) {
            case "2160p":
            case "4k":
                return 0xFFFF6B6B;
            case "1080p":
                return 0xFF4ECDC4;
            case "720p":
                return 0xFFFFA94D;
            case "480p":
                return 0xFFA5A5A5;
            default:
                return 0xFFA5A5A5;
        }
    }

    private static final DiffUtil.ItemCallback<TorrentItem> DIFF = new DiffUtil.ItemCallback<TorrentItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull TorrentItem a, @NonNull TorrentItem b) {
            if (a.infoHash != null && b.infoHash != null) return a.infoHash.equals(b.infoHash);
            return a == b;
        }
        @Override
        public boolean areContentsTheSame(@NonNull TorrentItem a, @NonNull TorrentItem b) {
            return a.seeders == b.seeders
                    && a.leechers == b.leechers
                    && a.isTrusted() == b.isTrusted();
        }
    };
}
