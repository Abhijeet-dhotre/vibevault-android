package com.muse.vibevault;

import org.json.JSONException;
import org.json.JSONObject;

/** One finished download, as shown in the history list. */
public class DownloadRecord {

    public final long id;
    public final String title;
    public final String sourceUrl;
    public final String presetLabel;
    public final String fileName;
    /** String form of the MediaStore / FileProvider URI used to open the file. */
    public final String contentUri;
    public final long sizeBytes;
    public final long createdAtMs;

    public DownloadRecord(long id, String title, String sourceUrl, String presetLabel,
                          String fileName, String contentUri, long sizeBytes, long createdAtMs) {
        this.id = id;
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.presetLabel = presetLabel;
        this.fileName = fileName;
        this.contentUri = contentUri;
        this.sizeBytes = sizeBytes;
        this.createdAtMs = createdAtMs;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("sourceUrl", sourceUrl);
        json.put("presetLabel", presetLabel);
        json.put("fileName", fileName);
        json.put("contentUri", contentUri);
        json.put("sizeBytes", sizeBytes);
        json.put("createdAtMs", createdAtMs);
        return json;
    }

    public static DownloadRecord fromJson(JSONObject json) throws JSONException {
        return new DownloadRecord(
                json.getLong("id"),
                json.optString("title", ""),
                json.optString("sourceUrl", ""),
                json.optString("presetLabel", ""),
                json.optString("fileName", ""),
                json.optString("contentUri", null),
                json.optLong("sizeBytes", 0),
                json.optLong("createdAtMs", 0));
    }
}
