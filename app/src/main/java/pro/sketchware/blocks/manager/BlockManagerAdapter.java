package pro.sketchware.blocks.manager;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.ItemBlockPaletteBinding;
import pro.sketchware.databinding.ItemBlockResultBinding;
import pro.sketchware.databinding.ItemBlockSectionBinding;
import pro.sketchware.utility.PropertiesUtil;

/** Rows of the Block Manager: section titles, palettes and the blocks a search finds. */
public final class BlockManagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Row {
    }

    public record Section(String title) implements Row {
    }

    public record Palette(PaletteListing.PaletteRow row, boolean selected, boolean reorderable) implements Row {
    }

    public record Block(PaletteListing.BlockRow row, String color) implements Row {
    }

    public interface Listener {
        void onPaletteClick(PaletteListing.PaletteRow row);

        void onPaletteLongClick(PaletteListing.PaletteRow row);

        void onPaletteMenu(PaletteListing.PaletteRow row, View anchor);

        void onStartDrag(RecyclerView.ViewHolder holder);

        void onBlockClick(PaletteListing.BlockRow row);
    }

    public static final int TYPE_SECTION = 0;
    public static final int TYPE_PALETTE = 1;
    public static final int TYPE_BLOCK = 2;

    private final Listener listener;
    private List<Row> rows = new ArrayList<>();

    public BlockManagerAdapter(Listener listener) {
        this.listener = listener;
    }

    public List<Row> rows() {
        return rows;
    }

    /** Shows new rows, animating only what changed (computed right away: the drag and drop needs it in step). */
    public void submit(List<Row> newRows) {
        List<Row> oldRows = rows;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldRows.size();
            }

            @Override
            public int getNewListSize() {
                return newRows.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return sameItem(oldRows.get(oldPosition), newRows.get(newPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return oldRows.get(oldPosition).equals(newRows.get(newPosition));
            }
        });
        rows = new ArrayList<>(newRows);
        diff.dispatchUpdatesTo(this);
    }

    /** Moves a row while it is dragged. */
    public void move(int from, int to) {
        rows.add(to, rows.remove(from));
        notifyItemMoved(from, to);
    }

    static boolean sameItem(Row a, Row b) {
        if (a instanceof Section sa && b instanceof Section sb) return sa.title().equals(sb.title());
        if (a instanceof Palette pa && b instanceof Palette pb) {
            // Palettes have no id: the same index with the same name is the same palette.
            return pa.row().index() == pb.row().index() && pa.row().name().equals(pb.row().name());
        }
        if (a instanceof Block ba && b instanceof Block bb) return ba.row().name().equals(bb.row().name());
        return false;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        Row row = getItem(position);
        if (row instanceof Section) return TYPE_SECTION;
        if (row instanceof Palette) return TYPE_PALETTE;
        return TYPE_BLOCK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return switch (viewType) {
            case TYPE_SECTION -> new SectionHolder(ItemBlockSectionBinding.inflate(inflater, parent, false));
            case TYPE_PALETTE -> new PaletteHolder(ItemBlockPaletteBinding.inflate(inflater, parent, false));
            default -> new BlockHolder(ItemBlockResultBinding.inflate(inflater, parent, false));
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof SectionHolder h && row instanceof Section s) {
            h.binding.title.setText(s.title());
        } else if (holder instanceof PaletteHolder h && row instanceof Palette p) {
            h.bind(p);
        } else if (holder instanceof BlockHolder h && row instanceof Block b) {
            h.bind(b);
        }
    }

    /** "12 blocks · Edited 5 min ago · Folder", without the parts that aren't known. */
    static String details(View view, PaletteListing.PaletteRow row, long now) {
        StringBuilder text = new StringBuilder(view.getResources().getQuantityString(R.plurals.bm_blocks_count, row.blocks(), row.blocks()));
        if (row.modified() > 0) {
            CharSequence when = DateUtils.getRelativeTimeSpanString(row.modified(), now, DateUtils.MINUTE_IN_MILLIS);
            text.append(" · ").append(view.getContext().getString(R.string.bm_edited, when));
        }
        if (!row.folder().isEmpty()) text.append(" · ").append(row.folder());
        return text.toString();
    }

    static int parseColor(String color) {
        try {
            return PropertiesUtil.parseColor(color);
        } catch (RuntimeException e) {
            return Color.GRAY;
        }
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        final ItemBlockSectionBinding binding;

        SectionHolder(ItemBlockSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            androidx.core.view.ViewCompat.setAccessibilityHeading(binding.title, true);
        }
    }

    final class PaletteHolder extends RecyclerView.ViewHolder {
        final ItemBlockPaletteBinding binding;
        private PaletteListing.PaletteRow row;

        @SuppressLint("ClickableViewAccessibility")
        PaletteHolder(ItemBlockPaletteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.card.setOnClickListener(v -> listener.onPaletteClick(row));
            binding.card.setOnLongClickListener(v -> {
                listener.onPaletteLongClick(row);
                return true;
            });
            binding.more.setOnClickListener(v -> listener.onPaletteMenu(row, binding.more));
            binding.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) listener.onStartDrag(this);
                return false;
            });
        }

        void bind(Palette item) {
            row = item.row();
            int color = parseColor(row.color());
            binding.name.setText(row.name());
            binding.details.setText(details(binding.getRoot(), row, System.currentTimeMillis()));
            binding.swatch.setBackgroundTintList(ColorStateList.valueOf(color));
            // The block icon stays readable on light and dark palette colors.
            binding.swatchIcon.setImageTintList(ColorStateList.valueOf(
                    ColorUtils.calculateLuminance(color | 0xFF000000) > 0.5 ? Color.BLACK : Color.WHITE));
            binding.favorite.setVisibility(row.favorite() ? View.VISIBLE : View.GONE);
            binding.card.setChecked(item.selected());
            binding.dragHandle.setVisibility(item.reorderable() ? View.VISIBLE : View.GONE);
            binding.dragHandle.setContentDescription(binding.getRoot().getContext().getString(R.string.bm_drag_handle, row.name()));
            binding.more.setContentDescription(binding.getRoot().getContext().getString(R.string.bm_more_options, row.name()));
        }
    }

    final class BlockHolder extends RecyclerView.ViewHolder {
        final ItemBlockResultBinding binding;
        private PaletteListing.BlockRow row;

        BlockHolder(ItemBlockResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> listener.onBlockClick(row));
        }

        void bind(Block item) {
            row = item.row();
            binding.name.setText(row.name());
            binding.spec.setText(row.spec());
            binding.palette.setText(row.inRecycleBin()
                    ? binding.getRoot().getContext().getString(R.string.bm_in_recycle_bin) : row.paletteName());
            binding.color.setBackgroundColor(parseColor(item.color()));
        }
    }

}
