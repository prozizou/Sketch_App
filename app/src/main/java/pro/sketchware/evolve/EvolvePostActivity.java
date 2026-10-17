package pro.sketchware.evolve;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.chip.Chip;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import a.a.a.Us;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityEvolvePostBinding;
import pro.sketchware.databinding.EvolveCommentItemBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;

/**
 * A post in full: its text, what it needs, screenshots, the code, the buttons to like, save, share, report, copy,
 * download and add it to a project, and the discussion. All of it is live: a like or a comment from somebody else
 * shows up while the page is open.
 */
public class EvolvePostActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "post_id";
    private static final String EXTRA_COMMENTS = "focus_comments";
    /** More than this much code is cut on screen (a 200 000 character TextView is slow on a 2 GB phone); copy and download give all of it. */
    private static final int MAX_SHOWN_CHARS = 40_000;

    private ActivityEvolvePostBinding binding;
    private EvolvePostViewModel model;
    private CommentAdapter comments;
    @Nullable
    private Comment replyTo;
    private boolean scrolledToComments;

    public static Intent intent(Context context, String postId, boolean focusComments) {
        return new Intent(context, EvolvePostActivity.class).putExtra(EXTRA_ID, postId).putExtra(EXTRA_COMMENTS, focusComments);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityEvolvePostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null || id.isEmpty()) {
            finish();
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.postRoot, (root, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            // Below Android 11 the window itself shrinks for the keyboard (adjustResize); from 11 on it is the inset
            int bottom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Math.max(bars.bottom, ime.bottom) : bars.bottom;
            root.setPadding(bars.left, bars.top, bars.right, bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        model = new ViewModelProvider(this).get(EvolvePostViewModel.class);
        comments = new CommentAdapter();
        binding.detailComments.setAdapter(comments);
        binding.detailComments.setItemAnimator(null);
        binding.postBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        setupReplyBox();
        observe();
        model.open(id);
    }

    // ---------------------------------------------------------------------------------------------------- the state

    private void observe() {
        model.post().observe(this, this::showPost);
        model.gone().observe(this, gone -> {
            if (gone) showState(getString(R.string.evolve_post_gone));
        });
        model.error().observe(this, error -> {
            if (error != null && model.post().getValue() == null) showState(error);
        });
        model.rows().observe(this, rows -> {
            comments.submitList(new ArrayList<>(rows));
            binding.detailCommentsEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            if (!rows.isEmpty() && getIntent().getBooleanExtra(EXTRA_COMMENTS, false) && !scrolledToComments) {
                scrolledToComments = true;
                binding.postScroll.post(() -> binding.postScroll.smoothScrollTo(0, binding.detailCommentsTitle.getTop()));
            }
        });
        model.canLoadOlder().observe(this, can -> binding.detailCommentsOlder.setVisibility(can ? View.VISIBLE : View.GONE));
        model.liked().observe(this, liked -> bindLike());
        model.saved().observe(this, saved -> bindSave());
        model.followingAuthor().observe(this, following -> bindFollow());
    }

    private void showState(String text) {
        binding.postProgress.setVisibility(View.GONE);
        binding.postContent.setVisibility(View.GONE);
        binding.postState.setText(text);
        binding.postState.setVisibility(View.VISIBLE);
        binding.replyBox.setVisibility(View.GONE);
    }

    private void showPost(SharedBlock post) {
        if (post == null) return;
        binding.postProgress.setVisibility(View.GONE);
        binding.postState.setVisibility(View.GONE);
        binding.postContent.setVisibility(View.VISIBLE);
        binding.replyBox.setVisibility(View.VISIBLE);
        Context context = this;
        binding.postScreenTitle.setText(post.name());
        binding.detailAuthor.setText(EvolveCard.authorName(context, post));
        binding.detailDate.setText(dateLine(post));
        binding.detailKind.setText(EvolvePostCard.kindBadge(post.kind()));
        binding.detailTitle.setText(post.name());
        binding.detailDescription.setText(post.description());
        binding.detailDescription.setVisibility(post.description().isEmpty() ? View.GONE : View.VISIBLE);
        binding.detailTags.setTags(EvolvePostCard.tags(post));
        bindFacts(post);
        bindScreenshots(post);
        bindCode(post);
        binding.detailDownloads.setText(getResources().getQuantityString(R.plurals.evolve_count_downloads,
                (int) Math.min(Math.max(0, post.downloads()), Integer.MAX_VALUE), EvolvePostCard.count(post.downloads())));
        binding.detailCommentsTitle.setText(getResources().getQuantityString(R.plurals.evolve_comments_count,
                (int) Math.min(post.meta().commentCount(), Integer.MAX_VALUE), EvolvePostCard.count(post.meta().commentCount())));
        boolean mine = post.authorUid().equals(model.myUid());
        binding.detailFollow.setVisibility(mine ? View.GONE : View.VISIBLE);
        bindLike();
        bindSave();
        bindFollow();
        binding.detailLike.setOnClickListener(v -> model.toggleLike(failure()));
        binding.detailSave.setOnClickListener(v -> model.toggleSave(failure()));
        binding.detailFollow.setOnClickListener(v -> model.toggleFollow(failure()));
        binding.detailShare.setOnClickListener(v -> EvolveActions.share(this, post));
        binding.postScreenMenu.setOnClickListener(v -> showMenu(v, post));
        binding.detailCopy.setOnClickListener(v -> EvolveImportFlow.copy(this, post));
        binding.detailCodeCopy.setOnClickListener(v -> EvolveImportFlow.copy(this, post));
        binding.detailDownload.setOnClickListener(v -> EvolveImportFlow.download(this, post));
        binding.detailAdd.setOnClickListener(v -> EvolveImportFlow.addToProject(this, post));
        binding.detailCommentsOlder.setOnClickListener(v -> model.loadOlderComments());
        comments.setContext(post, model.mentionable());
    }

    private String dateLine(SharedBlock post) {
        String line = EvolvePostCard.dateLine(this, post);
        if (post.meta().updatedAt() > 0) {
            String updated = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(post.meta().updatedAt()));
            line = line.isEmpty() ? getString(R.string.evolve_facts_updated, updated) : getString(R.string.evolve_meta_pair, line, getString(R.string.evolve_facts_updated, updated));
        }
        return line;
    }

    /** Compatibility and dependencies, in plain lines; a post that needs a newer Android than this device says so in red. */
    private void bindFacts(SharedBlock post) {
        PostMeta meta = post.meta();
        List<String> lines = new ArrayList<>();
        if (meta.minSdk() > 0) {
            lines.add(getString(R.string.evolve_facts_android, androidName(meta.minSdk()), meta.minSdk()));
        } else {
            lines.add(getString(R.string.evolve_facts_android_any));
        }
        lines.add(meta.dependencies().isEmpty() ? getString(R.string.evolve_facts_no_libraries)
                : getString(R.string.evolve_facts_libraries, String.join(", ", meta.dependencies())));
        if (!meta.version().isEmpty()) lines.add(getString(R.string.evolve_facts_version, meta.version()));
        if (!meta.fileName().isEmpty()) lines.add(getString(R.string.evolve_facts_file, meta.fileName()));
        binding.detailFacts.setText(String.join("\n", lines));
        String notice = EvolvePostCard.notice(this, post, Build.VERSION.SDK_INT);
        boolean tooNew = meta.minSdk() > Build.VERSION.SDK_INT;
        binding.detailNotice.setText(tooNew ? notice : null);
        binding.detailNotice.setVisibility(tooNew ? View.VISIBLE : View.GONE);
    }

    /** "8.0" for API 26 and so on; the API level alone for one this table doesn't know. */
    static String androidName(int api) {
        return switch (api) {
            case 21 -> "5.0";
            case 22 -> "5.1";
            case 23 -> "6.0";
            case 24 -> "7.0";
            case 25 -> "7.1";
            case 26 -> "8.0";
            case 27 -> "8.1";
            case 28 -> "9";
            case 29 -> "10";
            case 30 -> "11";
            case 31 -> "12";
            case 32 -> "12L";
            case 33 -> "13";
            case 34 -> "14";
            case 35 -> "15";
            case 36 -> "16";
            default -> String.valueOf(api);
        };
    }

    private void bindScreenshots(SharedBlock post) {
        List<String> shots = new ArrayList<>();
        for (String url : post.meta().screenshots()) {
            if (PostRules.isStorageUrl(url)) shots.add(url);
        }
        binding.detailScreenshots.setVisibility(shots.isEmpty() ? View.GONE : View.VISIBLE);
        binding.detailScreenshots.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.evolve_screenshot_item, parent, false);
                return new RecyclerView.ViewHolder(view) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Glide.with(holder.itemView).load(shots.get(position)).override(960, 640).centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE).into((android.widget.ImageView) holder.itemView);
            }

            @Override
            public int getItemCount() {
                return shots.size();
            }
        });
    }

    private void bindCode(SharedBlock post) {
        binding.detailCodeMore.setVisibility(View.GONE);
        binding.detailPreview.removeAllViews();
        binding.detailPreview.setVisibility(View.GONE);
        if (post.kind().hasBlocks()) {
            bindBlocks(post);
            return;
        }
        String code = post.payloadText();
        binding.detailCodeTitle.setText(post.meta().fileName().isEmpty() ? getString(EvolvePostCard.kindBadge(post.kind())) : post.meta().fileName());
        binding.detailCodeScroll.setVisibility(View.VISIBLE);
        showCode(CodeHighlighter.languageOf(post.kind()), code);
    }

    private void showCode(CodeHighlighter.Language language, String code) {
        binding.detailCodeScroll.setVisibility(View.VISIBLE);
        binding.detailCode.setText(HighlightedText.of(this, language, code, MAX_SHOWN_CHARS));
        if (code.length() > MAX_SHOWN_CHARS) {
            binding.detailCodeMore.setText(getString(R.string.evolve_code_truncated, EvolvePostCard.count(code.length())));
            binding.detailCodeMore.setVisibility(View.VISIBLE);
        }
    }

    /** A stack of blocks: drawn as the drawer draws it, and, when it writes raw Java into the app, that Java in full. */
    private void bindBlocks(SharedBlock post) {
        binding.detailCodeTitle.setText(post.name());
        ArrayList<BlockBean> blocks = SharedBlockCodec.decode(post.blocksJson());
        binding.detailCodeScroll.setVisibility(View.GONE);
        if (blocks == null || blocks.isEmpty()) {
            binding.detailCodeMore.setText(R.string.evolve_damaged);
            binding.detailCodeMore.setVisibility(View.VISIBLE);
            return;
        }
        try {
            BlockBean first = blocks.get(0);
            View stack = new Us(this, first.type, first.typeName, first.opCode, post.name(), blocks);
            stack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            binding.detailPreview.addView(stack);
            binding.detailPreview.setVisibility(View.VISIBLE);
        } catch (RuntimeException e) {
            binding.detailPreview.removeAllViews();
        }
        List<EvolveChecks.RawSnippet> raw = EvolveChecks.rawCode(blocks);
        String needs = EvolveCard.needs(this, post, blocks);
        StringBuilder note = new StringBuilder();
        if (needs != null) note.append(needs);
        if (!raw.isEmpty()) {
            if (note.length() > 0) note.append("\n\n");
            note.append(getString(R.string.evolve_preview_raw_header)).append('\n');
            for (EvolveChecks.RawSnippet snippet : raw) {
                note.append("// ").append(snippet.opCode()).append('\n').append(snippet.code()).append("\n\n");
            }
        }
        if (note.length() > 0) {
            binding.detailCodeScroll.setVisibility(View.VISIBLE);
            binding.detailCode.setText(HighlightedText.of(this, CodeHighlighter.Language.JAVA, note.toString().trim(), MAX_SHOWN_CHARS));
        }
    }

    private void bindLike() {
        SharedBlock post = model.post().getValue();
        boolean liked = Boolean.TRUE.equals(model.liked().getValue());
        long likes = post == null ? 0 : post.meta().likes();
        binding.detailLike.setText(EvolvePostCard.count(likes));
        binding.detailLike.setIconResource(liked ? R.drawable.ic_evolve_favorite : R.drawable.ic_evolve_favorite_border);
        binding.detailLike.setContentDescription(getString(liked ? R.string.evolve_unlike : R.string.evolve_like) + ", "
                + getResources().getQuantityString(R.plurals.evolve_likes_count, (int) Math.min(likes, Integer.MAX_VALUE), EvolvePostCard.count(likes)));
    }

    private void bindSave() {
        boolean saved = Boolean.TRUE.equals(model.saved().getValue());
        binding.detailSave.setIconResource(saved ? R.drawable.ic_evolve_bookmark : R.drawable.ic_evolve_bookmark_border);
        binding.detailSave.setText(saved ? R.string.evolve_unsave : R.string.evolve_save);
    }

    private void bindFollow() {
        boolean following = Boolean.TRUE.equals(model.followingAuthor().getValue());
        binding.detailFollow.setText(following ? R.string.evolve_following : R.string.evolve_follow);
    }

    private EvolveRepository.Callback<Void> failure() {
        return new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
            }

            @Override
            public void onError(@NonNull String message) {
                SketchwareUtil.toastError(getString(R.string.evolve_action_failed, message));
            }
        };
    }

    private void showMenu(View anchor, SharedBlock post) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 1, R.string.evolve_menu_share);
        menu.getMenu().add(0, 2, 2, R.string.evolve_menu_report);
        boolean mine = post.authorUid().equals(model.myUid());
        if (mine) menu.getMenu().add(0, 3, 3, R.string.evolve_menu_edit);
        if (mine || Boolean.TRUE.equals(model.moderator().getValue())) menu.getMenu().add(0, 4, 4, R.string.evolve_menu_delete);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> EvolveActions.share(this, post);
                case 2 -> EvolveActions.report(this, model.myUid(), post);
                case 3 -> startActivity(EvolvePublishActivity.intent(this, post.id()));
                case 4 -> EvolveActions.confirmDelete(this, post, () -> model.delete(new EvolveRepository.Callback<>() {
                    @Override
                    public void onSuccess(Void value) {
                        SketchwareUtil.toast(getString(R.string.evolve_deleted));
                        finish();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        SketchwareUtil.toastError(getString(R.string.evolve_delete_failed, message));
                    }
                }));
                default -> {
                    return false;
                }
            }
            return true;
        });
        menu.show();
    }

    // -------------------------------------------------------------------------------------------------- the reply box

    private void setupReplyBox() {
        binding.replySend.setOnClickListener(v -> send());
        binding.replyCancel.setOnClickListener(v -> setReplyTarget(null));
        binding.replyInput.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                showMentionSuggestions();
            }
        });
    }

    private void setReplyTarget(@Nullable Comment target) {
        replyTo = target;
        binding.replyBanner.setVisibility(target == null ? View.GONE : View.VISIBLE);
        if (target != null) {
            binding.replyBannerText.setText(getString(R.string.evolve_replying_to, target.authorName().isEmpty() ? getString(R.string.evolve_anonymous) : target.authorName()));
            binding.replyInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.replyInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void send() {
        String text = PostRules.clean(String.valueOf(binding.replyInput.getText()));
        switch (PostRules.checkComment(text)) {
            case EMPTY -> {
                SketchwareUtil.toastError(getString(R.string.evolve_comment_empty));
                return;
            }
            case TOO_LONG -> {
                SketchwareUtil.toastError(getString(R.string.evolve_comment_too_long));
                return;
            }
            case SPAM -> {
                SketchwareUtil.toastError(getString(R.string.evolve_comment_spam));
                return;
            }
            default -> {
            }
        }
        binding.replySend.setEnabled(false);
        model.comment(replyTo, text, new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(String id) {
                binding.replySend.setEnabled(true);
                binding.replyInput.setText("");
                setReplyTarget(null);
            }

            @Override
            public void onError(@NonNull String message) {
                binding.replySend.setEnabled(true);
                SketchwareUtil.toastError(getString(R.string.evolve_comment_failed, message));
            }
        });
    }

    /** While "@" is being typed, the people of the discussion whose name starts with what was typed. */
    private void showMentionSuggestions() {
        int caret = Math.max(0, binding.replyInput.getSelectionStart());
        String before = String.valueOf(binding.replyInput.getText()).substring(0, Math.min(caret, binding.replyInput.length()));
        String typing = Mentions.typing(before);
        binding.mentionChips.removeAllViews();
        if (typing == null) {
            binding.mentionScroll.setVisibility(View.GONE);
            return;
        }
        String prefix = typing.toLowerCase(Locale.ROOT);
        for (String name : model.mentionable().keySet()) {
            if (!name.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            Chip chip = new Chip(this);
            chip.setText("@" + name);
            chip.setOnClickListener(v -> insertMention(name, typing));
            binding.mentionChips.addView(chip);
        }
        binding.mentionScroll.setVisibility(binding.mentionChips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void insertMention(String name, String typed) {
        Editable text = binding.replyInput.getText();
        int caret = Math.max(0, binding.replyInput.getSelectionStart());
        int start = caret - typed.length() - 1;
        if (start < 0 || start >= text.length()) return;
        text.replace(start, caret, "@" + name + " ");
        binding.replyInput.setSelection(Math.min(text.length(), start + name.length() + 2));
    }

    // ---------------------------------------------------------------------------------------------- the discussion

    private final class CommentAdapter extends ListAdapter<CommentThreads.Row, CommentAdapter.Holder> {
        private SharedBlock post;
        private Map<String, String> people = Map.of();

        CommentAdapter() {
            super(new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull CommentThreads.Row a, @NonNull CommentThreads.Row b) {
                    return a.comment().id().equals(b.comment().id());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CommentThreads.Row a, @NonNull CommentThreads.Row b) {
                    return a.equals(b);
                }
            });
        }

        void setContext(SharedBlock post, Map<String, String> people) {
            boolean changed = this.post == null || !this.post.authorUid().equals(post.authorUid());
            this.post = post;
            this.people = people;
            if (changed) notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(EvolveCommentItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CommentThreads.Row row = getItem(position);
            Comment comment = row.comment();
            EvolveCommentItemBinding item = holder.item;
            float density = getResources().getDisplayMetrics().density;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) item.commentRoot.getLayoutParams();
            params.setMarginStart(Math.round(row.depth() * 14 * density));
            item.commentRoot.setLayoutParams(params);
            item.commentThreadLine.setVisibility(row.depth() > 0 ? View.VISIBLE : View.GONE);
            item.commentAuthor.setText(comment.authorName().isEmpty() ? getString(R.string.evolve_anonymous) : comment.authorName());
            item.commentTime.setText(comment.createdAt() <= 0 ? "" : DateUtils.getRelativeTimeSpanString(comment.createdAt(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            item.commentText.setText(withMentions(comment.text()));
            item.commentReply.setOnClickListener(v -> setReplyTarget(comment));
            boolean canDelete = comment.authorUid().equals(model.myUid()) || post != null && post.authorUid().equals(model.myUid())
                    || Boolean.TRUE.equals(model.moderator().getValue());
            item.commentDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            item.commentDelete.setOnClickListener(v -> model.deleteComment(comment, new EvolveRepository.Callback<>() {
                @Override
                public void onSuccess(Void value) {
                    SketchwareUtil.toast(getString(R.string.evolve_comment_deleted));
                }

                @Override
                public void onError(@NonNull String message) {
                    SketchwareUtil.toastError(getString(R.string.evolve_action_failed, message));
                }
            }));
        }

        /** The text as plain text, with the {@code @names} of people in the discussion in bold and in the accent colour. */
        private CharSequence withMentions(String text) {
            SpannableString spannable = new SpannableString(text);
            String lower = text.toLowerCase(Locale.ROOT);
            int accent = ContextCompat.getColor(EvolvePostActivity.this, R.color.evolve_accent);
            for (String name : people.keySet()) {
                String needle = "@" + name.toLowerCase(Locale.ROOT);
                for (int at = lower.indexOf(needle); at >= 0; at = lower.indexOf(needle, at + needle.length())) {
                    int end = at + needle.length();
                    boolean wordEnd = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
                    boolean wordStart = at == 0 || !Character.isLetterOrDigit(lower.charAt(at - 1));
                    if (wordStart && wordEnd) {
                        spannable.setSpan(new ForegroundColorSpan(accent), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        spannable.setSpan(new StyleSpan(Typeface.BOLD), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            return spannable;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final EvolveCommentItemBinding item;

            Holder(EvolveCommentItemBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }
    }
}
