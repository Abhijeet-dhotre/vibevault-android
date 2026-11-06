package com.muse.vibevault;

import androidx.annotation.StringRes;

/**
 * The download formats offered in the UI. The actual yt-dlp arguments for
 * each preset are built in {@link YtDlpEngine}.
 */
public enum FormatPreset {

    BEST(R.string.preset_best),
    P1080(R.string.preset_1080),
    P720(R.string.preset_720),
    P480(R.string.preset_480),
    MP3(R.string.preset_mp3),
    AUDIO_ORIGINAL(R.string.preset_audio_original);

    @StringRes
    public final int labelRes;

    FormatPreset(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    public boolean isAudioOnly() {
        return this == MP3 || this == AUDIO_ORIGINAL;
    }
}
