package com.muse.vibevault;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * User settings that affect downloads: the default quality used for
 * share-sheet downloads, and the optional Instagram cookies file.
 */
public final class SharePrefs {

    private static final String PREFS = "vibevault_settings";
    private static final String KEY_SHARE_QUALITY = "share_quality";

    public static final String QUALITY_BEST = "best";
    public static final String QUALITY_MEDIUM = "medium";
    public static final String QUALITY_LOW = "low";

    private SharePrefs() {
    }

    public static String getShareQuality(Context context) {
        return prefs(context).getString(KEY_SHARE_QUALITY, QUALITY_BEST);
    }

    public static void setShareQuality(Context context, String quality) {
        prefs(context).edit().putString(KEY_SHARE_QUALITY, quality).apply();
    }

    /** The preset a share-sheet download uses: video only, at the saved quality. */
    public static FormatPreset sharePreset(Context context) {
        switch (getShareQuality(context)) {
            case QUALITY_MEDIUM:
                return FormatPreset.P720;
            case QUALITY_LOW:
                return FormatPreset.P480;
            case QUALITY_BEST:
            default:
                return FormatPreset.BEST;
        }
    }

    /** Optional Netscape cookies.txt the user imported (e.g. Instagram login). */
    public static File cookiesFile(Context context) {
        return new File(context.getFilesDir(), "cookies.txt");
    }

    public static boolean hasCookies(Context context) {
        File file = cookiesFile(context);
        return file.exists() && file.length() > 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
