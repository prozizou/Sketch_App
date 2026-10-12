package pro.sketchware.blocks.editor;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.ItemBlockParamBinding;

/** The parameters of the block, derived from its spec, with their type and whether the code uses them. */
public final class ParamAdapter extends RecyclerView.Adapter<ParamAdapter.Holder> {
    /** @param number 1-based place of the parameter, the number the code uses with {@code %N$s} */
    public record Item(int number, String label, String spec, boolean usedInCode) {
    }

    public interface Listener {
        void onChangeType(int index);

        void onMenu(int index, View anchor);

        void onStartDrag(RecyclerView.ViewHolder holder);
    }

    private final Listener listener;
    private List<Item> items = new ArrayList<>();

    public ParamAdapter(Listener listener) {
        this.listener = listener;
    }

    public List<Item> items() {
        return items;
    }

    public void submit(List<Item> newItems) {
        List<Item> old = items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int o, int n) {
                return old.get(o).number() == newItems.get(n).number();
            }

            @Override
            public boolean areContentsTheSame(int o, int n) {
                return old.get(o).equals(newItems.get(n));
            }
        });
        items = new ArrayList<>(newItems);
        diff.dispatchUpdatesTo(this);
    }

    /** Moves a row while it is dragged (the spec changes when the drop ends). */
    public void move(int from, int to) {
        items.add(to, items.remove(from));
        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBlockParamBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemBlockParamBinding binding;

        @SuppressLint("ClickableViewAccessibility")
        Holder(ItemBlockParamBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onChangeType(position);
            });
            binding.more.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onMenu(position, binding.more);
            });
            binding.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) listener.onStartDrag(this);
                return false;
            });
        }

        void bind(Item item) {
            android.content.Context context = binding.getRoot().getContext();
            binding.title.setText(context.getString(R.string.be_param_title, item.number(), item.label()));
            binding.details.setText(item.spec() + "  ·  " + context.getString(item.usedInCode()
                    ? R.string.be_param_used : R.string.be_param_unused));
            binding.dragHandle.setContentDescription(context.getString(R.string.be_param_drag, item.number()));
            binding.more.setContentDescription(context.getString(R.string.be_param_more, item.number()));
        }
    }
}
