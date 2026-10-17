package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityEvolveBinding;
import pro.sketchware.databinding.EvolveCommentItemBinding;
import pro.sketchware.databinding.EvolvePostItemBinding;
import pro.sketchware.evolve.EvolveActivity;
import pro.sketchware.evolve.EvolvePostActivity;
import pro.sketchware.evolve.EvolvePostCard;
import pro.sketchware.evolve.EvolvePublishActivity;
import pro.sketchware.evolve.PostMeta;
import pro.sketchware.evolve.SharedBlock;
import pro.sketchware.evolve.SharedBlockCodec;
import pro.sketchware.evolve.SharedKind;

/** The community page: its cards for every kind of post, its layouts, and its screens opening safely without Firebase. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class EvolveCommunityScreenTest {

    private static Activity host(boolean dark) {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        try {
            ThemeHarness.prepareApplication();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Context app = ApplicationProvider.getApplicationContext();
        return Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
    }

    private static final EvolvePostCard.Listener NOTHING = new EvolvePostCard.Listener() {
        @Override
        public void onOpen(SharedBlock post) {
        }

        @Override
        public void onLike(SharedBlock post) {
        }

        @Override
        public void onComments(SharedBlock post) {
        }

        @Override
        public void onSave(SharedBlock post) {
        }

        @Override
        public void onMenu(View anchor, SharedBlock post) {
        }
    };

    private static SharedBlock post(SharedKind kind, String name, PostMeta meta, String blocksJson, String xml) {
        return new SharedBlock("id-" + name, name, "Wraps the sign-in call, retries on network errors and reports the failure with a clear message.",
                "uid", "Aziz", 1_760_000_000_000L, 1, 3, List.of("if", "doToast"), false, 1204, blocksJson, kind, "", xml, meta);
    }

    private static PostMeta meta(String code, String file, int minSdk, List<String> deps, long likes, long comments) {
        return new PostMeta(List.of("auth", "helper", "retry"), "1.2", minSdk, deps, List.of(), 0, likes, comments, code, file);
    }

    private static ArrayList<BlockBean> stack() {
        BlockBean toast = new BlockBean("10", "showToast %s", " ", "showToast");
        toast.parameters.add("@11");
        BlockBean variable = new BlockBean("11", "counter", "s", "getVar");
        return new ArrayList<>(List.of(toast, variable));
    }

    private static EvolvePostItemBinding card(Activity activity, LinearLayout column, SharedBlock post, ArrayList<BlockBean> blocks, boolean liked, boolean saved) {
        EvolvePostItemBinding item = EvolvePostItemBinding.inflate(LayoutInflater.from(activity), column, false);
        EvolvePostCard.bind(item, post, blocks, liked, saved, 34, NOTHING);
        column.addView(item.getRoot());
        return item;
    }

    @Test
    public void everyKindOfPostRendersInsideItsCard() throws Exception {
        Locale.setDefault(Locale.US);
        for (boolean dark : new boolean[]{true, false}) {
            Activity activity = host(dark);
            SharedBlock java = post(SharedKind.JAVA, "Login helper", meta("package a;\n\npublic class LoginHelper {\n    // retries\n    void signIn() { int n = 3; }\n}", "LoginHelper.java", 28, List.of("com.squareup.okhttp3:okhttp:4.12.0"), 12, 3), "", "");
            SharedBlock kotlin = post(SharedKind.KOTLIN, "Extensions", meta("fun String.isEmail() = contains(\"@\")", "Extensions.kt", 0, List.of(), 0, 0), "", "");
            SharedBlock xml = post(SharedKind.VIEW, "Login screen", meta("", "", 0, List.of(), 5, 1), "",
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n  <TextView android:text=\"Hi\"/>\n</LinearLayout>");
            SharedBlock blocks = post(SharedKind.BLOCKS, "Toast helper", meta("", "", 0, List.of(), 1204, 12000), SharedBlockCodec.encode(stack()), "");
            SharedBlock newer = post(SharedKind.JAVA, "Needs Android 15", meta("class A {}", "A.java", 35, List.of(), 0, 0), "", "");

            for (int widthDp : new int[]{320, 411}) {
                LinearLayout column = new LinearLayout(activity);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setBackgroundColor(activity.getColor(R.color.evolve_bg));
                int pad = dp(activity, 8);
                column.setPadding(pad, pad, pad, pad);
                EvolvePostItemBinding javaCard = card(activity, column, java, null, true, false);
                EvolvePostItemBinding kotlinCard = card(activity, column, kotlin, null, false, true);
                EvolvePostItemBinding xmlCard = card(activity, column, xml, null, false, false);
                EvolvePostItemBinding blocksCard = card(activity, column, blocks, SharedBlockCodec.decode(SharedBlockCodec.encode(stack())), false, false);
                EvolvePostItemBinding newerCard = card(activity, column, newer, null, false, false);

                int width = dp(activity, widthDp);
                column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                column.layout(0, 0, width, column.getMeasuredHeight());

                assertEquals("Aziz", javaCard.postAuthor.getText().toString());
                assertTrue(javaCard.postDate.getText().toString().endsWith("v1.2"));
                assertEquals("Java", javaCard.postKind.getText().toString());
                assertEquals(View.VISIBLE, javaCard.postCode.getVisibility());
                assertTrue("the code is highlighted", ((android.text.Spanned) javaCard.postCode.getText()).getSpans(0, javaCard.postCode.length(), android.text.style.ForegroundColorSpan.class).length > 0);
                assertEquals(View.GONE, javaCard.postPreview.getVisibility());
                assertEquals(View.VISIBLE, javaCard.postNotice.getVisibility());
                assertTrue(javaCard.postNotice.getText().toString().contains("okhttp"));
                assertEquals("12", javaCard.postLike.getText().toString());
                assertEquals("3", javaCard.postComments.getText().toString());
                assertTrue(javaCard.postLike.getContentDescription().toString().startsWith("Remove like"));
                assertTrue(kotlinCard.postSave.getContentDescription().toString().startsWith("Remove from saved"));
                assertEquals("Kotlin", kotlinCard.postKind.getText().toString());
                assertEquals("1,204 downloads", javaCard.postDownloads.getText().toString());

                assertEquals("XML shows its text", View.VISIBLE, xmlCard.postCode.getVisibility());
                assertEquals("blocks are drawn", View.VISIBLE, blocksCard.postPreview.getVisibility());
                assertEquals(View.GONE, blocksCard.postCode.getVisibility());
                assertEquals("12,000", blocksCard.postComments.getText().toString());
                assertEquals(View.VISIBLE, newerCard.postNotice.getVisibility());
                assertTrue(newerCard.postNotice.getText().toString().contains("35"));

                for (EvolvePostItemBinding item : List.of(javaCard, kotlinCard, xmlCard, blocksCard, newerCard)) {
                    assertTrue("title stays within two lines", item.postTitle.getLineCount() <= 2);
                    assertTrue("the counters stay on screen", item.postDownloads.getRight() <= ((View) item.postDownloads.getParent()).getWidth());
                    assertTrue(item.postLike.getHeight() >= dp(activity, 40));
                    assertTrue(item.postMenu.getWidth() >= dp(activity, 40));
                }
                save(column, "evolve-community-cards-" + widthDp + "-" + (dark ? "dark" : "light"));
            }
        }
    }

    @Test
    public void aScreenshotReplacesTheCodePreviewOnTheCard() {
        Activity activity = host(true);
        PostMeta withShot = new PostMeta(List.of(), "", 0, List.of(), List.of("https://firebasestorage.googleapis.com/v0/b/x/o/a.jpg"), 0, 0, 0, "class A {}", "A.java");
        LinearLayout column = new LinearLayout(activity);
        EvolvePostItemBinding item = card(activity, column, post(SharedKind.JAVA, "Shot", withShot, "", ""), null, false, false);
        assertEquals(View.VISIBLE, item.postScreenshot.getVisibility());
        assertEquals(View.GONE, item.postCode.getVisibility());
        PostMeta foreign = new PostMeta(List.of(), "", 0, List.of(), List.of("https://evil.example.com/a.jpg"), 0, 0, 0, "class A {}", "A.java");
        EvolvePostItemBinding other = card(activity, column, post(SharedKind.JAVA, "Foreign", foreign, "", ""), null, false, false);
        assertEquals("only the app's own storage is shown", View.GONE, other.postScreenshot.getVisibility());
        assertEquals(View.VISIBLE, other.postCode.getVisibility());
    }

    @Test
    public void theCommunityLayoutsInflateInBothThemes() {
        for (boolean dark : new boolean[]{true, false}) {
            Activity activity = host(dark);
            ActivityEvolveBinding page = ActivityEvolveBinding.inflate(LayoutInflater.from(activity));
            assertNotNull(page.evolveFeed);
            assertEquals(3, page.evolveBottomNav.getMenu().size());
            assertEquals(R.id.evolve_nav_explore, page.evolveBottomNav.getMenu().getItem(0).getItemId());
            assertEquals(R.id.evolve_nav_saved, page.evolveBottomNav.getMenu().getItem(1).getItemId());
            assertEquals(R.id.evolve_nav_profile, page.evolveBottomNav.getMenu().getItem(2).getItemId());
            assertEquals(View.GONE, page.evolveStateLayout.getVisibility());
            EvolveCommentItemBinding comment = EvolveCommentItemBinding.inflate(LayoutInflater.from(activity));
            assertNotNull(comment.commentReply);
        }
    }

    @Test
    public void theCommunityPageOpensWithoutFirebaseAndSaysSo() {
        Activity host = host(true);
        EvolveActivity activity = Robolectric.buildActivity(EvolveActivity.class, EvolveActivity.intent(host)).setup().get();
        Robolectric.flushForegroundThreadScheduler();
        TextViewState state = new TextViewState(activity);
        assertEquals(View.VISIBLE, state.layout.getVisibility());
        assertEquals(activity.getString(R.string.evolve_unavailable), state.text);
        assertEquals("nothing to retry without an account", View.GONE, activity.findViewById(R.id.evolve_retry).getVisibility());
        // the kind filters are all there
        com.google.android.material.chip.ChipGroup kinds = activity.findViewById(R.id.evolve_kinds);
        assertEquals(7, kinds.getChildCount());
        // Publishing is refused with a message, not a crash
        activity.findViewById(R.id.evolve_publish).performClick();
        activity.findViewById(R.id.evolve_bottom_nav);
        com.google.android.material.bottomnavigation.BottomNavigationView nav = activity.findViewById(R.id.evolve_bottom_nav);
        nav.setSelectedItemId(R.id.evolve_nav_saved);
        nav.setSelectedItemId(R.id.evolve_nav_profile);
        nav.setSelectedItemId(R.id.evolve_nav_explore);
        assertEquals("the feed tabs are Popular, Recent, Following", 3, ((com.google.android.material.tabs.TabLayout) activity.findViewById(R.id.evolve_tabs)).getTabCount());
    }

    private static final class TextViewState {
        final View layout;
        final String text;

        TextViewState(Activity activity) {
            layout = activity.findViewById(R.id.evolve_state_layout);
            text = String.valueOf(((android.widget.TextView) activity.findViewById(R.id.evolve_state)).getText());
        }
    }

    @Test
    public void aPostScreenOpensWithoutFirebaseAndSaysSo() {
        Activity host = host(true);
        EvolvePostActivity activity = Robolectric.buildActivity(EvolvePostActivity.class, EvolvePostActivity.intent(host, "abc", false)).setup().get();
        Robolectric.flushForegroundThreadScheduler();
        android.widget.TextView state = activity.findViewById(R.id.post_state);
        assertEquals(View.VISIBLE, state.getVisibility());
        assertEquals(activity.getString(R.string.evolve_unavailable), state.getText().toString());
        assertEquals(View.GONE, activity.findViewById(R.id.reply_box).getVisibility());
    }

    @Test
    public void aPostScreenWithoutAnIdClosesAtOnce() {
        Activity host = host(true);
        EvolvePostActivity activity = Robolectric.buildActivity(EvolvePostActivity.class, new Intent(host, EvolvePostActivity.class)).setup().get();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void thePublishScreenClosesWithoutAnAccount() {
        Activity host = host(true);
        EvolvePublishActivity activity = Robolectric.buildActivity(EvolvePublishActivity.class, EvolvePublishActivity.intent(host, null)).setup().get();
        assertTrue("nothing can be published without Firebase", activity.isFinishing());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void save(View view, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
