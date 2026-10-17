package pro.sketchware.evolve;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import pro.sketchware.databinding.EvolvePostItemBinding;

/** The feed's list: DiffUtil moves only what changed, so a like or a new comment redraws one card, not the page. */
public final class EvolvePostAdapter extends ListAdapter<SharedBlock, EvolvePostAdapter.Holder> {

    private static final DiffUtil.ItemCallback<SharedBlock> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull SharedBlock a, @NonNull SharedBlock b) {
            return a.id().equals(b.id());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SharedBlock a, @NonNull SharedBlock b) {
            return a.equals(b);
        }
    };

    private final EvolvePostCard.Listener listener;
    private final Map<String, ArrayList<BlockBean>> decoded = new HashMap<>();
    private Set<String> liked = new HashSet<>();
    private Set<String> saved = new HashSet<>();

    public EvolvePostAdapter(EvolvePostCard.Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** The posts the user likes and saved: a card whose state changed is redrawn, the others are not. */
    public void setMarks(Set<String> liked, Set<String> saved) {
        Set<String> changed = new HashSet<>(this.liked);
        changed.addAll(liked);
        Set<String> same = new HashSet<>(this.liked);
        same.retainAll(liked);
        changed.removeAll(same);
        Set<String> savedChanged = new HashSet<>(this.saved);
        savedChanged.addAll(saved);
        Set<String> savedSame = new HashSet<>(this.saved);
        savedSame.retainAll(saved);
        savedChanged.removeAll(savedSame);
        this.liked = new HashSet<>(liked);
        this.saved = new HashSet<>(saved);
        changed.addAll(savedChanged);
        for (int i = 0; i < getItemCount(); i++) {
            if (changed.contains(getItem(i).id())) notifyItemChanged(i);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(EvolvePostItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SharedBlock post = getItem(position);
        holder.bind(post, decode(post));
    }

    private ArrayList<BlockBean> decode(SharedBlock post) {
        if (!post.kind().hasBlocks()) return null;
        if (!decoded.containsKey(post.id())) {
            decoded.put(post.id(), SharedBlockCodec.decode(post.blocksJson()));
        }
        return decoded.get(post.id());
    }

    public final class Holder extends RecyclerView.ViewHolder {
        private final EvolvePostItemBinding item;

        Holder(EvolvePostItemBinding item) {
            super(item.getRoot());
            this.item = item;
        }

        void bind(SharedBlock post, ArrayList<BlockBean> blocks) {
            EvolvePostCard.bind(item, post, blocks, liked.contains(post.id()), saved.contains(post.id()), Build.VERSION.SDK_INT, listener);
        }
    }
}
