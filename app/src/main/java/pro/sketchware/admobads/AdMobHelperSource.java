package pro.sketchware.admobads;

/**
 * Generates {@code AdMobHelper.java}, the one class added to a project that has the AdMob library switched on.
 * The AdMob blocks only call its static methods, so block code never has to import Google Mobile Ads classes.
 * <p>
 * It covers every ad format of Google's sample: interstitial, rewarded, rewarded interstitial, app open, adaptive
 * and medium-rectangle banners, native (also with video), an exit dialog with an ad and a loading dialog with an
 * ad. Loads and shows are safe to call at any time: showing an ad that is not loaded yet does nothing and reports
 * through {@code onAdFailed}.
 * <p>
 * The output must compile with ECJ at Java 1.7 level: no lambdas, no default methods. It only uses Google Mobile Ads
 * classes that exist in the SDK version built into the app and in the one used when a project is exported.
 */
public final class AdMobHelperSource {
    public static final String FILE_NAME = "AdMobHelper.java";

    /** Google's public sample ad units: they always fill and never count as real impressions. */
    public static final String TEST_BANNER = "ca-app-pub-3940256099942544/6300978111";
    public static final String TEST_ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741";
    public static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    public static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    public static final String TEST_REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379";
    public static final String TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    public static final String TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110";
    public static final String TEST_NATIVE_VIDEO = "ca-app-pub-3940256099942544/1044960115";

    private AdMobHelperSource() {
    }

    /**
     * @param bannerId       the banner ad unit saved in the AdMob library settings, or empty
     * @param interstitialId the interstitial ad unit saved in the settings, or empty
     * @param rewardedId     the rewarded ad unit saved in the settings, or empty
     *                       (an empty one becomes Google's test ad unit, so a first run always shows something)
     */
    public static String generate(String packageName, String bannerId, String interstitialId, String rewardedId) {
        return TEMPLATE
                .replace("%PACKAGE%", packageName)
                .replace("%LIB_BANNER%", escape(orDefault(bannerId, TEST_BANNER)))
                .replace("%LIB_INTERSTITIAL%", escape(orDefault(interstitialId, TEST_INTERSTITIAL)))
                .replace("%LIB_REWARDED%", escape(orDefault(rewardedId, TEST_REWARDED)))
                .replace("%TEST_BANNER%", TEST_BANNER)
                .replace("%TEST_ADAPTIVE_BANNER%", TEST_ADAPTIVE_BANNER)
                .replace("%TEST_INTERSTITIAL%", TEST_INTERSTITIAL)
                .replace("%TEST_REWARDED_INTERSTITIAL%", TEST_REWARDED_INTERSTITIAL)
                .replace("%TEST_REWARDED%", TEST_REWARDED)
                .replace("%TEST_APP_OPEN%", TEST_APP_OPEN)
                .replace("%TEST_NATIVE_VIDEO%", TEST_NATIVE_VIDEO)
                .replace("%TEST_NATIVE%", TEST_NATIVE);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "");
    }

    private static final String TEMPLATE = """
            package %PACKAGE%;

            import android.app.Activity;
            import android.app.AlertDialog;
            import android.app.Dialog;
            import android.content.Context;
            import android.content.DialogInterface;
            import android.content.res.Configuration;
            import android.graphics.Color;
            import android.graphics.Typeface;
            import android.graphics.drawable.ColorDrawable;
            import android.graphics.drawable.GradientDrawable;
            import android.os.Handler;
            import android.os.Looper;
            import android.util.DisplayMetrics;
            import android.util.Log;
            import android.view.Gravity;
            import android.view.View;
            import android.view.ViewGroup;
            import android.view.Window;
            import android.widget.Button;
            import android.widget.FrameLayout;
            import android.widget.ImageView;
            import android.widget.LinearLayout;
            import android.widget.ProgressBar;
            import android.widget.TextView;

            import com.google.android.gms.ads.AdError;
            import com.google.android.gms.ads.AdListener;
            import com.google.android.gms.ads.AdLoader;
            import com.google.android.gms.ads.AdRequest;
            import com.google.android.gms.ads.AdSize;
            import com.google.android.gms.ads.AdView;
            import com.google.android.gms.ads.FullScreenContentCallback;
            import com.google.android.gms.ads.LoadAdError;
            import com.google.android.gms.ads.MobileAds;
            import com.google.android.gms.ads.OnUserEarnedRewardListener;
            import com.google.android.gms.ads.VideoOptions;
            import com.google.android.gms.ads.appopen.AppOpenAd;
            import com.google.android.gms.ads.interstitial.InterstitialAd;
            import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
            import com.google.android.gms.ads.nativead.MediaView;
            import com.google.android.gms.ads.nativead.NativeAd;
            import com.google.android.gms.ads.nativead.NativeAdOptions;
            import com.google.android.gms.ads.nativead.NativeAdView;
            import com.google.android.gms.ads.rewarded.RewardItem;
            import com.google.android.gms.ads.rewarded.RewardedAd;
            import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
            import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
            import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;

            /**
             * Generated by Sketchware NWS. Everything the AdMob blocks use lives here; it is regenerated on every
             * build, so changes made by hand are lost. Copy this file to the project's Java files to keep your own.
             */
            public final class AdMobHelper {
                private static final String TAG = "AdMobHelper";

                public static final String LIB_BANNER = "%LIB_BANNER%";
                public static final String LIB_INTERSTITIAL = "%LIB_INTERSTITIAL%";
                public static final String LIB_REWARDED = "%LIB_REWARDED%";
                public static final String TEST_BANNER = "%TEST_BANNER%";
                public static final String TEST_ADAPTIVE_BANNER = "%TEST_ADAPTIVE_BANNER%";
                public static final String TEST_INTERSTITIAL = "%TEST_INTERSTITIAL%";
                public static final String TEST_REWARDED = "%TEST_REWARDED%";
                public static final String TEST_REWARDED_INTERSTITIAL = "%TEST_REWARDED_INTERSTITIAL%";
                public static final String TEST_APP_OPEN = "%TEST_APP_OPEN%";
                public static final String TEST_NATIVE = "%TEST_NATIVE%";
                public static final String TEST_NATIVE_VIDEO = "%TEST_NATIVE_VIDEO%";

                /** Receives the ad type ("interstitial", "rewarded", ...) or, for failures, an error message. */
                public interface Callback {
                    void onEvent(String value);
                }

                public static Callback onAdLoaded;
                public static Callback onAdFailed;
                public static Callback onAdShown;
                public static Callback onAdClosed;
                public static Callback onUserRewarded;

                private static final Handler MAIN = new Handler(Looper.getMainLooper());
                private static boolean initialized;
                private static InterstitialAd interstitial;
                private static RewardedAd rewarded;
                private static RewardedInterstitialAd rewardedInterstitial;
                private static AppOpenAd appOpen;
                private static boolean showingFullScreen;
                private static boolean rewardEarned;
                private static String rewardedInterstitialId = "";
                private static int rewardAmount;
                private static String rewardType = "";
                private static Dialog loadingDialog;

                private AdMobHelper() {
                }

                // ---- setup

                public static void initialize(Context context) {
                    if (initialized) {
                        return;
                    }
                    initialized = true;
                    try {
                        MobileAds.initialize(context.getApplicationContext());
                    } catch (Throwable e) {
                        initialized = false;
                        failed("initialize: " + e);
                    }
                }

                public static boolean isInitialized() {
                    return initialized;
                }

                public static int rewardAmount() {
                    return rewardAmount;
                }

                public static String rewardType() {
                    return rewardType;
                }

                // ---- interstitial

                public static void loadInterstitial(final Activity activity, final String id) {
                    initialize(activity);
                    try {
                        InterstitialAd.load(activity, id, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
                            @Override
                            public void onAdLoaded(InterstitialAd ad) {
                                interstitial = ad;
                                loaded("interstitial");
                            }

                            @Override
                            public void onAdFailedToLoad(LoadAdError error) {
                                interstitial = null;
                                failed("interstitial load: " + error.getMessage());
                            }
                        });
                    } catch (Throwable e) {
                        failed("interstitial load: " + e);
                    }
                }

                public static boolean isInterstitialLoaded() {
                    return interstitial != null;
                }

                public static void showInterstitial(Activity activity) {
                    final InterstitialAd ad = interstitial;
                    if (ad == null) {
                        failed("interstitial is not loaded yet");
                        return;
                    }
                    interstitial = null;
                    ad.setFullScreenContentCallback(fullScreen("interstitial", null));
                    ad.show(activity);
                }

                // ---- rewarded

                public static void loadRewarded(final Activity activity, final String id) {
                    initialize(activity);
                    try {
                        RewardedAd.load(activity, id, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                            @Override
                            public void onAdLoaded(RewardedAd ad) {
                                rewarded = ad;
                                loaded("rewarded");
                            }

                            @Override
                            public void onAdFailedToLoad(LoadAdError error) {
                                rewarded = null;
                                failed("rewarded load: " + error.getMessage());
                            }
                        });
                    } catch (Throwable e) {
                        failed("rewarded load: " + e);
                    }
                }

                public static boolean isRewardedLoaded() {
                    return rewarded != null;
                }

                public static void showRewarded(Activity activity) {
                    final RewardedAd ad = rewarded;
                    if (ad == null) {
                        failed("rewarded is not loaded yet");
                        return;
                    }
                    rewarded = null;
                    ad.setFullScreenContentCallback(fullScreen("rewarded", null));
                    ad.show(activity, earned("rewarded"));
                }

                // ---- rewarded interstitial

                public static void loadRewardedInterstitial(final Activity activity, final String id) {
                    initialize(activity);
                    rewardedInterstitialId = id;
                    try {
                        RewardedInterstitialAd.load(activity, id, new AdRequest.Builder().build(), new RewardedInterstitialAdLoadCallback() {
                            @Override
                            public void onAdLoaded(RewardedInterstitialAd ad) {
                                rewardedInterstitial = ad;
                                loaded("rewarded interstitial");
                            }

                            @Override
                            public void onAdFailedToLoad(LoadAdError error) {
                                rewardedInterstitial = null;
                                failed("rewarded interstitial load: " + error.getMessage());
                            }
                        });
                    } catch (Throwable e) {
                        failed("rewarded interstitial load: " + e);
                    }
                }

                public static boolean isRewardedInterstitialLoaded() {
                    return rewardedInterstitial != null;
                }

                /**
                 * @param cancelable false keeps the player in the ad (immersive mode) and loads and shows another
                 *                   one when it is closed before the reward was earned
                 */
                public static void showRewardedInterstitial(Activity activity, boolean cancelable) {
                    RewardedInterstitialAd ad = rewardedInterstitial;
                    if (ad == null) {
                        failed("rewarded interstitial is not loaded yet");
                        return;
                    }
                    rewardedInterstitial = null;
                    showRewardedInterstitialAd(activity, ad, cancelable);
                }

                private static void showRewardedInterstitialAd(final Activity activity, RewardedInterstitialAd ad, final boolean cancelable) {
                    rewardEarned = false;
                    ad.setImmersiveMode(!cancelable);
                    ad.setFullScreenContentCallback(fullScreen("rewarded interstitial", new Runnable() {
                        @Override
                        public void run() {
                            if (!cancelable && !rewardEarned) {
                                MAIN.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        showAnotherRewardedInterstitial(activity);
                                    }
                                }, 1500);
                            }
                        }
                    }));
                    ad.show(activity, new OnUserEarnedRewardListener() {
                        @Override
                        public void onUserEarnedReward(RewardItem item) {
                            rewardEarned = true;
                            rewardType = item.getType();
                            rewardAmount = item.getAmount();
                            fire(onUserRewarded, "rewarded interstitial");
                        }
                    });
                }

                private static void showAnotherRewardedInterstitial(final Activity activity) {
                    if (rewardedInterstitialId.isEmpty() || activity.isFinishing()) {
                        return;
                    }
                    try {
                        RewardedInterstitialAd.load(activity, rewardedInterstitialId, new AdRequest.Builder().build(), new RewardedInterstitialAdLoadCallback() {
                            @Override
                            public void onAdLoaded(RewardedInterstitialAd ad) {
                                showRewardedInterstitialAd(activity, ad, false);
                            }

                            @Override
                            public void onAdFailedToLoad(LoadAdError error) {
                                failed("rewarded interstitial load: " + error.getMessage());
                            }
                        });
                    } catch (Throwable e) {
                        failed("rewarded interstitial load: " + e);
                    }
                }

                // ---- app open

                public static void loadAppOpen(final Activity activity, final String id) {
                    initialize(activity);
                    if (appOpen != null) {
                        return;
                    }
                    int orientation = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                            ? AppOpenAd.APP_OPEN_AD_ORIENTATION_LANDSCAPE : AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT;
                    try {
                        AppOpenAd.load(activity, id, new AdRequest.Builder().build(), orientation, new AppOpenAd.AppOpenAdLoadCallback() {
                            @Override
                            public void onAdLoaded(AppOpenAd ad) {
                                appOpen = ad;
                                loaded("app open");
                            }

                            @Override
                            public void onAdFailedToLoad(LoadAdError error) {
                                appOpen = null;
                                failed("app open load: " + error.getMessage());
                            }
                        });
                    } catch (Throwable e) {
                        failed("app open load: " + e);
                    }
                }

                public static boolean isAppOpenLoaded() {
                    return appOpen != null;
                }

                public static void showAppOpen(Activity activity) {
                    final AppOpenAd ad = appOpen;
                    if (ad == null || showingFullScreen) {
                        failed("app open ad is not ready");
                        return;
                    }
                    appOpen = null;
                    ad.setFullScreenContentCallback(fullScreen("app open", null));
                    ad.show(activity);
                }

                // ---- banners

                /** An anchored adaptive banner as wide as {@code container}, which must be a layout (not a button). */
                public static void showAdaptiveBanner(final Activity activity, final String id, final ViewGroup container) {
                    initialize(activity);
                    container.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                AdView adView = new AdView(activity);
                                adView.setAdUnitId(id);
                                float density = activity.getResources().getDisplayMetrics().density;
                                float width = container.getWidth();
                                if (width == 0) {
                                    width = activity.getResources().getDisplayMetrics().widthPixels;
                                }
                                adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, (int) (width / density)));
                                container.removeAllViews();
                                container.addView(adView);
                                adView.setAdListener(listener("banner"));
                                adView.loadAd(new AdRequest.Builder().build());
                            } catch (Throwable e) {
                                failed("banner: " + e);
                            }
                        }
                    });
                }

                /** A 300x250 medium rectangle. */
                public static void showMediumBanner(Activity activity, String id, ViewGroup container) {
                    initialize(activity);
                    try {
                        AdView adView = new AdView(activity);
                        adView.setAdSize(AdSize.MEDIUM_RECTANGLE);
                        adView.setAdUnitId(id);
                        container.removeAllViews();
                        container.addView(adView);
                        adView.setAdListener(listener("banner"));
                        adView.loadAd(new AdRequest.Builder().build());
                    } catch (Throwable e) {
                        failed("banner: " + e);
                    }
                }

                // ---- native

                /** A native ad in a card the helper builds itself; {@code withVideo} lets video ads start muted. */
                public static void showNative(final Activity activity, final String id, final ViewGroup container, boolean withVideo) {
                    initialize(activity);
                    try {
                        NativeAdOptions.Builder options = new NativeAdOptions.Builder();
                        if (withVideo) {
                            options.setVideoOptions(new VideoOptions.Builder().setStartMuted(true).build());
                        }
                        AdLoader loader = new AdLoader.Builder(activity, id)
                                .forNativeAd(new NativeAd.OnNativeAdLoadedListener() {
                                    @Override
                                    public void onNativeAdLoaded(NativeAd nativeAd) {
                                        container.removeAllViews();
                                        container.addView(nativeView(activity, nativeAd));
                                    }
                                })
                                .withAdListener(listener("native"))
                                .withNativeAdOptions(options.build())
                                .build();
                        loader.loadAd(new AdRequest.Builder().build());
                    } catch (Throwable e) {
                        failed("native: " + e);
                    }
                }

                private static int dp(Context context, int value) {
                    return Math.round(value * context.getResources().getDisplayMetrics().density);
                }

                private static View nativeView(Context context, NativeAd nativeAd) {
                    NativeAdView adView = new NativeAdView(context);
                    GradientDrawable card = new GradientDrawable();
                    card.setColor(Color.WHITE);
                    card.setStroke(dp(context, 1), 0xFFDADCE0);
                    card.setCornerRadius(dp(context, 12));
                    adView.setBackground(card);
                    adView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    LinearLayout column = new LinearLayout(context);
                    column.setOrientation(LinearLayout.VERTICAL);
                    column.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
                    adView.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    LinearLayout header = new LinearLayout(context);
                    header.setOrientation(LinearLayout.HORIZONTAL);
                    header.setGravity(Gravity.CENTER_VERTICAL);
                    column.addView(header);

                    ImageView icon = new ImageView(context);
                    header.addView(icon, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));
                    adView.setIconView(icon);

                    LinearLayout titles = new LinearLayout(context);
                    titles.setOrientation(LinearLayout.VERTICAL);
                    titles.setPadding(dp(context, 10), 0, 0, 0);
                    header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    TextView headline = new TextView(context);
                    headline.setTextSize(16);
                    headline.setTypeface(Typeface.DEFAULT_BOLD);
                    headline.setTextColor(0xFF202124);
                    titles.addView(headline);
                    adView.setHeadlineView(headline);

                    TextView advertiser = new TextView(context);
                    advertiser.setTextSize(12);
                    advertiser.setTextColor(0xFF5F6368);
                    titles.addView(advertiser);
                    adView.setAdvertiserView(advertiser);

                    TextView badge = new TextView(context);
                    badge.setText("Ad");
                    badge.setTextSize(11);
                    badge.setTextColor(Color.WHITE);
                    badge.setPadding(dp(context, 6), dp(context, 1), dp(context, 6), dp(context, 1));
                    GradientDrawable badgeBackground = new GradientDrawable();
                    badgeBackground.setColor(0xFFFBBC04);
                    badgeBackground.setCornerRadius(dp(context, 4));
                    badge.setBackground(badgeBackground);
                    header.addView(badge);

                    MediaView media = new MediaView(context);
                    LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 180));
                    mediaParams.topMargin = dp(context, 10);
                    column.addView(media, mediaParams);
                    adView.setMediaView(media);

                    TextView body = new TextView(context);
                    body.setTextSize(13);
                    body.setTextColor(0xFF3C4043);
                    body.setPadding(0, dp(context, 8), 0, 0);
                    column.addView(body);
                    adView.setBodyView(body);

                    Button action = new Button(context);
                    action.setAllCaps(false);
                    LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    actionParams.topMargin = dp(context, 8);
                    column.addView(action, actionParams);
                    adView.setCallToActionView(action);

                    headline.setText(nativeAd.getHeadline());
                    if (nativeAd.getMediaContent() != null) {
                        media.setMediaContent(nativeAd.getMediaContent());
                        media.setVisibility(View.VISIBLE);
                    } else {
                        media.setVisibility(View.GONE);
                    }
                    if (nativeAd.getBody() == null) {
                        body.setVisibility(View.GONE);
                    } else {
                        body.setText(nativeAd.getBody());
                    }
                    if (nativeAd.getCallToAction() == null) {
                        action.setVisibility(View.GONE);
                    } else {
                        action.setText(nativeAd.getCallToAction());
                    }
                    if (nativeAd.getIcon() == null) {
                        icon.setVisibility(View.GONE);
                    } else {
                        icon.setImageDrawable(nativeAd.getIcon().getDrawable());
                    }
                    if (nativeAd.getAdvertiser() == null) {
                        advertiser.setVisibility(View.GONE);
                    } else {
                        advertiser.setText(nativeAd.getAdvertiser());
                    }
                    adView.setNativeAd(nativeAd);
                    return adView;
                }

                // ---- dialogs

                /** "Exit app?" with a medium banner; Yes closes the app, Later stays. A failed ad still asks. */
                public static void showExitDialog(final Activity activity, String id) {
                    initialize(activity);
                    final AdView adView = new AdView(activity);
                    adView.setAdSize(AdSize.MEDIUM_RECTANGLE);
                    adView.setAdUnitId(id);
                    adView.setAdListener(new AdListener() {
                        @Override
                        public void onAdLoaded() {
                            exitDialog(activity, adView);
                        }

                        @Override
                        public void onAdFailedToLoad(LoadAdError error) {
                            failed("exit dialog: " + error.getMessage());
                            exitDialog(activity, null);
                        }
                    });
                    adView.loadAd(new AdRequest.Builder().build());
                }

                private static void exitDialog(final Activity activity, AdView adView) {
                    if (activity.isFinishing()) {
                        return;
                    }
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setGravity(Gravity.CENTER_HORIZONTAL);
                    if (adView != null) {
                        layout.addView(adView);
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle("Exit app?")
                            .setView(layout)
                            .setCancelable(false)
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    activity.finishAffinity();
                                }
                            })
                            .setNegativeButton("Later", null)
                            .show();
                }

                /** A dialog with a spinner and a medium banner, for the time a long task takes. Close it with hideLoading. */
                public static void showLoading(Activity activity, String id) {
                    initialize(activity);
                    hideLoading();
                    try {
                        Dialog dialog = new Dialog(activity);
                        dialog.setCancelable(false);
                        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                        LinearLayout base = new LinearLayout(activity);
                        base.setOrientation(LinearLayout.VERTICAL);
                        base.setGravity(Gravity.CENTER_HORIZONTAL);
                        base.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
                        LinearLayout row = new LinearLayout(activity);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.addView(new ProgressBar(activity));
                        TextView text = new TextView(activity);
                        text.setText("Loading...");
                        text.setTextColor(Color.WHITE);
                        text.setPadding(dp(activity, 8), 0, 0, 0);
                        row.addView(text);
                        base.addView(row);
                        final LinearLayout adBox = new LinearLayout(activity);
                        adBox.setGravity(Gravity.CENTER);
                        base.addView(adBox);

                        final AdView adView = new AdView(activity);
                        adView.setAdSize(AdSize.MEDIUM_RECTANGLE);
                        adView.setAdUnitId(id);
                        adView.setAdListener(new AdListener() {
                            @Override
                            public void onAdLoaded() {
                                adBox.removeAllViews();
                                adBox.addView(adView);
                            }

                            @Override
                            public void onAdFailedToLoad(LoadAdError error) {
                                failed("loading dialog: " + error.getMessage());
                            }
                        });
                        adView.loadAd(new AdRequest.Builder().build());
                        dialog.setContentView(base);
                        dialog.show();
                        loadingDialog = dialog;
                    } catch (Throwable e) {
                        failed("loading dialog: " + e);
                    }
                }

                public static void hideLoading() {
                    Dialog dialog = loadingDialog;
                    loadingDialog = null;
                    if (dialog != null && dialog.isShowing()) {
                        try {
                            dialog.dismiss();
                        } catch (Throwable ignored) {
                            // the activity is already gone
                        }
                    }
                }

                // ---- events

                private static void loaded(String type) {
                    Log.d(TAG, type + " ad loaded");
                    fire(onAdLoaded, type);
                }

                private static void failed(String message) {
                    Log.e(TAG, message);
                    fire(onAdFailed, message);
                }

                private static void fire(final Callback callback, final String value) {
                    if (callback == null) {
                        return;
                    }
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        callback.onEvent(value);
                    } else {
                        MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onEvent(value);
                            }
                        });
                    }
                }

                private static AdListener listener(final String type) {
                    return new AdListener() {
                        @Override
                        public void onAdLoaded() {
                            loaded(type);
                        }

                        @Override
                        public void onAdFailedToLoad(LoadAdError error) {
                            failed(type + " load: " + error.getMessage());
                        }
                    };
                }

                private static OnUserEarnedRewardListener earned(final String type) {
                    return new OnUserEarnedRewardListener() {
                        @Override
                        public void onUserEarnedReward(RewardItem item) {
                            rewardType = item.getType();
                            rewardAmount = item.getAmount();
                            fire(onUserRewarded, type);
                        }
                    };
                }

                private static FullScreenContentCallback fullScreen(final String type, final Runnable afterClose) {
                    return new FullScreenContentCallback() {
                        @Override
                        public void onAdShowedFullScreenContent() {
                            showingFullScreen = true;
                            fire(onAdShown, type);
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(AdError error) {
                            showingFullScreen = false;
                            failed(type + " show: " + error.getMessage());
                        }

                        @Override
                        public void onAdDismissedFullScreenContent() {
                            showingFullScreen = false;
                            fire(onAdClosed, type);
                            if (afterClose != null) {
                                afterClose.run();
                            }
                        }
                    };
                }
            }
            """;
}
