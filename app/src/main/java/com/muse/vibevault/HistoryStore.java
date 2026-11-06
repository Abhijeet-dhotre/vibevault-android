package com.muse.vibevault;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Download history persisted as a small JSON file in internal storage.
 * Newest first, capped at {@link #MAX_RECORDS}. Deliberately not a database —
 * a personal download list never justifies one.
 */
public final class HistoryStore {

    private static final String TAG = "HistoryStore";
    private static final String FILE_NAME = "history.json";
    private static final int MAX_RECORDS = 200;

    private static List<DownloadRecord> cache;

    private HistoryStore() {
    }

    public static synchronized List<DownloadRecord> load(Context context) {
        if (cache != null) {
            return new ArrayList<>(cache);
        }
        cache = new ArrayList<>();
        File file = file(context);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int read = in.read(data);
                if (read > 0) {
                    JSONArray array = new JSONArray(
                            new String(data, 0, read, StandardCharsets.UTF_8));
                    for (int i = 0; i < array.length(); i++) {
                        cache.add(DownloadRecord.fromJson(array.getJSONObject(i)));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "could not read history", e);
                cache = new ArrayList<>();
            }
        }
        return new ArrayList<>(cache);
    }

    public static synchronized void add(Context context, DownloadRecord record) {
        load(context);
        cache.add(0, record);
        while (cache.size() > MAX_RECORDS) {
            cache.remove(cache.size() - 1);
        }
        save(context);
    }

    public static synchronized void remove(Context context, long id) {
        load(context);
        cache.removeIf(r -> r.id == id);
        save(context);
    }

    private static void save(Context context) {
        try {
            JSONArray array = new JSONArray();
            for (DownloadRecord record : cache) {
                array.put(record.toJson());
            }
            File file = file(context);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "could not save history", e);
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}
