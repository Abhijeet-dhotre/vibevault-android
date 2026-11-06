package com.muse.vibevault;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/** All notification construction in one place. */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "downloads";
    public static final int ID_PROGRESS = 1001;
    public static final int ID_RESULT = 1002;

    private NotificationHelper() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.notif_channel_downloads_desc));
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static Notification progressNotification(Context context, DownloadState state) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0,
                new Intent(context, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(context, DownloadService.class)
                .setAction(DownloadService.ACTION_CANCEL);
        PendingIntent cancelPending = PendingIntent.getService(
                context, 1, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(state.title != null ? state.title
                        : context.getString(R.string.notif_downloading))
                .setContentText(state.line)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, context.getString(R.string.notif_action_cancel), cancelPending);

        if (state.status == DownloadState.Status.EXPORTING) {
            builder.setProgress(0, 0, true);
        } else if (state.progress >= 0) {
            builder.setProgress(100, Math.round(state.progress), false);
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    public static Notification resultNotification(Context context, DownloadState state) {
        String title;
        String text;
        if (state.status == DownloadState.Status.DONE) {
            title = context.getString(R.string.notif_complete);
            text = state.resultName;
        } else if (state.status == DownloadState.Status.CANCELLED) {
            title = context.getString(R.string.status_cancelled);
            text = state.title;
        } else {
            title = context.getString(R.string.notif_failed);
            text = state.error != null ? state.error : state.title;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);

        if (state.status == DownloadState.Status.DONE && state.resultUri != null) {
            Intent open = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(state.resultUri), stateResultMime(state.resultName))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openPending = PendingIntent.getActivity(
                    context, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(openPending);
            builder.addAction(0, context.getString(R.string.notif_action_open), openPending);
        } else {
            PendingIntent contentIntent = PendingIntent.getActivity(
                    context, 3,
                    new Intent(context, MainActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(contentIntent);
        }
        return builder.build();
    }

    private static String stateResultMime(String fileName) {
        if (fileName == null) {
            return "*/*";
        }
        String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                android.webkit.MimeTypeMap.getFileExtensionFromUrl(fileName));
        return mime != null ? mime : "*/*";
    }

    public static void notify(Context context, int id, Notification notification) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(id, notification);
        }
    }

    public static void cancel(Context context, int id) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(id);
        }
    }
}
