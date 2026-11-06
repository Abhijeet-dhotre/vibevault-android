package com.muse.vibevault;

/** Immutable-ish snapshot of the current (or last) download, shared service -> UI. */
public class DownloadState {

    public enum Status {
        IDLE, RUNNING, EXPORTING, DONE, FAILED, CANCELLED
    }

    public final String id;
    public final String url;
    public volatile String title;
    public final FormatPreset preset;

    public volatile Status status = Status.IDLE;
    /** 0-100, negative when yt-dlp hasn't reported a percentage yet. */
    public volatile float progress = -1f;
    public volatile long etaSeconds = -1;
    /** Latest yt-dlp output line, for the small log view. */
    public volatile String line = "";
    /** Short, user-readable error (full stderr stays in yt-dlp's exception). */
    public volatile String error;
    /** Final file name once DONE. */
    public volatile String resultName;
    /** Content URI (MediaStore) or file URI of the finished download. */
    public volatile String resultUri;
    public volatile long resultBytes;

    public DownloadState(String id, String url, String title, FormatPreset preset) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.preset = preset;
    }

    public boolean isActive() {
        return status == Status.RUNNING || status == Status.EXPORTING;
    }
}
