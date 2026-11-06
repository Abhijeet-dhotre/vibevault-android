package com.muse.vibevault;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.yausername.youtubedl_android.YoutubeDL;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;

/**
 * Foreground service that owns the actual downloads.
 *
 * <p>Flow: share/paste -> {@link #startDownload} -> stage into app-specific
 * storage (native processes can't use MediaStore) -> export the finished
 * file to public {@code Downloads/VibeVault} -> record history -> result
 * notification. Progress is published through {@link DownloadCoordinator}
 * for the UI and mirrored into the ongoing notification (throttled).</p>
 *
 * <p>Jobs <b>queue</b>: sharing several reels in a row downloads them one
 * after another in the background — a share never opens the app and never
 * gets a "busy" rejection. {@link #requestCancel} cancels the running job
 * and drops everything still queued.</p>
 */
public class DownloadService extends Service {

    private static final String TAG = "DownloadService";

    public static final String ACTION_START = "com.muse.vibevault.action.START";
    public static final String ACTION_CANCEL = "com.muse.vibevault.action.CANCEL";

    private static final String EXTRA_URL = "extra_url";
    private static final String EXTRA_PRESET = "extra_preset";
    private static final String EXTRA_TITLE = "extra_title";

    /** Failure whose message is already user-readable (skip error summarizing). */
    private static final class InstagramBlockedException extends Exception {
        InstagramBlockedException(String message) {
            super(message);
        }
    }

    private static final class Job {
        final String id;
        final String url;
        final FormatPreset preset;
        final String title;

        Job(String id, String url, FormatPreset preset, String title) {
            this.id = id;
            this.url = url;
            this.preset = preset;
            this.title = title;
        }
    }

    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private boolean workerRunning;
    private long jobSequence;
    private volatile String activeProcessId;

    // ------------------------------------------------------------------ API

    public static void startDownload(Context context, String url, FormatPreset preset,
                                     @Nullable String title) {
        Intent intent = new Intent(context, DownloadService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_PRESET, preset.name())
                .putExtra(EXTRA_TITLE, title);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void requestCancel(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_CANCEL);
        context.startService(intent);
    }

    // -------------------------------------------------------------- Service

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            synchronized (queue) {
                queue.clear();
            }
            if (activeProcessId != null) {
                YtDlpEngine.cancel(activeProcessId);
            } else {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            String presetName = intent.getStringExtra(EXTRA_PRESET);
            String title = intent.getStringExtra(EXTRA_TITLE);
            if (url == null) {
                return START_NOT_STICKY;
            }
            FormatPreset preset;
            try {
                preset = FormatPreset.valueOf(presetName);
            } catch (Exception e) {
                preset = FormatPreset.BEST;
            }
            Job job;
            synchronized (queue) {
                job = new Job(System.currentTimeMillis() + "-" + (++jobSequence),
                        url, preset, title);
                queue.add(job);
            }
            // startForegroundService() obliges us to go foreground promptly;
            // the worker refreshes this notification as the job progresses.
            startForegroundFor(new DownloadState(job.id, job.url, job.title, job.preset));
            ensureWorker();
        }
        return START_NOT_STICKY;
    }

    private void ensureWorker() {
        synchronized (queue) {
            if (workerRunning) {
                return;
            }
            workerRunning = true;
        }
        new Thread(this::workerLoop, "vibevault-download").start();
    }

    private void workerLoop() {
        while (true) {
            Job job;
            synchronized (queue) {
                job = queue.poll();
                if (job == null) {
                    workerRunning = false;
                    break;
                }
            }
            runDownload(job);
        }
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------- Download

    private void runDownload(Job job) {
        String processId = "vibevault-" + job.id;
        activeProcessId = processId;

        DownloadState state = new DownloadState(job.id, job.url, job.title, job.preset);
        state.status = DownloadState.Status.RUNNING;
        DownloadCoordinator.get().publish(state);
        startForegroundFor(state);

        File stagingDir = new File(stagingBase(), job.id);
        try {
            YtDlpEngine.ProgressListener listener = (progress, eta, line) -> {
                state.progress = progress;
                state.etaSeconds = eta;
                if (line != null && !line.trim().isEmpty()) {
                    state.line = line.trim();
                }
                DownloadCoordinator.get().publishProgress();
                maybeUpdateNotification(state);
            };

            File produced = produceFile(state, job, stagingDir, processId, listener);

            if (state.title == null || state.title.isEmpty()) {
                state.title = stripExtension(produced.getName());
            }

            state.status = DownloadState.Status.EXPORTING;
            DownloadCoordinator.get().publishProgress();
            NotificationHelper.notify(this, NotificationHelper.ID_PROGRESS,
                    NotificationHelper.progressNotification(this, state));

            MediaStoreExporter.ExportedFile exported =
                    MediaStoreExporter.export(this, produced);

            state.status = DownloadState.Status.DONE;
            state.resultName = exported.displayName;
            state.resultUri = exported.uri.toString();
            state.resultBytes = exported.sizeBytes;

            HistoryStore.add(this, new DownloadRecord(
                    System.currentTimeMillis(),
                    state.title,
                    job.url,
                    getString(job.preset.labelRes),
                    exported.displayName,
                    exported.uri.toString(),
                    exported.sizeBytes,
                    System.currentTimeMillis()));

            NotificationHelper.notify(this, NotificationHelper.ID_RESULT,
                    NotificationHelper.resultNotification(this, state));
        } catch (YoutubeDL.CanceledException e) {
            state.status = DownloadState.Status.CANCELLED;
            NotificationHelper.notify(this, NotificationHelper.ID_RESULT,
                    NotificationHelper.resultNotification(this, state));
        } catch (Exception e) {
            Log.e(TAG, "download failed", e);
            state.status = DownloadState.Status.FAILED;
            state.error = e instanceof InstagramBlockedException
                    ? e.getMessage()
                    : FormatUtils.summarizeError(e.getMessage());
            NotificationHelper.notify(this, NotificationHelper.ID_RESULT,
                    NotificationHelper.resultNotification(this, state));
        } finally {
            DownloadCoordinator.get().publish(state);
            deleteRecursive(stagingDir);
            activeProcessId = null;
            stopForeground(STOP_FOREGROUND_REMOVE);
            NotificationHelper.cancel(this, NotificationHelper.ID_PROGRESS);
        }
    }

    /**
     * Produces the media file: yt-dlp first. If Instagram refuses yt-dlp
     * (login wall), falls back to pulling the media straight out of the post
     * page — that path also covers photo posts, which yt-dlp cannot fetch.
     */
    private File produceFile(DownloadState state, Job job, File stagingDir, String processId,
                             YtDlpEngine.ProgressListener listener) throws Exception {
        try {
            return YtDlpEngine.download(this, job.url, job.preset, stagingDir, processId, listener);
        } catch (YoutubeDL.CanceledException e) {
            throw e;
        } catch (Exception e) {
            if (!YtDlpEngine.isInstagramUrl(job.url)) {
                throw e;
            }
            state.line = getString(R.string.notif_instagram_fallback);
            DownloadCoordinator.get().publishProgress();
            NotificationHelper.notify(this, NotificationHelper.ID_PROGRESS,
                    NotificationHelper.progressNotification(this, state));
            try {
                return InstagramFallback.download(this, job.url, stagingDir, listener);
            } catch (IOException fallbackError) {
                throw new InstagramBlockedException(getString(R.string.error_instagram_login));
            }
        }
    }

    // -------------------------------------------------------------- Helpers

    private File stagingBase() {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) {
            base = new File(getFilesDir(), "downloads");
        }
        return new File(base, "staging");
    }

    private void startForegroundFor(DownloadState state) {
        android.app.Notification notification =
                NotificationHelper.progressNotification(this, state);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NotificationHelper.ID_PROGRESS, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NotificationHelper.ID_PROGRESS, notification);
        }
    }

    private long lastNotificationAt;
    private int lastNotificationPercent = -2;

    /** Progress notifications are noisy — update on percent change, at most 1/sec. */
    private void maybeUpdateNotification(DownloadState state) {
        int percent = Math.round(state.progress);
        long now = System.currentTimeMillis();
        if (percent != lastNotificationPercent && now - lastNotificationAt >= 1000) {
            lastNotificationPercent = percent;
            lastNotificationAt = now;
            NotificationHelper.notify(this, NotificationHelper.ID_PROGRESS,
                    NotificationHelper.progressNotification(this, state));
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
