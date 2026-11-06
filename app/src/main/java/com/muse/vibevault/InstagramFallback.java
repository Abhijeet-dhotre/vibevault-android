package com.muse.vibevault;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback for Instagram posts/reels when yt-dlp itself is refused (which
 * happens a lot: Instagram walls anonymous extraction). This reads the post's
 * public page the way a browser would and pulls the media URL out of the
 * page's OpenGraph/JSON data — it covers video posts, reels, and plain
 * photo posts (which yt-dlp cannot fetch at all).
 *
 * <p>If the user imported Instagram cookies, they are sent along, which
 * unlocks posts that are only visible while logged in.</p>
 */
public final class InstagramFallback {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final Pattern META_TAG = Pattern.compile("<meta[^>]+>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTR =
            Pattern.compile("content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_URL_JSON =
            Pattern.compile("\"video_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHORTCODE =
            Pattern.compile("/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)");

    private InstagramFallback() {
    }

    /** Downloads the post's media into {@code outDir}; returns the file. */
    public static File download(Context context, String pageUrl, File outDir,
                                  YtDlpEngine.ProgressListener listener) throws IOException {
        String html = fetchText(context, pageUrl, 4 * 1024 * 1024);

        String mediaUrl = findJson(html, VIDEO_URL_JSON);
        boolean video = mediaUrl != null;
        if (mediaUrl == null) {
            mediaUrl = findMeta(html, "og:video:secure_url");
            if (mediaUrl == null) {
                mediaUrl = findMeta(html, "og:video");
            }
            video = mediaUrl != null;
        }
        if (mediaUrl == null) {
            mediaUrl = findMeta(html, "og:image");
        }
        if (mediaUrl == null) {
            mediaUrl = findMeta(html, "twitter:image");
        }
        if (mediaUrl == null) {
            throw new IOException("No media found on the Instagram page — "
                    + "the post is likely login-walled.");
        }
        mediaUrl = unescape(mediaUrl);

        String code = "post";
        Matcher m = SHORTCODE.matcher(pageUrl);
        if (m.find()) {
            code = m.group(1);
        }
        String ext = extensionOf(mediaUrl, video ? "mp4" : "jpg");
        File out = new File(outDir, "instagram-" + code + "." + ext);
        downloadFile(context, mediaUrl, pageUrl, out, listener);
        return out;
    }

    // ------------------------------------------------------------------

    private static String findMeta(String html, String key) {
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            if (tag.contains(key)) {
                Matcher c = CONTENT_ATTR.matcher(tag);
                if (c.find()) {
                    return c.group(1);
                }
            }
        }
        return null;
    }

    private static String findJson(String html, Pattern pattern) {
        Matcher m = pattern.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static String unescape(String s) {
        String out = s.replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("\\u0026", "&")
                .replace("\\u003D", "=")
                .replace("\\u003d", "=");
        // Any remaining unicode escape sequences.
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb,
                    Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extensionOf(String mediaUrl, String fallback) {
        try {
            String path = new URL(mediaUrl).getPath();
            int dot = path.lastIndexOf('.');
            if (dot >= 0) {
                String ext = path.substring(dot + 1).toLowerCase();
                if (ext.matches("[a-z0-9]{2,5}")) {
                    return ext.equals("webp") ? fallback : ext;
                }
            }
        } catch (Exception ignored) {
            // fall through to the default
        }
        return fallback;
    }

    private static String fetchText(Context context, String url, int maxBytes) throws IOException {
        HttpURLConnection conn = open(context, url, null);
        try {
            InputStream in = conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > maxBytes) {
                    break;
                }
                sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadFile(Context context, String mediaUrl, String referer, File out,
                                     YtDlpEngine.ProgressListener listener) throws IOException {
        HttpURLConnection conn = open(context, mediaUrl, referer);
        try {
            long total = conn.getContentLengthLong();
            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[16 * 1024];
                long done = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    fos.write(buf, 0, read);
                    done += read;
                    if (listener != null && total > 0) {
                        listener.onProgress(done * 100f / total, -1, "");
                    }
                }
            }
            if (out.length() == 0) {
                throw new IOException("Instagram returned an empty file.");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(Context context, String url, String referer)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", BROWSER_UA);
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        if (referer != null) {
            conn.setRequestProperty("Referer", referer);
        }
        String cookieHeader = cookieHeader(context);
        if (cookieHeader != null) {
            conn.setRequestProperty("Cookie", cookieHeader);
        }
        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Instagram page returned HTTP " + status);
        }
        return conn;
    }

    /** Builds a Cookie header for instagram.com from the imported cookies.txt. */
    private static String cookieHeader(Context context) {
        if (!SharePrefs.hasCookies(context)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new FileReader(SharePrefs.cookiesFile(context)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length >= 7 && parts[0].contains("instagram.com")) {
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }
                    sb.append(parts[5]).append('=').append(parts[6]);
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
