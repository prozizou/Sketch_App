package pro.sketchware.activities.git;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.git.ProjectGit.Change;
import pro.sketchware.activities.git.ProjectGit.Commit;
import pro.sketchware.activities.git.ProjectGit.Credentials;
import pro.sketchware.activities.git.ProjectGit.GitProblem;
import pro.sketchware.activities.snapshots.AutoSnapshots;
import pro.sketchware.activities.snapshots.ProjectSnapshots;
import pro.sketchware.activities.snapshots.ProjectSnapshotsActivity;
import pro.sketchware.databinding.ActivityProjectGitBinding;
import pro.sketchware.databinding.ItemSnapshotBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Version control for a project: commit, history, branches, and push and pull to a remote.
 * Finishes with {@link ProjectSnapshotsActivity#RESULT_RESTORED} after anything that rewrote the
 * project's files, so the editor that opened it can close without saving over them.
 */
public class ProjectGitActivity extends BaseAppCompatActivity {
    private static final String PREFERENCES = "project_git";
    private static final int HISTORY_LENGTH = 200;
    /** Remembered for as long as the app runs, never written to disk. */
    private static final Map<String, Credentials> SESSION_CREDENTIALS = new HashMap<>();

    private interface Job<T> {
        T run(ProjectGit git) throws Exception;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HistoryAdapter adapter = new HistoryAdapter();
    private ActivityProjectGitBinding binding;
    private SharedPreferences preferences;
    private String scId;
    private File sketchRoot;
    private File gitDir;
    private File workTree;
    private String remoteUrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityProjectGitBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = getIntent().getStringExtra("sc_id");
        if (scId == null) {
            finish();
            return;
        }
        preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        sketchRoot = new File(FileUtil.getExternalStorageDir(), ".sketch_nws");
        gitDir = ProjectGit.gitDirOf(sketchRoot, scId);
        workTree = ProjectGit.workTreeOf(sketchRoot, scId);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        UI.addSystemWindowInsetToPadding(binding.history, false, false, false, true);
        binding.history.setLayoutManager(new LinearLayoutManager(this));
        binding.history.setAdapter(adapter);

        Menu menu = binding.topAppBar.getMenu();
        menu.add(R.string.git_menu_branches).setOnMenuItemClickListener(item -> {
            showBranches();
            return true;
        });
        menu.add(R.string.git_menu_remote).setOnMenuItemClickListener(item -> {
            askRemote(null);
            return true;
        });
        menu.add(R.string.git_menu_identity).setOnMenuItemClickListener(item -> {
            askIdentity(null);
            return true;
        });
        menu.add(R.string.git_menu_discard).setOnMenuItemClickListener(item -> {
            confirmDiscard();
            return true;
        });

        binding.btnStart.setOnClickListener(v -> run(git -> ProjectGit.init(gitDir, workTree), started -> {
            started.close();
            refresh();
        }));
        binding.btnCommit.setOnClickListener(v -> commit());
        binding.btnPush.setOnClickListener(v -> sync(true));
        binding.btnPull.setOnClickListener(v -> sync(false));
        binding.btnChanges.setOnClickListener(v -> showChanges());

        refresh();
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    // ---- running Git off the main thread

    /** Runs {@code job} with the repository open (or a null one before version control was started). */
    private <T> void run(Job<T> job, Consumer<T> onSuccess) {
        binding.progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            T result = null;
            String problem = null;
            ProjectGit git = null;
            try {
                if (ProjectGit.exists(gitDir)) git = ProjectGit.open(gitDir, workTree);
                result = job.run(git);
            } catch (GitProblem e) {
                problem = e.getMessage();
            } catch (Exception | OutOfMemoryError e) {
                problem = e.getMessage() != null ? e.getMessage() : e.toString();
            } finally {
                if (git != null) git.close();
            }
            T finalResult = result;
            String finalProblem = problem;
            runOnUiThread(() -> {
                binding.progress.setVisibility(View.INVISIBLE);
                if (finalProblem != null) {
                    new MaterialAlertDialogBuilder(this).setMessage(finalProblem).setPositiveButton(android.R.string.ok, null).show();
                    refresh();
                } else {
                    onSuccess.accept(finalResult);
                }
            });
        });
    }

    /** Takes a snapshot of the project before something that rewrites its files. */
    private void snapshotFirst() throws Exception {
        AutoSnapshots.forProject(scId).create(ProjectSnapshots.Kind.BEFORE_RESTORE);
    }

    private void filesChanged() {
        setResult(ProjectSnapshotsActivity.RESULT_RESTORED);
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.git_done_reopen)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }

    // ---- showing the state

    private record State(boolean started, String branch, String remote, List<Change> changes, List<Commit> history) {
    }

    private void refresh() {
        run(git -> {
            if (git == null) return new State(false, "", null, List.of(), List.of());
            return new State(true, git.branch(), git.remoteUrl(), git.status(), git.log(HISTORY_LENGTH));
        }, this::show);
    }

    private void show(State state) {
        binding.notStarted.setVisibility(state.started() ? View.GONE : View.VISIBLE);
        binding.started.setVisibility(state.started() ? View.VISIBLE : View.GONE);
        if (!state.started()) return;

        remoteUrl = state.remote();
        binding.branch.setText(state.branch());
        binding.remote.setText(state.remote() == null ? getString(R.string.git_no_remote) : state.remote());
        binding.changesSummary.setText(state.changes().isEmpty()
                ? getString(R.string.git_no_changes)
                : getString(R.string.git_changes_count, state.changes().size()));
        adapter.submit(state.history());
        binding.emptyHistory.setVisibility(state.history().isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---- actions

    private void commit() {
        if (preferences.getString("name", "").isBlank()) {
            askIdentity(this::commit);
            return;
        }
        askText(R.string.git_commit, R.string.git_commit_message_hint, "", message -> run(git -> {
            Commit made = git.commitAll(message, preferences.getString("name", ""), preferences.getString("email", ""));
            return made == null ? null : made.shortId();
        }, shortId -> {
            SketchwareUtil.toast(shortId == null ? getString(R.string.git_nothing_to_commit) : getString(R.string.git_commit_done, shortId));
            refresh();
        }));
    }

    /** Pushes or pulls, asking first for whatever is still missing. */
    private void sync(boolean push) {
        if (remoteUrl == null) {
            askRemote(() -> sync(push));
            return;
        }
        Credentials known = SESSION_CREDENTIALS.get(remoteUrl);
        if (known == null && !remoteUrl.startsWith("file://")) {
            askCredentials(credentials -> {
                SESSION_CREDENTIALS.put(remoteUrl, credentials);
                sync(push);
            });
            return;
        }
        String url = remoteUrl;
        run(git -> {
            try {
                if (push) return git.push(known);
                snapshotFirst();
                return git.pull(known);
            } catch (GitProblem e) {
                // A refused sign-in shouldn't be tried again with the same wrong token
                if (e.getMessage() != null && e.getMessage().contains("refused your user name")) SESSION_CREDENTIALS.remove(url);
                throw e;
            }
        }, message -> {
            SketchwareUtil.toast(message);
            if (!push && message.startsWith("Pulled")) filesChanged();
            else refresh();
        });
    }

    private void showChanges() {
        run(git -> {
            StringBuilder text = new StringBuilder();
            for (Change change : git.status()) {
                text.append(switch (change.kind()) {
                    case ADDED -> "A  ";
                    case MODIFIED -> "M  ";
                    case DELETED -> "D  ";
                }).append(change.path()).append('\n');
            }
            if (text.length() == 0) return getString(R.string.git_no_changes);
            return text + "\n" + git.diffOfWorkingTree();
        }, text -> showText(getString(R.string.git_changes), text));
    }

    private void showCommit(Commit commit) {
        run(git -> git.diffOfCommit(commit.id()), diff -> showText(commit.shortId() + " " + commit.message(), diff));
    }

    private void askRestore(Commit commit) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.git_restore_files)
                .setMessage(R.string.git_restore_confirm)
                .setPositiveButton(R.string.snapshots_restore, (dialog, which) -> run(git -> {
                    snapshotFirst();
                    git.restoreFilesFrom(commit.id());
                    return null;
                }, nothing -> filesChanged()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDiscard() {
        if (!ProjectGit.exists(gitDir)) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.git_menu_discard)
                .setMessage(R.string.git_discard_confirm)
                .setPositiveButton(R.string.snapshots_delete, (dialog, which) -> run(git -> {
                    snapshotFirst();
                    git.discardChanges();
                    return null;
                }, nothing -> filesChanged()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showBranches() {
        if (!ProjectGit.exists(gitDir)) return;
        run(git -> new Object[]{git.branches(), git.branch()}, result -> {
            @SuppressWarnings("unchecked") List<String> branches = (List<String>) result[0];
            String current = (String) result[1];
            List<String> items = new ArrayList<>();
            for (String branch : branches) items.add(branch.equals(current) ? branch + "  ✓" : branch);
            items.add(getString(R.string.git_branch_new));
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.git_menu_branches)
                    .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
                        if (which == items.size() - 1) {
                            askText(R.string.git_branch_new, R.string.git_branch_name_hint, "", name -> run(git -> {
                                git.createBranch(name.strip());
                                return null;
                            }, nothing -> refresh()));
                        } else if (!branches.get(which).equals(current)) {
                            run(git -> {
                                snapshotFirst();
                                git.switchBranch(branches.get(which));
                                return null;
                            }, nothing -> filesChanged());
                        }
                    })
                    .show();
        });
    }

    // ---- asking the user

    private void askText(int title, int hint, String initial, Consumer<String> onOk) {
        askFields(title, new int[]{hint}, new String[]{initial}, new boolean[]{false}, values -> onOk.accept(values[0]));
    }

    private void askRemote(Runnable then) {
        askFields(R.string.git_menu_remote, new int[]{R.string.git_remote_hint}, new String[]{remoteUrl == null ? "" : remoteUrl}, new boolean[]{false}, values ->
                run(git -> {
                    git.setRemoteUrl(values[0]);
                    return git.remoteUrl();
                }, url -> {
                    remoteUrl = url;
                    SESSION_CREDENTIALS.remove(url);
                    if (then != null) then.run();
                    else refresh();
                }));
    }

    private void askIdentity(Runnable then) {
        askFields(R.string.git_menu_identity, new int[]{R.string.git_name_hint, R.string.git_email_hint},
                new String[]{preferences.getString("name", ""), preferences.getString("email", "")}, new boolean[]{false, false}, values -> {
                    preferences.edit().putString("name", values[0].strip()).putString("email", values[1].strip()).apply();
                    if (then != null) then.run();
                });
    }

    private void askCredentials(Consumer<Credentials> onOk) {
        askFields(R.string.git_credentials_title, new int[]{R.string.git_user_hint, R.string.git_token_hint},
                new String[]{preferences.getString("user", ""), ""}, new boolean[]{false, true}, values -> {
                    preferences.edit().putString("user", values[0].strip()).apply();
                    onOk.accept(new Credentials(values[0].strip(), values[1]));
                });
    }

    /** A dialog with one text field per hint; the OK button stays disabled until the first is filled. */
    private void askFields(int title, int[] hints, String[] initial, boolean[] secret, Consumer<String[]> onOk) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = SketchwareUtil.dpToPx(20);
        form.setPadding(padding, SketchwareUtil.dpToPx(8), padding, 0);
        List<TextInputEditText> fields = new ArrayList<>();
        for (int i = 0; i < hints.length; i++) {
            TextInputLayout layout = new TextInputLayout(this);
            layout.setHint(getString(hints[i]));
            TextInputEditText field = new TextInputEditText(layout.getContext());
            field.setSingleLine(true);
            field.setInputType(secret[i] ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
            field.setText(initial[i]);
            layout.addView(field);
            form.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            fields.add(field);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(form)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String[] values = new String[fields.size()];
                    for (int i = 0; i < values.length; i++) values[i] = String.valueOf(fields.get(i).getText());
                    onOk.accept(values);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        fields.get(0).requestFocus();
    }

    /** A scrollable monospaced view of a diff, with added and removed lines coloured. */
    private void showText(String title, String text) {
        int added = MaterialColors.getColor(this, R.attr.colorPrimary, 0);
        int removed = MaterialColors.getColor(this, R.attr.colorError, 0);
        int header = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant, 0);

        SpannableStringBuilder styled = new SpannableStringBuilder();
        for (String line : text.split("\n", -1)) {
            int start = styled.length();
            styled.append(line).append('\n');
            Integer color = null;
            if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("@@")) color = header;
            else if (line.startsWith("+")) color = added;
            else if (line.startsWith("-")) color = removed;
            if (color != null) styled.setSpan(new ForegroundColorSpan(color), start, styled.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        TextView view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11);
        view.setTextIsSelectable(true);
        view.setText(styled);
        int padding = SketchwareUtil.dpToPx(16);
        view.setPadding(padding, padding / 2, padding, padding / 2);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.addView(view);
        ScrollView vertical = new ScrollView(this);
        vertical.addView(horizontal);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(vertical)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ---- history list

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<Commit> commits = new ArrayList<>();

        void submit(List<Commit> newCommits) {
            commits = newCommits;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemSnapshotBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Commit commit = commits.get(position);
            holder.item.title.setText(commit.message());
            holder.item.subtitle.setText(commit.shortId() + " · " + commit.author() + " · "
                    + DateUtils.getRelativeTimeSpanString(commit.time(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            holder.itemView.setOnClickListener(v -> new MaterialAlertDialogBuilder(ProjectGitActivity.this)
                    .setTitle(commit.shortId() + " " + commit.message())
                    .setItems(new CharSequence[]{getString(R.string.git_view_changes), getString(R.string.git_restore_files)}, (dialog, which) -> {
                        if (which == 0) showCommit(commit);
                        else askRestore(commit);
                    })
                    .show());
        }

        @Override
        public int getItemCount() {
            return commits.size();
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
