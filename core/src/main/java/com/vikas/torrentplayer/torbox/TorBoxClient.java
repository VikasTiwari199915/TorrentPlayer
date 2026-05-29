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
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin synchronous client for the TorBox API (https://api.torbox.app/v1/api).
 *
 * <p>TorBox is a debrid service: you hand it a magnet, it downloads the torrent
 * on its own high-bandwidth servers, and then serves the finished file over a
 * plain HTTPS URL at full line speed — no P2P on the device. This client covers
 * the four calls we need: add magnet, poll status, list files, resolve a direct
 * download URL.
 *
 * <p>All methods block and must be called off the main thread. The TorBox
 * response envelope is {@code {success, error, detail, data}} and we navigate
 * it defensively with Gson trees (the published OpenAPI spec omits the 200
 * body schemas).
 */
public final class TorBoxClient {

    private static final String BASE = "https://api.torbox.app/v1/api";

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

    /** A file inside a TorBox torrent. */
    public static final class TbFile {
        public final int id;
        public final String name;
        public final long size;
        public TbFile(int id, String name, long size) {
            this.id = id; this.name = name; this.size = size;
        }
    }

    /** Snapshot of a TorBox torrent's server-side state. */
    public static final class TbTorrent {
        public final long id;
        public final String hash;
        public final String name;
        public final String downloadState;   // e.g. "downloading", "completed", "cached"
        public final boolean finished;       // download_finished || download_present
        public final double progress;        // 0..1
        public final long downloadSpeed;     // bytes/s on TorBox's side
        public final List<TbFile> files;
        public TbTorrent(long id, String hash, String name, String downloadState,
                         boolean finished, double progress, long downloadSpeed,
                         List<TbFile> files) {
            this.id = id; this.hash = hash; this.name = name;
            this.downloadState = downloadState; this.finished = finished;
            this.progress = progress; this.downloadSpeed = downloadSpeed;
            this.files = files;
        }
    }

    /**
     * Add a magnet to the user's TorBox account.
     * @return the created torrent id.
     * @throws IOException on network/auth/API failure.
     */
    public long addMagnet(@NonNull String magnet) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("magnet", magnet)
                .add("seed", "3")           // 3 = don't seed (download only)
                .add("allow_zip", "false")
                .build();
        Request req = new Request.Builder()
                .url(BASE + "/torrents/createtorrent")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
        JsonObject data = dataObject(exec(req));
        if (data == null) throw new IOException("TorBox: createtorrent returned no data");
        // Field is "torrent_id" on create.
        if (data.has("torrent_id") && !data.get("torrent_id").isJsonNull()) {
            return data.get("torrent_id").getAsLong();
        }
        if (data.has("id") && !data.get("id").isJsonNull()) {
            return data.get("id").getAsLong();
        }
        throw new IOException("TorBox: createtorrent gave no torrent_id");
    }

    /**
     * Fetch the current state (and file list) of one TorBox torrent.
     * @return null if TorBox doesn't (yet) know the id.
     */
    @Nullable
    public TbTorrent getTorrent(long torrentId) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE + "/torrents/mylist").newBuilder()
                .addQueryParameter("id", String.valueOf(torrentId))
                .addQueryParameter("bypass_cache", "true")
                .build();
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();
        JsonElement data = dataElement(exec(req));
        if (data == null || data.isJsonNull()) return null;
        // With ?id=, data is the object; some versions still wrap in an array.
        JsonObject o = data.isJsonArray()
                ? (data.getAsJsonArray().size() > 0 ? data.getAsJsonArray().get(0).getAsJsonObject() : null)
                : data.getAsJsonObject();
        if (o == null) return null;
        return parseTorrent(o);
    }

    /**
     * Resolve a direct, time-limited HTTPS URL for one file in a torrent.
     * Uses the {@code token} query param (not the Bearer header) per the API.
     */
    @NonNull
    public String requestDownloadUrl(long torrentId, int fileId) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE + "/torrents/requestdl").newBuilder()
                .addQueryParameter("token", apiKey)
                .addQueryParameter("torrent_id", String.valueOf(torrentId))
                .addQueryParameter("file_id", String.valueOf(fileId))
                .build();
        Request req = new Request.Builder().url(url).get().build();
        JsonElement data = dataElement(exec(req));
        if (data == null || !data.isJsonPrimitive()) {
            throw new IOException("TorBox: requestdl returned no URL");
        }
        return data.getAsString();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private static TbTorrent parseTorrent(JsonObject o) {
        long id = optLong(o, "id", 0);
        String hash = optString(o, "hash", "");
        String name = optString(o, "name", "");
        String state = optString(o, "download_state", "");
        boolean finished = optBool(o, "download_finished", false)
                || optBool(o, "download_present", false);
        double progress = optDouble(o, "progress", 0);
        long speed = optLong(o, "download_speed", 0);
        List<TbFile> files = new ArrayList<>();
        if (o.has("files") && o.get("files").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("files");
            for (JsonElement fe : arr) {
                if (!fe.isJsonObject()) continue;
                JsonObject fo = fe.getAsJsonObject();
                files.add(new TbFile(
                        (int) optLong(fo, "id", 0),
                        optString(fo, "name", optString(fo, "short_name", "file")),
                        optLong(fo, "size", 0)));
            }
        }
        return new TbTorrent(id, hash, name, state, finished, progress, speed, files);
    }

    /** Execute, parse the JSON envelope, throw if {@code success == false}. */
    private JsonObject exec(Request req) throws IOException {
        try (Response resp = http.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            String text = rb != null ? rb.string() : "";
            JsonObject root;
            try {
                root = JsonParser.parseString(text).getAsJsonObject();
            } catch (Throwable t) {
                if (!resp.isSuccessful()) {
                    throw new IOException("TorBox HTTP " + resp.code());
                }
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

    @Nullable
    private JsonObject dataObject(JsonObject root) {
        JsonElement e = root.get("data");
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : null;
    }

    @Nullable
    private JsonElement dataElement(JsonObject root) {
        return root.get("data");
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
