# VibeVault — Video Downloader: Save Reels, Shorts, TikToks & Videos

Save the vibe for later.

**VibeVault is a free, open-source Android video downloader.** Save videos,
Instagram Reels, YouTube Shorts, TikToks, and posts from X / Twitter to your
phone in MP4 video (up to 1080p) or MP3 audio — just share a link to the app
and get the file. No ads, no account, no server, no watermark.

VibeVault is an Android app that downloads video/audio from a link **shared to it via the
Android Share sheet** (YouTube, Instagram, X, TikTok, a browser — any app),
powered by a real on-device **yt-dlp**. Also works with pasted links.

## Features

- **Share to download** — share any video link from YouTube, Instagram, TikTok, X, or a browser; the app never needs to open
- **Video downloader** — best MP4, up to 1080p, 720p, or 480p
- **Audio downloader** — MP3 or original audio format
- **Reels & Shorts saver** — save Instagram Reels, YouTube Shorts, and TikTok videos for offline viewing
- **On-device yt-dlp** — no server, no account, no ads, no tracking
- **Download history** — finished files in `Downloads/VibeVault`, tap to open
- **Self-updating yt-dlp** — update the extractor in-app when a site changes, no app update needed

No server, no account, no ads: extraction and downloading happen entirely on
the phone via the [youtubedl-android](https://github.com/yausername/youtubedl-android)
library, which bundles CPython + yt-dlp + FFmpeg + QuickJS into the APK.

## Build

Requires JDK 17 and the Android SDK (platform 34). Open the folder in
Android Studio, or from the command line:

```bash
./gradlew assembleDebug
```

> Note: `gradle/wrapper/gradle-wrapper.jar` is not in this repo yet — add it
> from a Gradle distribution, or run `./gradlew` once in Android Studio so
> the wrapper is generated, before building from the command line.

Outputs (per-ABI splits + a universal APK) land in
`app/build/outputs/apk/debug/`. Most phones want the `arm64-v8a` APK.
Heads-up: each APK is large (~60–90 MB) — that's the bundled Python runtime,
unavoidable for running yt-dlp on-device.

## Use

1. In any app, tap **Share** on a video/post and choose **VibeVault**
   (or open VibeVault and paste a link).
2. The app pulls the first URL out of the shared text, fetches the title /
   thumbnail / duration, and waits for you to pick a format:
   **Best video (MP4) · up to 1080p · up to 720p · Audio MP3 · Audio original**.
3. **Download** runs in a foreground service with a progress notification
   and a Cancel action. Finished files are exported to
   **`Downloads/VibeVault`** and listed in the in-app history
   (tap to open, long-press to remove the entry).

## How it works

| Piece | File |
|---|---|
| Share-sheet + "Open with" intake, URL extraction from share text | `MainActivity`, `UrlExtractor` |
| yt-dlp wrapper: init, `--dump-json` info fetch, format presets, cancel, self-update | `YtDlpEngine` |
| Foreground download service, notifications, progress | `DownloadService`, `NotificationHelper`, `DownloadCoordinator` |
| Staging in app storage -> export to public Downloads (MediaStore on Android 10+, direct file below) | `MediaStoreExporter` |
| History (JSON file, newest first, cap 200) | `HistoryStore`, `DownloadRecord`, `HistoryAdapter` |

Design notes:

- **One download at a time**, with a named yt-dlp process id so Cancel kills
  exactly that process.
- **Shared links download that item only** (`--no-playlist`).
- **yt-dlp can update itself** (header button) — when a site changes and
  extraction breaks, updating the yt-dlp binary usually fixes it without an
  app release.
- Downloads are staged in app-specific storage because native subprocesses
  can't write through MediaStore; the finished file is then copied out.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE` (downloading), `FOREGROUND_SERVICE` +
`FOREGROUND_SERVICE_DATA_SYNC` (download service), `POST_NOTIFICATIONS`
(progress; optional), `WRITE_EXTERNAL_STORAGE` (only on Android ≤ 9, for the
public Downloads folder).

## Limits & disclaimer

- Only download content you own or have the right to download. Downloading
  copyrighted content without permission may infringe copyright, and
  downloading from YouTube, Instagram, TikTok and X via a third-party tool
  violates those platforms' Terms of Service.
- DRM-protected services (Netflix, Prime Video, Disney+, …) are not
  supported by yt-dlp. Private/login-only posts need an account session,
  which this app does not collect.
- Some sites throttle or block datacenter/mobile IPs or change their pages
  often — the in-app **Update yt-dlp** button is the first fix.
- VibeVault is not affiliated with, endorsed by, or connected to YouTube,
  Google, Instagram, Meta, TikTok, or X. Their names and logos belong to
  their respective owners.
- This app is distributed here for sideloading. It is not on Google Play —
  Play policy does not allow YouTube downloaders.

## Tests

```bash
./gradlew testDebugUnitTest   # UrlExtractorTest covers share-text parsing
```

## Credits / licenses

yt-dlp (Unlicense), youtubedl-android (GPL-3.0), FFmpeg (LGPL/GPL),
CPython (PSF). This project is GPL-3.0 — see `LICENSE` — for compatibility with
youtubedl-android.
