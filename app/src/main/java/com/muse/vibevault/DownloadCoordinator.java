package com.muse.vibevault;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide holder for the single in-flight download's state.
 * The {@link DownloadService} publishes; {@link MainActivity} subscribes.
 * One download at a time is a deliberate product choice (yt-dlp processes are
 * heavy) and keeps cancellation unambiguous.
 */
public final class DownloadCoordinator {

    public interface Listener {
        /** Called on whatever thread published the change — hop to main if touching UI. */
        void onDownloadStateChanged(DownloadState state);
    }

    private static final DownloadCoordinator INSTANCE = new DownloadCoordinator();

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile DownloadState current;

    private DownloadCoordinator() {
    }

    public static DownloadCoordinator get() {
        return INSTANCE;
    }

    public DownloadState current() {
        return current;
    }

    public boolean isRunning() {
        return current != null && current.isActive();
    }

    public void publish(DownloadState state) {
        current = state;
        notifyListeners(state);
    }

    /** Call after mutating the current state's progress fields. */
    public void publishProgress() {
        if (current != null) {
            notifyListeners(current);
        }
    }

    private void notifyListeners(DownloadState state) {
        for (Listener l : listeners) {
            l.onDownloadStateChanged(state);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
