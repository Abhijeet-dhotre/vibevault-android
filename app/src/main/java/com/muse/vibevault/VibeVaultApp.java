package com.muse.vibevault;

import android.app.Application;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application entry point: creates the notification channel and warms up the
 * yt-dlp engine (first init unpacks the bundled Python runtime, which takes a
 * second or two) so the first real download starts instantly.
 */
public class VibeVaultApp extends Application {

    private static final String TAG = "VibeVaultApp";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);

        ExecutorService warmup = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ytdlp-warmup");
            t.setDaemon(true);
            return t;
        });
        warmup.execute(() -> {
            try {
                YtDlpEngine.init(VibeVaultApp.this);
            } catch (Exception e) {
                Log.e(TAG, "yt-dlp engine warm-up failed", e);
            }
        });
        warmup.shutdown();
    }
}
