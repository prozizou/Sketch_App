package pro.sketchware.unityads;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean sdkInstalled;

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

        binding.layoutSwitchCard.setOnClickListener(v -> toggleEnabled());
        binding.btnDownloadSdk.setOnClickListener(v -> downloadSdk());
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
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", scId);
        super.onSaveInstanceState(outState);
    }

    private void refreshSdkStatus() {
        sdkInstalled = UnityAdsSdk.isInstalled(LocalLibrariesUtil.getLocalLibraries(scId));
        binding.sdkStatus.setText(sdkInstalled ? R.string.unity_ads_sdk_installed : R.string.unity_ads_sdk_missing);
        binding.btnDownloadSdk.setVisibility(sdkInstalled ? View.GONE : View.VISIBLE);
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
        executor.execute(() -> {
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
