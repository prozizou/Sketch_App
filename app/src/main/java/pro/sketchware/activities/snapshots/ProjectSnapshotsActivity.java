package pro.sketchware.activities.snapshots;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.snapshots.ProjectSnapshots.Kind;
import pro.sketchware.activities.snapshots.ProjectSnapshots.Snapshot;
import pro.sketchware.databinding.ActivityProjectSnapshotsBinding;
import pro.sketchware.databinding.ItemSnapshotBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Lists a project's snapshots and lets the user take one, restore one or delete one.
 * Finishes with {@link #RESULT_RESTORED} after a restore, so the editor that opened it can close
 * without saving what it still holds in memory over the restored project.
 */
public class ProjectSnapshotsActivity extends BaseAppCompatActivity {
    public static final int RESULT_RESTORED = Activity.RESULT_FIRST_USER;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SnapshotsAdapter adapter = new SnapshotsAdapter();
    private ActivityProjectSnapshotsBinding binding;
    private ProjectSnapshots snapshots;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityProjectSnapshotsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String scId = getIntent().getStringExtra("sc_id");
        if (scId == null) {
            finish();
            return;
        }
        snapshots = AutoSnapshots.forProject(scId);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        UI.addSystemWindowInsetToPadding(binding.list, false, false, false, true);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);
        binding.fabCreate.setOnClickListener(v -> work(() -> snapshots.create(Kind.MANUAL), R.string.snapshots_failed, R.string.snapshots_created));

        refresh();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    private void refresh() {
        executor.execute(() -> {
            List<Snapshot> list = snapshots.list();
            runOnUiThread(() -> {
                adapter.submit(list);
                binding.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private interface Job {
        void run() throws Exception;
    }

    /** Runs {@code job} off the main thread with the progress bar showing, then refreshes the list. */
    private void work(Job job, int failureMessage, int successMessage) {
        binding.progress.setVisibility(View.VISIBLE);
        binding.fabCreate.setEnabled(false);
        executor.execute(() -> {
            String error = null;
            try {
                job.run();
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            String finalError = error;
            runOnUiThread(() -> {
                binding.progress.setVisibility(View.INVISIBLE);
                binding.fabCreate.setEnabled(true);
                if (finalError != null) {
                    SketchwareUtil.toastError(getString(failureMessage, finalError));
                } else if (successMessage != 0) {
                    SketchwareUtil.toast(getString(successMessage));
                }
                refresh();
            });
        });
    }

    private void showActions(Snapshot snapshot) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(describe(snapshot))
                .setItems(new CharSequence[]{getString(R.string.snapshots_restore), getString(R.string.snapshots_delete)}, (dialog, which) -> {
                    if (which == 0) confirmRestore(snapshot);
                    else work(() -> snapshots.delete(snapshot), 0, 0);
                })
                .show();
    }

    private void confirmRestore(Snapshot snapshot) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.snapshots_restore)
                .setMessage(R.string.snapshots_restore_confirm)
                .setPositiveButton(R.string.snapshots_restore, (dialog, which) -> restore(snapshot))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void restore(Snapshot snapshot) {
        binding.progress.setVisibility(View.VISIBLE);
        binding.fabCreate.setEnabled(false);
        executor.execute(() -> {
            try {
                snapshots.restore(snapshot);
                runOnUiThread(() -> {
                    setResult(RESULT_RESTORED);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.INVISIBLE);
                    binding.fabCreate.setEnabled(true);
                    SketchwareUtil.toastError(getString(R.string.snapshots_restore_failed, e.getMessage() != null ? e.getMessage() : e.toString()));
                    refresh();
                });
            }
        });
    }

    private String kindLabel(Kind kind) {
        return getString(switch (kind) {
            case SAVE -> R.string.snapshots_kind_save;
            case BUILD -> R.string.snapshots_kind_build;
            case MANUAL -> R.string.snapshots_kind_manual;
            case BEFORE_RESTORE -> R.string.snapshots_kind_before_restore;
        });
    }

    private String describe(Snapshot snapshot) {
        return kindLabel(snapshot.kind()) + " · " + DateFormat.getMediumDateFormat(this).format(new Date(snapshot.time()))
                + " " + DateFormat.getTimeFormat(this).format(new Date(snapshot.time()));
    }

    private class SnapshotsAdapter extends RecyclerView.Adapter<SnapshotsAdapter.ViewHolder> {
        private List<Snapshot> items = new ArrayList<>();

        void submit(List<Snapshot> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemSnapshotBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Snapshot snapshot = items.get(position);
            Date date = new Date(snapshot.time());
            holder.item.title.setText(kindLabel(snapshot.kind()));
            holder.item.subtitle.setText(DateFormat.getMediumDateFormat(holder.itemView.getContext()).format(date)
                    + " " + DateFormat.getTimeFormat(holder.itemView.getContext()).format(date)
                    + " · " + Formatter.formatShortFileSize(holder.itemView.getContext(), snapshot.size()));
            holder.itemView.setOnClickListener(v -> showActions(snapshot));
        }

        @Override
        public int getItemCount() {
            return items.size();
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
