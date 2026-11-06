package com.muse.vibevault;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invisible share target. When the user shares a link to VibeVault from
 * another app, this activity flashes for a frame, hands the URL straight to
 * {@link DownloadService} (video only, at the user's default share quality)
 * and finishes — the user never leaves the app they were scrolling in.
 *
 * <p>The full UI ({@link MainActivity}) only appears when the user opens
 * VibeVault themselves from the launcher.</p>
 */
public class ShareReceiverActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = extractSharedUrl(getIntent());
        if (url == null) {
            Toast.makeText(this, R.string.error_no_url_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DownloadService.startDownload(this, url, SharePrefs.sharePreset(this), null);
        Toast.makeText(this, R.string.toast_share_started, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String extractSharedUrl(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String url = UrlExtractor.extract(intent.getStringExtra(Intent.EXTRA_TEXT));
            if (url == null) {
                url = UrlExtractor.extract(intent.getStringExtra(Intent.EXTRA_SUBJECT));
            }
            return url;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            return intent.getData().toString();
        }
        return null;
    }
}
