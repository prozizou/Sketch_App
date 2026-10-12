package pro.sketchware.unityads;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aldi.sayuti.editor.manage.LibraryDownloaderDialogFragment;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.MavenVersions;
import mod.hey.studios.build.BuildSettings;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageLibraryUnityAdsBinding;
import pro.sketchware.utility.SketchwareUtil;

/** Settings screen of the Unity Ads library: Game ID, placements, test mode and the SDK download. */
public class UnityAdsActivity extends BaseAppCompatActivity {
    private static final String DOWNLOADER_TAG = "unity_ads_sdk_downloader";

    private ManageLibraryUnityAdsBinding binding;
    private String scId;
    /** Reads class files; slow, and must never hold up a download. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** Downloads and repairs. */
    private final ExecutorService workExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean sdkInstalled;
    private String statusText = "";
    private boolean needsReinstall;
    private int statusGeneration;
    private List<UnityAdsRequirements.Requirement> missingRequirements = new ArrayList<>();
    /** Row views of the libraries list, by coordinate, to show each download's progress and failure on its row. */
    private final Map<String, MaterialButton> rowButtons = new HashMap<>();
    private final Map<String, TextView> rowStatuses = new HashMap<>();
    private List<UnityAdsKnownLibraries.Entry> missingLibraries = new ArrayList<>();
    private boolean downloadingAll;

    /** What the Manage libraries screen shows for this library: ON when the project would generate code. */
    public static boolean isEnabled(String scId) {
        return UnityAdsStore.isActive(scId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isStoragePermissionGranted()) {
            finish();
            return;
        }

        binding = ManageLibraryUnityAdsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        UnityAdsConfig config = UnityAdsStore.read(scId);
        binding.libSwitch.setChecked(config.enabled);
        binding.gameId.setText(config.gameId);
        binding.bannerPlacement.setText(config.bannerPlacement);
        binding.interstitialPlacement.setText(config.interstitialPlacement);
        binding.rewardedPlacement.setText(config.rewardedPlacement);
        binding.testMode.setChecked(config.testMode);
        binding.autoBanner.setChecked(config.autoBanner);

        binding.layoutSwitchCard.setOnClickListener(v -> toggleEnabled());
        binding.btnDownloadSdk.setOnClickListener(v -> downloadSdk());
        binding.btnRepairSdk.setOnClickListener(v -> repairSdk());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSdkStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (binding != null) {
            save();
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        workExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", scId);
        super.onSaveInstanceState(outState);
    }

    private void refreshSdkStatus() {
        sdkInstalled = UnityAdsSdk.isInstalled(LocalLibrariesUtil.getLocalLibraries(scId));
        int generation = ++statusGeneration;
        statusText = getString(sdkInstalled ? R.string.unity_ads_sdk_installed : R.string.unity_ads_sdk_missing);
        binding.sdkStatus.setText(statusText);
        binding.btnDownloadSdk.setVisibility(sdkInstalled ? View.GONE : View.VISIBLE);
        binding.btnRepairSdk.setVisibility(View.GONE);
        binding.btnRepairSdk.setText(R.string.unity_ads_repair_sdk);
        needsReinstall = false;
        if (sdkInstalled) {
            missingRequirements = UnityAdsStore.missingRequirements(scId);
            List<String> uncompiled = UnityAdsStore.librariesWithUncompiledJars(scId);
            if (!missingRequirements.isEmpty() || !uncompiled.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (UnityAdsRequirements.Requirement requirement : missingRequirements) {
                    names.add(requirement.artifactId());
                }
                for (String library : uncompiled) {
                    names.add(library + " (embedded libraries)");
                }
                statusText = getString(R.string.unity_ads_sdk_incomplete, String.join(", ", names));
                binding.sdkStatus.setText(statusText);
                binding.btnRepairSdk.setVisibility(View.VISIBLE);
            }
            showMissingClassesLater(generation);
            showLibrariesLater(generation);
        } else {
            binding.libraryList.removeAllViews();
        }
    }

    /** Lists the SDK's libraries the project lacks, each with its own download button. */
    private void showLibrariesLater(int generation) {
        String version = UnityAdsRequirements.sdkVersion(LocalLibrariesUtil.getLocalLibraries(scId));
        if (!UnityAdsKnownLibraries.SDK_VERSION.equals(version)) {
            // Another SDK version than the table describes: only the full reinstall is reliable.
            binding.libraryList.removeAllViews();
            return;
        }
        executor.execute(() -> {
            Set<String> available = UnityAdsStore.availableClasses(scId, UnityAdsKnownLibraries.classFiles());
            List<UnityAdsKnownLibraries.Entry> missing = UnityAdsKnownLibraries.missing(available);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || generation != statusGeneration) return;
                missingLibraries = missing;
                renderLibraries(UnityAdsKnownLibraries.all().size() - missing.size());
            });
        });
    }

    private void renderLibraries(int present) {
        binding.libraryList.removeAllViews();
        rowButtons.clear();
        rowStatuses.clear();
        if (missingLibraries.isEmpty()) {
            return;
        }
        TextView summary = new TextView(this);
        summary.setText(getString(R.string.unity_ads_libraries_summary, present, missingLibraries.size()));
        summary.setPadding(0, dp(8), 0, dp(4));
        binding.libraryList.addView(summary);

        MaterialButton all = newButton(getString(R.string.unity_ads_download_all, missingLibraries.size()));
        all.setOnClickListener(v -> downloadAllMissing());
        all.setEnabled(!downloadingAll);
        binding.libraryList.addView(all);

        for (UnityAdsKnownLibraries.Entry entry : missingLibraries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(entry.artifactId() + " " + entry.version());
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton button = newButton(getString(R.string.unity_ads_download));
            button.setOnClickListener(v -> downloadOne(entry));
            row.addView(button);
            binding.libraryList.addView(row);

            TextView status = new TextView(this);
            status.setTextIsSelectable(true);
            status.setVisibility(View.GONE);
            binding.libraryList.addView(status);
            rowButtons.put(entry.coordinate, button);
            rowStatuses.put(entry.coordinate, status);
        }
    }

    private MaterialButton newButton(String label) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setRow(String coordinate, @Nullable String message, boolean buttonEnabled) {
        TextView status = rowStatuses.get(coordinate);
        MaterialButton button = rowButtons.get(coordinate);
        if (status != null && message != null) {
            status.setText(message);
            status.setVisibility(View.VISIBLE);
        }
        if (button != null) {
            button.setEnabled(buttonEnabled);
        }
    }

    /** Downloads one library and shows the result, or the reason it failed, on its own row. */
    private void downloadOne(UnityAdsKnownLibraries.Entry entry) {
        setRow(entry.coordinate, getString(R.string.unity_ads_downloading), false);
        workExecutor.execute(() -> {
            UnityAdsRepair.Result result = runRepair(entry.coordinate);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result.failed.isEmpty()) {
                    setRow(entry.coordinate, getString(R.string.unity_ads_done), false);
                    refreshSdkStatus();
                } else {
                    setRow(entry.coordinate, getString(R.string.unity_ads_row_failed, String.join("\n", result.failed)), true);
                }
            });
        });
    }

    private void downloadAllMissing() {
        if (downloadingAll) {
            return;
        }
        downloadingAll = true;
        List<UnityAdsKnownLibraries.Entry> todo = new ArrayList<>(missingLibraries);
        for (UnityAdsKnownLibraries.Entry entry : todo) {
            setRow(entry.coordinate, getString(R.string.unity_ads_downloading), false);
        }
        workExecutor.execute(() -> {
            for (UnityAdsKnownLibraries.Entry entry : todo) {
                UnityAdsRepair.Result result = runRepair(entry.coordinate);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setRow(entry.coordinate, result.failed.isEmpty() ? getString(R.string.unity_ads_done)
                            : getString(R.string.unity_ads_row_failed, String.join("\n", result.failed)), !result.failed.isEmpty());
                });
            }
            mainHandler.post(() -> {
                downloadingAll = false;
                if (!isFinishing() && !isDestroyed()) {
                    refreshSdkStatus();
                }
            });
        });
    }

    /** Never throws: a failure becomes a line of the result, so it is always shown to the person. */
    private UnityAdsRepair.Result runRepair(String coordinate) {
        try {
            return UnityAdsRepair.run(scId, Collections.singletonList(coordinate), false, (what, index, total) -> {
            });
        } catch (Throwable e) {
            UnityAdsRepair.Result result = new UnityAdsRepair.Result();
            result.failed.add(coordinate + ": " + e);
            return result;
        }
    }

    /** Scans the SDK's classes in the background and adds what the app won't contain to the status. */
    private void showMissingClassesLater(int generation) {
        executor.execute(() -> {
            Map<String, String> missing = UnityAdsStore.missingClasses(scId);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || generation != statusGeneration || missing.isEmpty()) return;
                List<String> names = new ArrayList<>();
                for (String name : missing.keySet()) {
                    if (names.size() == 6) break;
                    names.add(name.replace('/', '.'));
                }
                String more = missing.size() > names.size() ? " (+" + (missing.size() - names.size()) + ")" : "";
                String text = statusText + "\n\n"
                        + getString(R.string.unity_ads_missing_classes, String.join(", ", names) + more);
                List<String> withoutCode = UnityAdsStore.librariesWithoutCode(scId);
                if (!withoutCode.isEmpty()) {
                    text += "\n\n" + getString(R.string.unity_ads_libs_without_code, String.join(", ", withoutCode));
                }
                binding.sdkStatus.setText(text);
                // Nothing smaller fixes this: download the whole SDK again.
                needsReinstall = true;
                binding.btnRepairSdk.setText(R.string.unity_ads_reinstall_sdk);
                binding.btnRepairSdk.setVisibility(View.VISIBLE);
            });
        });
    }

    private void repairSdk() {
        List<UnityAdsRequirements.Requirement> toRepair = new ArrayList<>(missingRequirements);
        boolean reinstall = needsReinstall;
        if (toRepair.isEmpty() && !reinstall && UnityAdsStore.librariesWithUncompiledJars(scId).isEmpty()) {
            return;
        }
        binding.btnRepairSdk.setEnabled(false);
        List<String> coordinates = new ArrayList<>();
        for (UnityAdsRequirements.Requirement requirement : toRepair) {
            coordinates.add(requirement.coordinate);
        }
        workExecutor.execute(() -> {
            UnityAdsRepair.Result result;
            try {
                result = UnityAdsRepair.run(scId, coordinates, reinstall, (coordinate, index, total) ->
                        mainHandler.post(() -> {
                            if (binding != null) {
                                binding.sdkStatus.setText(getString(R.string.unity_ads_repairing, coordinate, index + 1, total));
                            }
                        }));
            } catch (Throwable e) {
                result = new UnityAdsRepair.Result();
                result.failed.add("Unexpected error: " + e);
            }
            UnityAdsRepair.Result finalResult = result;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.btnRepairSdk.setEnabled(true);
                refreshSdkStatus();
                if (finalResult.failed.isEmpty()) {
                    SketchwareUtil.toast(getString(R.string.unity_ads_repair_done));
                } else {
                    SketchwareUtil.showAnErrorOccurredDialog(this,
                            getString(R.string.unity_ads_repair_failed, String.join("\n", finalResult.failed)));
                }
            });
        });
    }

    private void toggleEnabled() {
        boolean turnOn = !binding.libSwitch.isChecked();
        if (turnOn) {
            if (!sdkInstalled) {
                SketchwareUtil.toastError(getString(R.string.unity_ads_need_sdk));
                return;
            }
            if (!showProblems(readFields())) {
                SketchwareUtil.toastError(getString(R.string.unity_ads_fix_values));
                return;
            }
        }
        binding.libSwitch.setChecked(turnOn);
    }

    /** Marks the invalid fields; true when there are none. */
    private boolean showProblems(UnityAdsConfig config) {
        boolean gameIdOk = UnityAdsConfig.isValidGameId(config.gameId);
        setError(binding.gameIdLayout, !gameIdOk);
        setError(binding.bannerLayout, !UnityAdsConfig.isValidPlacement(config.bannerPlacement));
        setError(binding.interstitialLayout, !UnityAdsConfig.isValidPlacement(config.interstitialPlacement));
        setError(binding.rewardedLayout, !UnityAdsConfig.isValidPlacement(config.rewardedPlacement));
        return config.problems().isEmpty();
    }

    private static void setError(TextInputLayout layout, boolean hasError) {
        layout.setError(hasError ? layout.getHint() : null);
    }

    private UnityAdsConfig readFields() {
        UnityAdsConfig config = new UnityAdsConfig();
        config.enabled = binding.libSwitch.isChecked();
        config.gameId = text(binding.gameId.getText());
        config.bannerPlacement = text(binding.bannerPlacement.getText());
        config.interstitialPlacement = text(binding.interstitialPlacement.getText());
        config.rewardedPlacement = text(binding.rewardedPlacement.getText());
        config.testMode = binding.testMode.isChecked();
        config.autoBanner = binding.autoBanner.isChecked();
        return config;
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    /** Saves the screen. It never stores "enabled" for settings that could not generate working code. */
    private void save() {
        UnityAdsConfig config = readFields();
        if (config.enabled && (!sdkInstalled || !config.problems().isEmpty())) {
            config.enabled = false;
        }
        UnityAdsStore.write(scId, config);
    }

    private void downloadSdk() {
        if (getSupportFragmentManager().findFragmentByTag(DOWNLOADER_TAG) != null) {
            return;
        }
        binding.btnDownloadSdk.setEnabled(false);
        binding.sdkStatus.setText(R.string.unity_ads_checking_version);
        workExecutor.execute(() -> {
            String version = latestVersion();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.btnDownloadSdk.setEnabled(true);
                refreshSdkStatus();
                if (version == null) {
                    SketchwareUtil.toastError("Couldn't look up the SDK version, check your connection");
                    return;
                }
                openDownloader(version);
            });
        });
    }

    private void openDownloader(String version) {
        Bundle arguments = new Bundle();
        arguments.putBoolean("notAssociatedWithProject", false);
        arguments.putSerializable("buildSettings", new BuildSettings(scId));
        arguments.putString("localLibFile", LocalLibrariesUtil.getLocalLibFile(scId).getAbsolutePath());
        arguments.putString("dependency", UnityAdsSdk.dependency(version));

        LibraryDownloaderDialogFragment fragment = new LibraryDownloaderDialogFragment();
        fragment.setArguments(arguments);
        fragment.setOnLibraryDownloadedTask(this::refreshSdkStatus);
        fragment.show(getSupportFragmentManager(), DOWNLOADER_TAG);
    }

    /** @return the newest stable SDK version on Maven Central, or null when it can't be found. */
    @Nullable
    private static String latestVersion() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(UnityAdsSdk.METADATA_URL).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                }
            }
            List<String> versions = UnityAdsSdk.parseVersions(body.toString());
            return MavenVersions.pickBest(null, versions);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
