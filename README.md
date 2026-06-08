# Torrent Player

Torrent Player is an Android and Android TV app for discovering torrent-backed movies and shows, managing local downloads, and playing downloaded or in-progress media with Media3/ExoPlayer.

The project is split into a shared engine module plus separate phone and TV front ends.

## Features

- Search movies and TV shows through the TorrentClaw API.
- Discover trending, popular, recently added, and top streaming movies/shows.
- Filter searches by content type, genre, season, and episode.
- Browse TMDB season and episode metadata for shows.
- Start P2P downloads and stream playable files while they are still downloading.
- Persist downloads with Room so they survive app restarts.
- Foreground download service for long-running transfers.
- Download details screen with info, files, pieces, trackers, and peers.
- Piece verification action for stuck or incomplete torrents.
- File tree view with per-file download/skip controls.
- Built-in Media3 player with subtitle support.
- Optional TorBox integration for cloud-assisted streaming/downloads.
- GitHub Releases based self-update flow for sideloaded installs.
- Separate Android TV UI optimized for D-pad navigation.

## Project Structure

```text
.
├── app/      # Phone/tablet Android app
├── tv/       # Android TV app
├── core/     # Shared API, torrent engine, persistence, services, utilities
└── .github/  # Release workflow
```

## Requirements

- Android Studio or command-line Android SDK.
- JDK 17.
- Android Gradle Plugin and Gradle wrapper from this repository.
- TorrentClaw API key.
- Optional TMDB API credential for season/episode metadata.
- Optional TorBox API key.

Minimum SDK:

- Phone app: Android 12 / API 31.
- TV app: Android 11 / API 30.

## Configuration

Runtime settings are configured inside the app:

- `TorrentClaw API key`: required for search and discover endpoints.
- `TMDB API credential`: optional v4 read token or v3 API key for show metadata.
- `TorBox API key`: optional cloud download/streaming integration.
- Download storage location and resume behavior.

The app does not ship with API keys.

## Build

Debug builds:

```bash
./gradlew :app:assembleDebug
./gradlew :tv:assembleDebug
```

Release builds:

```bash
./gradlew :app:assembleRelease :tv:assembleRelease -PversionName=1.0.2
```

If `keystore.jks` exists in the repository root, release builds use it automatically. The following environment variables are expected:

```bash
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

Without `keystore.jks`, local release builds are left unsigned by the project signing config.

## GitHub Releases

The included workflow builds signed APKs when a tag matching `v*` is pushed.

```bash
git tag v1.0.2
git push origin v1.0.2
```

The workflow:

- Decodes `KEYSTORE_BASE64` into `keystore.jks`.
- Builds both `:app` and `:tv`.
- Passes the tag version into Gradle as `-PversionName`.
- Uploads:
  - `torrentplayer-app-<version>.apk`
  - `torrentplayer-tv-<version>.apk`

The in-app updater uses GitHub Releases and selects the correct asset by module (`app` or `tv`).

Required repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Storage Notes

The app can store downloads in app media folders so gallery and external players can discover completed media. Android TV builds also include storage options for external drives. Some TV firmware exposes storage permissions differently, so the settings screen includes storage diagnostics and volume selection helpers.

## Torrent Playback Notes

Playback while downloading depends on container layout and codec support:

- MKV files often become playable once enough beginning and tail data exists.
- Some MP4 releases require the full file before playback if metadata is stored at the end.
- The Pieces tab can verify and recheck incomplete pieces.
- If missing pieces are unavailable from peers, verification can identify the issue but cannot complete the torrent until peers provide those pieces.

## Responsible Use

Torrent Player is a client application. It does not host or distribute media. Use it only with content you have the right to access, download, or share. Respect copyright law and the terms of any services you connect to.

## Development

Useful commands:

```bash
./gradlew :app:assembleDebug :tv:assembleDebug
./gradlew :app:assembleRelease :tv:assembleRelease -PversionName=1.0.2
```

The shared torrent engine lives in `core`, so changes there should be verified against both app modules.

## License

No license file is currently included. Add a license before publishing or accepting external contributions.
