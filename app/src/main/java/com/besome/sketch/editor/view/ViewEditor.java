package com.besome.sketch.editor.view;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.beans.WidgetCollectionBean;
import com.besome.sketch.editor.view.item.ItemHorizontalScrollView;
import com.besome.sketch.editor.view.item.ItemVerticalScrollView;
import com.besome.sketch.editor.view.palette.IconAdView;
import com.besome.sketch.editor.view.palette.IconBase;
import com.besome.sketch.editor.view.palette.IconLinearHorizontal;
import com.besome.sketch.editor.view.palette.IconLinearVertical;
import com.besome.sketch.editor.view.palette.IconMapView;
import com.besome.sketch.editor.view.palette.PaletteFavorite;
import com.besome.sketch.editor.view.palette.PaletteWidget;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.DB;
import a.a.a.GB;
import a.a.a.Iw;
import a.a.a.Op;
import a.a.a.Rp;
import a.a.a.ay;
import a.a.a.bB;
import a.a.a.cC;
import a.a.a.cy;
import a.a.a.jC;
import a.a.a.oB;
import a.a.a.uy;
import a.a.a.wB;
import a.a.a.wq;
import mod.agus.jcoderz.beans.ViewBeans;
import mod.hey.studios.util.ProjectFile;
import mod.jbk.util.LogUtil;
import pro.sketchware.R;
import pro.sketchware.utility.AnalyticsHelper;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.widgets.IconCustomWidget;
import pro.sketchware.widgets.WidgetsCreatorManager;

@SuppressLint({"ClickableViewAccessibility", "SetTextI18n"})
public class ViewEditor extends RelativeLayout implements View.OnClickListener, View.OnTouchListener {
    private final int[] posDummy = new int[2];
    private final Handler handler = new Handler();
    public boolean isLayoutChanged = true;
    public PaletteWidget paletteWidget;
    public WidgetsCreatorManager widgetsCreatorManager;
    private ObjectAnimator animatorTranslateX;
    private boolean isAnimating = false;
    private boolean C = false;
    private boolean D = false;
    private ItemView selectedItem;
    private int defaultIconWidth = 50;
    private int defaultIconHeight = 30;
    private boolean useVibrate;
    private cy widgetSelectedListener;
    private Iw propertyClickListener;
    private DraggingListener draggingListener;
    private ay historyChangeListener;
    private ProjectFileBean projectFileBean;
    private boolean S = true;
    private boolean T = false;
    private LinearLayout paletteGroup;
    private View palettePanel;
    private com.google.android.material.button.MaterialButton togglePaletteButton;
    private boolean paletteExpanded = true;
    private boolean focusPreview;
    private boolean paletteBeforeFocus = true;
    private java.util.function.Consumer<Boolean> focusPreviewListener;
    private TextView dropHint;
    private TextView zoomLabel;
    private float previewZoom = 1f;
    private float paletteWidthDp = SidebarWidth.DEFAULT_DP;
    private android.view.ScaleGestureDetector pinchDetector;
    private String a;
    private LinearLayout aa;
    private String b;
    private int screenType;
    private boolean da = true;
    private int[] countItems = new int[20];
    private float dip = 0;
    private int displayWidth;
    private int displayHeight;
    private PaletteFavorite paletteFavorite;
    private LinearLayout bgStatus;
    private ImageView phoneFrame;
    private PhoneFrame currentFrame = PhoneFrame.byKey(PhoneFrame.DEFAULT_KEY);
    private pro.sketchware.editor.preview.DevicePreset previewDevice = pro.sketchware.editor.preview.DevicePreset.ALL.get(0);
    /** {@code null} keeps the screen as the device holds it. */
    private pro.sketchware.editor.preview.Orientation previewOrientation;
    private boolean previewSafeAreas;
    private PreviewOverlay previewOverlay;
    private GuidesOverlay guidesOverlay;
    private android.content.SharedPreferences uiPrefs;
    private TextView fileName;
    private ImageView imgPhoneTopBg;
    private LinearLayout toolbar;
    private ViewPane viewPane;
    private Vibrator vibrator;
    private View currentTouchedView = null;
    private boolean isDragged = false;
    private float posInitX = 0;
    private float posInitY = 0;
    private int minDist = 0;
    private ViewDummy dummyView;
    private ImageView deleteIcon;
    private TextView deleteText;
    private MaterialCardView deleteView;
    private ObjectAnimator animatorTranslateY;
    private int colorSurfaceContainerHighest;
    private int colorCoolGreenContainer;
    private int colorCoolGreen;
    private int colorError;
    private final Runnable longPressRunnable = this::e;
    private int colorErrorContainer;

    public ViewEditor(Context context) {
        this(context, null);
    }

    public ViewEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public static void shakeView(View view) {
        ObjectAnimator
                .ofFloat(view, "translationX", 0, 35, -35, 35, -35, 25, -25, 12, -12, 0)
                .setDuration(200)
                .start();
    }

    private void animateUpDown() {
        animatorTranslateY = ObjectAnimator.ofFloat(deleteView, "TranslationY", 0.0f);
        animatorTranslateY.setDuration(500L);
        animatorTranslateY.setInterpolator(new OvershootInterpolator());
        animatorTranslateX = ObjectAnimator.ofFloat(deleteView, "TranslationY", deleteView.getHeight() * 2);
        animatorTranslateX.setDuration(500L);
        animatorTranslateX.setInterpolator(new OvershootInterpolator());
        isAnimating = true;
    }

    private void addPaletteGroupItems() {
        LinearLayout.LayoutParams paletteLayoutParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT);
        paletteLayoutParams.weight = 1f;

        PaletteGroupItem basicPalette = new PaletteGroupItem(getContext());
        basicPalette.setLayoutParams(paletteLayoutParams);
        basicPalette.setPaletteGroup(PaletteGroup.BASIC);
        basicPalette.setSelected(true);

        PaletteGroupItem favoritePalette = new PaletteGroupItem(getContext());
        favoritePalette.setLayoutParams(paletteLayoutParams);
        favoritePalette.setPaletteGroup(PaletteGroup.FAVORITE);
        favoritePalette.setSelected(false);
        favoritePalette.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.6f).start();

        basicPalette.setOnClickListener(v -> {
            showPaletteWidget();
            basicPalette.animate().scaleX(1).scaleY(1).alpha(1).start();
            favoritePalette.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.6f).start();
            basicPalette.setSelected(true);
            favoritePalette.setSelected(false);
        });

        favoritePalette.setOnClickListener(v -> {
            showPaletteFavorite();
            basicPalette.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.6f).start();
            favoritePalette.animate().scaleX(1).scaleY(1).alpha(1).start();
            basicPalette.setSelected(false);
            favoritePalette.setSelected(true);
        });

        paletteGroup.addView(basicPalette);
        paletteGroup.addView(favoritePalette);
    }

    public ProjectFileBean getProjectFile() {
        return projectFileBean;
    }

    public void h() {
        viewPane.setResourceManager(jC.d(a));
    }

    public void i() {
        if (selectedItem != null) {
            selectedItem.setSelection(false);
            selectedItem = null;
        }
        refreshGuides();
        if (widgetSelectedListener != null) widgetSelectedListener.a(false, "");
    }

    public void j() {
        viewPane.updateRootLayout(a, projectFileBean.getXmlName());
        viewPane.clearViewPane();
        l();
        i();
    }

    public void removeFab() {
        viewPane.removeFabView();
    }

    public void l() {
        countItems = new int[99];
    }

    private void showMoreProperties() {
        if (propertyClickListener != null) propertyClickListener.a(b, selectedItem.getBean());
    }

    private void showPaletteFavorite() {
        paletteWidget.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction(() -> {
                    paletteWidget.setVisibility(View.GONE);
                    paletteFavorite.setAlpha(0f);
                    paletteFavorite.setVisibility(View.VISIBLE);
                    paletteFavorite.animate()
                            .alpha(1f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }

    private void showPaletteWidget() {
        paletteFavorite.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction(() -> {
                    paletteFavorite.setVisibility(View.GONE);
                    paletteWidget.setAlpha(0f);
                    paletteWidget.setVisibility(View.VISIBLE);
                    paletteWidget.animate()
                            .alpha(1f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }


    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_editproperties) {
            showMoreProperties();
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (isLayoutChanged) a();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        String str;
        int actionMasked = motionEvent.getActionMasked();
        if (motionEvent.getPointerId(motionEvent.getActionIndex()) > 0) {
            return true;
        }
        if (view == viewPane) {
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                i();
                currentTouchedView = null;
            }
            return true;
        } else if (actionMasked == MotionEvent.ACTION_DOWN) {
            isDragged = false;
            posInitX = motionEvent.getRawX();
            posInitY = motionEvent.getRawY();
            currentTouchedView = view;
            if (view instanceof ItemView bean && bean.getFixed()) {
                return true;
            }
            if (isInsideItemScrollView(view) && draggingListener != null) {
                draggingListener.b();
            }
            handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout() / 2);
            return true;
        } else if (actionMasked != MotionEvent.ACTION_UP) {
            if (actionMasked != MotionEvent.ACTION_MOVE) {
                if (actionMasked == MotionEvent.ACTION_CANCEL || actionMasked == MotionEvent.ACTION_SCROLL) {
                    paletteWidget.setScrollEnabled(true);
                    paletteFavorite.setScrollEnabled(true);
                    if (draggingListener != null) {
                        draggingListener.d();
                    }
                    b(false, false);
                    dummyView.setDummyVisibility(View.GONE);
                    viewPane.clearViews();
                    handler.removeCallbacks(longPressRunnable);
                    isDragged = false;
                    return true;
                }
                return true;
            } else if (!isDragged) {
                if (Math.abs(posInitX - motionEvent.getRawX()) >= minDist || Math.abs(posInitY - motionEvent.getRawY()) >= minDist) {
                    currentTouchedView = null;
                    handler.removeCallbacks(longPressRunnable);
                    return true;
                }
                return true;
            } else {
                handler.removeCallbacks(longPressRunnable);
                dummyView.a(view, motionEvent.getRawX(), motionEvent.getRawY(), posInitX, posInitY);
                if (hitTestIconDelete(motionEvent.getRawX(), motionEvent.getRawY())) {
                    dummyView.setAllow(true);
                    updateDeleteIcon(true, currentTouchedView instanceof IconCustomWidget);
                    return true;
                }
                if (D) updateDeleteIcon(false, currentTouchedView instanceof IconCustomWidget);
                if (hitTestToPane(motionEvent.getRawX(), motionEvent.getRawY())) {
                    dummyView.setAllow(true);
                    boolean isNotIcon = !isViewAnIconBase(currentTouchedView);
                    int width = isNotIcon ? currentTouchedView.getWidth() : currentTouchedView instanceof IconLinearHorizontal ?
                            ViewGroup.LayoutParams.MATCH_PARENT : defaultIconWidth;
                    int height = isNotIcon ? currentTouchedView.getHeight() : currentTouchedView instanceof IconLinearVertical ?
                            ViewGroup.LayoutParams.MATCH_PARENT : defaultIconHeight;
                    viewPane.updateView((int) motionEvent.getRawX(), (int) motionEvent.getRawY(), width, height);
                } else {
                    dummyView.setAllow(false);
                    viewPane.resetView(true);
                }
                return true;
            }
        } else if (!isDragged) {
            if (currentTouchedView instanceof ItemView sy) {
                a(sy, true);
            }
            if (draggingListener != null) {
                draggingListener.d();
            }
            dummyView.setDummyVisibility(View.GONE);
            currentTouchedView = null;
            viewPane.clearViews();
            handler.removeCallbacks(longPressRunnable);
            return true;
        } else {
            lol:
            if (dummyView.getAllow()) {
                if (D && currentTouchedView instanceof ItemView widget) {
                    deleteWidget(widget.getBean());
                    break lol;
                }
                if (D && currentTouchedView instanceof uy collectionWidget) {
                    deleteWidgetFromCollection(collectionWidget.getName());
                    break lol;
                }
                if (D && currentTouchedView instanceof IconCustomWidget) {
                    widgetsCreatorManager.showActionsDialog((int) view.getTag());
                    break lol;
                }
                viewPane.resetView(false);
                if (currentTouchedView instanceof uy uyVar) {
                    ArrayList<ViewBean> arrayList = new ArrayList<>();
                    oB oBVar = new oB();
                    boolean areImagesAdded = false;
                    for (int i3 = 0; i3 < uyVar.getData().size(); i3++) {
                        ViewBean viewBean = uyVar.getData().get(i3);
                        arrayList.add(viewBean.clone());
                        String backgroundResource = viewBean.layout.backgroundResource;
                        String resName = viewBean.image.resName;
                        if (!jC.d(a).l(backgroundResource) && Op.g().b(backgroundResource)) {
                            ProjectResourceBean a2 = Op.g().a(backgroundResource);
                            try {
                                oBVar.a(wq.a() + File.separator + "image" + File.separator + "data" + File.separator + a2.resFullName, wq.g() + File.separator + a + File.separator + a2.resFullName);
                            } catch (Exception e) {
                                LogUtil.e("ViewEditor", "", e);
                            }
                            jC.d(a).b.add(a2);
                            areImagesAdded = true;
                        }
                        if (!jC.d(a).l(resName) && Op.g().b(resName)) {
                            ProjectResourceBean a3 = Op.g().a(resName);
                            try {
                                oBVar.a(wq.a() + File.separator + "image" + File.separator + "data" + File.separator + a3.resFullName, wq.g() + File.separator + a + File.separator + a3.resFullName);
                            } catch (Exception e2) {
                                LogUtil.e("ViewEditor", "", e2);
                            }
                            jC.d(a).b.add(a3);
                            areImagesAdded = true;
                        }
                    }
                    if (areImagesAdded) {
                        bB.a(getContext(), getString(R.string.view_widget_favorites_image_auto_added), bB.TOAST_NORMAL).show();
                    }
                    if (!arrayList.isEmpty()) {
                        HashMap<String, String> idMappings = new HashMap<>();
                        viewPane.updateViewBeanProperties(arrayList.get(0), (int) motionEvent.getRawX(), (int) motionEvent.getRawY());
                        for (ViewBean next : arrayList) {
                            if (jC.a(a).h(projectFileBean.getXmlName(), next.id)) {
                                idMappings.put(next.id, generateWidgetId(next));
                            } else {
                                idMappings.put(next.id, next.id);
                            }
                            next.id = idMappings.get(next.id);
                            if (arrayList.indexOf(next) != 0 && (str = next.parent) != null && !str.isEmpty()) {
                                next.parent = idMappings.get(next.parent);
                            }
                            jC.a(a).a(b, next);
                        }
                        a(a(arrayList, true), true);
                    }
                } else if (currentTouchedView instanceof IconBase icon) {
                    ViewBean bean = icon.getBean();
                    bean.id = generateWidgetId(bean);
                    viewPane.updateViewBeanProperties(bean, (int) motionEvent.getRawX(), (int) motionEvent.getRawY());
                    jC.a(a).a(b, bean);
                    AnalyticsHelper.logUiComponentAdded(getContext(), icon.getWidgetName());
                    if (bean.type == 3 && projectFileBean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                        jC.a(a).a(projectFileBean.getJavaName(), 1, bean.type, bean.id, "onClick");
                    }
                    a(a(bean, true), true);
                } else if (currentTouchedView instanceof ItemView sy) {
                    ViewBean bean = sy.getBean();
                    viewPane.updateViewBeanProperties(bean, (int) motionEvent.getRawX(), (int) motionEvent.getRawY());
                    a(b(bean, true), true);
                }
            } else {
                if (currentTouchedView instanceof ItemView) {
                    currentTouchedView.setVisibility(View.VISIBLE);
                }
            }
            paletteWidget.setScrollEnabled(true);
            paletteFavorite.setScrollEnabled(true);
            if (draggingListener != null) {
                draggingListener.d();
            }
            b(false, false);
            dummyView.setDummyVisibility(View.GONE);
            currentTouchedView = null;
            viewPane.clearViews();
            handler.removeCallbacks(longPressRunnable);
            isDragged = false;
            return true;
        }
    }

    public void deleteWidget(ViewBean viewBean) {
        ArrayList<ViewBean> b2 = jC.a(a).b(b, viewBean);
        for (int size = b2.size() - 1; size >= 0; size--) {
            jC.a(a).a(projectFileBean, b2.get(size));
        }
        b(b2, true);
    }

    public void setFavoriteData(ArrayList<WidgetCollectionBean> arrayList) {
        clearCollectionWidget();
        for (WidgetCollectionBean next : arrayList) {
            addFavoriteViews(next.name, next.widgets);
        }
    }

    public void setIsAdLoaded(boolean z) {
        da = z;
    }

    public void setOnDraggingListener(DraggingListener dragListener) {
        draggingListener = dragListener;
    }

    public void setOnHistoryChangeListener(ay ayVar) {
        historyChangeListener = ayVar;
    }

    public void setOnPropertyClickListener(Iw iw) {
        propertyClickListener = iw;
    }

    public void setOnWidgetSelectedListener(cy cyVar) {
        widgetSelectedListener = cyVar;
    }

    public void setPaletteLayoutVisible(int i) {
        paletteWidget.setLayoutVisible(i);
    }

    public void setScreenType(int i) {
        if (i == 1) {
            screenType = 0;
        } else {
            screenType = 1;
        }
    }

    /**
     * The widget palette can be collapsed so the preview gets the whole width, and filtered with the
     * search field. The preview scale is recomputed from the palette's current width in {@link #a()}.
     */
    private void setupWidgetPaletteUi(Context context) {
        dip = wB.a(context, 1.0f); // the rest of initialize() sets it later, but the sidebar needs it now
        palettePanel = findViewById(R.id.layout_palette);
        togglePaletteButton = findViewById(R.id.btn_toggle_palette);
        android.content.SharedPreferences prefs = context.getSharedPreferences("view_editor_ui", Context.MODE_PRIVATE);
        uiPrefs = prefs;
        currentFrame = PhoneFrame.byKey(prefs.getString("phone_frame", PhoneFrame.DEFAULT_KEY));
        previewDevice = pro.sketchware.editor.preview.DevicePreset.byKey(prefs.getString("preview_device", pro.sketchware.editor.preview.DevicePreset.THIS_DEVICE_KEY));
        String savedOrientation = prefs.getString("preview_orientation", "");
        previewOrientation = "portrait".equals(savedOrientation) ? pro.sketchware.editor.preview.Orientation.PORTRAIT
                : "landscape".equals(savedOrientation) ? pro.sketchware.editor.preview.Orientation.LANDSCAPE : null;
        previewSafeAreas = prefs.getBoolean("preview_safe_areas", false);
        paletteExpanded = prefs.getBoolean("palette_expanded", true);
        paletteWidthDp = SidebarWidth.clampDp(prefs.getFloat("palette_width_dp", SidebarWidth.DEFAULT_DP));
        applyPaletteWidth();
        applyPaletteExpanded();
        setupPaletteResize(prefs);
        setupPinchZoom(context);
        togglePaletteButton.setOnClickListener(v -> {
            paletteExpanded = !paletteExpanded;
            prefs.edit().putBoolean("palette_expanded", paletteExpanded).apply();
            applyPaletteExpanded();
            isLayoutChanged = true;
            requestLayout();
        });
        android.widget.EditText search = findViewById(R.id.search_widgets);
        search.addTextChangedListener(new com.besome.sketch.editor.logic.PaletteSelector.SimpleTextWatcher(
                text -> paletteWidget.filter(text.toString())));
        zoomLabel = findViewById(R.id.tv_vzoom);
        findViewById(R.id.btn_vzoom_in).setOnClickListener(v -> setPreviewZoom(previewZoom + 0.25f));
        findViewById(R.id.btn_vzoom_out).setOnClickListener(v -> setPreviewZoom(previewZoom - 0.25f));
        findViewById(R.id.btn_vfit).setOnClickListener(v -> setPreviewZoom(1f));
        findViewById(R.id.btn_focus_preview).setOnClickListener(v -> setFocusPreview(!focusPreview));
        findViewById(R.id.btn_phone_frame).setOnClickListener(v -> showPhoneFramePicker());
        View devicePreviewButton = findViewById(R.id.btn_device_preview);
        devicePreviewButton.setOnClickListener(v -> showDevicePreviewDialog());
        boolean devicePreviewOn = pro.sketchware.flags.FeatureFlags.isEnabled(pro.sketchware.flags.FeatureFlag.DEVICE_PREVIEW);
        devicePreviewButton.setVisibility(devicePreviewOn ? View.VISIBLE : View.GONE);
        // The zoom bar can be moved off the preview; where it was left is remembered.
        new FloatingBarDragger(findViewById(R.id.view_canvas_controls), findViewById(R.id.view_canvas_controls_handle),
                this, "view_zoom_bar", 8 * dip, 6 * dip);
    }

    /** Applies the sidebar width to the panel; a narrow sidebar keeps only the "+" of "+ Widget". */
    private void applyPaletteWidth() {
        ViewGroup.LayoutParams params = palettePanel.getLayoutParams();
        params.width = Math.round(paletteWidthDp * dip);
        palettePanel.setLayoutParams(params);
        View label = palettePanel.findViewById(R.id.tv_new_widget);
        if (label != null) {
            label.setVisibility(SidebarWidth.showsLabel(paletteWidthDp) ? View.VISIBLE : View.GONE);
        }
    }

    /** Drag the grabber on the sidebar's edge to make it narrower or wider; the width is remembered. */
    @SuppressLint("ClickableViewAccessibility")
    private void setupPaletteResize(android.content.SharedPreferences prefs) {
        View handle = findViewById(R.id.palette_resize_handle);
        final float[] grab = new float[2];
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    grab[0] = event.getRawX();
                    grab[1] = paletteWidthDp;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    paletteWidthDp = SidebarWidth.clampDp(grab[1] + (event.getRawX() - grab[0]) / dip);
                    applyPaletteWidth();
                    isLayoutChanged = true;
                    requestLayout();
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit().putFloat("palette_width_dp", paletteWidthDp).apply();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        });
    }

    /** Two fingers zoom the preview smoothly; one finger still drags widgets as before. */
    private void setupPinchZoom(Context context) {
        pinchDetector = new android.view.ScaleGestureDetector(context,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(android.view.ScaleGestureDetector detector) {
                        setPreviewZoom(previewZoom * detector.getScaleFactor(), false);
                        return true;
                    }
                });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (pinchDetector != null) {
            pinchDetector.onTouchEvent(ev);
            if (pinchDetector.isInProgress() || ev.getPointerCount() > 1) {
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pinchDetector != null) {
            pinchDetector.onTouchEvent(event);
            if (pinchDetector.isInProgress() || event.getPointerCount() > 1) {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * View-only preview zoom (50%-200%). Folds into the fit scale on the next layout pass. Fit/Center
     * reset to 100%. Hit-testing reads viewPane.getScaleX(), which includes this factor, so widget
     * placement stays consistent; at other-than-100% the phone may sit off-centre (no scroll).
     */
    private void setPreviewZoom(float zoom) {
        setPreviewZoom(zoom, true);
    }

    /** {@code snap} rounds to quarter steps (buttons); a pinch zooms continuously. */
    private void setPreviewZoom(float zoom, boolean snap) {
        previewZoom = Math.max(0.5f, Math.min(2.0f, snap ? Math.round(zoom * 4f) / 4f : zoom));
        if (zoomLabel != null) zoomLabel.setText(Math.round(previewZoom * 100) + "%");
        isLayoutChanged = true;
        requestLayout();
    }

    /** Empty-state hint over the preview; shown only while the screen has no widgets. */
    private void updateDropHint() {
        boolean empty;
        int count = viewPane.getChildCount();
        if (count == 0) {
            empty = true;
        } else if (count == 1 && viewPane.getChildAt(0) instanceof ViewGroup root) {
            empty = root.getChildCount() == 0;
        } else {
            empty = false;
        }
        dropHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;

        int w = (int) (viewPane.getWidth() * viewPane.getScaleX() * 0.8f);
        int h = (int) (96 * dip);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dropHint.getLayoutParams();
        if (params.width != w || params.height != h) {
            params.width = w;
            params.height = h;
            dropHint.setLayoutParams(params);
        }
        dropHint.setX(viewPane.getX() + viewPane.getWidth() / 2f - w / 2f);
        dropHint.setY(viewPane.getY() + viewPane.getHeight() / 2f - h / 2f);
    }

    /** Hides the palette and lets the host hide its chrome (tabs, footer) so the preview fills the screen. */
    private void setFocusPreview(boolean focus) {
        focusPreview = focus;
        if (focus) {
            paletteBeforeFocus = paletteExpanded;
            paletteExpanded = false;
        } else {
            paletteExpanded = paletteBeforeFocus;
        }
        applyPaletteExpanded();
        togglePaletteButton.setVisibility(focus ? View.GONE : View.VISIBLE);
        if (focusPreviewListener != null) focusPreviewListener.accept(focus);
        isLayoutChanged = true;
        requestLayout();
    }

    public boolean isFocusPreview() {
        return focusPreview;
    }

    public void exitFocusPreview() {
        if (focusPreview) setFocusPreview(false);
    }

    public void setOnFocusPreviewChangedListener(java.util.function.Consumer<Boolean> listener) {
        focusPreviewListener = listener;
    }

    private void applyPaletteExpanded() {
        palettePanel.setVisibility(paletteExpanded ? View.VISIBLE : View.GONE);
        togglePaletteButton.setIconResource(paletteExpanded ? R.drawable.ic_mtrl_close : R.drawable.ic_mtrl_component);
    }

    private void initialize(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setAccessibilityPaneTitle("ViewEditor");
        }
        setContentDescription("SketchwarePro_ViewEditor");

        wB.a(context, this, R.layout.view_editor);

        paletteWidget = findViewById(R.id.palette_widget);
        paletteFavorite = findViewById(R.id.palette_favorite);
        dummyView = findViewById(R.id.dummy);
        deleteIcon = findViewById(R.id.icon_delete);
        deleteText = findViewById(R.id.text_delete);
        deleteView = findViewById(R.id.delete_view);
        FrameLayout shape = findViewById(R.id.shape);
        paletteGroup = findViewById(R.id.palette_group);

        addPaletteGroupItems();
        setupWidgetPaletteUi(context);

        findViewById(R.id.btn_editproperties).setOnClickListener(this);
        findViewById(R.id.img_close).setOnClickListener(this);

        dip = wB.a(context, 1.0f);
        defaultIconWidth = (int) (defaultIconWidth * dip);
        defaultIconHeight = (int) (defaultIconHeight * dip);
        displayWidth = getResources().getDisplayMetrics().widthPixels;
        displayHeight = getResources().getDisplayMetrics().heightPixels;

        aa = new LinearLayout(context);
        aa.setOrientation(LinearLayout.VERTICAL);
        aa.setGravity(Gravity.CENTER);
        aa.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, displayHeight));
        shape.addView(aa);

        bgStatus = new LinearLayout(context);
        bgStatus.setBackgroundColor(0xff0084c2);
        bgStatus.setOrientation(LinearLayout.HORIZONTAL);
        bgStatus.setGravity(Gravity.CENTER_VERTICAL);
        bgStatus.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, (int) (dip * 25f)));

        fileName = new TextView(context);
        fileName.setTextColor(Color.WHITE);
        fileName.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        fileName.setPadding((int) (dip * 8f), 0, 0, 0);
        fileName.setGravity(Gravity.CENTER_VERTICAL);
        bgStatus.addView(fileName);

        imgPhoneTopBg = new ImageView(context);
        imgPhoneTopBg.setImageResource(R.drawable.phone_bg_top);
        imgPhoneTopBg.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        imgPhoneTopBg.setScaleType(ImageView.ScaleType.FIT_END);
        bgStatus.addView(imgPhoneTopBg);
        shape.addView(bgStatus);

        toolbar = new LinearLayout(context);
        toolbar.setBackgroundColor(0xff008dcd);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, (int) (dip * 48f)));

        TextView tvToolbar = new TextView(context);
        tvToolbar.setTextColor(Color.WHITE);
        tvToolbar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tvToolbar.setPadding((int) (dip * 16f), 0, 0, 0);
        tvToolbar.setGravity(Gravity.CENTER_VERTICAL);
        tvToolbar.setTextSize(15f);
        tvToolbar.setText("Toolbar");
        tvToolbar.setTypeface(null, Typeface.BOLD);
        toolbar.addView(tvToolbar);
        shape.addView(toolbar);

        viewPane = new ViewPane(getContext());
        viewPane.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, displayHeight));
        viewPane.setOnTouchListener(this);
        shape.addView(viewPane);

        // The phone's bezel sits over the preview; it is see-through and never takes touches.
        phoneFrame = new ImageView(context);
        showPhoneFrame(currentFrame);
        phoneFrame.setScaleType(ImageView.ScaleType.FIT_XY);
        phoneFrame.setClickable(false);
        phoneFrame.setFocusable(false);
        phoneFrame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shape.addView(phoneFrame, new FrameLayout.LayoutParams(0, 0));

        previewOverlay = new PreviewOverlay(context);
        shape.addView(previewOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        guidesOverlay = new GuidesOverlay(context);
        shape.addView(guidesOverlay, new FrameLayout.LayoutParams(displayWidth, displayHeight));

        dropHint = new TextView(context);
        dropHint.setText(R.string.view_drop_components_here);
        dropHint.setGravity(Gravity.CENTER);
        dropHint.setTextSize(13f);
        dropHint.setTypeface(null, Typeface.BOLD);
        dropHint.setTextColor(0xff5a626e);
        dropHint.setBackgroundResource(R.drawable.bg_drop_zone);
        dropHint.setClickable(false);
        dropHint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shape.addView(dropHint, new FrameLayout.LayoutParams(0, 0));
        viewPane.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            updateDropHint();
            refreshGuides();
        });

        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        useVibrate = new DB(context, "P12").a("P12I0", true);
        minDist = ViewConfiguration.get(context).getScaledTouchSlop();

        paletteWidget.cardView.setOnClickListener(view -> widgetsCreatorManager.showWidgetsCreatorDialog(-1));

        colorSurfaceContainerHighest = ThemeUtils.getColor(deleteView, R.attr.colorSurfaceContainerHighest);
        colorCoolGreenContainer = ThemeUtils.getColor(deleteView, R.attr.colorCoolGreenContainer);
        colorCoolGreen = ThemeUtils.getColor(deleteView, R.attr.colorCoolGreen);
        colorErrorContainer = ThemeUtils.getColor(deleteView, R.attr.colorErrorContainer);
        colorError = ThemeUtils.getColor(deleteView, R.attr.colorOnErrorContainer);

        initialDeleteViewUi();
    }

    public void b(ArrayList<ViewBean> arrayList, boolean z) {
        if (z) {
            cC.c(a).b(projectFileBean.getXmlName(), arrayList);
            if (historyChangeListener != null) {
                historyChangeListener.a();
            }
        }
        int size = arrayList.size();
        while (true) {
            size--;
            if (size < 0) {
                return;
            }
            d(arrayList.get(size));
        }
    }

    private void clearCollectionWidget() {
        paletteFavorite.removeAllWidgets();
    }

    public void removeWidgetsAndLayouts() {
        paletteWidget.removeWidgetLayouts();
        paletteWidget.removeWidgets();
    }

    public ItemView e(ViewBean viewBean) {
        ItemView g = viewPane.g(viewBean);
        widgetSelectedListener.a();
        widgetSelectedListener.a(viewBean.id);
        return g;
    }

    public void d(ViewBean viewBean) {
        viewPane.removeView(viewBean);
    }

    private void e() {
        if (currentTouchedView == null) return;
        if (isViewAnIconBase(currentTouchedView)) {
            boolean isAppCompatEnabled = jC.c(a).c().isEnabled();
            if (currentTouchedView instanceof uy collectionWidget) {
                var collectionData = collectionWidget.getData();
                boolean isAdViewUsed = false;
                for (ViewBean view : collectionData) {
                    if (view.type == ViewBean.VIEW_TYPE_WIDGET_ADVIEW) {
                        isAdViewUsed = true;
                        break;
                    }
                }
                if (isAdViewUsed && !draggingListener.isAdmobEnabled()) {
                    bB.b(getContext(), getString(R.string.design_library_guide_setup_first), bB.TOAST_NORMAL).show();
                    return;
                }

                boolean isMapViewUsed = false;
                for (ViewBean view : collectionData) {
                    if (view.type == ViewBean.VIEW_TYPE_WIDGET_MAPVIEW) {
                        isMapViewUsed = true;
                        break;
                    }
                }
                if (isMapViewUsed && !draggingListener.isGoogleMapEnabled()) {
                    bB.b(getContext(), getString(R.string.design_library_guide_setup_first), bB.TOAST_NORMAL).show();
                    return;
                }
                boolean isAppCompatViewUsed = false;
                for (ViewBean view : collectionData) {
                    switch (view.type) {
                        case ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON,
                             ViewBeans.VIEW_TYPE_WIDGET_RECYCLERVIEW,
                             ViewBeans.VIEW_TYPE_LAYOUT_BOTTOMNAVIGATIONVIEW,
                             ViewBeans.VIEW_TYPE_LAYOUT_TABLAYOUT,
                             ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER,
                             ViewBeans.VIEW_TYPE_LAYOUT_COLLAPSINGTOOLBARLAYOUT,
                             ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT,
                             ViewBeans.VIEW_TYPE_LAYOUT_SWIPEREFRESHLAYOUT,
                             ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW -> isAppCompatViewUsed = true;
                    }
                    if (isAppCompatViewUsed) {
                        break;
                    }
                }

                if (isAppCompatViewUsed && !isAppCompatEnabled) {
                    bB.b(getContext(), getString(R.string.design_library_guide_setup_first), bB.TOAST_NORMAL).show();
                    return;
                }
            } else if (currentTouchedView instanceof IconAdView && !draggingListener.isAdmobEnabled()) {
                bB.b(getContext(), getString(R.string.design_library_guide_setup_first), bB.TOAST_NORMAL).show();
                return;
            } else if (currentTouchedView instanceof IconMapView && !draggingListener.isGoogleMapEnabled()) {
                bB.b(getContext(), getString(R.string.design_library_guide_setup_first), bB.TOAST_NORMAL).show();
                return;
            } else if (currentTouchedView instanceof AndroidxOrMaterialView && !isAppCompatEnabled) {
                bB.b(getContext(), getString(R.string.design_library_guide_setup_first), bB.TOAST_NORMAL).show();
                return;
            }
        }
        paletteWidget.setScrollEnabled(false);
        paletteFavorite.setScrollEnabled(false);
        if (draggingListener != null) draggingListener.b();
        if (useVibrate) vibrator.vibrate(100L);
        isDragged = true;
        dummyView.b(currentTouchedView);
        dummyView.bringToFront();
        i();
        dummyView.a(currentTouchedView, posInitX, posInitY, posInitX, posInitY);
        dummyView.a(posDummy);
        if (isViewAnIconBase(currentTouchedView)) {
            if (currentTouchedView instanceof uy || currentTouchedView instanceof IconCustomWidget) {
                b(true, currentTouchedView instanceof IconCustomWidget);
                viewPane.addRootLayout(null);
            } else {
                b(false, false);
                viewPane.addRootLayout(null);
            }
        } else {
            currentTouchedView.setVisibility(View.GONE);
            b(true, currentTouchedView instanceof IconCustomWidget);
            viewPane.addRootLayout(((ItemView) currentTouchedView).getBean());
        }
        if (hitTestToPane(posInitX, posInitY)) {
            dummyView.setAllow(true);
            boolean isNotIcon = !isViewAnIconBase(currentTouchedView);
            int width = isNotIcon ? currentTouchedView.getWidth() : currentTouchedView instanceof IconLinearHorizontal ?
                    ViewGroup.LayoutParams.MATCH_PARENT : defaultIconWidth;
            int height = isNotIcon ? currentTouchedView.getHeight() : currentTouchedView instanceof IconLinearVertical ?
                    ViewGroup.LayoutParams.MATCH_PARENT : defaultIconHeight;
            viewPane.updateView((int) posInitX, (int) posInitY, width, height);
            return;
        }
        dummyView.setAllow(false);
        viewPane.resetView(true);
    }

    public ItemView b(ViewBean viewBean, boolean z) {
        if (z) {
            cC.c(a).b(projectFileBean.getXmlName(), viewBean);
            if (historyChangeListener != null) {
                historyChangeListener.a();
            }
        }
        return viewPane.d(viewBean);
    }

    public ItemView createAndAddView(ViewBean viewBean) {
        View itemView = viewPane.createItemView(viewBean);
        itemView.setContentDescription("SketchwareItem_" + viewBean.id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            itemView.setAccessibilityPaneTitle("ItemView: " + viewBean.id);
        }

        viewPane.addViewAndUpdateIndex(itemView);
        String generatedId = wq.b(viewBean.type);
        if (viewBean.id.indexOf(generatedId) == 0 && viewBean.id.length() > generatedId.length()) {
            try {
                int intValue = Integer.parseInt(viewBean.id.substring(generatedId.length()));
                if (countItems[viewBean.type] < intValue) {
                    countItems[viewBean.type] = intValue;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        itemView.setOnTouchListener(this);
        return (ItemView) itemView;
    }

    private boolean isInsideItemScrollView(View view) {
        for (ViewParent parent = view.getParent(); parent != null && parent != this; parent = parent.getParent()) {
            if (parent instanceof ItemVerticalScrollView || parent instanceof ItemHorizontalScrollView) {
                return true;
            }
        }
        return false;
    }

    private boolean hitTestToPane(float x, float y) {
        int[] locationOnScreen = new int[2];
        viewPane.getLocationOnScreen(locationOnScreen);
        if (!(x > locationOnScreen[0])) return false;
        if (!(x < locationOnScreen[0] + viewPane.getWidth() * viewPane.getScaleX())) return false;
        if (!(y > locationOnScreen[1])) return false;
        return y < locationOnScreen[1] + viewPane.getHeight() * viewPane.getScaleY();
    }

    private void deleteWidgetFromCollection(String str) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(getString(R.string.view_widget_favorites_delete_title));
        dialog.setIcon(R.drawable.ic_mtrl_delete);
        dialog.setMessage(getString(R.string.view_widget_favorites_delete_message));
        dialog.setPositiveButton(getString(R.string.common_word_delete), (v, which) -> {
            Rp.h().a(str, true);
            setFavoriteData(Rp.h().f());
            v.dismiss();
        });
        dialog.setNegativeButton(getString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private String getString(@StringRes int res) {
        return getContext().getString(res);
    }

    private void cancelAnimation() {
        if (animatorTranslateY.isRunning()) animatorTranslateY.cancel();
        if (animatorTranslateX.isRunning()) animatorTranslateX.cancel();
    }

    private void setPreviewColors(String str) {
        bgStatus.setBackgroundColor(ProjectFile.getColor(str, ProjectFile.COLOR_PRIMARY_DARK));
        imgPhoneTopBg.setBackgroundColor(ProjectFile.getColor(str, ProjectFile.COLOR_PRIMARY_DARK));
        toolbar.setBackgroundColor(ProjectFile.getColor(str, ProjectFile.COLOR_PRIMARY));
    }

    private void b(boolean z, boolean isCustomWidget) {
        if (isCustomWidget) {
            deleteIcon.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_mtrl_edit));
            deleteText.setText("Drag here to see the Actions");
        } else if (z) {
            deleteIcon.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_mtrl_delete));
            deleteText.setText("Drag here to delete");
            setDeleteViewIconAndTextUi(false);
        }
        deleteView.bringToFront();
        if (!isAnimating) {
            animateUpDown();
        }
        if (C == z) return;
        C = z;
        cancelAnimation();
        if (z) {
            animatorTranslateY.start();
        } else {
            animatorTranslateX.start();
        }
    }

    public void initialize(String str, ProjectFileBean projectFileBean) {
        a = str;
        setPreviewColors(str);
        if (viewPane != null) {
            viewPane.initialize(str, false);
        }
        this.projectFileBean = projectFileBean;
        b = projectFileBean.getXmlName();
        if (projectFileBean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_DRAWER) {
            fileName.setText(projectFileBean.fileName.substring(1));
        } else {
            fileName.setText(projectFileBean.getXmlName());
        }
        removeFab();
        if (projectFileBean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
            S = projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR);
            T = projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN);
            if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB)) {
                addFab(jC.a(str).h(projectFileBean.getXmlName()));
            }
        } else {
            S = false;
            T = false;
        }
        isLayoutChanged = true;
    }

    /**
     * Selects the widget with that id as a tap would, which also shows its properties.
     *
     * @return false when the screen has no such widget
     */
    public boolean selectWidget(String id) {
        if (id == null || id.isEmpty()) return false;
        ItemView item = viewPane.findItemViewByTag(id);
        if (item == null) return false;
        a(item, true);
        return true;
    }

    public void updateSelection(String tag) {
        ItemView syVar;
        ItemView itemView = viewPane.findItemViewByTag(tag);
        if (itemView == null || (syVar = selectedItem) == itemView) {
            return;
        }
        if (syVar != null) {
            syVar.setSelection(false);
        }
        itemView.setSelection(true);
        selectedItem = itemView;
        refreshGuides();
    }

    private void a() {
        toolbar.setVisibility(S ? View.VISIBLE : View.GONE);
        bgStatus.setVisibility(T ? View.GONE : View.VISIBLE);

        viewPane.setVisibility(View.VISIBLE);
        int realWidth = getResources().getDisplayMetrics().widthPixels;
        int realHeight = getResources().getDisplayMetrics().heightPixels;
        int[] previewSize = previewPixels(realWidth, realHeight);
        displayWidth = previewSize[0];
        displayHeight = previewSize[1];
        boolean showsFrame = currentFrame != null && !previewOverridesSize();
        boolean isLandscapeMode = realWidth > realHeight;
        int var4 = (int) (dip * (!isLandscapeMode ? 12.0F : 24.0F));
        int var5 = (int) (dip * (!isLandscapeMode ? 20.0F : 10.0F));
        // A frame with thick bezels (an old phone's chin, say) takes its room from the preview.
        float frameScale = phoneFrameScale();
        if (showsFrame) {
            var4 = Math.max(var4, (int) Math.ceil(Math.max(currentFrame.insetLeft(), currentFrame.insetRight()) * frameScale) + 2);
            var5 = Math.max(var5, (int) Math.ceil(Math.max(currentFrame.insetTop(), currentFrame.insetBottom()) * frameScale) + 2);
        }
        final int marginX = var4;
        final int marginY = var5;
        int statusBarHeight = GB.f(getContext());
        int toolBarHeight = GB.a(getContext());
        int var9 = realWidth - (paletteExpanded ? Math.round(paletteWidthDp * dip) : 0);
        int var8 = realHeight - statusBarHeight - toolBarHeight - (int) (dip * 48.0F) - (int) (dip * 48.0F);
        if (screenType == 0 && da) {
            Log.d("ViewEditor", "hmmm");
            var8 -= (int) (dip * 56.0F);
        }

        float var11 = Math.min((float) var9 / (float) displayWidth, (float) var8 / (float) displayHeight);
        float var3 = Math.min((float) (var9 - var4 * 2) / (float) displayWidth, (float) (var8 - var5 * 2) / (float) displayHeight);
        // Manual preview zoom folded into the fit scale so viewPane.getScaleX() (read by hit-testing) stays consistent.
        var11 *= previewZoom;
        var3 *= previewZoom;

        aa.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, displayHeight));
        aa.setScaleX(var11);
        aa.setScaleY(var11);
        aa.setX(-((int) ((displayWidth - displayWidth * var11) / 2.0F)));
        aa.setY(-((int) ((displayHeight - displayHeight * var11) / 2.0F)));
        int var10 = var4 - (int) ((displayWidth - displayWidth * var3) / 2.0F);
        int var13 = var5;
        if (bgStatus.getVisibility() == View.VISIBLE) {
            bgStatus.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, statusBarHeight));
            bgStatus.setScaleX(var3);
            bgStatus.setScaleY(var3);
            var11 = statusBarHeight;
            float var12 = var11 * var3;
            bgStatus.setX(var10);
            bgStatus.setY(var5 - (int) ((var11 - var12) / 2.0F));
            var13 = var5 + (int) var12;
        }

        var8 = var13;
        if (toolbar.getVisibility() == View.VISIBLE) {
            toolbar.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, toolBarHeight));
            toolbar.setScaleX(var3);
            toolbar.setScaleY(var3);
            var11 = (float) toolBarHeight * var3;
            toolbar.setX(var10);
            toolbar.setY(var13 - (int) (((float) toolBarHeight - var11) / 2.0F));
            var8 = var13 + (int) var11;
        }

        var13 = displayHeight;
        if (bgStatus.getVisibility() == View.VISIBLE) {
            var13 = displayHeight - statusBarHeight;
        }

        var5 = var13;
        if (toolbar.getVisibility() == View.VISIBLE) {
            var5 = var13 - toolBarHeight;
        }

        viewPane.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, var5));
        viewPane.setScaleX(var3);
        viewPane.setScaleY(var3);
        var11 = var5;
        viewPane.setX(var10);
        viewPane.setY(var8 - (int) ((var11 - var3 * var11) / 2.0F));
        guidesOverlay.setLayoutParams(new FrameLayout.LayoutParams(displayWidth, var5));
        guidesOverlay.setScaleX(var3);
        guidesOverlay.setScaleY(var3);
        guidesOverlay.setX(viewPane.getX());
        guidesOverlay.setY(viewPane.getY());
        phoneFrame.setVisibility(showsFrame ? View.VISIBLE : View.GONE);
        if (showsFrame) {
            updatePhoneFrame(marginX, marginY, displayWidth * var3, displayHeight * var3);
        }
        updatePreviewOverlay(marginX, marginY, var3);
        isLayoutChanged = false;
    }

    /** Scale of the frame picture: a side bezel of about 7dp, top and bottom never thicker than 40dp. */
    private float phoneFrameScale() {
        if (currentFrame == null || previewOverridesSize()) {
            return 0f;
        }
        return PhoneFrameDrawable.scaleFor(currentFrame, 7 * dip, 40 * dip);
    }

    /** Puts the phone bezel around the preview's screen. */
    private void updatePhoneFrame(int screenLeft, int screenTop, float screenWidth, float screenHeight) {
        if (currentFrame == null || !(phoneFrame.getDrawable() instanceof PhoneFrameDrawable drawable)) {
            return;
        }
        float scale = phoneFrameScale();
        drawable.setScale(scale);
        android.graphics.RectF outer = PhoneFrameDrawable.outerBounds(currentFrame, screenLeft, screenTop, screenWidth, screenHeight, scale);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) phoneFrame.getLayoutParams();
        int width = Math.round(outer.width());
        int height = Math.round(outer.height());
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            phoneFrame.setLayoutParams(params);
        }
        phoneFrame.setX(outer.left);
        phoneFrame.setY(outer.top);
    }

    /** Shows {@code frame} around the preview, or nothing for {@code null}. */
    private void showPhoneFrame(PhoneFrame frame) {
        currentFrame = frame;
        if (frame == null) {
            phoneFrame.setImageDrawable(null);
            phoneFrame.setVisibility(View.GONE);
        } else {
            phoneFrame.setImageDrawable(new PhoneFrameDrawable(getResources(), frame));
            phoneFrame.setVisibility(View.VISIBLE);
        }
        isLayoutChanged = true;
        requestLayout();
    }

    private void choosePhoneFrame(PhoneFrame frame) {
        if (uiPrefs != null) {
            uiPrefs.edit().putString("phone_frame", frame == null ? PhoneFrame.NONE_KEY : frame.key()).apply();
        }
        showPhoneFrame(frame);
    }

    /** Pane-pixel rectangle of {@code view}, as the guide engine wants it. */
    private pro.sketchware.editor.layout.Box boxInPane(View view) {
        android.graphics.Rect rect = new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight());
        viewPane.offsetDescendantRectToMyCoords(view, rect);
        return new pro.sketchware.editor.layout.Box(rect.left, rect.top, rect.right, rect.bottom);
    }

    /** Shows alignment guides and distances for the selected widget, or clears them. */
    private void refreshGuides() {
        if (guidesOverlay == null) {
            return;
        }
        if (!pro.sketchware.flags.FeatureFlags.isEnabled(pro.sketchware.flags.FeatureFlag.LAYOUT_GUIDES)
                || !(selectedItem instanceof View selected) || selected.getWidth() == 0
                || !(selected.getParent() instanceof ViewGroup parent) || !selected.isAttachedToWindow()) {
            guidesOverlay.clear();
            return;
        }
        try {
            pro.sketchware.editor.layout.Box target = boxInPane(selected);
            java.util.List<pro.sketchware.editor.layout.Box> siblings = new java.util.ArrayList<>();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child != selected && child.getVisibility() == View.VISIBLE && child.getWidth() > 0 && child.getHeight() > 0
                        && child.getTag() != null) {
                    siblings.add(boxInPane(child));
                }
            }
            pro.sketchware.editor.layout.Box parentBox = boxInPane(parent);
            guidesOverlay.show(target, pro.sketchware.editor.layout.GuideEngine.alignments(target, siblings, parentBox, 1f),
                    pro.sketchware.editor.layout.GuideEngine.gaps(target, siblings, parentBox));
        } catch (RuntimeException e) {
            guidesOverlay.clear();
        }
    }

    private boolean previewActive() {
        return pro.sketchware.flags.FeatureFlags.isEnabled(pro.sketchware.flags.FeatureFlag.DEVICE_PREVIEW);
    }

    /** True when the preview is not simply the device's own screen, so the phone bezel no longer fits. */
    private boolean previewOverridesSize() {
        return previewActive() && (!previewDevice.isThisDevice() || previewOrientation != null);
    }

    /** Pixel size of the previewed screen: the chosen device in its orientation, or this device's screen as is. */
    private int[] previewPixels(int realWidth, int realHeight) {
        if (!previewActive()) {
            return new int[]{realWidth, realHeight};
        }
        if (previewDevice.isThisDevice()) {
            if (previewOrientation == null) {
                return new int[]{realWidth, realHeight};
            }
            int shortSide = Math.min(realWidth, realHeight);
            int longSide = Math.max(realWidth, realHeight);
            return previewOrientation == pro.sketchware.editor.preview.Orientation.PORTRAIT
                    ? new int[]{shortSide, longSide} : new int[]{longSide, shortSide};
        }
        pro.sketchware.editor.preview.Orientation orientation = previewOrientation == null
                ? pro.sketchware.editor.preview.Orientation.PORTRAIT : previewOrientation;
        return new int[]{Math.round(previewDevice.width(orientation) * dip), Math.round(previewDevice.height(orientation) * dip)};
    }

    private void updatePreviewOverlay(int screenLeft, int screenTop, float scale) {
        if (previewOverlay == null) {
            return;
        }
        if (!previewActive() || (previewDevice.isThisDevice() && previewOrientation == null && !previewSafeAreas)) {
            previewOverlay.update(null, false, "", 0, 0, 1f);
            return;
        }
        pro.sketchware.editor.preview.Orientation orientation = displayWidth > displayHeight
                ? pro.sketchware.editor.preview.Orientation.LANDSCAPE : pro.sketchware.editor.preview.Orientation.PORTRAIT;
        int widthDp = Math.round(displayWidth / dip);
        int heightDp = Math.round(displayHeight / dip);
        pro.sketchware.editor.preview.SafeArea area = pro.sketchware.editor.preview.SafeArea.of(previewDevice, orientation, widthDp, heightDp);
        String label = widthDp + " × " + heightDp + " dp · " + pro.sketchware.editor.preview.WindowSizeClass.of(widthDp, heightDp).label();
        previewOverlay.update(area, previewSafeAreas, label, screenLeft, screenTop, dip * scale);
    }

    private void savePreviewChoice() {
        if (uiPrefs != null) {
            uiPrefs.edit()
                    .putString("preview_device", previewDevice.key())
                    .putString("preview_orientation", previewOrientation == null ? ""
                            : previewOrientation == pro.sketchware.editor.preview.Orientation.PORTRAIT ? "portrait" : "landscape")
                    .putBoolean("preview_safe_areas", previewSafeAreas)
                    .apply();
        }
        isLayoutChanged = true;
        requestLayout();
    }

    /** Pick the device the screen is previewed on, its orientation and whether to mark the unsafe areas. */
    private void showDevicePreviewDialog() {
        Context context = getContext();
        java.util.List<pro.sketchware.editor.preview.DevicePreset> presets = pro.sketchware.editor.preview.DevicePreset.ALL;
        int pad = (int) (20 * dip);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, (int) (8 * dip), pad, 0);

        android.widget.RadioGroup group = new android.widget.RadioGroup(context);
        for (int i = 0; i < presets.size(); i++) {
            pro.sketchware.editor.preview.DevicePreset preset = presets.get(i);
            android.widget.RadioButton radio = new android.widget.RadioButton(context);
            radio.setId(i + 1);
            radio.setText(preset.isThisDevice() ? preset.name()
                    : preset.name() + " (" + preset.widthDp() + " × " + preset.heightDp() + " dp)");
            group.addView(radio);
            if (preset == previewDevice) {
                group.check(i + 1);
            }
        }
        content.addView(group);

        android.widget.CheckBox landscape = new android.widget.CheckBox(context);
        landscape.setText("Landscape");
        landscape.setChecked(previewOrientation == pro.sketchware.editor.preview.Orientation.LANDSCAPE
                || (previewOrientation == null && displayWidth > displayHeight && previewDevice.isThisDevice()));
        content.addView(landscape);

        android.widget.CheckBox safeAreas = new android.widget.CheckBox(context);
        safeAreas.setText("Mark the areas the system or a hinge can cover");
        safeAreas.setChecked(previewSafeAreas);
        content.addView(safeAreas);

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(content);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Device preview")
                .setView(scroll)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    int id = group.getCheckedRadioButtonId();
                    previewDevice = id > 0 ? presets.get(id - 1) : presets.get(0);
                    pro.sketchware.editor.preview.Orientation chosen = landscape.isChecked()
                            ? pro.sketchware.editor.preview.Orientation.LANDSCAPE
                            : pro.sketchware.editor.preview.Orientation.PORTRAIT;
                    // "This device" in the orientation it already has stays untouched, so the default view is unchanged.
                    boolean deviceIsLandscape = getResources().getDisplayMetrics().widthPixels > getResources().getDisplayMetrics().heightPixels;
                    boolean naturalChoice = previewDevice.isThisDevice()
                            && (chosen == pro.sketchware.editor.preview.Orientation.LANDSCAPE) == deviceIsLandscape;
                    previewOrientation = naturalChoice ? null : chosen;
                    previewSafeAreas = safeAreas.isChecked();
                    savePreviewChoice();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    /** A grid of the available frames (and "no frame") to pick from. */
    private void showPhoneFramePicker() {
        Context context = getContext();
        android.widget.GridLayout grid = new android.widget.GridLayout(context);
        grid.setColumnCount(3);
        int pad = (int) (12 * dip);
        grid.setPadding(pad, pad, pad, pad);

        androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];
        java.util.ArrayList<PhoneFrame> options = new java.util.ArrayList<>();
        options.add(null);
        options.addAll(PhoneFrame.ALL);
        for (PhoneFrame option : options) {
            grid.addView(phoneFrameChoice(context, option, option == currentFrame, () -> {
                choosePhoneFrame(option);
                if (dialog[0] != null) dialog[0].dismiss();
            }));
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(grid);
        dialog[0] = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.phone_frame_title)
                .setView(scroll)
                .setNegativeButton(R.string.common_word_cancel, null)
                .create();
        dialog[0].show();
    }

    private View phoneFrameChoice(Context context, PhoneFrame frame, boolean selected, Runnable onChoose) {
        int cell = (int) (92 * dip);
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(card, R.attr.colorSurfaceContainerHigh));
        card.setRadius(12 * dip);
        card.setCardElevation(0f);
        card.setStrokeWidth((int) ((selected ? 2 : 1) * dip));
        card.setStrokeColor(com.google.android.material.color.MaterialColors.getColor(card,
                selected ? androidx.appcompat.R.attr.colorPrimary : R.attr.colorOutlineVariant));
        card.setClickable(true);
        card.setOnClickListener(v -> onChoose.run());

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int inner = (int) (6 * dip);
        content.setPadding(inner, inner, inner, inner);

        ImageView thumb = new ImageView(context);
        thumb.setLayoutParams(new LinearLayout.LayoutParams((int) (52 * dip), (int) (96 * dip)));
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (frame != null) {
            thumb.setImageResource(frame.drawableRes());
        } else {
            thumb.setImageResource(R.drawable.ic_mtrl_close);
            thumb.setPadding((int) (14 * dip), (int) (30 * dip), (int) (14 * dip), (int) (30 * dip));
            thumb.setColorFilter(com.google.android.material.color.MaterialColors.getColor(card, R.attr.colorOnSurfaceVariant));
        }
        content.addView(thumb);

        TextView label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTextSize(11f);
        label.setTextColor(com.google.android.material.color.MaterialColors.getColor(card, R.attr.colorOnSurface));
        label.setText(frame == null ? context.getString(R.string.phone_frame_none) : phoneFrameName(context, frame));
        content.addView(label);

        card.addView(content);
        android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
        params.width = cell;
        params.setMargins((int) (4 * dip), (int) (4 * dip), (int) (4 * dip), (int) (4 * dip));
        card.setLayoutParams(params);
        return card;
    }

    static String phoneFrameName(Context context, PhoneFrame frame) {
        return frame.labelNumber() > 0
                ? context.getString(frame.labelRes(), frame.labelNumber())
                : context.getString(frame.labelRes());
    }

    public void addWidgetLayout(PaletteWidget.a aVar, String str) {
        View widget = paletteWidget.a(aVar, str);
        widget.setClickable(true);
        widget.setOnTouchListener(this);
    }

    public void extraWidgetLayout(String str, String str2) {
        View extraWidgetLayout = paletteWidget.extraWidgetLayout(str, str2);
        extraWidgetLayout.setClickable(true);
        extraWidgetLayout.setOnTouchListener(this);
    }

    public void addWidget(PaletteWidget.b bVar, String str, String str2, String str3) {
        View widget = paletteWidget.a(bVar, str, str2, str3);
        widget.setClickable(true);
        widget.setOnTouchListener(this);
    }

    public void extraWidget(String str, String str2, String str3) {
        View extraWidget = paletteWidget.extraWidget(str, str2, str3);
        extraWidget.setClickable(true);
        extraWidget.setOnTouchListener(this);
    }

    private boolean isViewAnIconBase(View view) {
        return view instanceof IconBase;
    }

    private void addFavoriteViews(String str, ArrayList<ViewBean> arrayList) {
        View a2 = paletteFavorite.a(str, arrayList);
        a2.setClickable(true);
        a2.setOnTouchListener(this);
    }

    private String generateWidgetId(ViewBean bean) {
        int type = bean.type;
        String b2 = !bean.isCustomWidget ? wq.b(type) : widgetsCreatorManager.generateCustomWidgetId(bean.convert);
        StringBuilder sb = new StringBuilder();
        sb.append(b2);
        int i2 = countItems[type] + 1;
        countItems[type] = i2;
        sb.append(i2);
        String sb2 = sb.toString();
        ArrayList<ViewBean> d = jC.a(a).d(b);
        while (true) {
            boolean isIdUsed = false;
            for (ViewBean view : d) {
                if (sb2.equals(view.id)) {
                    isIdUsed = true;
                    break;
                }
            }
            if (!isIdUsed) {
                return sb2;
            }
            StringBuilder sb3 = new StringBuilder();
            sb3.append(b2);
            int i3 = countItems[type] + 1;
            countItems[type] = i3;
            sb3.append(i3);
            sb2 = sb3.toString();
        }
    }

    public ItemView a(ArrayList<ViewBean> arrayList, boolean z) {
        if (z) {
            cC.c(a).a(projectFileBean.getXmlName(), arrayList);
            if (historyChangeListener != null) {
                historyChangeListener.a();
            }
        }
        ItemView syVar = null;
        for (ViewBean view : arrayList) {
            if (arrayList.indexOf(view) == 0) {
                syVar = createAndAddView(view);
            } else {
                createAndAddView(view);
            }
        }
        return syVar;
    }

    public ItemView a(ViewBean viewBean, boolean isInHistory) {
        if (isInHistory) {
            cC.c(a).a(projectFileBean.getXmlName(), viewBean);
            if (historyChangeListener != null) {
                historyChangeListener.a();
            }
        }
        return createAndAddView(viewBean);
    }

    public void a(ArrayList<ViewBean> arrayList) {
        if (arrayList == null || arrayList.isEmpty()) {
            return;
        }
        for (ViewBean view : arrayList) {
            createAndAddView(view);
        }
    }

    public void addFab(ViewBean viewBean) {
        viewPane.addFab(viewBean).setOnTouchListener(this);
    }

    public void a(ItemView syVar, boolean z) {
        if (selectedItem != null) {
            selectedItem.setSelection(false);
        }
        selectedItem = syVar;
        selectedItem.setSelection(true);
        refreshGuides();
        if (widgetSelectedListener != null) {
            widgetSelectedListener.a(z, selectedItem.getBean().id);
        }
    }

    private boolean hitTestIconDelete(float x, float y) {
        int[] locationOnScreen = new int[2];
        deleteView.getLocationOnScreen(locationOnScreen);
        if (!(x > locationOnScreen[0])) return false;
        if (!(x < locationOnScreen[0] + deleteView.getWidth())) return false;
        if (!(y > locationOnScreen[1])) return false;
        return y < locationOnScreen[1] + deleteView.getHeight();
    }

    private void updateDeleteIcon(boolean z, boolean isCustomWidget) {
        if (D == z) return;
        D = z;
        if (D) {
            setSelectedDeleteViewUi(isCustomWidget);
            shakeView(deleteView);
        } else {
            initialDeleteViewUi();
            setDeleteViewIconAndTextUi(isCustomWidget);
        }
        if (isCustomWidget) {
            deleteIcon.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_mtrl_edit));
            deleteText.setText(D ? "Release to see the actions" : "Drag here to see the Actions");
        } else {
            deleteIcon.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_mtrl_delete));
            deleteText.setText(D ? "Release to delete" : "Drag here to delete");
        }
    }

    private void initialDeleteViewUi() {
        deleteView.setCardBackgroundColor(colorSurfaceContainerHighest);
    }

    private void setSelectedDeleteViewUi(boolean isCustomWidget) {
        deleteView.setCardBackgroundColor(isCustomWidget ? colorCoolGreenContainer : colorErrorContainer);
        setDeleteViewIconAndTextUi(isCustomWidget);
    }

    private void setDeleteViewIconAndTextUi(boolean isCustomWidget) {
        if (isCustomWidget) {
            deleteText.setTextColor(colorCoolGreen);
            deleteIcon.setColorFilter(colorCoolGreen);
        } else {
            deleteText.setTextColor(colorError);
            deleteIcon.setColorFilter(colorError);
        }
    }

    public void createCustomWidget(HashMap<String, Object> map) {
        View extraWidget = paletteWidget.customWidget(map);
        extraWidget.setClickable(true);
        Object position = map.get("position");
        int tagValue = 0;
        if (position instanceof Integer) {
            tagValue = (Integer) position;
        } else if (position instanceof Double) {
            tagValue = ((Double) position).intValue();
        }
        extraWidget.setTag(tagValue);
        extraWidget.setOnTouchListener(this);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(ViewEditor.class.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.setPaneTitle("ViewEditor");
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ViewEditor.class.getName();
    }

    @NonNull
    @Override
    public String toString() {
        return "ViewEditor{project=" + a + ", file=" + b + ", screenType=" + screenType + "}";
    }

    enum PaletteGroup {
        BASIC,
        FAVORITE
    }

    static class PaletteGroupItem extends LinearLayout implements View.OnClickListener {
        private final ImageView imgGroup;

        public PaletteGroupItem(Context context) {
            super(context);

            wB.a(context, this, R.layout.palette_group_item);
            imgGroup = findViewById(R.id.img_group);
        }

        @Override
        public void onClick(View view) {
        }

        public void setPaletteGroup(PaletteGroup group) {
            imgGroup.setImageResource(group == PaletteGroup.BASIC ?
                    R.drawable.selector_palette_tab_ic_sketchware :
                    R.drawable.selector_palette_tab_ic_bookmark);
            setOnClickListener(this);
        }
    }
}
