package com.muse.vibevault;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Moves a finished download out of the app's private staging area into the
 * user's public {@code Downloads/VibeVault} folder.
 *
 * <p>yt-dlp (a native subprocess) cannot write through MediaStore itself, and
 * on Android 10+ raw-path writes to public storage are blocked by scoped
 * storage — hence: download to app-specific storage first, export second.
 * On Android 10+ the export goes through MediaStore (no permission needed);
 * below that it copies the file directly and media-scans it.</p>
 */
public final class MediaStoreExporter {

    public static final String SUBDIR = "VibeVault";

    public static class ExportedFile {
        public final Uri uri;
        public final String displayName;
        public final long sizeBytes;
        /** Non-null only on the pre–Android 10 direct-file path. */
        public final File file;

        ExportedFile(Uri uri, String displayName, long sizeBytes, File file) {
            this.uri = uri;
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
            this.file = file;
        }
    }

    private MediaStoreExporter() {
    }

    public static ExportedFile export(Context context, File source) throws IOException {
        String mime = mimeOf(source.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return exportViaMediaStore(context, source, mime);
        }
        return exportViaFile(context, source);
    }

    // Android 10+: MediaStore.Downloads with a relative path.
    private static ExportedFile exportViaMediaStore(Context context, File source, String mime)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri item = resolver.insert(collection, values);
        if (item == null) {
            throw new IOException("MediaStore refused the new file");
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = resolver.openOutputStream(item)) {
            if (out == null) {
                throw new IOException("Could not open MediaStore output stream");
            }
            copy(in, out);
        } catch (IOException e) {
            resolver.delete(item, null, null);
            throw e;
        }
        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(item, values, null, null);
        return new ExportedFile(item, source.getName(), source.length(), null);
    }

    // Android 7–9: direct write to public Downloads, then media scan.
    private static ExportedFile exportViaFile(Context context, File source) throws IOException {
        File publicDownloads =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(publicDownloads, SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        File dest = uniqueFile(dir, source.getName());
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            copy(in, out);
        }
        MediaScannerConnection.scanFile(context,
                new String[]{dest.getAbsolutePath()}, null, null);
        Uri uri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".files", dest);
        return new ExportedFile(uri, dest.getName(), dest.length(), dest);
    }

    private static File uniqueFile(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) {
            return candidate;
        }
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int i = 1;
        while (candidate.exists()) {
            candidate = new File(dir, base + " (" + i + ")" + ext);
            i++;
        }
        return candidate;
    }

    private static String mimeOf(String fileName) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(fileName);
        String mime = ext == null ? null
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return mime != null ? mime : "application/octet-stream";
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }
}
