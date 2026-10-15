package pro.sketchware.resolver.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.databinding.ResolverIconCellBinding;
import pro.sketchware.databinding.ResolverIconPickerBinding;
import pro.sketchware.resolver.DownloadPolicy;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.IconifyClient;
import pro.sketchware.resolver.OnlineIcon;
import pro.sketchware.resolver.SvgToVector;
import pro.sketchware.resolver.UrlConnectionFetcher;

/**
 * Chooses an icon for one missing resource: from the Material Icons that are on the device, or by searching online.
 * What is chosen is handed over as SVG text; the resolver checks and converts it like any other file.
 */
public class IconPickerSheet extends BottomSheetDialogFragment {
    /** The screen that opened the picker. */
    public interface Listener {
        /** @param label the icon's name ({@code home}, {@code mdi:home}); @param svg its drawing */
        void onIconChosen(String problemKey, String label, byte[] svg, String colour);

        @Nullable
        IconLibrary resolverIcons();

        String resolverIconColour();
    }

    static final String ARG_KEY = "key";
    static final String ARG_NAME = "name";
    private static final int RESULTS = 60;
    private static final long SEARCH_DELAY_MS = 350;

    /** One icon in the grid. */
    private record Cell(String id, String label, @Nullable String style, @Nullable String svg, @Nullable String license, @Nullable String setName) {
    }

    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private ResolverIconPickerBinding binding;
    private final List<Cell> cells = new ArrayList<>();
    private CellAdapter adapter;
    private boolean online;
    private String style = IconLibrary.DEFAULT_STYLE;
    private int generation;
    private final Runnable searchLater = this::search;

    public static IconPickerSheet newInstance(String problemKey, String problemName) {
        IconPickerSheet sheet = new IconPickerSheet();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, problemKey);
        args.putString(ARG_NAME, problemName);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    private Listener listener() {
        Fragment parent = getParentFragment();
        if (parent instanceof Listener l) return l;
        return getActivity() instanceof Listener l ? l : null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ResolverIconPickerBinding.inflate(inflater, container, false);
        // fill the sheet: the grid takes the space between the search and the footer
        binding.getRoot().setMinimumHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.85f));
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        binding.pickerTitle.setText(getString(R.string.resolver_picker_title, args.getString(ARG_NAME, "")));
        Listener listener = listener();
        IconLibrary library = listener == null ? null : listener.resolverIcons();

        binding.pickerTabs.addTab(binding.pickerTabs.newTab().setText(R.string.resolver_tab_online));
        binding.pickerTabs.addTab(binding.pickerTabs.newTab().setText(R.string.resolver_tab_offline));
        // offline first when there is a library: it needs no connection
        online = library == null;
        TabLayout.Tab first = binding.pickerTabs.getTabAt(online ? 0 : 1);
        if (first != null) first.select();

        adapter = new CellAdapter();
        int columns = Math.max(3, (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density / 88));
        binding.pickerGrid.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        binding.pickerGrid.setAdapter(adapter);

        binding.pickerTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                online = tab.getPosition() == 0;
                bindModeControls();
                search();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.pickerSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                main.removeCallbacks(searchLater);
                main.postDelayed(searchLater, SEARCH_DELAY_MS);
            }
        });
        binding.pickerSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                main.removeCallbacks(searchLater);
                search();
                return true;
            }
            return false;
        });

        binding.pickerColors.setOnCheckedStateChangeListener((group, ids) -> {
            if (!ids.isEmpty()) {
                adapter.notifyDataSetChanged();
            }
        });
        buildStyleChips(library);
        bindModeControls();
        search();
    }

    private String colour() {
        int id = binding.pickerColors.getCheckedChipId();
        if (id == R.id.picker_color_white) return "#FFFFFF";
        if (id == R.id.picker_color_gray) return "#757575";
        return "#000000";
    }

    private void buildStyleChips(@Nullable IconLibrary library) {
        binding.pickerStyles.removeAllViews();
        String[] styles = {"baseline", "outline", "round", "sharp", "twotone"};
        for (String s : styles) {
            Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.resolver_style_chip, binding.pickerStyles, false);
            chip.setId(View.generateViewId());
            chip.setText(Describe.styleLabel(requireContext(), s));
            chip.setTag(s);
            chip.setChecked(s.equals(style));
            binding.pickerStyles.addView(chip);
        }
        binding.pickerStyles.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof String s) {
                style = s;
                search();
            }
        });
    }

    private void bindModeControls() {
        // styles are an offline-library idea; online sets each have their own look
        binding.pickerStyles.setVisibility(online ? View.GONE : View.VISIBLE);
        Listener listener = listener();
        binding.pickerFooter.setText(online
                ? getString(R.string.resolver_picker_license_online)
                : getString(R.string.resolver_picker_license_offline));
        if (!online && (listener == null || listener.resolverIcons() == null)) {
            showState(getString(R.string.resolver_picker_offline_missing), null, null);
        }
    }

    private void search() {
        main.removeCallbacks(searchLater);
        int mine = ++generation;
        String query = binding.pickerSearch.getText() == null ? "" : binding.pickerSearch.getText().toString().trim();
        if (online) searchOnline(query, mine);
        else searchOffline(query, mine);
    }

    private void searchOffline(String query, int mine) {
        Listener listener = listener();
        IconLibrary library = listener == null ? null : listener.resolverIcons();
        if (library == null) {
            cells.clear();
            adapter.notifyDataSetChanged();
            showState(getString(R.string.resolver_picker_offline_missing), null, null);
            return;
        }
        showLoading();
        NETWORK.execute(() -> {
            List<Cell> found = new ArrayList<>();
            for (IconLibrary.IconInfo info : library.search(query, RESULTS)) {
                String chosen = info.styles().contains(style) ? style : info.styles().get(0);
                try {
                    found.add(new Cell(info.name(), info.name(), chosen, library.svg(info.name(), chosen), "Apache-2.0", "Material Icons"));
                } catch (IOException e) {
                    // an unreadable icon is left out
                }
            }
            main.post(() -> {
                if (mine != generation || binding == null) return;
                show(found, getString(R.string.resolver_picker_empty));
            });
        });
    }

    private void searchOnline(String query, int mine) {
        if (query.isEmpty()) {
            cells.clear();
            adapter.notifyDataSetChanged();
            Listener listener = listener();
            boolean hasOffline = listener != null && listener.resolverIcons() != null;
            showState(getString(R.string.resolver_picker_start), hasOffline ? getString(R.string.resolver_picker_use_offline) : null, this::selectOffline);
            return;
        }
        showLoading();
        NETWORK.execute(() -> {
            try {
                IconifyClient client = new IconifyClient(new UrlConnectionFetcher(DownloadPolicy.ICON_SERVICE));
                List<OnlineIcon> icons = client.search(query, RESULTS);
                Map<OnlineIcon, String> svgs = client.fetchSvgs(icons);
                List<Cell> found = new ArrayList<>();
                for (OnlineIcon icon : icons) {
                    String svg = svgs.get(icon);
                    if (svg != null) found.add(new Cell(icon.id(), icon.suggestedResourceName(), null, svg, icon.license(), icon.setName()));
                }
                main.post(() -> {
                    if (mine != generation || binding == null) return;
                    show(found, getString(R.string.resolver_picker_empty));
                });
            } catch (IOException e) {
                String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                main.post(() -> {
                    if (mine != generation || binding == null) return;
                    cells.clear();
                    adapter.notifyDataSetChanged();
                    Listener listener = listener();
                    boolean hasOffline = listener != null && listener.resolverIcons() != null;
                    showState(getString(R.string.resolver_picker_error, why), getString(hasOffline ? R.string.resolver_picker_use_offline : R.string.resolver_picker_retry),
                            hasOffline ? this::selectOffline : this::search);
                });
            }
        });
    }

    private void selectOffline() {
        TabLayout.Tab tab = binding.pickerTabs.getTabAt(1);
        if (tab != null) tab.select();
    }

    private void show(List<Cell> found, String emptyText) {
        cells.clear();
        cells.addAll(found);
        adapter.notifyDataSetChanged();
        if (found.isEmpty()) {
            showState(emptyText, null, null);
        } else {
            binding.pickerState.setVisibility(View.GONE);
            binding.pickerGrid.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading() {
        binding.pickerGrid.setVisibility(View.INVISIBLE);
        binding.pickerState.setVisibility(View.VISIBLE);
        binding.pickerProgress.setVisibility(View.VISIBLE);
        binding.pickerStateText.setText(R.string.resolver_picker_loading);
        binding.pickerStateAction.setVisibility(View.GONE);
    }

    private void showState(String text, @Nullable String action, @Nullable Runnable onAction) {
        binding.pickerGrid.setVisibility(View.INVISIBLE);
        binding.pickerState.setVisibility(View.VISIBLE);
        binding.pickerProgress.setVisibility(View.GONE);
        binding.pickerStateText.setText(text);
        binding.pickerStateAction.setVisibility(action == null ? View.GONE : View.VISIBLE);
        if (action != null) {
            binding.pickerStateAction.setText(action);
            binding.pickerStateAction.setOnClickListener(v -> onAction.run());
        }
    }

    private void choose(Cell cell) {
        Listener listener = listener();
        if (listener == null || cell.svg() == null) return;
        listener.onIconChosen(requireArguments().getString(ARG_KEY, ""), cell.label(), cell.svg().getBytes(StandardCharsets.UTF_8), colour());
        dismiss();
    }

    @Override
    public void onDestroyView() {
        main.removeCallbacksAndMessages(null);
        generation++;
        binding = null;
        super.onDestroyView();
    }

    private final class CellAdapter extends RecyclerView.Adapter<CellAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ResolverIconCellBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(cells.get(position));
        }

        @Override
        public int getItemCount() {
            return cells.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            private final ResolverIconCellBinding cell;

            Holder(ResolverIconCellBinding cell) {
                super(cell.getRoot());
                this.cell = cell;
            }

            void bind(Cell data) {
                cell.cellName.setText(data.label());
                cell.cellPreview.clear();
                try {
                    cell.cellPreview.showVector(SvgToVector.convert(data.svg(), colour()));
                } catch (SvgToVector.Unsupported | RuntimeException e) {
                    cell.cellPreview.clear();
                }
                String line = data.setName() == null ? data.label() : data.label() + ", " + data.setName() + ", " + data.license();
                itemView.setContentDescription(line);
                itemView.setOnClickListener(v -> choose(data));
            }
        }
    }
}
