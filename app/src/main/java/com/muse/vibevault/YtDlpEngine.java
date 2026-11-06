package com.muse.vibevault;

import android.content.Context;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * Thin, blocking wrapper around the youtubedl-android library (which bundles
 * CPython + the yt-dlp executable + QuickJS).
 *
 * <p>Every method here blocks — always call from a background thread.</p>
 */
public final class YtDlpEngine {

    /** Progress callback: percent (may be negative if unknown), ETA, raw yt-dlp output line. */
    public interface ProgressListener {
        void onProgress(float progressPercent, long etaSeconds, String line);
    }

    private static Context appContext;
    private static boolean initialized;

    private YtDlpEngine() {
    }

    /** Idempotent. First call unpacks the Python runtime; takes a moment. */
    public static synchronized void init(Context context) throws YoutubeDLException {
        appContext = context.getApplicationContext();
        if (initialized) {
            return;
        }
        YoutubeDL.getInstance().init(appContext);
        FFmpeg.getInstance().init(appContext);
        initialized = true;
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    /** Bundled / last-updated yt-dlp version name, e.g. "2025.10.14". May be null. */
    public static String versionName(Context context) {
        try {
            return YoutubeDL.getInstance().versionName(context.getApplicationContext());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Downloads the newest yt-dlp binary from GitHub (stable channel) and
     * swaps it in — extractors break when sites change, so this is the fix
     * that doesn't need an app update.
     *
     * @return true if an update was installed, false if already up to date.
     */
    public static boolean update(Context context) throws YoutubeDLException {
        init(context);
        YoutubeDL.UpdateStatus status = YoutubeDL.getInstance().updateYoutubeDL(
                context.getApplicationContext(), YoutubeDL.UpdateChannel.STABLE.INSTANCE);
        return status == YoutubeDL.UpdateStatus.DONE;
    }

    /** Equivalent of {@code yt-dlp --dump-json <url>}. */
    public static VideoInfo fetchInfo(Context context, String url)
            throws YoutubeDLException, InterruptedException, YoutubeDL.CanceledException {
        init(context);
        return YoutubeDL.getInstance().getInfo(url);
    }

    /**
     * Runs a download into {@code outDir} and returns the finished media file.
     *
     * @param processId unique id for this run; pass the same id to
     *                  {@link #cancel(String)} to stop it.
     */
    public static File download(Context context, String url, FormatPreset preset,
                                File outDir, String processId, ProgressListener listener)
            throws YoutubeDLException, InterruptedException, YoutubeDL.CanceledException {
        init(context);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new YoutubeDLException("Could not create download folder: " + outDir);
        }
        Set<String> before = snapshotNames(outDir);

        YoutubeDLRequest request = buildRequest(url, preset, outDir);

        Function3<Float, Long, String, Unit> callback = new Function3<Float, Long, String, Unit>() {
            @Override
            public Unit invoke(Float progress, Long etaSeconds, String line) {
                if (listener != null) {
                    listener.onProgress(
                            progress == null ? -1f : progress,
                            etaSeconds == null ? -1L : etaSeconds,
                            line == null ? "" : line);
                }
                return Unit.INSTANCE;
            }
        };

        YoutubeDLResponse response =
                YoutubeDL.getInstance().execute(request, processId, callback);
        if (response.getExitCode() != 0) {
            throw new YoutubeDLException(
                    FormatUtils.summarizeError(response.getErr()));
        }

        File result = findProducedFile(outDir, before);
        if (result == null) {
            throw new YoutubeDLException("yt-dlp finished but produced no file");
        }
        return result;
    }

    /** Stops a running download. @return true if a process was killed. */
    public static boolean cancel(String processId) {
        try {
            return YoutubeDL.getInstance().destroyProcessById(processId);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------

    static YoutubeDLRequest buildRequest(String url, FormatPreset preset, File outDir) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-mtime");        // use download time, not the site's upload time
        request.addOption("--no-playlist");     // a shared link = that one item, not its playlist
        request.addOption("--newline");         // machine-readable progress lines
        request.addOption("-o", outDir.getAbsolutePath() + "/%(title).100B [%(id)s].%(ext)s");

        // If the user imported a cookies.txt (Instagram login), use it for
        // Instagram — that site refuses most anonymous extraction.
        if (appContext != null && isInstagramUrl(url) && SharePrefs.hasCookies(appContext)) {
            request.addOption("--cookies",
                    SharePrefs.cookiesFile(appContext).getAbsolutePath());
        }

        // Deliberately NO container/codec restrictions: whatever the site
        // offers is accepted, and yt-dlp/ffmpeg merge into a container that
        // fits. Restrictive selectors (ext=mp4 etc.) made downloads fail on
        // sites that don't offer exactly those formats — Instagram included.
        switch (preset) {
            case BEST:
                request.addOption("-f", "bestvideo+bestaudio/best");
                break;
            case P1080:
                request.addOption("-f",
                        "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best");
                break;
            case P720:
                request.addOption("-f",
                        "bestvideo[height<=720]+bestaudio/best[height<=720]/best");
                break;
            case P480:
                request.addOption("-f",
                        "bestvideo[height<=480]+bestaudio/best[height<=480]/best");
                break;
            case MP3:
                request.addOption("-f", "bestaudio/best");
                request.addOption("-x");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", "0");
                break;
            case AUDIO_ORIGINAL:
                request.addOption("-f", "bestaudio/best");
                request.addOption("-x");
                request.addOption("--audio-format", "best");
                break;
        }
        return request;
    }

    public static boolean isInstagramUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("instagram.com/") || lower.contains("www.instagram.");
    }

    private static Set<String> snapshotNames(File dir) {
        Set<String> names = new HashSet<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                names.add(f.getName());
            }
        }
        return names;
    }

    /**
     * Finds the media file yt-dlp just produced: a new file in {@code outDir}
     * that is not a partial/temp artifact. Picks the largest candidate —
     * merged output wins over any leftover intermediate.
     */
    private static File findProducedFile(File outDir, Set<String> before) {
        File[] files = outDir.listFiles();
        if (files == null) {
            return null;
        }
        File best = null;
        for (File f : files) {
            if (!f.isFile() || before.contains(f.getName()) || f.length() == 0) {
                continue;
            }
            String name = f.getName().toLowerCase();
            if (name.endsWith(".part") || name.endsWith(".ytdl")
                    || name.endsWith(".temp") || name.endsWith(".tmp")
                    || name.contains(".part-frag")) {
                continue;
            }
            if (best == null || f.length() > best.length()) {
                best = f;
            }
        }
        return best;
    }
}
