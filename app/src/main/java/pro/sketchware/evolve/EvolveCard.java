package pro.sketchware.evolve;

import android.content.Context;
import android.content.res.Resources;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.besome.sketch.beans.BlockBean;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Us;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.databinding.EvolveItemBinding;

/** The texts of an Evolve card, worked out from the entry alone, in the user's language and number format. */
public final class EvolveCard {
    private static final Pattern XML_ELEMENT = Pattern.compile("<([A-Za-z_][\\w.:-]*)");

    private EvolveCard() {
    }

    /** "by Name · Oct 15, 2026" (no date before the entry was published). */
    public static String author(Context context, SharedBlock block) {
        String by = context.getString(R.string.evolve_by, authorName(context, block));
        if (block.createdAt() <= 0) return by;
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(block.createdAt()));
        return context.getString(R.string.evolve_meta_pair, by, date);
    }

    public static String authorName(Context context, SharedBlock block) {
        return block.authorName().isEmpty() ? context.getString(R.string.evolve_anonymous) : block.authorName();
    }

    /** "1 block · 2 downloads", "12 elements · 1,204 downloads": plural forms of the user's language. */
    public static String meta(Context context, SharedBlock block) {
        Resources res = context.getResources();
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        int size = Math.max(0, block.blockCount());
        long downloads = Math.max(0, block.downloads());
        String count = res.getQuantityString(block.kind() == SharedKind.VIEW ? R.plurals.evolve_count_elements : R.plurals.evolve_count_blocks,
                size, numbers.format(size));
        String loads = res.getQuantityString(R.plurals.evolve_count_downloads, (int) Math.min(downloads, Integer.MAX_VALUE),
                numbers.format(downloads));
        return context.getString(R.string.evolve_meta_pair, count, loads);
    }

    /** The technical tags of the footer: the opcodes of a stack, the element names of a layout. */
    public static List<String> tags(SharedBlock block) {
        if (block.kind() != SharedKind.VIEW) {
            return new ArrayList<>(block.opCodes());
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = XML_ELEMENT.matcher(block.xml());
        while (matcher.find()) {
            String name = matcher.group(1);
            names.add(name.substring(name.lastIndexOf('.') + 1));
        }
        return new ArrayList<>(names);
    }

    /**
     * What the entry needs from the project, or the risk of importing it, in one line; null when there is nothing to
     * say. A newer app version comes first: then even the variables listed may not be all.
     */
    @Nullable
    public static String needs(Context context, SharedBlock block, @Nullable List<BlockBean> blocks) {
        if (EvolveChecks.madeWithNewerApp(block.appVersion(), BuildConfig.VERSION_CODE)) {
            return context.getString(R.string.evolve_newer_app, block.appVersion());
        }
        if (blocks == null) return null;
        List<String> variables = EvolveChecks.variables(blocks);
        if (variables.isEmpty()) return null;
        return context.getResources().getQuantityString(R.plurals.evolve_needs_variables, variables.size(),
                variables.size(), String.join(", ", variables));
    }

    /** Whether the entry writes Java straight into the app: flagged on the entry, or found in its blocks. */
    public static boolean usesRawCode(SharedBlock block, @Nullable List<BlockBean> blocks) {
        return block.usesRawCode() || blocks != null && SharedBlockRules.usesRawCode(SharedBlockCodec.opCodes(blocks));
    }

    /** What a screen reader says for the whole card: kind, name, author and date, size and downloads. */
    public static String description(Context context, String kind, SharedBlock block) {
        return context.getString(R.string.evolve_card_description, kind, block.name(), author(context, block), meta(context, block));
    }

    /**
     * Fills one card. Business logic stays with the caller: {@code onPick} and {@code onMenu} are what the sheet does.
     *
     * @param blocks the entry's decoded blocks (null for a layout, or when the entry is damaged)
     * @param kind   the badge text of the entry's kind
     */
    public static void bind(EvolveItemBinding item, SharedBlock block, @Nullable ArrayList<BlockBean> blocks, @StringRes int kind,
                            Runnable onPick, java.util.function.Consumer<View> onMenu) {
        Context context = item.getRoot().getContext();
        boolean isXml = block.kind() == SharedKind.VIEW;
        String kindLabel = context.getString(kind);
        item.itemKind.setText(kindLabel);
        item.itemName.setText(block.name());
        item.itemAuthor.setText(author(context, block));
        item.itemMeta.setText(meta(context, block));
        item.getRoot().setContentDescription(description(context, kindLabel, block));
        item.itemDescription.setText(block.description());
        item.itemDescription.setVisibility(block.description().isEmpty() ? View.GONE : View.VISIBLE);
        item.itemOpcodes.setTags(tags(block));
        item.itemXml.setText(isXml ? block.xml() : "");
        item.itemXml.setVisibility(isXml && !block.xml().isEmpty() ? View.VISIBLE : View.GONE);
        boolean damaged = !isXml && blocks == null;
        // A layout shows its XML instead of a drawn stack.
        boolean drawn = !isXml && bindPreview(item.itemPreview, block, blocks);
        if (isXml) {
            item.itemPreview.removeAllViews();
            item.itemPreview.setVisibility(View.GONE);
        }
        item.itemPreview.setContentDescription(context.getString(R.string.evolve_preview_description, block.name()));
        // A stack the editor can't draw still says so; a damaged one says why it can't be imported.
        item.itemPreviewMissing.setText(damaged ? R.string.evolve_damaged : R.string.evolve_preview_unavailable);
        item.itemPreviewMissing.setVisibility(!isXml && !drawn ? View.VISIBLE : View.GONE);
        String needs = needs(context, block, blocks);
        item.itemNeeds.setText(needs);
        item.itemNeeds.setVisibility(needs == null ? View.GONE : View.VISIBLE);
        item.itemRawBadge.setVisibility(usesRawCode(block, blocks) ? View.VISIBLE : View.GONE);
        item.itemPick.setEnabled(!damaged);
        item.itemPick.setContentDescription(context.getString(R.string.evolve_pick_description, block.name()));
        item.itemPick.setOnClickListener(v -> onPick.run());
        item.itemMenu.setOnClickListener(onMenu::accept);
    }

    /**
     * Draws the stack like the drawer does, scaled to the card by {@link PreviewFrame}.
     *
     * @return whether a preview is shown (otherwise the card says there is none)
     */
    private static boolean bindPreview(PreviewFrame holder, SharedBlock block, @Nullable ArrayList<BlockBean> blocks) {
        holder.removeAllViews();
        holder.setVisibility(View.GONE);
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        try {
            BlockBean first = blocks.get(0);
            View stack = new Us(holder.getContext(), first.type, first.typeName, first.opCode, block.name(), blocks);
            stack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            holder.addView(stack);
            holder.setVisibility(View.VISIBLE);
            return true;
        } catch (RuntimeException e) {
            holder.removeAllViews();
            return false;
        }
    }
}
