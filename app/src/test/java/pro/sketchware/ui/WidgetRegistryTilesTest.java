package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.AndroidxOrMaterialView;
import com.besome.sketch.editor.view.palette.IconBase;
import com.besome.sketch.editor.view.palette.PaletteWidget;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

import mod.agus.jcoderz.beans.ViewBeans;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.widgets.registry.IconRegistryWidget;
import pro.sketchware.widgets.registry.WidgetRegistry;
import pro.sketchware.widgets.registry.WidgetSpec;
import pro.sketchware.widgets.registry.WidgetSpec.Base;

/** The palette tiles built from the widget registry produce the beans the editor, the XML and the Java expect. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class WidgetRegistryTilesTest {

    private static Activity activity() {
        RuntimeEnvironment.setQualifiers("+notnight");
        try {
            ThemeHarness.prepareApplication();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Context app = ApplicationProvider.getApplicationContext();
        return Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
    }

    private static int viewTypeOf(Base base) {
        return switch (base) {
            case LINEAR_VERTICAL, LINEAR_HORIZONTAL -> ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
            case SCROLL_VERTICAL -> ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW;
            case BUTTON -> ViewBean.VIEW_TYPE_WIDGET_BUTTON;
            case MATERIAL_BUTTON -> ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON;
            case TEXT_VIEW -> ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW;
            case EDIT_TEXT -> ViewBean.VIEW_TYPE_WIDGET_EDITTEXT;
            case SWITCH -> ViewBean.VIEW_TYPE_WIDGET_SWITCH;
            case IMAGE_VIEW -> ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW;
            case CARD_VIEW -> ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW;
            case AUTO_COMPLETE_TEXT_VIEW -> ViewBeans.VIEW_TYPE_WIDGET_AUTOCOMPLETETEXTVIEW;
            case NONE -> throw new AssertionError("no base");
        };
    }

    @Test
    public void everyIntegratedWidgetBuildsTheBeanOfItsBaseTypeWithItsOwnClass() {
        Activity activity = activity();
        List<WidgetSpec> integrated = WidgetRegistry.integrated();
        assertFalse(integrated.isEmpty());
        for (WidgetSpec spec : integrated) {
            IconBase tile = IconRegistryWidget.create(activity, spec);
            ViewBean bean = tile.getBean();
            assertNotNull(spec.id(), bean);
            assertEquals(spec.id() + " type", viewTypeOf(spec.base()), bean.type);
            assertEquals(spec.id() + " class", spec.className(), bean.convert);
            assertEquals(spec.id() + " attributes", spec.defaultInject(), bean.inject);
            assertFalse(spec.id() + " is not a user widget", bean.isCustomWidget);
            assertEquals(spec.id() + " gating", !spec.dependency().isPlatform(), tile instanceof AndroidxOrMaterialView);
        }
    }

    @Test
    public void beansAreIndependentCopies() {
        Activity activity = activity();
        WidgetSpec spec = WidgetRegistry.byId("layout.table");
        IconBase tile = IconRegistryWidget.create(activity, spec);
        assertTrue(tile.getBean() != tile.getBean());
    }

    @Test
    public void tableLayoutIsVerticalAndTableRowHorizontal() {
        Activity activity = activity();
        ViewBean table = IconRegistryWidget.create(activity, WidgetRegistry.byId("layout.table")).getBean();
        ViewBean row = IconRegistryWidget.create(activity, WidgetRegistry.byId("layout.tablerow")).getBean();
        assertEquals("android.widget.TableLayout", table.convert);
        assertEquals("android.widget.TableRow", row.convert);
        assertEquals(1, table.layout.orientation);
        assertEquals(0, row.layout.orientation);
    }

    @Test
    public void textWidgetsStartWithTheirNameAsDesignTimeText() {
        Activity activity = activity();
        ViewBean chronometer = IconRegistryWidget.create(activity, WidgetRegistry.byId("form.chronometer")).getBean();
        assertEquals("Chronometer", chronometer.text.text);
        ViewBean fab = IconRegistryWidget.create(activity, WidgetRegistry.byId("material.efab")).getBean();
        assertEquals("Extended FAB", fab.text.text);
        // The Material button's own corner radius must not leak into the extended FAB.
        assertEquals("", fab.inject);
    }

    @Test
    public void paletteSectionsTellHowManyWidgetsTheyHold() {
        Activity activity = activity();
        PaletteWidget palette = new PaletteWidget(activity);
        palette.extraTitle("Forms", 1);
        palette.addRegistryWidget(WidgetRegistry.byId("form.textclock"), false);
        palette.addRegistryWidget(WidgetRegistry.byId("form.chronometer"), false);
        palette.refreshSections();
        TextView header = findText(palette, "Forms");
        assertNotNull(header);
        assertEquals("Forms", header.getText().toString());
        assertEquals("Forms (2)", header.getContentDescription().toString());
    }

    @Test
    public void searchFindsRegistryWidgets() {
        Activity activity = activity();
        PaletteWidget palette = new PaletteWidget(activity);
        View tile = palette.addRegistryWidget(WidgetRegistry.byId("layout.table"), true);
        View other = palette.addRegistryWidget(WidgetRegistry.byId("layout.nestedscroll"), true);
        palette.filter("tablel");
        assertEquals(View.VISIBLE, tile.getVisibility());
        assertEquals(View.GONE, other.getVisibility());
        palette.filter("");
        assertEquals(View.VISIBLE, other.getVisibility());
    }

    private static TextView findText(View root, String contains) {
        if (root instanceof TextView text && text.getText().toString().contains(contains)) {
            return text;
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), contains);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
