package com.besome.sketch.design;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.besome.sketch.editor.view.LandscapeWorkspace;
import com.besome.sketch.editor.view.SidebarWidth;
import com.besome.sketch.editor.view.ViewProperty;

/**
 * Moves the properties editor between its two homes: a sheet under the preview (portrait, as always) and, in
 * the landscape workspace, a resizable column to the right of the preview. The activity handles rotation
 * itself, so this runs again on every configuration change instead of relying on a landscape layout file.
 */
final class PropertyDock {
    private static final String PREFS = "view_editor_ui";

    private final Context context;
    private final ViewGroup floatingParent;
    private final LinearLayout row;
    private final View handle;
    private final ViewProperty property;
    private final ViewGroup.LayoutParams floatingParams;
    private final SharedPreferences prefs;
    private final float dip;
    private float widthDp;
    private boolean collapsed;
    private boolean docked;
    private boolean tabVisible = true;
    private int screenWidthDp = 800;

    PropertyDock(ViewGroup floatingParent, LinearLayout row, View handle, ViewProperty property) {
        this.context = property.getContext();
        this.floatingParent = floatingParent;
        this.row = row;
        this.handle = handle;
        this.property = property;
        this.floatingParams = property.getLayoutParams();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.dip = context.getResources().getDisplayMetrics().density;
        this.widthDp = prefs.getFloat("properties_width_dp", LandscapeWorkspace.PROPERTIES_DEFAULT_DP);
        this.collapsed = prefs.getBoolean("properties_collapsed", false);
        setupResize();
    }

    boolean isDocked() {
        return docked;
    }

    /** Called on creation and on every rotation. */
    void apply(Configuration configuration) {
        screenWidthDp = configuration.screenWidthDp;
        boolean landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean wanted = LandscapeWorkspace.isEnabled(landscape, configuration.screenWidthDp);
        if (wanted && !docked) {
            floatingParent.removeView(property);
            property.setTranslationY(0f);
            row.addView(property, new LinearLayout.LayoutParams(widthPx(), ViewGroup.LayoutParams.MATCH_PARENT));
            property.setSidePanel(true);
            docked = true;
        } else if (!wanted && docked) {
            row.removeView(property);
            property.setSidePanel(false);
            floatingParent.addView(property, floatingParams);
            // Back under the preview the sheet starts hidden, until a widget is selected
            property.setTranslationY(floatingParams.height > 0 ? floatingParams.height : 170 * dip);
            docked = false;
        }
        refresh();
    }

    /** The panel only belongs to the View tab. */
    void setTabVisible(boolean visible) {
        tabVisible = visible;
        refresh();
    }

    void toggleCollapsed() {
        collapsed = !collapsed;
        prefs.edit().putBoolean("properties_collapsed", collapsed).apply();
        refresh();
    }

    private void refresh() {
        if (!docked) {
            property.setVisibility(tabVisible ? View.VISIBLE : View.GONE);
            handle.setVisibility(View.GONE);
            return;
        }
        int visibility = tabVisible && !collapsed ? View.VISIBLE : View.GONE;
        property.setVisibility(visibility);
        handle.setVisibility(visibility);
        ViewGroup.LayoutParams params = property.getLayoutParams();
        int width = widthPx();
        if (params.width != width) {
            params.width = width;
            property.setLayoutParams(params);
        }
    }

    private int widthPx() {
        float palette = prefs.getFloat("palette_width_dp", SidebarWidth.DEFAULT_DP);
        return Math.round(LandscapeWorkspace.clampPropertiesDp(widthDp, screenWidthDp, palette) * dip);
    }

    /** Drag the grabber on the panel's edge: leftwards makes the panel wider. The width is remembered. */
    @SuppressLint("ClickableViewAccessibility")
    private void setupResize() {
        final float[] grab = new float[2];
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    grab[0] = event.getRawX();
                    grab[1] = widthDp;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float palette = prefs.getFloat("palette_width_dp", SidebarWidth.DEFAULT_DP);
                    widthDp = LandscapeWorkspace.clampPropertiesDp(grab[1] - (event.getRawX() - grab[0]) / dip, screenWidthDp, palette);
                    refresh();
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit().putFloat("properties_width_dp", widthDp).apply();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        });
    }
}
