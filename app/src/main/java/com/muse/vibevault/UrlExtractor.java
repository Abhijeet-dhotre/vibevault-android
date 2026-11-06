package com.muse.vibevault;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the first usable URL out of arbitrary shared text.
 *
 * <p>Share-sheet payloads are rarely a bare URL — YouTube shares
 * "Watch this! https://youtu.be/xyz via @YouTube", Instagram adds captions,
 * browsers add page titles. This finds the link inside all of that and trims
 * trailing punctuation that prose typically glues onto it.</p>
 */
public final class UrlExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[^\\s<>\"'\\[\\]{}|]+)");

    private static final String TRAILING_JUNK = ".,;:!?)";

    private UrlExtractor() {
    }

    /**
     * @return the first http(s) URL found in {@code text}, or {@code null}
     * if there is none.
     */
    public static String extract(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(text.trim());
        if (!matcher.find()) {
            return null;
        }
        String url = matcher.group(1);
        while (url.length() > 0
                && TRAILING_JUNK.indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.regionMatches(true, 0, "www.", 0, 4)) {
            url = "https://" + url;
        }
        return url;
    }

    /** @return true if the whole string is a single URL. */
    public static boolean isUrl(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        String found = extract(trimmed);
        return found != null && found.equalsIgnoreCase(trimmed);
    }
}
