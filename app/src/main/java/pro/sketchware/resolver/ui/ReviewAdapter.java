package pro.sketchware.resolver.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.ResolverReviewItemBinding;
import pro.sketchware.resolver.Conflict;
import pro.sketchware.resolver.PlannedChange;
import pro.sketchware.resolver.ResolverFlow;
import pro.sketchware.utility.ThemeUtils;

/** The review: each change to confirm (with a tick), then what can't be applied and what stays unresolved. */
public final class ReviewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onChecked(int index, boolean checked);
    }

    private static final int NOTE = 0;
    private static final int CHANGE = 1;

    private record Row(int type, int index, String text, boolean error) {
    }

    private final Listener listener;
    private ResolverFlow.Review review;
    private final List<Row> rows = new ArrayList<>();

    public ReviewAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setReview(Context context, ResolverFlow.Review review) {
        this.review = review;
        rows.clear();
        if (review != null) {
            rows.add(new Row(NOTE, -1, context.getResources().getQuantityString(R.plurals.resolver_review_intro, review.changes.size(), review.changes.size()), false));
            for (int i = 0; i < review.changes.size(); i++) rows.add(new Row(CHANGE, i, "", false));
            if (!review.conflicts.isEmpty()) {
                rows.add(new Row(NOTE, -1, context.getString(R.string.resolver_review_not_applied), false));
                for (Conflict conflict : review.conflicts) rows.add(new Row(NOTE, -1, conflict.key() + ": " + conflict.message(), true));
            }
            if (!review.unresolved.isEmpty()) {
                rows.add(new Row(NOTE, -1, context.getResources().getQuantityString(R.plurals.resolver_review_unresolved, review.unresolved.size(), review.unresolved.size()), false));
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == NOTE) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.resolver_note_item, parent, false)) {
            };
        }
        return new ChangeHolder(ResolverReviewItemBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.type() == NOTE) {
            TextView text = (TextView) holder.itemView;
            text.setText(row.text());
            text.setTextColor(ThemeUtils.getColor(text, row.error() ? R.attr.colorError : R.attr.colorOnSurfaceVariant));
            return;
        }
        ((ChangeHolder) holder).bind(row.index());
    }

    private final class ChangeHolder extends RecyclerView.ViewHolder {
        private final ResolverReviewItemBinding card;

        ChangeHolder(ResolverReviewItemBinding card) {
            super(card.getRoot());
            this.card = card;
        }

        void bind(int index) {
            PlannedChange change = review.changes.get(index);
            Context context = itemView.getContext();
            card.reviewCheck.setOnCheckedChangeListener(null);
            card.reviewCheck.setChecked(review.checked[index]);
            card.reviewCheck.setContentDescription(change.key());
            card.reviewCheck.setOnCheckedChangeListener((button, checked) -> listener.onChecked(index, checked));
            card.reviewTitle.setText(change.key());
            card.reviewDestination.setText(context.getString(R.string.resolver_review_goes_to, change.path()));
            card.reviewSource.setText(change.kind() == PlannedChange.Kind.VALUE && change.entry() != null
                    ? change.source() + ": " + change.entry().value() : change.source());
            card.reviewNotes.setVisibility(change.notes().isEmpty() ? View.GONE : View.VISIBLE);
            card.reviewNotes.setText(String.join("\n", change.notes()));
            Previews.show(card.reviewPreview, change);
            itemView.setOnClickListener(v -> card.reviewCheck.toggle());
        }
    }
}
