package pro.sketchware.control;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.BuildConfig;
import pro.sketchware.utility.Network;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Checks a remote manifest for a newer app version and, when the update is marked mandatory,
 * shows a non-dismissable dialog that forces the user to update before continuing.
 *
 * The manifest (update.json) lives at the repository root and is served raw from GitHub:
 * {"versionCode": N, "versionName": "...", "mandatory": true, "apkUrl": "...", "url": "...", "notes": "..."}.
 * "apkUrl" is downloaded in-app with a progress bar and then handed to the package installer; when it
 * is missing, "url" is opened in the browser instead. Optional "sha256" is the hex digest of the APK.
 *
 * Downloaded APKs are only handed to the installer after they pass these checks: https download URL,
 * matching SHA-256 (when the manifest has one), same package name, a higher versionCode than the
 * installed build, and the same signing certificate as the installed app. The last mandatory manifest
 * is cached, so the update prompt keeps showing while the device is offline.
 */
public class UpdateChecker {

    private static final String BASE_URL = "https://raw.githubusercontent.com/prozizou/Sketch_App/main/";
    private static final String MANIFEST_URL = BASE_URL + "update.json";

    private static final String PREFS = "update_checker";
    private static final String KEY_MANDATORY_MANIFEST = "mandatory_manifest";

    private final Network network = new Network();

    public void check(AppCompatActivity activity) {
        check(activity, false);
    }

    /**
     * @param manual true when the user asked for the check: they then also get told when nothing is new.
     */
    public void check(AppCompatActivity activity, boolean manual) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, AppCompatActivity.MODE_PRIVATE);
        network.get(MANIFEST_URL, response -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            boolean shown;
            if (response == null) {
                // Offline or GitHub unreachable: keep enforcing the last mandatory update we saw.
                shown = handle(activity, prefs, prefs.getString(KEY_MANDATORY_MANIFEST, null), false, true);
                if (!shown && manual) {
                    SketchwareUtil.toast("Couldn't reach the update server");
                    return;
                }
            } else {
                shown = handle(activity, prefs, response, true, true);
            }
            if (!shown) {
                checkChannel(activity, prefs, manual);
            }
        });
    }

    /**
     * Beta and Dev builds are announced in their own manifest. The stable manifest is always checked first,
     * so a mandatory stable update can't be skipped by switching channel, and channel updates are never mandatory.
     */
    private void checkChannel(AppCompatActivity activity, SharedPreferences prefs, boolean manual) {
        String channelUrl = switch (ConfigActivity.getStringSetting(ConfigActivity.SETTING_UPDATE_CHANNEL)) {
            case "beta" -> BASE_URL + "update-beta.json";
            case "dev" -> BASE_URL + "update-dev.json";
            default -> null;
        };
        if (channelUrl == null) {
            if (manual) SketchwareUtil.toast("You're up to date");
            return;
        }
        network.get(channelUrl, response -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            boolean shown = response != null && handle(activity, prefs, response, false, false);
            if (!shown && manual) {
                SketchwareUtil.toast(response == null ? "Couldn't reach the update server" : "You're up to date");
            }
        });
    }

    /**
     * @param allowMandatory false for channel manifests, whose updates are always optional.
     * @return true if an update dialog was shown.
     */
    private boolean handle(AppCompatActivity activity, SharedPreferences prefs, String raw, boolean fresh, boolean allowMandatory) {
        if (raw == null) {
            return false;
        }
        try {
            JSONObject manifest = new JSONObject(raw);
            int latest = manifest.optInt("versionCode", -1);
            if (latest <= BuildConfig.VERSION_CODE) {
                if (fresh) prefs.edit().remove(KEY_MANDATORY_MANIFEST).apply();
                return false;
            }
            boolean mandatory = allowMandatory && manifest.optBoolean("mandatory", false);
            if (fresh) {
                if (mandatory) {
                    prefs.edit().putString(KEY_MANDATORY_MANIFEST, raw).apply();
                } else {
                    prefs.edit().remove(KEY_MANDATORY_MANIFEST).apply();
                }
            }
            showUpdateDialog(activity, mandatory, manifest.optString("versionName", ""),
                    manifest.optString("notes", ""), manifest.optString("url", ""),
                    manifest.optString("apkUrl", ""), manifest.optString("sha256", ""));
            return true;
        } catch (Exception ignored) {
            // Malformed manifest: fail silently, never block the app on our own bug.
            return false;
        }
    }

    private void showUpdateDialog(AppCompatActivity activity, boolean mandatory,
                                  String versionName, String notes, String url, String apkUrl, String sha256) {
        StringBuilder message = new StringBuilder();
        if (!versionName.isEmpty()) {
            message.append("Version ").append(versionName).append('\n');
        }
        message.append(notes.isEmpty() ? "A new version is available." : notes);

        float dip = activity.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (24 * dip), (int) (8 * dip), (int) (24 * dip), 0);

        TextView messageView = new TextView(activity);
        messageView.setText(message);
        content.addView(messageView);

        LinearProgressIndicator progress = new LinearProgressIndicator(activity);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = (int) (16 * dip);
        content.addView(progress, progressParams);

        TextView status = new TextView(activity);
        status.setVisibility(View.GONE);
        status.setPadding(0, (int) (6 * dip), 0, 0);
        content.addView(status);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle("Update available")
                .setView(content)
                .setCancelable(!mandatory)
                .setPositiveButton("Update now", null);
        if (!mandatory) {
            builder.setNegativeButton("Later", null);
        }

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(!mandatory);
        dialog.show();

        // The positive button never dismisses the dialog: for a mandatory update the app stays
        // blocked until the newer build (with a higher versionCode) is installed.
        android.widget.Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        button.setOnClickListener(v -> {
            if (apkUrl.isEmpty()) {
                openUrl(activity, url);
                return;
            }
            if (!apkUrl.startsWith("https://")) {
                status.setVisibility(View.VISIBLE);
                status.setText("Update rejected: the download link is not secure (https).");
                return;
            }
            File apk = new File(updatesDir(activity), "update.apk");
            if (apk.isFile() && button.getTag() == Boolean.TRUE) {
                install(activity, apk);
                return;
            }
            button.setEnabled(false);
            progress.setIndeterminate(true);
            progress.setVisibility(View.VISIBLE);
            status.setVisibility(View.VISIBLE);
            status.setText("Starting download\u2026");
            download(activity, apkUrl, sha256, apk, (percent, text) -> {
                if (percent >= 0) {
                    progress.setIndeterminate(false);
                    progress.setProgressCompat(percent, true);
                }
                status.setText(text);
            }, error -> {
                button.setEnabled(true);
                if (error == null) {
                    button.setTag(Boolean.TRUE);
                    button.setText("Install");
                    progress.setIndeterminate(false);
                    progress.setProgressCompat(100, true);
                    status.setText("Download complete");
                    install(activity, apk);
                } else {
                    button.setText("Retry");
                    progress.setVisibility(View.GONE);
                    status.setText("Download failed: " + error);
                }
            });
        });
    }

    private interface ProgressListener {
        void onProgress(int percent, String text);
    }

    private interface DoneListener {
        /** @param error null on success */
        void onDone(String error);
    }

    private File updatesDir(AppCompatActivity activity) {
        File dir = new File(activity.getExternalFilesDir(null), "updates");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private void download(AppCompatActivity activity, String apkUrl, String expectedSha256, File target,
                          ProgressListener progressListener, DoneListener doneListener) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            File partial = new File(target.getPath() + ".part");
            String error = null;
            try {
                Request request = new Request.Builder().url(apkUrl).build();
                try (Response response = new OkHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IllegalStateException("HTTP " + response.code());
                    }
                    long total = response.body().contentLength();
                    try (InputStream in = response.body().byteStream();
                         OutputStream out = new FileOutputStream(partial)) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] buffer = new byte[16 * 1024];
                        long done = 0;
                        int lastPercent = -2;
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            done += read;
                            int percent = total > 0 ? (int) (done * 100 / total) : -1;
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                String text = total > 0
                                        ? String.format(Locale.US, "Downloading\u2026 %d%%  (%.1f / %.1f MB)", percent, done / 1048576f, total / 1048576f)
                                        : String.format(Locale.US, "Downloading\u2026 %.1f MB", done / 1048576f);
                                activity.runOnUiThread(() -> progressListener.onProgress(percent, text));
                            }
                        }
                        out.flush();
                        if (!expectedSha256.isEmpty() && !toHex(digest.digest()).equalsIgnoreCase(expectedSha256.trim())) {
                            throw new SecurityException("checksum mismatch, the file is corrupted or was tampered with");
                        }
                    }
                }
                String rejection = verifyApk(activity, partial);
                if (rejection != null) {
                    throw new SecurityException(rejection);
                }
                if (target.exists() && !target.delete()) {
                    throw new IllegalStateException("Cannot replace previous download");
                }
                if (!partial.renameTo(target)) {
                    throw new IllegalStateException("Cannot save the downloaded file");
                }
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (e instanceof SecurityException) {
                    error = "update rejected: " + error;
                }
            }
            String result = error;
            activity.runOnUiThread(() -> doneListener.onDone(result));
            executor.shutdown();
        });
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    /** @return null when the downloaded APK may be installed, otherwise the reason it was rejected. */
    private String verifyApk(AppCompatActivity activity, File apk) {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo downloaded = pm.getPackageArchiveInfo(apk.getPath(), flags);
        if (downloaded == null) {
            return "the downloaded file is not a valid APK";
        }
        if (!activity.getPackageName().equals(downloaded.packageName)) {
            return "the APK belongs to another app (" + downloaded.packageName + ")";
        }
        long downloadedVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? downloaded.getLongVersionCode() : downloaded.versionCode;
        if (downloadedVersion <= BuildConfig.VERSION_CODE) {
            return "the APK is not newer than the installed version";
        }
        try {
            PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
            if (!signers(downloaded).equals(signers(installed)) || signers(installed).isEmpty()) {
                return "the APK is signed with a different key than the installed app";
            }
        } catch (PackageManager.NameNotFoundException e) {
            return "cannot read the installed app signature";
        }
        return null;
    }

    private static Set<String> signers(PackageInfo info) {
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo != null) {
                signatures = info.signingInfo.getApkContentsSigners();
            }
        } else {
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures != null) {
            for (Signature signature : signatures) {
                try {
                    result.add(toHex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private void install(AppCompatActivity activity, File apk) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
                // The user has to allow installs from this app once; they tap "Install" again afterwards.
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private void openUrl(AppCompatActivity activity, String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }
}
