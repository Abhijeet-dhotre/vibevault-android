package com.muse.vibevault;

import java.util.Locale;

/** Small display-format helpers shared by the activity, service and list. */
public final class FormatUtils {

    private FormatUtils() {
    }

    /** 1536 -> "1.5 KB", 734003200 -> "700.0 MB". */
    public static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    /** 75 -> "1:15", 3725 -> "1:02:05". */
    public static String duration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "";
        }
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /** First meaningful (last non-empty) line of a long error dump, capped. */
    public static String summarizeError(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Unknown error";
        }
        String[] lines = raw.trim().split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line.length() > 300 ? line.substring(0, 300) + "…" : line;
            }
        }
        return "Unknown error";
    }
}
