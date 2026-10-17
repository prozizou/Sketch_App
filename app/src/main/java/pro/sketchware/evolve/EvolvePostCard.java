package pro.sketchware.evolve;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.besome.sketch.beans.BlockBean;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import a.a.a.Us;
import pro.sketchware.R;
import pro.sketchware.databinding.EvolvePostItemBinding;

/** Fills one card of the community feed from a post. The texts come from {@link PostTexts}-style helpers kept here. */
public final class EvolvePostCard {

    /** What the card's buttons do; the screen decides. */
    public interface Listener {
        void onOpen(SharedBlock post);

        void onLike(SharedBlock post);

        void onComments(SharedBlock post);

        void onSave(SharedBlock post);

        void onMenu(View anchor, SharedBlock post);
    }

    private EvolvePostCard() {
    }

    @StringRes
    public static int kindBadge(SharedKind kind) {
        return switch (kind) {
            case BLOCKS -> R.string.evolve_blocks_kind_badge;
            case MOREBLOCK -> R.string.evolve_moreblock_kind_badge;
            case VIEW -> R.string.evolve_xml_kind_badge;
            case JAVA -> R.string.evolve_java_kind_badge;
            case KOTLIN -> R.string.evolve_kotlin_kind_badge;
            case COMPONENT -> R.string.evolve_component_kind_badge;
        };
    }

    /** "Oct 15, 2026 · v1.2", the date alone when the post has no version, "" before it was published. */
    public static String dateLine(Context context, SharedBlock post) {
        String date = post.createdAt() > 0 ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(post.createdAt())) : "";
        String version = post.meta().version().isEmpty() ? "" : context.getString(R.string.evolve_version, post.meta().version());
        if (date.isEmpty()) return version;
        return version.isEmpty() ? date : context.getString(R.string.evolve_meta_pair, date, version);
    }

    /** The tags shown on the card: the author's, or for older posts the opcodes or element names. */
    public static List<String> tags(SharedBlock post) {
        return post.meta().tags().isEmpty() ? EvolveCard.tags(post) : post.meta().tags();
    }

    /**
     * What the post needs, in one line, or null: a newer Android than this device (shown first, since then it can't
     * work), else the Android it asks for, else its libraries.
     */
    @Nullable
    public static String notice(Context context, SharedBlock post, int deviceSdk) {
        int minSdk = post.meta().minSdk();
        if (minSdk > deviceSdk) {
            return context.getString(R.string.evolve_requires_sdk_newer, minSdk, deviceSdk);
        }
        if (!post.meta().dependencies().isEmpty()) {
            return context.getString(R.string.evolve_has_dependencies, String.join(", ", post.meta().dependencies()));
        }
        return null;
    }

    public static String count(long number) {
        return NumberFormat.getIntegerInstance().format(Math.max(0, number));
    }

    /**
     * @param blocks the decoded blocks of a block post (null for other kinds or a damaged post)
     * @param liked  whether the signed-in user likes it
     * @param saved  whether the signed-in user saved it
     */
    public static void bind(EvolvePostItemBinding item, SharedBlock post, @Nullable ArrayList<BlockBean> blocks, boolean liked, boolean saved,
                            int deviceSdk, Listener listener) {
        Context context = item.getRoot().getContext();
        item.postAuthor.setText(EvolveCard.authorName(context, post));
        item.postDate.setText(dateLine(context, post));
        item.postKind.setText(kindBadge(post.kind()));
        item.postTitle.setText(post.name());
        item.postDescription.setText(post.description());
        item.postDescription.setVisibility(post.description().isEmpty() ? View.GONE : View.VISIBLE);
        item.postTags.setTags(tags(post));

        bindAvatar(item, post);
        bindContent(item, post, blocks);
        String notice = notice(context, post, deviceSdk);
        item.postNotice.setText(notice);
        item.postNotice.setVisibility(notice == null ? View.GONE : View.VISIBLE);

        item.postLike.setText(count(post.meta().likes()));
        item.postLike.setIconResource(liked ? R.drawable.ic_evolve_favorite : R.drawable.ic_evolve_favorite_border);
        item.postLike.setContentDescription(context.getString(liked ? R.string.evolve_unlike : R.string.evolve_like)
                + ", " + context.getResources().getQuantityString(R.plurals.evolve_likes_count, (int) Math.min(post.meta().likes(), Integer.MAX_VALUE), count(post.meta().likes())));
        item.postComments.setText(count(post.meta().commentCount()));
        item.postComments.setContentDescription(context.getResources().getQuantityString(R.plurals.evolve_comments_count,
                (int) Math.min(post.meta().commentCount(), Integer.MAX_VALUE), count(post.meta().commentCount())));
        item.postSave.setIconResource(saved ? R.drawable.ic_evolve_bookmark : R.drawable.ic_evolve_bookmark_border);
        item.postSave.setContentDescription(context.getString(saved ? R.string.evolve_unsave : R.string.evolve_save));
        item.postDownloads.setText(context.getResources().getQuantityString(R.plurals.evolve_count_downloads,
                (int) Math.min(Math.max(0, post.downloads()), Integer.MAX_VALUE), count(post.downloads())));

        item.getRoot().setOnClickListener(v -> listener.onOpen(post));
        item.postLike.setOnClickListener(v -> listener.onLike(post));
        item.postComments.setOnClickListener(v -> listener.onComments(post));
        item.postSave.setOnClickListener(v -> listener.onSave(post));
        item.postMenu.setOnClickListener(v -> listener.onMenu(v, post));
    }

    private static void bindAvatar(EvolvePostItemBinding item, SharedBlock post) {
        // Avatars are not stored with posts: the profile picture is the generic one until the author's profile is known.
        item.postAvatar.setImageResource(R.drawable.ic_evolve_person);
    }

    private static void bindContent(EvolvePostItemBinding item, SharedBlock post, @Nullable ArrayList<BlockBean> blocks) {
        Context context = item.getRoot().getContext();
        String screenshot = post.meta().screenshots().isEmpty() ? null : post.meta().screenshots().get(0);
        boolean hasScreenshot = screenshot != null && PostRules.isStorageUrl(screenshot);
        if (hasScreenshot) {
            item.postScreenshot.setVisibility(View.VISIBLE);
            Glide.with(context).load(screenshot).override(720, 360).centerCrop().diskCacheStrategy(DiskCacheStrategy.RESOURCE).into(item.postScreenshot);
        } else {
            Glide.with(context).clear(item.postScreenshot);
            item.postScreenshot.setVisibility(View.GONE);
        }
        boolean text = !post.kind().hasBlocks();
        String code = text ? post.payloadText() : "";
        if (text && !code.isEmpty() && !hasScreenshot) {
            item.postCode.setText(HighlightedText.of(context, CodeHighlighter.languageOf(post.kind()), code, HighlightedText.CARD_CHARS));
            item.postCode.setVisibility(View.VISIBLE);
        } else {
            item.postCode.setText("");
            item.postCode.setVisibility(View.GONE);
        }
        item.postPreview.removeAllViews();
        item.postPreview.setVisibility(View.GONE);
        if (!text && blocks != null && !blocks.isEmpty() && !hasScreenshot) {
            try {
                BlockBean first = blocks.get(0);
                View stack = new Us(context, first.type, first.typeName, first.opCode, post.name(), blocks);
                stack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                item.postPreview.addView(stack);
                item.postPreview.setVisibility(View.VISIBLE);
            } catch (RuntimeException e) {
                item.postPreview.removeAllViews();
            }
        }
    }
}
