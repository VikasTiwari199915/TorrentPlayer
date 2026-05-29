package com.vikas.torrentplayer.torbox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin synchronous client for the TorBox API (https://api.torbox.app/v1/api).
 *
 * <p>TorBox is a debrid service: hand it a magnet, it downloads the torrent on
 * its high-bandwidth servers, then serves each file over a plain HTTPS URL at
 * full line speed — no P2P on the device. Cached torrents are available
 * instantly.
 *
 * <p>All methods block and must be called off the main thread. The TorBox
 * envelope is {@code {success, error, detail, data}}; field names below match
 * real API responses.
 */
public final class TorBoxClient {

    private static final String BASE = "https://api.torbox.app/v1/api";
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final String[] VIDEO_EXTS = {
            ".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v",
            ".flv", ".ts", ".m2ts", ".wmv", ".mpg", ".mpeg"
    };

    private final OkHttpClient http;
    private final String apiKey;

    public TorBoxClient(@NonNull String apiKey) {
        this.apiKey = apiKey;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    // ------------------------------------------------------------------
    // Models
    // ------------------------------------------------------------------

    /** A file inside a TorBox torrent. */
    public static final class TbFile {
        public final int id;
        public final String name;       // full path within the torrent
        public final String shortName;  // leaf file name
        public final long size;
        public final String mimetype;
        public TbFile(int id, String name, String shortName, long size, String mimetype) {
            this.id = id; this.name = name; this.shortName = shortName;
            this.size = size; this.mimetype = mimetype;
        }
        public boolean isVideo() {
            if (mimetype != null && mimetype.startsWith("video/")) return true;
            String n = (shortName != null ? shortName : name);
            if (n == null) return false;
            String low = n.toLowerCase(Locale.US);
            for (String e : VIDEO_EXTS) if (low.endsWith(e)) return true;
            return false;
        }
    }

    /** A torrent in the user's TorBox account / a freshly-added one. */
    public static final class TbTorrent {
        public final long id;
        public final String hash;
        public final String name;
        public final long size;
        public final String downloadState;   // "cached", "downloading", "completed", …
        public final boolean cached;
        public final boolean finished;        // download_finished || download_present
        public final double progress;         // 0..1
        public final long downloadSpeed;      // bytes/s on TorBox's side
        public final String createdAt;
        public final List<TbFile> files;
        public TbTorrent(long id, String hash, String name, long size, String downloadState,
                         boolean cached, boolean finished, double progress, long downloadSpeed,
                         String createdAt, List<TbFile> files) {
            this.id = id; this.hash = hash; this.name = name; this.size = size;
            this.downloadState = downloadState; this.cached = cached; this.finished = finished;
            this.progress = progress; this.downloadSpeed = downloadSpeed;
            this.createdAt = createdAt; this.files = files;
        }
        @Nullable public TbFile largestVideo() {
            TbFile best = null;
            for (TbFile f : files) {
                if (!f.isVideo()) continue;
                if (best == null || f.size > best.size) best = f;
            }
            if (best == null) { // no video ext — fall back to largest overall
                for (TbFile f : files) if (best == null || f.size > best.size) best = f;
            }
            return best;
        }
    }

    /** Result of adding a magnet. */
    public static final class AddResult {
        public final long torrentId;
        public final String hash;
        public final boolean cached;   // detail said "Found Cached Torrent"
        public AddResult(long torrentId, String hash, boolean cached) {
            this.torrentId = torrentId; this.hash = hash; this.cached = cached;
        }
    }

    // ------------------------------------------------------------------
    // Endpoints
    // ------------------------------------------------------------------

    /** Add a magnet to the user's TorBox account. */
    public AddResult addMagnet(@NonNull String magnet) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("magnet", magnet)
                .add("seed", "3")           // 3 = download only, don't seed
                .add("allow_zip", "false")
                .build();
        Request req = new Request.Builder()
                .url(BASE + "/torrents/createtorrent")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
        JsonObject root = exec(req);
        JsonObject data = root.getAsJsonObject("data");
        if (data == null) throw new IOException("TorBox: createtorrent returned no data");
        long id = optLong(data, "torrent_id", -1);
        if (id < 0) id = optLong(data, "id", -1);
        if (id < 0) throw new IOException("TorBox: createtorrent gave no torrent_id");
        boolean cached = optString(root, "detail", "").toLowerCase(Locale.US).contains("cached");
        return new AddResult(id, optString(data, "hash", ""), cached);
    }

    /** Fetch one torrent's current state + file list, or null if unknown. */
    @Nullable
    public TbTorrent getTorrent(long torrentId) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE + "/torrents/mylist").newBuilder()
                .addQueryParameter("id", String.valueOf(torrentId))
                .addQueryParameter("bypass_cache", "true")
                .build();
        JsonElement data = exec(get(url)).get("data");
        if (data == null || data.isJsonNull()) return null;
        JsonObject o = data.isJsonArray()
                ? (data.getAsJsonArray().size() > 0 ? data.getAsJsonArray().get(0).getAsJsonObject() : null)
                : data.getAsJsonObject();
        return o == null ? null : parseTorrent(o);
    }

    /** List every torrent in the user's TorBox account. */
    public List<TbTorrent> listTorrents() throws IOException {
        JsonElement data = exec(get(HttpUrl.parse(BASE + "/torrents/mylist"))).get("data");
        List<TbTorrent> out = new ArrayList<>();
        if (data != null && data.isJsonArray()) {
            for (JsonElement e : data.getAsJsonArray()) {
                if (e.isJsonObject()) out.add(parseTorrent(e.getAsJsonObject()));
            }
        }
        return out;
    }

    /** Cache-check a single hash; returns the cached torrent (with files) or null. */
    @Nullable
    public TbTorrent checkCached(@NonNull String hash) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE + "/torrents/checkcached").newBuilder()
                .addQueryParameter("hash", hash)
                .addQueryParameter("format", "object")
                .addQueryParameter("list_files", "true")
                .build();
        JsonElement data = exec(get(url)).get("data");
        if (data == null || !data.isJsonObject()) return null;
        JsonObject obj = data.getAsJsonObject();
        // data is a map keyed by hash; take the first entry.
        for (String k : obj.keySet()) {
            JsonElement e = obj.get(k);
            if (e != null && e.isJsonObject()) {
                JsonObject t = e.getAsJsonObject();
                return new TbTorrent(0, optString(t, "hash", hash), optString(t, "name", ""),
                        optLong(t, "size", 0), "cached", true, true, 1.0, 0, null,
                        parseFiles(t));
            }
        }
        return null;
    }

    /** Resolve a direct, time-limited HTTPS URL for one file in a torrent. */
    @NonNull
    public String requestDownloadUrl(long torrentId, int fileId) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE + "/torrents/requestdl").newBuilder()
                .addQueryParameter("token", apiKey)
                .addQueryParameter("torrent_id", String.valueOf(torrentId))
                .addQueryParameter("file_id", String.valueOf(fileId))
                .addQueryParameter("zip_link", "false")
                .build();
        JsonElement data = exec(get(url)).get("data");
        if (data == null || !data.isJsonPrimitive()) {
            throw new IOException("TorBox: requestdl returned no URL");
        }
        return data.getAsString();
    }

    /** Control a torrent — operation is one of: delete, pause, resume, reannounce. */
    public void controlTorrent(long torrentId, @NonNull String operation) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("torrent_id", torrentId);
        payload.addProperty("operation", operation);
        Request req = new Request.Builder()
                .url(BASE + "/torrents/controltorrent")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(payload.toString(), JSON))
                .build();
        exec(req); // throws on success == false
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private Request get(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();
    }

    private static TbTorrent parseTorrent(JsonObject o) {
        boolean finished = optBool(o, "download_finished", false)
                || optBool(o, "download_present", false);
        return new TbTorrent(
                optLong(o, "id", 0),
                optString(o, "hash", ""),
                optString(o, "name", ""),
                optLong(o, "size", 0),
                optString(o, "download_state", ""),
                optBool(o, "cached", false),
                finished,
                optDouble(o, "progress", 0),
                optLong(o, "download_speed", 0),
                optString(o, "created_at", null),
                parseFiles(o));
    }

    private static List<TbFile> parseFiles(JsonObject o) {
        List<TbFile> files = new ArrayList<>();
        if (o.has("files") && o.get("files").isJsonArray()) {
            for (JsonElement fe : o.getAsJsonArray("files")) {
                if (!fe.isJsonObject()) continue;
                JsonObject fo = fe.getAsJsonObject();
                files.add(new TbFile(
                        (int) optLong(fo, "id", 0),
                        optString(fo, "name", ""),
                        optString(fo, "short_name", optString(fo, "name", "file")),
                        optLong(fo, "size", 0),
                        optString(fo, "mimetype", "")));
            }
        }
        return files;
    }

    private JsonObject exec(Request req) throws IOException {
        try (Response resp = http.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            String text = rb != null ? rb.string() : "";
            JsonObject root;
            try {
                root = JsonParser.parseString(text).getAsJsonObject();
            } catch (Throwable t) {
                if (!resp.isSuccessful()) throw new IOException("TorBox HTTP " + resp.code());
                throw new IOException("TorBox: unparseable response");
            }
            boolean success = optBool(root, "success", resp.isSuccessful());
            if (!success) {
                String detail = optString(root, "detail",
                        optString(root, "error", "TorBox request failed"));
                throw new IOException("TorBox: " + detail);
            }
            return root;
        }
    }

    private static String optString(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }
    private static long optLong(JsonObject o, String k, long def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : def; }
        catch (Throwable t) { return def; }
    }
    private static double optDouble(JsonObject o, String k, double def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def; }
        catch (Throwable t) { return def; }
    }
    private static boolean optBool(JsonObject o, String k, boolean def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def; }
        catch (Throwable t) { return def; }
    }
}
