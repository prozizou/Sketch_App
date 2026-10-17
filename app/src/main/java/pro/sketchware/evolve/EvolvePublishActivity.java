package pro.sketchware.evolve;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.BlockCollectionBean;
import com.besome.sketch.beans.MoreBlockCollectionBean;
import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import a.a.a.Mp;
import a.a.a.Pp;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.auth.AuthGate;
import pro.sketchware.databinding.ActivityEvolvePublishBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Publishes a post, or edits one of the user's own. The form checks everything {@link PostRules} and
 * {@link SharedBlockRules} check, says what is wrong next to the field, and only then uploads the screenshots and the
 * post. Blocks and more blocks are chosen from the user's collections; Java, Kotlin, XML and components are typed,
 * pasted or opened from a file.
 */
public class EvolvePublishActivity extends AppCompatActivity {

    private static final String EXTRA_EDIT = "edit_id";

    /** A screenshot on the form: already uploaded (an address) or just picked (a picture on the phone). */
    private record Shot(@Nullable String url, @Nullable Uri uri) {
    }

    private ActivityEvolvePublishBinding binding;
    private final List<Shot> shots = new ArrayList<>();
    private ShotAdapter shotAdapter;
    @Nullable
    private String editId;
    @Nullable
    private SharedBlock editing;
    private SharedKind kind = SharedKind.JAVA;
    private String sourceName = "";
    private String sourceSpec = "";
    private ArrayList<BlockBean> sourceBlocks;
    private int minSdk;
    private boolean dirty;
    private boolean busy;
    private ActivityResultLauncher<String> pickImages;
    private ActivityResultLauncher<String> pickFile;

    /** @param editId the post to edit, or null to publish a new one */
    public static Intent intent(Context context, @Nullable String editId) {
        return new Intent(context, EvolvePublishActivity.class).putExtra(EXTRA_EDIT, editId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityEvolvePublishBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.publishRoot, (root, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            // Below Android 11 the window itself shrinks for the keyboard (adjustResize); from 11 on it is the inset
            int bottom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Math.max(bars.bottom, ime.bottom) : bars.bottom;
            root.setPadding(bars.left, bars.top, bars.right, bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        editId = getIntent().getStringExtra(EXTRA_EDIT);
        pickImages = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            for (Uri uri : uris) {
                if (shots.size() >= PostRules.MAX_SCREENSHOTS) {
                    SketchwareUtil.toastError(getString(R.string.evolve_publish_too_many_screenshots));
                    break;
                }
                shots.add(new Shot(null, uri));
            }
            dirty = true;
            shotAdapter.notifyDataSetChanged();
        });
        pickFile = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) readFile(uri);
        });
        shotAdapter = new ShotAdapter();
        binding.publishScreenshots.setAdapter(shotAdapter);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (busy) return;
                if (dirty) {
                    new MaterialAlertDialogBuilder(EvolvePublishActivity.this)
                            .setTitle(R.string.evolve_publish_discard_title)
                            .setMessage(R.string.evolve_publish_discard_message)
                            .setNegativeButton(R.string.evolve_publish_keep, null)
                            .setPositiveButton(R.string.evolve_publish_discard, (d, w) -> finish())
                            .show();
                } else {
                    finish();
                }
            }
        });
        binding.publishBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        if (!EvolveShare.available(this)) {
            SketchwareUtil.toastError(getString(R.string.evolve_unavailable));
            finish();
            return;
        }
        setupKinds();
        setupSdk();
        setupFields();
        binding.publishSubmit.setOnClickListener(v -> submit());
        binding.publishAddScreenshot.setOnClickListener(v -> {
            if (shots.size() >= PostRules.MAX_SCREENSHOTS) {
                SketchwareUtil.toastError(getString(R.string.evolve_publish_too_many_screenshots));
            } else {
                pickImages.launch("image/*");
            }
        });
        binding.publishChooseSource.setOnClickListener(v -> chooseSource());
        binding.publishPaste.setOnClickListener(v -> paste());
        binding.publishFromFile.setOnClickListener(v -> pickFile.launch("*/*"));
        if (editId != null) {
            binding.publishTitleBar.setText(R.string.evolve_publish_edit);
            binding.publishSubmit.setText(R.string.evolve_publish_update);
            load(editId);
        } else {
            showKind(SharedKind.JAVA);
        }
    }

    // ------------------------------------------------------------------------------------------------- the form

    private void setupKinds() {
        for (SharedKind choice : new SharedKind[]{SharedKind.JAVA, SharedKind.KOTLIN, SharedKind.VIEW, SharedKind.BLOCKS, SharedKind.MOREBLOCK, SharedKind.COMPONENT}) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.evolve_kind_chip, binding.publishKinds, false);
            chip.setId(View.generateViewId());
            chip.setText(kindLabel(choice));
            chip.setTag(choice);
            chip.setChecked(choice == kind);
            binding.publishKinds.addView(chip);
        }
        binding.publishKinds.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty() || editId != null) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof SharedKind chosen && chosen != kind) {
                dirty = true;
                showKind(chosen);
            }
        });
    }

    @StringRes
    private static int kindLabel(SharedKind kind) {
        return switch (kind) {
            case BLOCKS -> R.string.evolve_kind_blocks;
            case MOREBLOCK -> R.string.evolve_chip_moreblocks;
            case VIEW -> R.string.evolve_chip_xml;
            case JAVA -> R.string.evolve_kind_java;
            case KOTLIN -> R.string.evolve_kind_kotlin;
            case COMPONENT -> R.string.evolve_kind_component;
        };
    }

    private void setupSdk() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.evolve_publish_sdk_any));
        for (int api = PostRules.MIN_SDK_LOWEST; api <= 36; api++) {
            labels.add(getString(R.string.evolve_facts_android, EvolvePostActivity.androidName(api), api));
        }
        binding.publishSdk.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        binding.publishSdk.setText(labels.get(0), false);
        binding.publishSdk.setOnItemClickListener((parent, view, position, id) -> {
            minSdk = position == 0 ? 0 : PostRules.MIN_SDK_LOWEST + position - 1;
            dirty = true;
        });
    }

    private void selectSdk(int api) {
        minSdk = api;
        int position = api == 0 ? 0 : Math.max(0, Math.min(api - PostRules.MIN_SDK_LOWEST + 1, 36 - PostRules.MIN_SDK_LOWEST + 1));
        binding.publishSdk.setText(binding.publishSdk.getAdapter().getItem(position).toString(), false);
    }

    private void setupFields() {
        BaseTextWatcher watcher = new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                dirty = true;
                binding.publishNameLayout.setError(null);
                binding.publishTagsLayout.setError(null);
                binding.publishVersionLayout.setError(null);
                binding.publishDependenciesLayout.setError(null);
                binding.publishFileLayout.setError(null);
                binding.publishCodeLayout.setError(null);
                binding.publishDescriptionLayout.setError(null);
            }
        };
        binding.publishName.addTextChangedListener(watcher);
        binding.publishDescription.addTextChangedListener(watcher);
        binding.publishTags.addTextChangedListener(watcher);
        binding.publishVersion.addTextChangedListener(watcher);
        binding.publishDependencies.addTextChangedListener(watcher);
        binding.publishFile.addTextChangedListener(watcher);
        binding.publishCode.addTextChangedListener(watcher);
    }

    /** Shows the fields that {@code chosen} needs: a source from the collections, or code and (for files) a name. */
    private void showKind(SharedKind chosen) {
        kind = chosen;
        boolean fromCollection = chosen.hasBlocks();
        binding.publishSourceBox.setVisibility(fromCollection ? View.VISIBLE : View.GONE);
        binding.publishCodeLayout.setVisibility(fromCollection ? View.GONE : View.VISIBLE);
        binding.publishCodeTools.setVisibility(fromCollection ? View.GONE : View.VISIBLE);
        boolean source = chosen == SharedKind.JAVA || chosen == SharedKind.KOTLIN;
        binding.publishFileLayout.setVisibility(source ? View.VISIBLE : View.GONE);
        binding.publishFromFile.setVisibility(chosen == SharedKind.COMPONENT ? View.VISIBLE : View.VISIBLE);
        for (int i = 0; i < binding.publishKinds.getChildCount(); i++) {
            Chip chip = (Chip) binding.publishKinds.getChildAt(i);
            chip.setChecked(chip.getTag() == chosen);
            chip.setEnabled(editId == null);
        }
        if (source && binding.publishFile.length() == 0) {
            binding.publishFile.setText(PostRules.suggestFileName(chosen, String.valueOf(binding.publishName.getText())));
        }
        if (!fromCollection) {
            binding.publishCodeLayout.setHint(chosen == SharedKind.VIEW ? getString(R.string.evolve_xml_title) : getString(R.string.evolve_publish_code_hint));
        }
    }

    // ------------------------------------------------------------------------------------------------ the sources

    private void chooseSource() {
        List<String> names = new ArrayList<>();
        List<Runnable> picks = new ArrayList<>();
        if (kind == SharedKind.BLOCKS) {
            for (BlockCollectionBean bean : Mp.h().f()) {
                if (bean.blocks == null || bean.blocks.isEmpty()) continue;
                names.add(bean.name);
                picks.add(() -> setSource(bean.name, "", new ArrayList<>(bean.blocks)));
            }
        } else {
            for (MoreBlockCollectionBean bean : Pp.h().f()) {
                if (bean.blocks == null || bean.blocks.isEmpty()) continue;
                names.add(bean.name);
                picks.add(() -> setSource(bean.name, bean.spec, new ArrayList<>(bean.blocks)));
            }
        }
        if (names.isEmpty()) {
            SketchwareUtil.toastError(getString(R.string.evolve_publish_collection_empty));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(kind == SharedKind.BLOCKS ? R.string.evolve_target_blocks : R.string.evolve_target_moreblocks)
                .setItems(names.toArray(new String[0]), (d, which) -> picks.get(which).run())
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void setSource(String name, String spec, ArrayList<BlockBean> blocks) {
        sourceName = name;
        sourceSpec = spec;
        sourceBlocks = blocks;
        dirty = true;
        binding.publishSource.setText(getString(R.string.evolve_publish_source_chosen, name));
        if (binding.publishName.length() == 0) binding.publishName.setText(name);
    }

    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).coerceToText(this).length() == 0) {
            SketchwareUtil.toastError(getString(R.string.evolve_copy_failed));
            return;
        }
        binding.publishCode.setText(clip.getItemAt(0).coerceToText(this));
    }

    /** Reads a text file the user opened into the code field (at most the size a post may have). */
    private void readFile(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("cannot open the file");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > SharedBlockRules.MAX_JSON_LENGTH * 4) throw new IOException(getString(R.string.evolve_publish_problem_large));
                }
                String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                runOnUiThread(() -> binding.publishCode.setText(text));
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> SketchwareUtil.toastError(String.valueOf(e.getMessage())));
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ loading (edit)

    private void load(String id) {
        binding.publishProgress.setVisibility(View.VISIBLE);
        EvolveSocialRepository.loadPost(id, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(SharedBlock post) {
                binding.publishProgress.setVisibility(View.GONE);
                if (post == null) {
                    SketchwareUtil.toastError(getString(R.string.evolve_post_gone));
                    finish();
                    return;
                }
                FirebaseUser user = AuthGate.currentUser(EvolvePublishActivity.this);
                if (user == null || !post.authorUid().equals(user.getUid())) {
                    finish();
                    return;
                }
                editing = post;
                fill(post);
            }

            @Override
            public void onError(@NonNull String message) {
                binding.publishProgress.setVisibility(View.GONE);
                SketchwareUtil.toastError(getString(R.string.evolve_error, message));
                finish();
            }
        });
    }

    private void fill(SharedBlock post) {
        kind = post.kind();
        showKind(post.kind());
        binding.publishName.setText(post.name());
        binding.publishDescription.setText(post.description());
        binding.publishTags.setText(String.join(" ", post.meta().tags()));
        binding.publishVersion.setText(post.meta().version());
        binding.publishDependencies.setText(String.join("\n", post.meta().dependencies()));
        selectSdk(post.meta().minSdk());
        if (post.kind().hasBlocks()) {
            sourceBlocks = SharedBlockCodec.decode(post.blocksJson());
            sourceName = post.name();
            sourceSpec = post.spec();
            binding.publishSource.setText(getString(R.string.evolve_publish_source_chosen, post.name()));
            // The blocks themselves can't be changed here: publish a new post for different blocks
            binding.publishChooseSource.setVisibility(View.GONE);
        } else {
            binding.publishCode.setText(post.payloadText());
            binding.publishFile.setText(post.meta().fileName());
        }
        for (String url : post.meta().screenshots()) shots.add(new Shot(url, null));
        shotAdapter.notifyDataSetChanged();
        dirty = false;
    }

    // ------------------------------------------------------------------------------------------------ submitting

    /** Checks the form and shows what is wrong; true when everything is fine. */
    private boolean validate() {
        boolean ok = true;
        SharedBlockRules.Problem name = SharedBlockRules.checkName(String.valueOf(binding.publishName.getText()));
        if (name != SharedBlockRules.Problem.NONE) {
            binding.publishNameLayout.setError(getString(name == SharedBlockRules.Problem.NAME_EMPTY ? R.string.evolve_problem_name_empty : R.string.evolve_problem_name_too_long));
            ok = false;
        }
        if (SharedBlockRules.checkDescription(String.valueOf(binding.publishDescription.getText())) != SharedBlockRules.Problem.NONE) {
            binding.publishDescriptionLayout.setError(getString(R.string.evolve_problem_description_too_long));
            ok = false;
        }
        if (PostRules.checkTags(tags()) != PostRules.Problem.NONE) {
            binding.publishTagsLayout.setError(getString(R.string.evolve_publish_problem_tags));
            ok = false;
        }
        if (PostRules.checkVersion(version()) != PostRules.Problem.NONE) {
            binding.publishVersionLayout.setError(getString(R.string.evolve_publish_problem_version));
            ok = false;
        }
        if (PostRules.checkDependencies(dependencies()) != PostRules.Problem.NONE) {
            binding.publishDependenciesLayout.setError(getString(R.string.evolve_publish_problem_deps));
            ok = false;
        }
        if (kind.hasBlocks()) {
            if (sourceBlocks == null || sourceBlocks.isEmpty()) {
                SketchwareUtil.toastError(getString(R.string.evolve_publish_no_source));
                ok = false;
            }
        } else {
            ok &= validateCode();
        }
        return ok;
    }

    private boolean validateCode() {
        String code = String.valueOf(binding.publishCode.getText());
        if (kind == SharedKind.VIEW) {
            SharedBlockRules.Problem problem = SharedXml.check(code).problem();
            if (problem != SharedBlockRules.Problem.NONE) {
                binding.publishCodeLayout.setError(getString(problem == SharedBlockRules.Problem.XML_EMPTY ? R.string.evolve_publish_problem_empty
                        : problem == SharedBlockRules.Problem.XML_INVALID ? R.string.evolve_problem_xml_invalid : R.string.evolve_publish_problem_large));
                return false;
            }
            return true;
        }
        PostRules.Problem problem = PostRules.checkCode(kind, code);
        if (problem != PostRules.Problem.NONE) {
            binding.publishCodeLayout.setError(getString(switch (problem) {
                case CODE_EMPTY -> R.string.evolve_publish_problem_empty;
                case CODE_TOO_LARGE -> R.string.evolve_publish_problem_large;
                case JAVA_SYNTAX -> R.string.evolve_publish_problem_java;
                default -> R.string.evolve_publish_problem_component;
            }));
            return false;
        }
        if (PostRules.checkFileName(kind, fileName()) != PostRules.Problem.NONE) {
            binding.publishFileLayout.setError(getString(R.string.evolve_publish_problem_file));
            return false;
        }
        return true;
    }

    private List<String> tags() {
        return PostRules.splitTags(String.valueOf(binding.publishTags.getText()));
    }

    private String version() {
        return String.valueOf(binding.publishVersion.getText()).trim();
    }

    private List<String> dependencies() {
        return PostRules.splitDependencies(String.valueOf(binding.publishDependencies.getText()));
    }

    private String fileName() {
        return String.valueOf(binding.publishFile.getText()).trim();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        binding.publishSubmit.setEnabled(!busy);
        binding.publishProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void submit() {
        if (busy || !validate()) return;
        setBusy(true);
        uploadNewShots(editId != null ? editId : EvolveRepository.newId());
    }

    /** Uploads the pictures picked on this form one after the other, then continues with the post. */
    private void uploadNewShots(String postId) {
        FirebaseUser user = AuthGate.currentUser(this);
        if (user == null) {
            setBusy(false);
            SketchwareUtil.toastError(getString(R.string.evolve_unavailable));
            return;
        }
        List<String> urls = new ArrayList<>();
        List<Uri> pending = new ArrayList<>();
        for (Shot shot : shots) {
            if (shot.url() != null) urls.add(shot.url());
            else if (shot.uri() != null) pending.add(shot.uri());
        }
        uploadNext(user, postId, urls, pending, 0, new ArrayList<>());
    }

    private void uploadNext(FirebaseUser user, String postId, List<String> urls, List<Uri> pending, int index, List<String> uploaded) {
        if (index >= pending.size()) {
            List<String> all = new ArrayList<>(urls);
            all.addAll(uploaded);
            finish(user, postId, all);
            return;
        }
        Context app = getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            byte[] jpeg;
            try {
                jpeg = EvolveStorage.prepare(app, pending.get(index));
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> screenshotFailed(user, postId, urls, pending, index, uploaded, String.valueOf(e.getMessage())));
                return;
            }
            runOnUiThread(() -> EvolveStorage.upload(user.getUid(), postId, index, jpeg, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
                @Override
                public void onSuccess(String url) {
                    uploaded.add(url);
                    uploadNext(user, postId, urls, pending, index + 1, uploaded);
                }

                @Override
                public void onError(@NonNull String message) {
                    screenshotFailed(user, postId, urls, pending, index, uploaded, message);
                }
            }));
        });
    }

    private void screenshotFailed(FirebaseUser user, String postId, List<String> urls, List<Uri> pending, int index, List<String> uploaded, String reason) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.evolve_publish_screenshots_failed, reason))
                .setNegativeButton(R.string.common_word_cancel, (d, w) -> setBusy(false))
                .setPositiveButton(R.string.evolve_publish_without, (d, w) -> uploadNext(user, postId, urls, pending, index + 1, uploaded))
                .setOnCancelListener(d -> setBusy(false))
                .show();
    }

    private void finish(FirebaseUser user, String postId, List<String> screenshots) {
        if (editId != null && editing != null) {
            update(postId, screenshots);
            return;
        }
        SharedBlock post = build(user, screenshots);
        if (post == null) {
            setBusy(false);
            return;
        }
        EvolveRepository.publish(postId, post, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(String id) {
                EvolveCache.clear();
                SketchwareUtil.toast(getString(R.string.evolve_publish_done));
                startActivity(EvolvePostActivity.intent(EvolvePublishActivity.this, id, false));
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                setBusy(false);
                SketchwareUtil.toastError(getString(R.string.evolve_publish_failed, message));
            }
        });
    }

    /** @return the post to publish from the form, or null after telling the user why it can't be */
    @Nullable
    private SharedBlock build(FirebaseUser user, List<String> screenshots) {
        String author = user.getDisplayName() == null || user.getDisplayName().isBlank() ? getString(R.string.evolve_anonymous) : user.getDisplayName();
        String name = String.valueOf(binding.publishName.getText()).trim();
        String description = PostRules.clean(String.valueOf(binding.publishDescription.getText()));
        String code = kind.hasBlocks() ? "" : String.valueOf(binding.publishCode.getText());
        PostMeta meta = new PostMeta(tags(), version(), minSdk, dependencies(), screenshots, 0, 0, 0,
                kind.isCode() ? code : "", kind == SharedKind.JAVA || kind == SharedKind.KOTLIN ? fileName() : "");
        if (kind.hasBlocks()) {
            String json = SharedBlockCodec.encode(sourceBlocks);
            SharedBlockRules.Problem problem = SharedBlockRules.checkStack(sourceBlocks.size(), json);
            if (problem != SharedBlockRules.Problem.NONE) {
                SketchwareUtil.toastError(getString(R.string.evolve_problem_too_many_blocks));
                return null;
            }
            List<String> opCodes = SharedBlockRules.distinctOpCodes(SharedBlockCodec.opCodes(sourceBlocks));
            return new SharedBlock("", name, description, user.getUid(), author, 0, BuildConfig.VERSION_CODE, sourceBlocks.size(), opCodes,
                    SharedBlockRules.usesRawCode(opCodes), 0, json, kind, kind == SharedKind.MOREBLOCK ? sourceSpec : "", "", meta);
        }
        int count = kind == SharedKind.VIEW ? SharedXml.check(code).elements() : Math.max(1, Math.min(1000, code.split("\\R").length));
        return new SharedBlock("", name, description, user.getUid(), author, 0, BuildConfig.VERSION_CODE, count, List.of(), false, 0, "", kind, "",
                kind == SharedKind.VIEW ? code : "", meta);
    }

    private void update(String postId, List<String> screenshots) {
        Map<String, Object> changes = new HashMap<>();
        changes.put(SharedBlock.NAME, String.valueOf(binding.publishName.getText()).trim());
        changes.put(SharedBlock.DESCRIPTION, PostRules.clean(String.valueOf(binding.publishDescription.getText())));
        changes.put(PostMeta.TAGS, tags().isEmpty() ? null : new ArrayList<>(tags()));
        changes.put(PostMeta.VERSION, version().isEmpty() ? null : version());
        changes.put(PostMeta.MIN_SDK, minSdk == 0 ? null : minSdk);
        changes.put(PostMeta.DEPENDENCIES, dependencies().isEmpty() ? null : new ArrayList<>(dependencies()));
        changes.put(PostMeta.SCREENSHOTS, screenshots.isEmpty() ? null : new ArrayList<>(screenshots));
        if (kind == SharedKind.VIEW) {
            changes.put(SharedBlock.XML, String.valueOf(binding.publishCode.getText()));
        } else if (kind.isCode()) {
            changes.put(PostMeta.CODE, String.valueOf(binding.publishCode.getText()));
            if (kind != SharedKind.COMPONENT) changes.put(PostMeta.FILE_NAME, fileName());
        }
        List<String> removed = new ArrayList<>(editing.meta().screenshots());
        removed.removeAll(screenshots);
        EvolveSocialRepository.updatePost(postId, changes, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                EvolveStorage.delete(removed);
                EvolveCache.clear();
                SketchwareUtil.toast(getString(R.string.evolve_publish_updated));
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                setBusy(false);
                SketchwareUtil.toastError(getString(R.string.evolve_publish_failed, message));
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ screenshots

    private final class ShotAdapter extends RecyclerView.Adapter<ShotAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.FrameLayout frame = new android.widget.FrameLayout(parent.getContext());
            float density = getResources().getDisplayMetrics().density;
            frame.setLayoutParams(new RecyclerView.LayoutParams(Math.round(120 * density), Math.round(80 * density)));
            ImageView image = new ImageView(parent.getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundResource(R.drawable.evolve_preview_bg);
            image.setContentDescription(getString(R.string.evolve_screenshot_description));
            frame.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -1));
            ImageButton remove = new ImageButton(parent.getContext());
            remove.setImageResource(R.drawable.ic_mtrl_close);
            remove.setBackgroundResource(R.drawable.evolve_badge_dot);
            remove.setColorFilter(0xFFFFFFFF);
            remove.setContentDescription(getString(R.string.evolve_publish_remove_screenshot));
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(Math.round(28 * density), Math.round(28 * density), android.view.Gravity.TOP | android.view.Gravity.END);
            params.setMargins(0, Math.round(4 * density), Math.round(4 * density), 0);
            remove.setPadding(Math.round(4 * density), Math.round(4 * density), Math.round(4 * density), Math.round(4 * density));
            frame.addView(remove, params);
            return new Holder(frame, image, remove);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Shot shot = shots.get(position);
            Object source = shot.url() != null ? shot.url() : shot.uri();
            Glide.with(holder.image).load(source).override(360, 240).centerCrop().into(holder.image);
            holder.remove.setOnClickListener(v -> {
                int at = holder.getBindingAdapterPosition();
                if (at >= 0) {
                    shots.remove(at);
                    dirty = true;
                    notifyItemRemoved(at);
                }
            });
        }

        @Override
        public int getItemCount() {
            return shots.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final ImageButton remove;

            Holder(View root, ImageView image, ImageButton remove) {
                super(root);
                this.image = image;
                this.remove = remove;
            }
        }
    }
}
