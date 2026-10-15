package pro.sketchware.resolver.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.databinding.ResolverItemBinding;
import pro.sketchware.databinding.ResolverSuggestionBinding;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.MissingResource;
import pro.sketchware.resolver.PlannedChange;
import pro.sketchware.resolver.ResolverSession;
import pro.sketchware.resolver.ResourceTypes;
import pro.sketchware.resolver.Suggestion;
import pro.sketchware.utility.ThemeUtils;

/** The list of missing resources, one card each, in the state the {@link ResolverSession} says. */
public final class ProblemAdapter extends RecyclerView.Adapter<ProblemAdapter.Holder> {
    /** What the cards can ask for. */
    public interface Actions {
        void onUseSuggestion(MissingResource problem, Suggestion suggestion);

        void onLocal(MissingResource problem);

        void onOnline(MissingResource problem);

        /** The person typed a value and tapped Use. */
        void onTyped(MissingResource problem, String text);

        void onRemove(MissingResource problem);

        void onOpenLocation(MissingResource problem, MissingResource.Occurrence occurrence);
    }

    private static final int MAX_SUGGESTIONS = 3;

    private final ResolverSession session;
    @Nullable
    private final IconLibrary library;
    private final Actions actions;
    private List<MissingResource> items = new ArrayList<>();
    private final Map<String, String> drafts = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();

    public ProblemAdapter(ResolverSession session, @Nullable IconLibrary library, Actions actions) {
        this.session = session;
        this.library = library;
        this.actions = actions;
        setHasStableIds(true);
    }

    public void setItems(List<MissingResource> problems) {
        items = new ArrayList<>(problems);
        notifyDataSetChanged();
    }

    /** @return the position of the card for {@code key} ({@code drawable/ic_add}), or -1 */
    public int indexOf(String key) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) return i;
        return -1;
    }

    /** Redraws one card (after it was chosen, cleared or refused). */
    public void refresh(MissingResource problem) {
        int index = items.indexOf(problem);
        if (index >= 0) notifyItemChanged(index);
    }

    /** Shows why a typed value or a file was refused, under the card's input. */
    public void showError(MissingResource problem, @Nullable String message) {
        if (message == null) errors.remove(problem.key());
        else errors.put(problem.key(), message);
        refresh(problem);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key().hashCode();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ResolverItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    public final class Holder extends RecyclerView.ViewHolder {
        private final ResolverItemBinding card;
        @Nullable
        private MissingResource current;
        private boolean binding;

        Holder(ResolverItemBinding card) {
            super(card.getRoot());
            this.card = card;
            card.itemValue.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (binding || current == null) return;
                    drafts.put(current.key(), s.toString());
                    errors.remove(current.key());
                    card.itemValueLayout.setError(null);
                    updateSwatch(current, s.toString());
                    card.itemValueUse.setEnabled(!s.toString().isBlank());
                }
            });
            card.itemValue.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    card.itemValueUse.performClick();
                    return true;
                }
                return false;
            });
        }

        void bind(MissingResource problem) {
            binding = true;
            current = problem;
            Context context = itemView.getContext();
            ResolverSession.Status status = session.status(problem);
            card.itemType.setText(problem.type());
            card.itemName.setText(problem.name());
            bindStatus(context, status);
            bindLocation(context, problem);

            boolean fixable = problem.fixable();
            boolean open = status == ResolverSession.Status.SUGGESTED || status == ResolverSession.Status.NEEDS_INPUT;
            card.itemReason.setVisibility(fixable ? View.GONE : View.VISIBLE);
            card.itemReason.setText(Describe.reason(context, problem));
            bindChoice(context, problem, status);
            bindSuggestions(context, problem, open);
            boolean valueType = ResourceTypes.isValue(problem.type());
            card.itemValueRow.setVisibility(open && valueType ? View.VISIBLE : View.GONE);
            if (open && valueType) {
                String draft = drafts.getOrDefault(problem.key(), "");
                card.itemValueLayout.setHint(Describe.valueHint(problem.type()));
                card.itemValue.setText(draft);
                card.itemValueUse.setEnabled(!draft.isBlank());
                card.itemValueLayout.setError(errors.get(problem.key()));
                updateSwatch(problem, draft);
            }
            card.itemSources.setVisibility(open && ResourceTypes.isFile(problem.type()) ? View.VISIBLE : View.GONE);
            if (open && ResourceTypes.isFile(problem.type()) && errors.containsKey(problem.key())) {
                card.itemReason.setVisibility(View.VISIBLE);
                card.itemReason.setText(errors.get(problem.key()));
                card.itemReason.setTextColor(ThemeUtils.getColor(itemView, R.attr.colorError));
            } else if (fixable) {
                card.itemReason.setTextColor(ThemeUtils.getColor(itemView, R.attr.colorOnSurfaceVariant));
            }

            card.itemLocal.setOnClickListener(v -> actions.onLocal(problem));
            card.itemOnline.setOnClickListener(v -> actions.onOnline(problem));
            card.itemValueUse.setOnClickListener(v -> actions.onTyped(problem, card.itemValue.getText() == null ? "" : card.itemValue.getText().toString()));
            card.itemChoiceRemove.setOnClickListener(v -> actions.onRemove(problem));
            card.itemChoiceRemove.setContentDescription(context.getString(R.string.resolver_choice_remove_description, problem.key()));
            binding = false;
        }

        private void bindStatus(Context context, ResolverSession.Status status) {
            int text;
            int color;
            int icon = 0;
            switch (status) {
                case CHOSEN -> {
                    text = R.string.resolver_status_chosen;
                    color = ThemeUtils.getColor(itemView, R.attr.colorCoolGreen);
                    icon = R.drawable.ic_mtrl_check;
                }
                case APPLIED -> {
                    text = R.string.resolver_status_applied;
                    color = ThemeUtils.getColor(itemView, R.attr.colorCoolGreen);
                    icon = R.drawable.ic_mtrl_check;
                }
                case SUGGESTED -> {
                    text = R.string.resolver_status_suggested;
                    color = context.getColor(R.color.evolve_accent);
                }
                case NEEDS_INPUT -> {
                    text = R.string.resolver_status_needs;
                    color = ThemeUtils.getColor(itemView, R.attr.colorAmber);
                }
                default -> {
                    text = R.string.resolver_status_unfixable;
                    color = ThemeUtils.getColor(itemView, R.attr.colorOnSurfaceVariant);
                }
            }
            card.itemStatus.setText(text);
            card.itemStatus.setTextColor(color);
            if (icon != 0) {
                android.graphics.drawable.Drawable drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, icon).mutate();
                int size = (int) (14 * context.getResources().getDisplayMetrics().density);
                drawable.setBounds(0, 0, size, size);
                drawable.setTintList(ColorStateList.valueOf(color));
                card.itemStatus.setCompoundDrawablesRelative(drawable, null, null, null);
            } else {
                card.itemStatus.setCompoundDrawablesRelative(null, null, null, null);
            }
        }

        private void bindLocation(Context context, MissingResource problem) {
            List<MissingResource.Occurrence> places = problem.distinctLocations();
            MissingResource.Occurrence first = places.isEmpty() ? null : places.get(0);
            if (first == null || first.path().isEmpty()) {
                card.itemLocation.setVisibility(View.GONE);
                return;
            }
            String where = first.line() > 0
                    ? context.getString(R.string.resolver_location, Describe.shortPath(first.path()), first.line())
                    : Describe.shortPath(first.path());
            card.itemLocation.setText(places.size() > 1
                    ? context.getResources().getQuantityString(R.plurals.resolver_location_more, places.size() - 1, where, places.size() - 1)
                    : where);
            card.itemLocation.setContentDescription(context.getString(R.string.resolver_open_file, where));
            card.itemLocation.setVisibility(View.VISIBLE);
            card.itemLocation.setOnClickListener(v -> actions.onOpenLocation(problem, first));
        }

        private void bindChoice(Context context, MissingResource problem, ResolverSession.Status status) {
            PlannedChange change = status == ResolverSession.Status.CHOSEN ? session.plannedFor(problem) : null;
            card.itemChoice.setVisibility(change == null ? View.GONE : View.VISIBLE);
            if (change == null) return;
            card.itemChoiceTitle.setText(change.source());
            card.itemChoiceDestination.setText("res/" + change.path());
            Previews.show(card.itemChoicePreview, change);
        }

        private void bindSuggestions(Context context, MissingResource problem, boolean open) {
            card.itemSuggestions.removeAllViews();
            List<Suggestion> suggestions = open ? session.suggestions(problem) : List.of();
            card.itemSuggestions.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(context);
            for (int i = 0; i < Math.min(MAX_SUGGESTIONS, suggestions.size()); i++) {
                Suggestion suggestion = suggestions.get(i);
                ResolverSuggestionBinding row = ResolverSuggestionBinding.inflate(inflater, card.itemSuggestions, false);
                row.suggestionTitle.setText(Describe.suggestionTitle(context, suggestion));
                row.suggestionDetail.setText(Describe.suggestionDetail(context, suggestion));
                Previews.show(row.suggestionPreview, library, session.iconColour(), suggestion);
                row.suggestionUse.setOnClickListener(v -> actions.onUseSuggestion(problem, suggestion));
                card.itemSuggestions.addView(row.getRoot());
            }
        }

        private void updateSwatch(MissingResource problem, String text) {
            boolean color = problem.type().equals("color");
            int argb = parseColor(text);
            card.itemValueSwatch.setVisibility(color && argb != 0 ? View.VISIBLE : View.GONE);
            if (color && argb != 0) {
                GradientDrawable swatch = new GradientDrawable();
                swatch.setColor(argb);
                swatch.setCornerRadius(8 * itemView.getResources().getDisplayMetrics().density);
                swatch.setStroke((int) itemView.getResources().getDisplayMetrics().density, 0x33808080);
                card.itemValueSwatch.setBackground(swatch);
            }
        }
    }

    /** The colour of a {@code #RRGGBB} or {@code #AARRGGBB} text, 0 when it isn't one. */
    static int parseColor(String text) {
        String t = text == null ? "" : text.trim();
        if (t.matches("#[0-9A-Fa-f]{6}")) return (int) (0xFF000000L | Long.parseLong(t.substring(1), 16));
        if (t.matches("#[0-9A-Fa-f]{8}")) return (int) Long.parseLong(t.substring(1), 16);
        return 0;
    }
}
