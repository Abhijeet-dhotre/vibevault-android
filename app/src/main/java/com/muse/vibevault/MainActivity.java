package com.muse.vibevault;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single-screen UI: paste a link -> (optional) fetch info ->
 * pick a format -> download in {@link DownloadService} -> history list.
 *
 * <p>This screen only appears when the user opens VibeVault themselves.
 * Sharing a link from another app goes to {@link ShareReceiverActivity},
 * which downloads in the background without ever opening this UI.</p>
 */
public class MainActivity extends AppCompatActivity
        implements DownloadCoordinator.Listener {

    private EditText etUrl;
    private View infoCard;
    private ImageView ivThumbnail;
    private TextView tvInfoTitle;
    private TextView tvInfoMeta;
    private RadioGroup rgFormat;
    private Button btnDownload;
    private Button btnCancel;
    private Button btnFetchInfo;
    private Button btnUpdate;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvVersion;
    private TextView tvHistoryEmpty;
    private HistoryAdapter historyAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newCachedThreadPool();

    /** Title of the last successful info fetch — passed to the service for nicer notifications. */
    private String fetchedTitle;
    private String fetchedForUrl;

    // ------------------------------------------------------------ Lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        bindActions();
        bindSettings();
        refreshHistory();
        showVersion();
        maybeRequestNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        DownloadCoordinator.get().addListener(this);
        DownloadState state = DownloadCoordinator.get().current();
        if (state != null) {
            renderState(state);
        }
    }

    @Override
    protected void onStop() {
        DownloadCoordinator.get().removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        background.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- Views

    private void bindViews() {
        etUrl = findViewById(R.id.et_url);
        infoCard = findViewById(R.id.info_card);
        ivThumbnail = findViewById(R.id.iv_thumbnail);
        tvInfoTitle = findViewById(R.id.tv_info_title);
        tvInfoMeta = findViewById(R.id.tv_info_meta);
        rgFormat = findViewById(R.id.rg_format);
        btnDownload = findViewById(R.id.btn_download);
        btnCancel = findViewById(R.id.btn_cancel);
        btnFetchInfo = findViewById(R.id.btn_fetch_info);
        btnUpdate = findViewById(R.id.btn_update);
        progressBar = findViewById(R.id.progress);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        tvVersion = findViewById(R.id.tv_ytdlp_version);
        tvHistoryEmpty = findViewById(R.id.tv_history_empty);

        ListView listHistory = findViewById(R.id.list_history);
        historyAdapter = new HistoryAdapter(this);
        listHistory.setAdapter(historyAdapter);
        listHistory.setOnItemClickListener((parent, view, position, id) ->
                openRecord(historyAdapter.getItem(position)));
        listHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmRemoveRecord(historyAdapter.getItem(position));
            return true;
        });
    }

    private void bindActions() {
        findViewById(R.id.btn_paste).setOnClickListener(v -> pasteFromClipboard());
        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            etUrl.setText("");
            hideInfo();
        });
        btnFetchInfo.setOnClickListener(v -> {
            String url = currentUrlOrError();
            if (url != null) {
                fetchInfo(url);
            }
        });
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String url = currentUrlOrError();
                if (url != null) {
                    fetchInfo(url);
                }
                return true;
            }
            return false;
        });
        btnDownload.setOnClickListener(v -> startDownload());
        btnCancel.setOnClickListener(v -> DownloadService.requestCancel(this));
        btnUpdate.setOnClickListener(v -> updateYtDlp());
    }

    // ---------------------------------------------------------------- Actions

    private void pasteFromClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        String url = UrlExtractor.extract(text == null ? null : text.toString());
        if (url == null) {
            Toast.makeText(this, R.string.error_no_url_found, Toast.LENGTH_SHORT).show();
            return;
        }
        etUrl.setText(url);
        etUrl.setSelection(url.length());
    }

    /** Reads the field, pulling the first URL out of any pasted share text. */
    private String currentUrlOrError() {
        String raw = etUrl.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            etUrl.setError(getString(R.string.error_no_url));
            return null;
        }
        String url = UrlExtractor.extract(raw);
        if (url == null) {
            etUrl.setError(getString(R.string.error_no_url_found));
            return null;
        }
        if (!url.equals(raw)) {
            etUrl.setText(url);
            etUrl.setSelection(url.length());
        }
        return url;
    }

    private void startDownload() {
        String url = currentUrlOrError();
        if (url == null) {
            return;
        }
        String title = url.equals(fetchedForUrl) ? fetchedTitle : null;
        // Downloads queue in the service — starting another one is fine.
        DownloadService.startDownload(this, url, selectedPreset(), title);
        Toast.makeText(this, R.string.toast_share_started, Toast.LENGTH_SHORT).show();
    }

    private FormatPreset selectedPreset() {
        int checked = rgFormat.getCheckedRadioButtonId();
        if (checked == R.id.rb_1080) return FormatPreset.P1080;
        if (checked == R.id.rb_720) return FormatPreset.P720;
        if (checked == R.id.rb_480) return FormatPreset.P480;
        if (checked == R.id.rb_mp3) return FormatPreset.MP3;
        if (checked == R.id.rb_audio_original) return FormatPreset.AUDIO_ORIGINAL;
        return FormatPreset.BEST;
    }

    // --------------------------------------------------------------- Settings

    private static final int REQUEST_COOKIES_FILE = 77;
    private RadioGroup rgShareQuality;
    private TextView tvCookiesStatus;

    private void bindSettings() {
        rgShareQuality = findViewById(R.id.rg_share_quality);
        tvCookiesStatus = findViewById(R.id.tv_cookies_status);

        String quality = SharePrefs.getShareQuality(this);
        rgShareQuality.check(SharePrefs.QUALITY_MEDIUM.equals(quality) ? R.id.rb_quality_medium
                : SharePrefs.QUALITY_LOW.equals(quality) ? R.id.rb_quality_low
                : R.id.rb_quality_best);
        rgShareQuality.setOnCheckedChangeListener((group, checkedId) -> {
            String value = checkedId == R.id.rb_quality_medium ? SharePrefs.QUALITY_MEDIUM
                    : checkedId == R.id.rb_quality_low ? SharePrefs.QUALITY_LOW
                    : SharePrefs.QUALITY_BEST;
            SharePrefs.setShareQuality(this, value);
        });

        findViewById(R.id.btn_cookies_import).setOnClickListener(v -> {
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            open.addCategory(Intent.CATEGORY_OPENABLE);
            open.setType("*/*");
            startActivityForResult(open, REQUEST_COOKIES_FILE);
        });
        findViewById(R.id.btn_cookies_paste).setOnClickListener(v -> showPasteCookiesDialog());
        findViewById(R.id.btn_cookies_clear).setOnClickListener(v -> {
            //noinspection ResultOfMethodCallIgnored
            SharePrefs.cookiesFile(this).delete();
            refreshCookiesStatus();
            Toast.makeText(this, R.string.cookies_cleared, Toast.LENGTH_SHORT).show();
        });
        refreshCookiesStatus();
    }

    private void refreshCookiesStatus() {
        if (tvCookiesStatus != null) {
            tvCookiesStatus.setText(SharePrefs.hasCookies(this)
                    ? R.string.cookies_set : R.string.cookies_none);
        }
    }

    private void showPasteCookiesDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.dialog_cookies_hint);
        input.setMinLines(4);
        input.setMaxLines(10);
        input.setTextSize(11);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_cookies_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String text = input.getText().toString();
                    if (!TextUtils.isEmpty(text.trim())) {
                        saveCookies(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_COOKIES_FILE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            background.execute(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in != null) {
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int read;
                        while ((read = in.read(chunk)) != -1) {
                            buffer.write(chunk, 0, read);
                        }
                        saveCookies(buffer.toByteArray());
                    }
                } catch (Exception ignored) {
                    // Status text stays "not imported"; nothing else to do.
                }
            });
        }
    }

    private void saveCookies(byte[] bytes) {
        try (java.io.FileOutputStream out =
                     new java.io.FileOutputStream(SharePrefs.cookiesFile(this))) {
            out.write(bytes);
            mainHandler.post(() -> {
                refreshCookiesStatus();
                Toast.makeText(this, R.string.cookies_saved, Toast.LENGTH_SHORT).show();
            });
        } catch (Exception ignored) {
            // Leave the previous state untouched.
        }
    }

    // -------------------------------------------------------------- Info fetch

    private void fetchInfo(String url) {
        btnFetchInfo.setEnabled(false);
        tvStatus.setText(R.string.status_fetching);
        background.execute(() -> {
            try {
                VideoInfo info = YtDlpEngine.fetchInfo(this, url);
                mainHandler.post(() -> showInfo(url, info));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnFetchInfo.setEnabled(true);
                    tvStatus.setText(R.string.error_engine);
                    tvLog.setText(FormatUtils.summarizeError(e.getMessage()));
                });
            }
        });
    }

    private void showInfo(String url, VideoInfo info) {
        btnFetchInfo.setEnabled(true);
        fetchedForUrl = url;
        fetchedTitle = firstNonEmpty(info.getFulltitle(), info.getTitle());
        tvInfoTitle.setText(fetchedTitle);

        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(info.getUploader())) {
            meta.append(info.getUploader());
        }
        String duration = FormatUtils.duration(info.getDuration());
        if (!duration.isEmpty()) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(duration);
        }
        if (!TextUtils.isEmpty(info.getExtractorKey())) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(info.getExtractorKey());
        }
        tvInfoMeta.setText(meta.toString());
        infoCard.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.status_ready);

        if (!TextUtils.isEmpty(info.getThumbnail())) {
            loadThumbnail(info.getThumbnail());
        }
    }

    private void hideInfo() {
        infoCard.setVisibility(View.GONE);
        fetchedTitle = null;
        fetchedForUrl = null;
    }

    private void loadThumbnail(String thumbnailUrl) {
        background.execute(() -> {
            Bitmap bitmap = null;
            try {
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(thumbnailUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                try (InputStream in = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
            } catch (Exception ignored) {
                // Thumbnails are decoration; a failure just leaves the placeholder empty.
            }
            Bitmap result = bitmap;
            if (result != null) {
                mainHandler.post(() -> ivThumbnail.setImageBitmap(result));
            }
        });
    }

    // ----------------------------------------------------------- yt-dlp update

    private void showVersion() {
        background.execute(() -> {
            try {
                YtDlpEngine.init(this);
            } catch (Exception ignored) {
            }
            String version = YtDlpEngine.versionName(this);
            if (version != null) {
                mainHandler.post(() ->
                        tvVersion.setText(getString(R.string.ytdlp_version, version)));
            }
        });
    }

    private void updateYtDlp() {
        btnUpdate.setEnabled(false);
        tvStatus.setText(R.string.status_updating);
        background.execute(() -> {
            String message;
            try {
                boolean updated = YtDlpEngine.update(this);
                String version = YtDlpEngine.versionName(this);
                message = getString(updated ? R.string.ytdlp_update_done
                        : R.string.ytdlp_updated, version);
                mainHandler.post(() -> tvVersion.setText(
                        getString(R.string.ytdlp_version, version)));
            } catch (Exception e) {
                message = getString(R.string.error_engine) + ": "
                        + FormatUtils.summarizeError(e.getMessage());
            }
            String result = message;
            mainHandler.post(() -> {
                btnUpdate.setEnabled(true);
                tvStatus.setText(result);
            });
        });
    }

    // ---------------------------------------------------------------- History

    private void refreshHistory() {
        java.util.List<DownloadRecord> records = HistoryStore.load(this);
        historyAdapter.submit(records);
        tvHistoryEmpty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openRecord(DownloadRecord record) {
        if (record.contentUri == null) {
            return;
        }
        Uri uri = Uri.parse(record.contentUri);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(record.fileName));
        Intent open = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime != null ? mime : "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(open);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmRemoveRecord(DownloadRecord record) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(R.string.dialog_delete_msg)
                .setPositiveButton(R.string.dialog_delete, (dialog, which) -> {
                    HistoryStore.remove(this, record.id);
                    refreshHistory();
                })
                .setNegativeButton(R.string.dialog_keep, null)
                .show();
    }

    // ------------------------------------------------- Download state -> UI

    @Override
    public void onDownloadStateChanged(DownloadState state) {
        mainHandler.post(() -> renderState(state));
    }

    private void renderState(DownloadState state) {
        boolean active = state.isActive();
        btnDownload.setEnabled(true); // downloads queue; never blocked
        btnCancel.setVisibility(active ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(active || state.status == DownloadState.Status.DONE
                ? View.VISIBLE : View.GONE);

        switch (state.status) {
            case RUNNING:
                if (state.progress >= 0) {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(Math.round(state.progress));
                    String eta = state.etaSeconds > 0
                            ? "  •  ETA " + FormatUtils.duration((int) state.etaSeconds) : "";
                    tvStatus.setText(getString(R.string.status_downloading)
                            + " " + Math.round(state.progress) + "%" + eta);
                } else {
                    progressBar.setIndeterminate(true);
                    tvStatus.setText(R.string.status_downloading);
                }
                tvLog.setText(state.line);
                break;
            case EXPORTING:
                progressBar.setIndeterminate(true);
                tvStatus.setText("Saving to Downloads/VibeVault…");
                break;
            case DONE:
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
                tvStatus.setText(R.string.status_done);
                tvLog.setText(state.resultName + "  •  "
                        + FormatUtils.humanBytes(state.resultBytes));
                refreshHistory();
                break;
            case FAILED:
                tvStatus.setText(R.string.status_failed);
                tvLog.setText(state.error);
                break;
            case CANCELLED:
                tvStatus.setText(R.string.status_cancelled);
                tvLog.setText("");
                break;
            default:
                break;
        }
    }

    // -------------------------------------------------------------- Permissions

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Nothing to retry either way: downloads work without notifications,
        // the user just won't see progress outside the app.
    }

    private static String firstNonEmpty(String a, String b) {
        return !TextUtils.isEmpty(a) ? a : (b == null ? "" : b);
    }
}
