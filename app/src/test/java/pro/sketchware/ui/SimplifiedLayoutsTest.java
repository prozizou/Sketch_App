package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;

/** Layouts flattened for lint (UselessParent, NestedWeights, Overdraw) keep their ids, order and look. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class SimplifiedLayoutsTest {
    private Activity activity() {
        RuntimeEnvironment.setQualifiers("+notnight");
        try {
            ThemeHarness.prepareApplication();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Context app = ApplicationProvider.getApplicationContext();
        return Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
    }

    private static View inflate(Activity activity, int layout, ViewGroup parent, int width, int height) {
        View root = LayoutInflater.from(activity).inflate(layout, parent, false);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                height > 0 ? View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY) : View.MeasureSpec.UNSPECIFIED);
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        return root;
    }

    @Test
    public void projectsListScrollsDirectlyUnderTheRefreshLayout() {
        Activity activity = activity();
        ViewGroup root = (ViewGroup) inflate(activity, R.layout.myprojects, null, 1080, 2340);
        assertEquals(1, root.getChildCount());
        assertTrue(root.getChildAt(0) instanceof NestedScrollView);
        // Title and subtitle sit directly in the title container, which keeps its id for the insets.
        LinearLayout title = root.findViewById(R.id.title_container);
        assertEquals(LinearLayout.VERTICAL, title.getOrientation());
        assertEquals(2, title.getChildCount());
        assertTrue(title.getChildAt(0) instanceof TextView);
        for (int id : new int[]{R.id.special_action_container, R.id.sort_chips, R.id.loading_container, R.id.myprojects}) {
            assertNotNull(root.findViewById(id));
        }
        // Same order as before: special action, title, sort chips, loading, list.
        ViewGroup column = (ViewGroup) root.findViewById(R.id.title_container).getParent();
        assertEquals(column, root.findViewById(R.id.special_action_container).getParent());
        assertEquals(column, root.findViewById(R.id.myprojects).getParent());
        assertTrue(column.indexOfChild(title) < column.indexOfChild(root.findViewById(R.id.myprojects)));
    }

    @Test
    public void gitScreenFillsTheHeightWithoutNestedWeights() {
        Activity activity = activity();
        View root = inflate(activity, R.layout.activity_project_git, null, 1080, 2340);
        View started = root.findViewById(R.id.started);
        started.setVisibility(View.VISIBLE);
        root.findViewById(R.id.notStarted).setVisibility(View.GONE);
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
        View column = (View) started.getParent();
        assertEquals("the started panel reaches the bottom", column.getHeight(), started.getBottom());
        View history = root.findViewById(R.id.history);
        assertTrue("the history list gets the rest of the height", history.getHeight() > 1000);
    }

    @Test
    public void settingsSearchAndWarningRowsAreSingleCards() {
        Activity activity = activity();
        FrameLayout list = new FrameLayout(activity);
        View search = inflate(activity, R.layout.preference_search, list, 1080, 0);
        assertTrue(search instanceof LinearLayout);
        assertNotNull(search.getBackground());
        assertNotNull(search.getForeground());
        assertNotNull(search.findViewById(android.R.id.title));
        assertEquals(Math.round(56 * activity.getResources().getDisplayMetrics().density), search.getHeight());

        View warning = inflate(activity, R.layout.preference_warning, list, 1080, 0);
        assertTrue(warning instanceof LinearLayout);
        assertNotNull(warning.getBackground());
        assertNotNull(warning.findViewById(android.R.id.title));
        assertNotNull(warning.findViewById(android.R.id.summary));
        // The 16 dp side gap that the wrapping FrameLayout's padding used to give is now the card's margin.
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) warning.getLayoutParams();
        assertEquals(Math.round(16 * activity.getResources().getDisplayMetrics().density), params.getMarginStart());
    }

    @Test
    public void listRowsKeepTheirTouchRippleAsForeground() {
        Activity activity = activity();
        FrameLayout list = new FrameLayout(activity);
        for (int layout : new int[]{R.layout.item_finding, R.layout.item_project_search_result, R.layout.item_snapshot,
                R.layout.item_whats_new, R.layout.preference_icon_row}) {
            View row = inflate(activity, layout, list, 1080, 0);
            String name = activity.getResources().getResourceEntryName(layout);
            assertNotNull(name + " keeps its ripple", row.getForeground());
            assertNull(name + " paints no background under the list", row.getBackground());
        }
    }
}
