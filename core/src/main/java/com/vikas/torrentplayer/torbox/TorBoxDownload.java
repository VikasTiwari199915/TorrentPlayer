package com.vikas.torrentplayer.torbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A byte-weighted snapshot of one local torrent, including all its file transfers. */
public final class TorBoxDownload {
    public final long id;
    public final String title, posterUrl, infoHash, description;
    public final List<TorBoxManager.Download> files;
    public final long size, downloaded, speed;
    public final int percent;
    public final TorBoxManager.State state;

    private TorBoxDownload(List<TorBoxManager.Download> list) {
        TorBoxManager.Download first = list.get(0);
        id = first.torrentId;
        String name = first.torrentName, poster = null, hash = null, detail = null;
        long total = 0, done = 0, rate = 0;
        boolean active = false, paused = false, error = false;
        for (TorBoxManager.Download file : list) {
            if (file.torrentName != null) name = file.torrentName;
            if (file.posterUrl != null) poster = file.posterUrl;
            if (file.infoHash != null) hash = file.infoHash;
            if (file.description != null) detail = file.description;
            total += Math.max(0, file.size);
            done += Math.max(0, Math.min(file.size, file.downloaded));
            if (file.state == TorBoxManager.State.DOWNLOADING) { active = true; rate += file.speed; }
            paused |= file.state == TorBoxManager.State.PAUSED;
            error |= file.state == TorBoxManager.State.ERROR;
        }
        if (name == null) {
            name = first.title;
            if (first.fileName != null) {
                String leaf = first.fileName.replace('\\', '/');
                leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
                String suffix = " - " + leaf;
                if (name != null && name.endsWith(suffix)) name = name.substring(0, name.length() - suffix.length());
            }
        }
        title = name != null && !name.isEmpty() ? name : "TorBox torrent " + id;
        posterUrl = poster; infoHash = hash; description = detail;
        size = total; downloaded = done; speed = rate;
        state = active ? TorBoxManager.State.DOWNLOADING : error ? TorBoxManager.State.ERROR
                : paused ? TorBoxManager.State.PAUSED : TorBoxManager.State.DONE;
        percent = total > 0 ? (int) Math.min(100, done * 100 / total)
                : state == TorBoxManager.State.DONE ? 100 : 0;
        files = Collections.unmodifiableList(new ArrayList<>(list));
    }

    public static List<TorBoxDownload> group(List<TorBoxManager.Download> files) {
        Map<Long, List<TorBoxManager.Download>> grouped = new LinkedHashMap<>();
        for (TorBoxManager.Download file : files)
            grouped.computeIfAbsent(file.torrentId, ignored -> new ArrayList<>()).add(file);
        List<TorBoxDownload> result = new ArrayList<>();
        for (List<TorBoxManager.Download> group : grouped.values()) result.add(new TorBoxDownload(group));
        return result;
    }
}
