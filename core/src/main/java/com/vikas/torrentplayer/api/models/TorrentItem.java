package com.vikas.torrentplayer.api.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/**
 * Mirrors a single entry from {@code SearchResult.torrents}. Field shape follows
 * the actual TorrentClaw response (which differs from the docs schema in a few
 * places — see notes inline).
 */
public class TorrentItem implements Serializable {
    @SerializedName("infoHash")
    public String infoHash;

    @SerializedName("rawTitle")
    public String rawTitle;

    /** e.g. "480p", "720p", "1080p", "2160p" */
    @SerializedName("quality")
    public String quality;

    @SerializedName("codec")
    public String codec;

    @SerializedName("sourceType")
    public String sourceType;

    /** Bytes (the API sends a JSON number, not a string). */
    @SerializedName("sizeBytes")
    public long sizeBytes;

    @SerializedName("seeders")
    public int seeders;

    @SerializedName("leechers")
    public int leechers;

    @SerializedName("magnetUrl")
    public String magnetUrl;

    @SerializedName("torrentUrl")
    public String torrentUrl;

    @SerializedName("source")
    public String source;

    @SerializedName("qualityScore")
    public int qualityScore;

    @SerializedName("uploadedAt")
    public String uploadedAt;

    @SerializedName("languages")
    public List<String> languages;

    @SerializedName("audioCodec")
    public String audioCodec;

    @SerializedName("audioChannels")
    public Integer audioChannels;

    @SerializedName("audioTracks")
    public List<AudioTrack> audioTracks;

    @SerializedName("subtitleTracks")
    public List<SubtitleTrack> subtitleTracks;

    /** Convenience list of subtitle language codes (e.g. ["en", "hi"]). */
    @SerializedName("subtitleLanguages")
    public List<String> subtitleLanguages;

    @SerializedName("videoInfo")
    public VideoInfo videoInfo;

    /** "success" / "error" / null — present on scanned torrents. */
    @SerializedName("scanStatus")
    public String scanStatus;

    /** "clean" / null — security scan verdict. */
    @SerializedName("threatLevel")
    public String threatLevel;

    @SerializedName("hdrType")
    public String hdrType;

    @SerializedName("releaseGroup")
    public String releaseGroup;

    @SerializedName("isProper")
    public boolean isProper;

    @SerializedName("isRepack")
    public boolean isRepack;

    @SerializedName("isRemastered")
    public boolean isRemastered;

    @SerializedName("season")
    public Integer season;

    @SerializedName("episode")
    public Integer episode;

    /**
     * Paid-tier responses include an explicit {@code verified} flag. Free-tier
     * responses don't, so callers should prefer {@link #isTrusted()}.
     */
    @SerializedName("verified")
    public Boolean verified;

    /**
     * A torrent is considered "trusted" when the API explicitly says
     * {@code verified=true}, OR when both its scan succeeded and the threat
     * scan came back clean. This is the signal the UI displays as a tick.
     */
    public boolean isTrusted() {
        if (verified != null && verified) return true;
        return "success".equalsIgnoreCase(scanStatus)
                && "clean".equalsIgnoreCase(threatLevel);
    }

    /** "1920×800" style resolution, or null if videoInfo missing. */
    public String resolutionLabel() {
        if (videoInfo == null) return null;
        if (videoInfo.width <= 0 || videoInfo.height <= 0) return null;
        return videoInfo.width + "×" + videoInfo.height;
    }

    /** Best-effort HDR label combining videoInfo.hdr + hdrType. */
    public String hdrLabel() {
        if (hdrType != null && !hdrType.isEmpty()) return hdrType;
        if (videoInfo != null && videoInfo.hdr != null && !videoInfo.hdr.isEmpty())
            return videoInfo.hdr;
        return null;
    }

    /** Audio summary like "Hindi · DD+ 5.1" — pulls from audioTracks if present. */
    public String audioLabel() {
        // Prefer first default audio track
        if (audioTracks != null && !audioTracks.isEmpty()) {
            AudioTrack first = null;
            for (AudioTrack t : audioTracks) {
                if (t != null && t.isDefault) { first = t; break; }
            }
            if (first == null) first = audioTracks.get(0);
            StringBuilder sb = new StringBuilder();
            if (first.lang != null) sb.append(first.lang.toUpperCase());
            if (first.codec != null) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(first.codec.toUpperCase());
            }
            if (first.channels > 0) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(channelsToLabel(first.channels));
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        // Fallback to top-level audioCodec
        if (audioCodec == null && audioChannels == null) return null;
        StringBuilder sb = new StringBuilder();
        if (audioCodec != null) sb.append(audioCodec.toUpperCase());
        if (audioChannels != null && audioChannels > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(channelsToLabel(audioChannels));
        }
        return sb.toString();
    }

    private static String channelsToLabel(int channels) {
        switch (channels) {
            case 1: return "1.0";
            case 2: return "2.0";
            case 6: return "5.1";
            case 8: return "7.1";
            default: return channels + "ch";
        }
    }

    // ----- Nested types (flat in the real response, not under trueSpec) -----

    public static class AudioTrack implements Serializable {
        @SerializedName("codec") public String codec;
        @SerializedName("channels") public int channels;
        @SerializedName("lang") public String lang;
        @SerializedName("title") public String title;
        @SerializedName("default") public boolean isDefault;
    }

    public static class SubtitleTrack implements Serializable {
        @SerializedName("lang") public String lang;
        @SerializedName("codec") public String codec;
        @SerializedName("title") public String title;
        @SerializedName("forced") public boolean forced;
        @SerializedName("default") public boolean isDefault;
    }

    public static class VideoInfo implements Serializable {
        @SerializedName("codec") public String codec;
        @SerializedName("width") public int width;
        @SerializedName("height") public int height;
        @SerializedName("profile") public String profile;
        @SerializedName("bitDepth") public int bitDepth;
        @SerializedName("duration") public double duration;
        @SerializedName("frameRate") public float frameRate;
        @SerializedName("hdr") public String hdr;
    }
}
