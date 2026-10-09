package pro.sketchware.releases;

import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.analysis.FindingsDialog;
import pro.sketchware.databinding.ActivityReleaseManagerBinding;
import pro.sketchware.databinding.ItemSnapshotBinding;
import pro.sketchware.releases.ReleaseArchive.Release;
import pro.sketchware.releases.SizeAnalyzer.Part;
import pro.sketchware.utility.UI;

/**
 * The exported releases of a project: version, size and how it changed, and whether the R8 mapping of that build was kept.
 * Tapping a release shows its details and can break its size down.
 */
public class ReleaseManagerActivity extends BaseAppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ReleasesAdapter adapter = new ReleasesAdapter();
    private ActivityReleaseManagerBinding binding;
    private ReleaseArchive archive;
    private String scId;
    private List<Release> releases = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityReleaseManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = getIntent().getStringExtra("sc_id");
        if (scId == null) {
            finish();
            return;
        }
        archive = new ReleaseArchive(new File(Environment.getExternalStorageDirectory(), ".sketch_nws/releases"));

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        UI.addSystemWindowInsetToPadding(binding.list, false, false, false, true);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);
        refresh();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void refresh() {
        executor.execute(() -> {
            List<Release> found = archive.list(scId);
            runOnUiThread(() -> {
                releases = found;
                adapter.notifyDataSetChanged();
                binding.empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private String describe(Release release) {
        Long change = ReleaseArchive.sizeChange(releases, release);
        String size = SizeAnalyzer.format(release.sizeBytes);
        if (change != null && change != 0) size += " (" + (change > 0 ? "+" : "-") + SizeAnalyzer.format(Math.abs(change)) + ")";
        Date date = new Date(release.time);
        return DateFormat.getMediumDateFormat(this).format(date) + " " + DateFormat.getTimeFormat(this).format(date) + " · " + size
                + " · " + getString(release.hasMapping ? R.string.releases_mapping_kept : R.string.releases_no_mapping);
    }

    private void showDetails(Release release) {
        File mapping = archive.mappingFile(release);
        String text = release.projectName + " " + release.versionName + " (" + release.versionCode + ")\n"
                + release.packageName + "\n\n"
                + release.type.toUpperCase(Locale.ROOT) + " · " + SizeAnalyzer.format(release.sizeBytes) + "\n"
                + "SHA-256: " + release.sha256 + "\n"
                + release.artifactPath + "\n"
                + (mapping != null ? "\nR8 mapping: " + mapping.getAbsolutePath() : "");
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.releases_details)
                .setMessage(text)
                .setPositiveButton(R.string.releases_analyse_size, (dialog, which) -> analyseSize(release))
                .setNeutralButton(R.string.releases_delete, (dialog, which) -> {
                    archive.delete(release);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void analyseSize(Release release) {
        File artifact = new File(release.artifactPath);
        if (!artifact.isFile()) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.releases_file_missing).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        binding.topAppBar.setSubtitle("…");
        executor.execute(() -> {
            try {
                SizeAnalyzer.Report report = SizeAnalyzer.analyze(artifact, 8);
                runOnUiThread(() -> {
                    binding.topAppBar.setSubtitle(null);
                    showSize(report);
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    binding.topAppBar.setSubtitle(null);
                    new MaterialAlertDialogBuilder(this).setMessage(getString(R.string.releases_size_failed, e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null).show();
                });
            }
        });
    }

    private void showSize(SizeAnalyzer.Report report) {
        StringBuilder text = new StringBuilder();
        for (Part part : Part.values()) {
            Long bytes = report.byPart().get(part);
            if (bytes == null || bytes == 0) continue;
            text.append(partLabel(part)).append(": ").append(SizeAnalyzer.format(bytes)).append('\n');
        }
        text.append('\n').append(getString(R.string.releases_largest)).append('\n');
        for (SizeAnalyzer.Entry entry : report.largest()) {
            text.append(SizeAnalyzer.format(entry.compressed())).append("  ").append(entry.name()).append('\n');
        }
        if (report.findings().isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.releases_size_title).setMessage(text).setPositiveButton(android.R.string.ok, null).show();
        } else {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.releases_size_title).setMessage(text)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.releases_suggestions, (dialog, which) ->
                            FindingsDialog.show(this, getString(R.string.releases_size_title), report.findings(), ""))
                    .show();
        }
    }

    private String partLabel(Part part) {
        return getString(switch (part) {
            case CODE -> R.string.releases_part_code;
            case RESOURCES -> R.string.releases_part_resources;
            case ASSETS -> R.string.releases_part_assets;
            case NATIVE_LIBRARIES -> R.string.releases_part_native;
            case OTHER -> R.string.releases_part_other;
        });
    }

    private class ReleasesAdapter extends RecyclerView.Adapter<ReleasesAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemSnapshotBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Release release = releases.get(position);
            holder.item.title.setText(release.versionName + " (" + release.versionCode + ") · " + release.type.toUpperCase(Locale.ROOT));
            holder.item.subtitle.setText(describe(release));
            holder.itemView.setOnClickListener(v -> showDetails(release));
        }

        @Override
        public int getItemCount() {
            return releases.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ItemSnapshotBinding item;

            ViewHolder(ItemSnapshotBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }
    }
}
