package pro.sketchware.control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import okhttp3.OkHttpClient;
import pro.sketchware.R;

/**
 * Downloads an app update in a foreground service, so it keeps going when the user leaves the update dialog or the
 * app and shows its progress in a notification. The dialog in {@link UpdateChecker} follows the progress through
 * {@link #setListener}; once the APK has been downloaded and verified, a notification offers to install it.
 */
public class UpdateDownloadService extends Service {

    private static final String EXTRA_APK_URL = "apkUrl";
    private static final String EXTRA_SHA256 = "sha256";
    private static final String EXTRA_SAME_VERSION = "sameVersionAllowed";
    private static final String ACTION_CANCEL = "pro.sketchware.control.CANCEL_UPDATE_DOWNLOAD";

    private static final String CHANNEL_ID = "update_download";
    private static final int PROGRESS_ID = 4210;
    private static final int RESULT_ID = 4211;

    public interface Listener {
        /** @param percent 0-100, or -1 when unknown */
        void onProgress(int percent, String text);

        /** @param error null on success; the verified APK is then at {@link #updateFile} */
        void onDone(@Nullable String error);

        /**
         * @return whether the user is looking at it now. When not (the app went to the background with the dialog
         * open), the result is also shown as a notification, or it would go unnoticed.
         */
        default boolean isWatching() {
            return true;
        }
    }

    /** A dropped connection is waited out this many times, a little apart, before the download gives up. */
    private static final int NETWORK_WAIT_ROUNDS = 20;
    private static final long NETWORK_WAIT_MILLIS = 15_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean running;
    private static volatile boolean cancelled;
    private static volatile int lastPercent = -1;
    private static volatile String lastText = "Starting download…";
    @Nullable
    private static Listener listener;

    public static boolean isRunning() {
        return running;
    }

    public static int lastPercent() {
        return lastPercent;
    }

    public static String lastText() {
        return lastText;
    }

    /** Only one screen follows the download at a time; pass null when it goes away. Call on the main thread. */
    public static void setListener(@Nullable Listener newListener) {
        listener = newListener;
    }

    public static File updatesDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "updates");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static File updateFile(Context context) {
        return new File(updatesDir(context), "update.apk");
    }

    public static File partialFile(File target) {
        return new File(target.getPath() + ".part");
    }

    public static File partialShaFile(File target) {
        return new File(target.getPath() + ".part.sha256");
    }

    /** Starts the download unless one is already running. */
    public static void start(Context context, String apkUrl, String sha256, boolean sameVersionAllowed) {
        if (running) {
            return;
        }
        running = true;
        cancelled = false;
        lastPercent = -1;
        lastText = "Starting download…";
        Intent intent = new Intent(context, UpdateDownloadService.class)
                .putExtra(EXTRA_APK_URL, apkUrl)
                .putExtra(EXTRA_SHA256, sha256)
                .putExtra(EXTRA_SAME_VERSION, sameVersionAllowed);
        context.startForegroundService(intent);
    }

    @Nullable
    private Thread worker;
    @Nullable
    private Intent startIntent;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            return START_NOT_STICKY;
        }
        if (intent == null || intent.getStringExtra(EXTRA_APK_URL) == null) {
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        createChannel();
        startForeground(PROGRESS_ID, progressNotification(-1, lastText));
        if (worker != null && worker.isAlive()) {
            return START_REDELIVER_INTENT;
        }
        // Also set here: after the system restarted the service in a new process, start() was never called.
        running = true;
        cancelled = false;
        startIntent = new Intent(intent);
        String apkUrl = intent.getStringExtra(EXTRA_APK_URL);
        String sha256 = intent.getStringExtra(EXTRA_SHA256);
        boolean sameVersionAllowed = intent.getBooleanExtra(EXTRA_SAME_VERSION, false);
        worker = new Thread(() -> run(apkUrl, sha256, sameVersionAllowed), "update-download");
        worker.start();
        // If the system kills the app mid-download (memory, battery savers), it restarts the service with the same
        // request, and the download continues from the bytes already saved.
        return START_REDELIVER_INTENT;
    }

    private void run(String apkUrl, String sha256, boolean sameVersionAllowed) {
        File target = updateFile(this);
        File partial = partialFile(target);
        File partialSha = partialShaFile(target);
        String error = null;
        // No https -> http (or http -> https) redirects: a downgrade would defeat the https-only rule.
        OkHttpClient client = new OkHttpClient.Builder().followSslRedirects(false).build();
        // Keep the CPU and Wi-Fi awake with the screen off, or the download stalls and gives up.
        PowerManager.WakeLock wakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NWS:update-download");
        WifiManager wifiManager = getApplicationContext().getSystemService(WifiManager.class);
        WifiManager.WifiLock wifiLock = wifiManager == null ? null
                : wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NWS:update-download");
        wakeLock.acquire(60 * 60 * 1000L);
        if (wifiLock != null) wifiLock.acquire();
        try {
            downloadWaitingOutNetworkLoss(client, apkUrl, sha256, partial, partialSha);
            if (cancelled) {
                throw new IOException("download cancelled");
            }
            String rejection = UpdateChecker.verifyApk(this, partial, sameVersionAllowed);
            if (rejection != null) {
                ResumableDownloader.deletePartial(partial, partialSha);
                throw new SecurityException(rejection);
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Cannot replace previous download");
            }
            if (!partial.renameTo(target)) {
                throw new IllegalStateException("Cannot save the downloaded file");
            }
            //noinspection ResultOfMethodCallIgnored
            partialSha.delete();
        } catch (IOException e) {
            // The kept bytes stay: "Continue" continues from there.
            error = (e.getMessage() != null ? e.getMessage() : "connection lost")
                    + (partial.length() > 0 ? String.format(Locale.US,
                    ". Tap Continue to resume from %.1f MB.", partial.length() / 1048576f) : "");
        } catch (Exception e) {
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (e instanceof SecurityException) {
                error = "update rejected: " + error;
            }
        } finally {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
            if (wakeLock.isHeld()) wakeLock.release();
        }
        String result = error;
        running = false;
        MAIN.post(() -> {
            Listener current = listener;
            if (current != null) {
                current.onDone(result);
            }
            if (current == null || !current.isWatching()) {
                // Nobody is looking at the dialog: tell the user through a notification.
                showResultNotification(result);
            }
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
    }

    /**
     * Downloads, and when the connection drops for longer than the downloader's own quick retries (a phone going
     * to sleep, a network switch), waits for it to come back and continues from the bytes already saved.
     */
    private void downloadWaitingOutNetworkLoss(OkHttpClient client, String apkUrl, String sha256, File partial,
                                               File partialSha) throws Exception {
        ResumableDownloader.Listener progress = new ResumableDownloader.Listener() {
            private long lastNotified;

            @Override
            public void onProgress(int percent, String text) {
                lastPercent = percent;
                lastText = text;
                MAIN.post(() -> {
                    if (listener != null) listener.onProgress(percent, text);
                });
                long now = System.currentTimeMillis();
                if (now - lastNotified > 700) {
                    lastNotified = now;
                    // Posting again also brings the notification back if it was swiped away.
                    getSystemService(NotificationManager.class)
                            .notify(PROGRESS_ID, progressNotification(percent, text));
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
        for (int round = 0; ; round++) {
            try {
                new ResumableDownloader(client).download(apkUrl, sha256, partial, partialSha, progress);
                return;
            } catch (IOException e) {
                if (cancelled || round >= NETWORK_WAIT_ROUNDS) {
                    throw e;
                }
                progress.onProgress(-1, String.format(Locale.US,
                        "Waiting for the connection (%.1f MB kept)…", partial.length() / 1048576f));
                Thread.sleep(NETWORK_WAIT_MILLIS);
            }
        }
    }

    private void showResultNotification(@Nullable String error) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(PROGRESS_ID);
        if (cancelled) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mtrl_download)
                .setAutoCancel(true);
        if (error == null) {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", updateFile(this));
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            builder.setContentTitle("Update downloaded")
                    .setContentText("Tap to install")
                    .setContentIntent(PendingIntent.getActivity(this, 0, install,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            builder.setContentTitle("Update download failed")
                    .setContentText(error)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(error));
            if (startIntent != null && !error.startsWith("update rejected")) {
                // Continues from the bytes already saved, without opening the app.
                builder.addAction(R.drawable.ic_mtrl_download, "Continue",
                        PendingIntent.getForegroundService(this, 2, new Intent(startIntent),
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (open != null) {
                builder.setContentIntent(PendingIntent.getActivity(this, 0, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        }
        manager.notify(RESULT_ID, builder.build());
    }

    private Notification progressNotification(int percent, String text) {
        Intent cancel = new Intent(this, UpdateDownloadService.class).setAction(ACTION_CANCEL);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mtrl_downloading)
                .setContentTitle("Downloading update")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, Math.max(percent, 0), percent < 0)
                .addAction(R.drawable.ic_cancel_white_96dp, "Cancel",
                        PendingIntent.getService(this, 1, cancel,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open != null) {
            builder.setContentIntent(PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        return builder.build();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "App updates",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progress of app update downloads");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
