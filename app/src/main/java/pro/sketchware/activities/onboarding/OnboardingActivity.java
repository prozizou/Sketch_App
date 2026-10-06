package pro.sketchware.activities.onboarding;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;

import pro.sketchware.R;

/**
 * First-launch guide: welcome, file access (with a button that actually requests it) and where to get
 * old projects back from. Shown once; {@link #isNeeded(Context)} tells MainActivity whether to launch it.
 */
public class OnboardingActivity extends AppCompatActivity {

    public static final String TELEGRAM_URL = "https://t.me/nwsketch";
    private static final String PREFS = "onboarding";
    private static final String KEY_DONE = "done";
    private static final int REQUEST_STORAGE = 9501;
    private static final int PAGE_COUNT = 3;
    private static final int DOT_SIZE_DP = 8;
    private static final int SELECTED_DOT_WIDTH_DP = 22;

    private ViewPager pager;
    private MaterialButton nextButton;
    private MaterialButton accessButton;
    private View[] dots;

    public static boolean isNeeded(Context context) {
        return !context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DONE, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        View root = findViewById(R.id.onboarding_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });

        pager = findViewById(R.id.pager);
        nextButton = findViewById(R.id.btn_next);
        pager.setAdapter(new PagesAdapter());
        buildDots();

        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updateControls(position);
            }
        });
        nextButton.setOnClickListener(v -> {
            if (pager.getCurrentItem() < PAGE_COUNT - 1) {
                pager.setCurrentItem(pager.getCurrentItem() + 1, true);
            } else {
                complete();
            }
        });
        findViewById(R.id.btn_skip).setOnClickListener(v -> complete());
        updateControls(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccessButton();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshAccessButton();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (pager.getCurrentItem() > 0) {
            pager.setCurrentItem(pager.getCurrentItem() - 1, true);
        } else {
            complete();
        }
    }

    private void complete() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply();
        setResult(RESULT_OK);
        finish();
    }

    private void buildDots() {
        android.widget.LinearLayout container = findViewById(R.id.dots);
        float dip = getResources().getDisplayMetrics().density;
        dots = new View[PAGE_COUNT];
        for (int i = 0; i < PAGE_COUNT; i++) {
            View dot = new View(this);
            // A bare View with WRAP_CONTENT fills all the space it is offered, so the size must be explicit.
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams((int) (DOT_SIZE_DP * dip), (int) (DOT_SIZE_DP * dip));
            params.setMargins((int) (4 * dip), 0, (int) (4 * dip), 0);
            container.addView(dot, params);
            dots[i] = dot;
        }
    }

    private void updateControls(int position) {
        float dip = getResources().getDisplayMetrics().density;
        for (int i = 0; i < dots.length; i++) {
            boolean selected = i == position;
            dots[i].setBackgroundResource(selected ? R.drawable.bg_onboarding_dot_on : R.drawable.bg_onboarding_dot_off);
            ViewGroup.LayoutParams params = dots[i].getLayoutParams();
            params.width = (int) ((selected ? SELECTED_DOT_WIDTH_DP : DOT_SIZE_DP) * dip);
            params.height = (int) (DOT_SIZE_DP * dip);
            dots[i].setLayoutParams(params);
        }
        boolean last = position == PAGE_COUNT - 1;
        nextButton.setText(last ? R.string.onboarding_get_started : R.string.onboarding_next);
        findViewById(R.id.btn_skip).setVisibility(last ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * The editor only needs the regular storage permission. "All files access" is optional (it makes builds
     * faster on Android 11+) and is offered separately, and skippably, by the main screen.
     */
    private boolean hasFileAccess() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshAccessButton() {
        if (accessButton == null) return;
        boolean granted = hasFileAccess();
        accessButton.setText(granted ? R.string.onboarding_access_granted : R.string.onboarding_access_button);
        accessButton.setEnabled(!granted);
    }

    private void requestFileAccess() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
    }

    private void openTelegram() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)));
        } catch (Exception ignored) {
        }
    }

    private class PagesAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View page = LayoutInflater.from(container.getContext()).inflate(R.layout.onboarding_page, container, false);
            ImageView icon = page.findViewById(R.id.page_icon);
            TextView title = page.findViewById(R.id.page_title);
            TextView description = page.findViewById(R.id.page_description);
            MaterialButton action = page.findViewById(R.id.page_action);

            switch (position) {
                case 0 -> {
                    icon.setImageResource(R.drawable.ic_mtrl_component);
                    title.setText(R.string.onboarding_welcome_title);
                    description.setText(R.string.onboarding_welcome_text);
                }
                case 1 -> {
                    icon.setImageResource(R.drawable.ic_mtrl_shield_lock);
                    title.setText(R.string.onboarding_access_title);
                    description.setText(R.string.onboarding_access_text);
                    action.setVisibility(View.VISIBLE);
                    action.setOnClickListener(v -> requestFileAccess());
                    accessButton = action;
                    refreshAccessButton();
                }
                default -> {
                    icon.setImageResource(R.drawable.ic_mtrl_history);
                    title.setText(R.string.onboarding_projects_title);
                    description.setText(R.string.onboarding_projects_text);
                    action.setVisibility(View.VISIBLE);
                    action.setText(R.string.onboarding_join_telegram);
                    action.setOnClickListener(v -> openTelegram());
                }
            }
            container.addView(page);
            return page;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }
    }
}
