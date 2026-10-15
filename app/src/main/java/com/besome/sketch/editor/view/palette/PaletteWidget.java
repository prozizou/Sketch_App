package com.besome.sketch.editor.view.palette;

import static pro.sketchware.utility.SketchwareUtil.dpToPx;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.lib.ui.CustomScrollView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.HashMap;

import a.a.a.wB;
import dev.aldi.sayuti.editor.view.palette.IconBadgeView;
import dev.aldi.sayuti.editor.view.palette.IconBottomNavigationView;
import dev.aldi.sayuti.editor.view.palette.IconCardView;
import dev.aldi.sayuti.editor.view.palette.IconCircleImageView;
import dev.aldi.sayuti.editor.view.palette.IconCodeView;
import dev.aldi.sayuti.editor.view.palette.IconCollapsingToolbar;
import dev.aldi.sayuti.editor.view.palette.IconGoogleSignInButton;
import dev.aldi.sayuti.editor.view.palette.IconLottieAnimation;
import dev.aldi.sayuti.editor.view.palette.IconMaterialButton;
import dev.aldi.sayuti.editor.view.palette.IconOTPView;
import dev.aldi.sayuti.editor.view.palette.IconPatternLockView;
import dev.aldi.sayuti.editor.view.palette.IconRadioGroup;
import dev.aldi.sayuti.editor.view.palette.IconRecyclerView;
import dev.aldi.sayuti.editor.view.palette.IconSwipeRefreshLayout;
import dev.aldi.sayuti.editor.view.palette.IconTabLayout;
import dev.aldi.sayuti.editor.view.palette.IconTextInputLayout;
import dev.aldi.sayuti.editor.view.palette.IconViewPager;
import dev.aldi.sayuti.editor.view.palette.IconWaveSideBar;
import dev.aldi.sayuti.editor.view.palette.IconYoutubePlayer;
import mod.agus.jcoderz.editor.view.palette.IconAnalogClock;
import mod.agus.jcoderz.editor.view.palette.IconAutoCompleteTextView;
import mod.agus.jcoderz.editor.view.palette.IconDatePicker;
import mod.agus.jcoderz.editor.view.palette.IconDigitalClock;
import mod.agus.jcoderz.editor.view.palette.IconGridView;
import mod.agus.jcoderz.editor.view.palette.IconMultiAutoCompleteTextView;
import mod.agus.jcoderz.editor.view.palette.IconRadioButton;
import mod.agus.jcoderz.editor.view.palette.IconRatingBar;
import mod.agus.jcoderz.editor.view.palette.IconSearchView;
import mod.agus.jcoderz.editor.view.palette.IconTimePicker;
import mod.agus.jcoderz.editor.view.palette.IconVideoView;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.widgets.IconCustomWidget;
import pro.sketchware.widgets.registry.IconRegistryWidget;
import pro.sketchware.widgets.registry.WidgetSpec;

public class PaletteWidget extends LinearLayout {

    public MaterialCardView cardView;
    private LinearLayout layoutContainer;
    private LinearLayout widgetsContainer;
    private TextView titleLayouts;
    private TextView titleWidgets;
    private CustomScrollView scrollView;
    private String searchQuery = "";
    private boolean layoutsCollapsed;
    private boolean widgetsCollapsed;

    public PaletteWidget(Context context) {
        super(context);
        initialize(context);
    }

    public PaletteWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public void addCustomWidgets(View view) {
        layoutContainer.addView(view);
    }

    public View customWidget(HashMap<String, Object> map) {
        String title = map.get("title").toString();
        String name = map.get("name").toString();
        if (map.get("Class").toString().equals("Layouts")) {
            LinearLayout iconBase;
            Context context = getContext();
            iconBase = new IconCustomWidget(map, context);
            layoutContainer.addView(iconBase);
            return iconBase;
        } else {
            IconBase iconBase;
            Context context = getContext();
            iconBase = new IconCustomWidget(map, context);
            iconBase.setText(title);
            iconBase.setName(name);
            if (map.get("Class").toString().equals("AndroidX")) {
                layoutContainer.addView(iconBase);
            } else {
                widgetsContainer.addView(iconBase);
            }
            return iconBase;
        }
    }

    public View a(PaletteWidget.a layoutType, String tag) {
        LinearLayout layout = switch (layoutType) {
            case a -> new IconLinearHorizontal(getContext());
            case b -> new IconLinearVertical(getContext());
            case c -> new IconScrollViewHorizontal(getContext());
            case d -> new IconScrollViewVertical(getContext());
        };

        if (tag != null && !tag.isEmpty()) {
            layout.setTag(tag);
        }

        layoutContainer.addView(layout);
        return layout;
    }

    public View a(PaletteWidget.b widgetType, String tag, String text, String resourceName) {
        IconBase iconBase;
        switch (widgetType) {
            case a -> iconBase = new IconButton(getContext());
            case c -> iconBase = new IconEditText(getContext());
            case b -> iconBase = new IconTextView(getContext());
            case d -> {
                iconBase = new IconImageView(getContext());
                ((IconImageView) iconBase).setResourceName(resourceName);
            }
            case e -> iconBase = new IconListView(getContext());
            case f -> iconBase = new IconSpinner(getContext());
            case g -> iconBase = new IconCheckBox(getContext());
            case h -> iconBase = new IconWebView(getContext());
            case i -> iconBase = new IconSwitch(getContext());
            case j -> iconBase = new IconSeekBar(getContext());
            case k -> iconBase = new IconCalendarView(getContext());
            case l -> iconBase = new IconAdView(getContext());
            case m -> iconBase = new IconProgressBar(getContext());
            case n -> iconBase = new IconMapView(getContext());
            default -> iconBase = new IconBase(getContext());
        }

        if (tag != null && !tag.isEmpty()) {
            iconBase.setTag(tag);
        }

        iconBase.setText(text);
        iconBase.setName(resourceName);
        widgetsContainer.addView(iconBase);
        return iconBase;
    }

    public void removeWidgetLayouts() {
        layoutContainer.removeAllViews();
    }

    private void initialize(Context context) {
        wB.a(context, this, R.layout.palette_widget);
        layoutContainer = findViewById(R.id.layout);
        widgetsContainer = findViewById(R.id.widget);
        titleLayouts = findViewById(R.id.tv_layout);
        titleWidgets = findViewById(R.id.tv_widget);
        titleLayouts.setText(Helper.getResString(R.string.view_panel_title_layouts));
        titleWidgets.setText(Helper.getResString(R.string.view_panel_title_widgets));
        scrollView = findViewById(R.id.scv);
        cardView = findViewById(R.id.cardView);
        titleLayouts.setOnClickListener(v -> {
            layoutsCollapsed = !layoutsCollapsed;
            updateSection(titleLayouts, layoutContainer, R.string.view_panel_title_layouts, layoutsCollapsed);
        });
        titleWidgets.setOnClickListener(v -> {
            widgetsCollapsed = !widgetsCollapsed;
            updateSection(titleWidgets, widgetsContainer, R.string.view_panel_title_widgets, widgetsCollapsed);
        });
        updateSection(titleLayouts, layoutContainer, R.string.view_panel_title_layouts, false);
        updateSection(titleWidgets, widgetsContainer, R.string.view_panel_title_widgets, false);
    }

    /** Accordion header: the section title, with a chevron on the right; collapsing only hides the section's container. */
    private void updateSection(TextView title, LinearLayout container, int titleRes, boolean collapsed) {
        title.setText(Helper.getResString(titleRes));
        styleHeader(title, title == titleLayouts ? R.drawable.ic_palette_layouts : R.drawable.ic_palette_widgets, collapsed);
        container.setVisibility(collapsed ? View.GONE : View.VISIBLE);
    }

    /** Section header look: an accent icon, the bold title and an up/down chevron at the end. */
    private static void styleHeader(TextView title, int iconRes, boolean collapsed) {
        Context context = title.getContext();
        int size = dpToPx(20);
        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(context, iconRes).mutate();
        icon.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.event_accent));
        icon.setBounds(0, 0, size, size);
        android.graphics.drawable.Drawable chevron = androidx.core.content.ContextCompat.getDrawable(context,
                collapsed ? R.drawable.ic_palette_chevron_down : R.drawable.ic_palette_chevron_up).mutate();
        chevron.setTint(MaterialColors.getColor(title, R.attr.colorOnSurfaceVariant));
        chevron.setBounds(0, 0, size, size);
        title.setCompoundDrawablesRelative(icon, null, chevron, null);
        title.setCompoundDrawablePadding(dpToPx(10));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(MaterialColors.getColor(title, R.attr.colorOnSurface));
        title.setMinHeight(dpToPx(44));
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        title.setPaddingRelative(dpToPx(10), 0, dpToPx(10), 0);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    /** Shows only the widgets whose name contains the query (case-insensitive). Purely visual. */
    public void filter(String query) {
        searchQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        applySectionState(layoutContainer);
        applySectionState(widgetsContainer);
    }

    /** Header of a collapsible sub-section ("AndroidX", "List", ...) inside the layouts/widgets containers. */
    private static final class SectionHeader {
        final String title;
        boolean collapsed;
        /** Widgets in the section, refreshed whenever the palette is laid out again. */
        int count;

        SectionHeader(String title) {
            this.title = title;
        }

        String label() {
            return title;
        }

        /** The count stays off the header (it would not fit) but is read out by screen readers. */
        String description() {
            return title + " (" + count + ")";
        }

        int icon(boolean inLayouts) {
            return inLayouts ? R.drawable.ic_palette_layers : R.drawable.ic_mtrl_category;
        }
    }

    /**
     * Recomputes the visibility of every child of a container from the active search query and the
     * collapsed state of the sub-section header preceding it. While searching, sections are ignored.
     */
    private void applySectionState(LinearLayout container) {
        updateSectionCounts(container);
        boolean collapsed = false;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.getTag() instanceof SectionHeader header) {
                collapsed = header.collapsed;
                child.setVisibility(searchQuery.isEmpty() ? View.VISIBLE : View.GONE);
            } else if (child instanceof com.besome.sketch.lib.base.BaseWidget widget) {
                boolean visible = searchQuery.isEmpty()
                        ? !collapsed
                        : widget.getWidgetName().toLowerCase(java.util.Locale.ROOT).contains(searchQuery);
                child.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }
    }

    /** Counts the widgets under each sub-section header, and puts the count in the header's description. */
    private void updateSectionCounts(LinearLayout container) {
        TextView headerView = null;
        SectionHeader header = null;
        int count = 0;
        for (int i = 0; i <= container.getChildCount(); i++) {
            View child = i < container.getChildCount() ? container.getChildAt(i) : null;
            if (child == null || child.getTag() instanceof SectionHeader) {
                if (header != null) {
                    header.count = count;
                    headerView.setText(header.label());
                    headerView.setContentDescription(header.description());
                    styleHeader(headerView, header.icon(container == layoutContainer), header.collapsed);
                }
                if (child != null) {
                    headerView = (TextView) child;
                    header = (SectionHeader) child.getTag();
                    count = 0;
                }
            } else if (child instanceof com.besome.sketch.lib.base.BaseWidget) {
                count++;
            }
        }
    }

    /**
     * Adds the tile of a registry component, at the end of the layouts or of the widgets.
     *
     * @param intoLayouts whether the component is a container (shown with the layouts)
     */
    public View addRegistryWidget(WidgetSpec spec, boolean intoLayouts) {
        IconBase tile = IconRegistryWidget.create(getContext(), spec);
        (intoLayouts ? layoutContainer : widgetsContainer).addView(tile);
        return tile;
    }

    /** Re-applies the collapsed state of every sub-section; call once the palette is populated. */
    public void refreshSections() {
        applySectionState(layoutContainer);
        applySectionState(widgetsContainer);
    }

    public void removeWidgets() {
        widgetsContainer.removeAllViews();
    }

    public void extraTitle(String title, int targetType) {
        LinearLayout target = targetType == 0 ? layoutContainer : widgetsContainer;

        TextView titleView = new TextView(getContext());
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, dpToPx(4), 0, 0);
        titleView.setLayoutParams(layoutParams);
        var header = new SectionHeader(title);
        header.collapsed = true;
        titleView.setTag(header);
        titleView.setText(header.label());
        styleHeader(titleView, header.icon(targetType == 0), header.collapsed);
        titleView.setOnClickListener(v -> {
            header.collapsed = !header.collapsed;
            titleView.setText(header.label());
            styleHeader(titleView, header.icon(targetType == 0), header.collapsed);
            applySectionState(target);
        });
        target.addView(titleView);
    }

    public View extraWidget(String tag, String title, String name) {
        IconBase iconBase;
        Context context = getContext();
        iconBase = switch (title) {
            case "DatePicker" -> new IconDatePicker(context);
            case "RatingBar" -> new IconRatingBar(context);
            case "SearchView" -> new IconSearchView(context);
            case "DigitalClock" -> new IconDigitalClock(context);
            case "RadioButton" -> new IconRadioButton(context);
            case "GridView" -> new IconGridView(context);
            case "AutoCompleteTextView" -> new IconAutoCompleteTextView(context);
            case "MultiAutoCompleteTextView" -> new IconMultiAutoCompleteTextView(context);
            case "VideoView" -> new IconVideoView(context);
            case "TimePicker" -> new IconTimePicker(context);
            case "AnalogClock" -> new IconAnalogClock(context);
            case "ViewPager" -> new IconViewPager(context);
            case "BadgeView" -> new IconBadgeView(context);
            case "PatternLockView" -> new IconPatternLockView(context);
            case "WaveSideBar" -> new IconWaveSideBar(context);
            case "SignInButton" -> new IconGoogleSignInButton(context);
            case "MaterialButton" -> new IconMaterialButton(context);
            case "CircleImageView" -> new IconCircleImageView(context);
            case "LottieAnimation" -> new IconLottieAnimation(context);
            case "YoutubePlayer" -> new IconYoutubePlayer(context);
            case "OTPView" -> new IconOTPView(context);
            case "CodeView" -> new IconCodeView(context);
            case "RecyclerView" -> new IconRecyclerView(context);
            default -> new IconBase(context);
        };
        if (tag != null && !tag.isEmpty()) {
            iconBase.setTag(tag);
        }

        iconBase.setText(title);
        iconBase.setName(name);
        widgetsContainer.addView(iconBase);
        return iconBase;
    }

    public View extraWidgetLayout(String tag, String name) {
        IconBase iconBase;
        Context context = getContext();
        iconBase = switch (name) {
            case "TabLayout" -> new IconTabLayout(context);
            case "BottomNavigationView" -> new IconBottomNavigationView(context);
            case "CollapsingToolbarLayout" -> new IconCollapsingToolbar(context);
            case "SwipeRefreshLayout" -> new IconSwipeRefreshLayout(context);
            case "RadioGroup" -> new IconRadioGroup(context);
            case "CardView" -> new IconCardView(context);
            case "TextInputLayout" -> new IconTextInputLayout(context);
            case "RelativeLayout" -> new IconRelativeLayout(context);
            default -> new IconBase(context);
        };
        if (tag != null && !tag.isEmpty()) {
            iconBase.setTag(tag);
        }

        layoutContainer.addView(iconBase);
        return iconBase;
    }

    public void setLayoutVisible(int visibility) {
        layoutContainer.setVisibility(visibility == View.VISIBLE && layoutsCollapsed ? View.GONE : visibility);
        titleLayouts.setVisibility(visibility);
    }

    public void setScrollEnabled(boolean scrollEnabled) {
        if (scrollEnabled) {
            scrollView.b();
        } else {
            scrollView.a();
        }
    }

    public void setWidgetVisible(int visibility) {
        widgetsContainer.setVisibility(visibility == View.VISIBLE && widgetsCollapsed ? View.GONE : visibility);
        titleWidgets.setVisibility(visibility);
    }

    public enum a {
        a, //eLinearHorizontal
        b, //eLinearVertical
        c, //eScrollHorizontal
        d //eScrollVertical
    }

    public enum b {
        a, //eButton
        b, //eTextView
        c, //eEditText
        d, //eImageView
        e, //eListView
        f, //eSpinner
        g, //eCheckBox
        h, //eWebView
        i, //eSwitch
        j, //eSeekBar
        k, //eCalenderView
        l, //eAddView
        m, //eProgressBar
        n, //eMapView
        o //eRadioButton
    }
}