package pro.sketchware.unityads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Every library the Unity Ads SDK {@value #SDK_VERSION} is published with (its POM's whole dependency tree, as the
 * downloader resolves it), each with a class that only that library provides. A project has a library when that class
 * is in the app; this is how the settings screen lists exactly which ones are missing and downloads them one by one.
 * <p>
 * For another SDK version the versions below may be out of date: the screen then offers the full reinstall only.
 */
public final class UnityAdsKnownLibraries {
    public static final String SDK_VERSION = "4.21.0";

    public static final class Entry {
        /** A class only this library contains, as a path inside a jar. */
        public final String classFile;
        /** group:artifact:version */
        public final String coordinate;

        Entry(String classFile, String coordinate) {
            this.classFile = classFile;
            this.coordinate = coordinate;
        }

        public String artifactId() {
            return coordinate.split(":")[1];
        }

        public String version() {
            return coordinate.split(":")[2];
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("androidx/activity/Api19Impl.class", "androidx.activity:activity-ktx:1.7.1"),
            new Entry("androidx/activity/Cancellable.class", "androidx.activity:activity:1.7.1"),
            new Entry("androidx/annotation/OptIn.class", "androidx.annotation:annotation-experimental:1.3.0"),
            new Entry("androidx/annotation/Px.class", "androidx.annotation:annotation:1.2.0"),
            new Entry("androidx/arch/core/util/Function.class", "androidx.arch.core:core-common:2.2.0"),
            new Entry("androidx/arch/core/executor/TaskExecutor.class", "androidx.arch.core:core-runtime:2.2.0"),
            new Entry("androidx/asynclayoutinflater/view/AsyncLayoutInflater.class", "androidx.asynclayoutinflater:asynclayoutinflater:1.0.0"),
            new Entry("androidx/collection/ArrayMap.class", "androidx.collection:collection:1.0.0"),
            new Entry("androidx/concurrent/futures/DirectExecutor.class", "androidx.concurrent:concurrent-futures:1.1.0"),
            new Entry("androidx/coordinatorlayout/widget/ViewGroupUtils.class", "androidx.coordinatorlayout:coordinatorlayout:1.0.0"),
            new Entry("androidx/core/net/UriKt.class", "androidx.core:core-ktx:1.9.0"),
            new Entry("androidx/core/util/Pair.class", "androidx.core:core:1.9.0"),
            new Entry("androidx/cursoradapter/widget/CursorFilter.class", "androidx.cursoradapter:cursoradapter:1.0.0"),
            new Entry("androidx/customview/view/AbsSavedState.class", "androidx.customview:customview:1.0.0"),
            new Entry("androidx/datastore/core/Data.class", "androidx.datastore:datastore-core:1.0.0"),
            new Entry("androidx/datastore/DataStoreFile.class", "androidx.datastore:datastore:1.0.0"),
            new Entry("androidx/documentfile/provider/DocumentFile.class", "androidx.documentfile:documentfile:1.0.0"),
            new Entry("androidx/drawerlayout/widget/DrawerLayout.class", "androidx.drawerlayout:drawerlayout:1.0.0"),
            new Entry("androidx/fragment/app/Fragment.class", "androidx.fragment:fragment:1.0.0"),
            new Entry("androidx/interpolator/view/animation/LookupTableInterpolator.class", "androidx.interpolator:interpolator:1.0.0"),
            new Entry("androidx/legacy/widget/Space.class", "androidx.legacy:legacy-support-core-ui:1.0.0"),
            new Entry("androidx/legacy/content/WakefulBroadcastReceiver.class", "androidx.legacy:legacy-support-core-utils:1.0.0"),
            new Entry("androidx/lifecycle/Lifecycle.class", "androidx.lifecycle:lifecycle-common:2.6.1"),
            new Entry("androidx/lifecycle/LiveData.class", "androidx.lifecycle:lifecycle-livedata-core:2.6.1"),
            new Entry("androidx/lifecycle/Transformations.class", "androidx.lifecycle:lifecycle-livedata:2.6.1"),
            new Entry("androidx/lifecycle/LifecycleDispatcher.class", "androidx.lifecycle:lifecycle-process:2.6.1"),
            new Entry("androidx/lifecycle/ViewKt.class", "androidx.lifecycle:lifecycle-runtime-ktx:2.6.1"),
            new Entry("androidx/lifecycle/ReportFragment.class", "androidx.lifecycle:lifecycle-runtime:2.6.1"),
            new Entry("androidx/lifecycle/LifecycleService.class", "androidx.lifecycle:lifecycle-service:2.6.1"),
            new Entry("androidx/lifecycle/ViewModelKt.class", "androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1"),
            new Entry("androidx/lifecycle/SavedStateHandle.class", "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1"),
            new Entry("androidx/lifecycle/ViewModel.class", "androidx.lifecycle:lifecycle-viewmodel:2.6.1"),
            new Entry("androidx/loader/content/Loader.class", "androidx.loader:loader:1.0.0"),
            new Entry("androidx/localbroadcastmanager/content/LocalBroadcastManager.class", "androidx.localbroadcastmanager:localbroadcastmanager:1.0.0"),
            new Entry("androidx/print/PrintHelper.class", "androidx.print:print:1.0.0"),
            new Entry("androidx/profileinstaller/Encoding.class", "androidx.profileinstaller:profileinstaller:1.3.0"),
            new Entry("androidx/room/Dao.class", "androidx.room:room-common:2.2.5"),
            new Entry("androidx/room/Room.class", "androidx.room:room-runtime:2.2.5"),
            new Entry("androidx/savedstate/ViewKt.class", "androidx.savedstate:savedstate-ktx:1.2.1"),
            new Entry("androidx/savedstate/Recreator.class", "androidx.savedstate:savedstate:1.2.1"),
            new Entry("androidx/slidingpanelayout/widget/SlidingPaneLayout.class", "androidx.slidingpanelayout:slidingpanelayout:1.0.0"),
            new Entry("androidx/sqlite/db/framework/FrameworkSQLiteProgram.class", "androidx.sqlite:sqlite-framework:2.1.0"),
            new Entry("androidx/sqlite/db/SimpleSQLiteQuery.class", "androidx.sqlite:sqlite:2.1.0"),
            new Entry("androidx/startup/Initializer.class", "androidx.startup:startup-runtime:1.1.1"),
            new Entry("androidx/swiperefreshlayout/widget/CircleImageView.class", "androidx.swiperefreshlayout:swiperefreshlayout:1.0.0"),
            new Entry("androidx/tracing/Trace.class", "androidx.tracing:tracing:1.0.0"),
            new Entry("androidx/versionedparcelable/ParcelImpl.class", "androidx.versionedparcelable:versionedparcelable:1.1.1"),
            new Entry("androidx/viewpager/widget/ViewPager.class", "androidx.viewpager:viewpager:1.0.0"),
            new Entry("androidx/webkit/ProxyConfig.class", "androidx.webkit:webkit:1.6.1"),
            new Entry("androidx/work/DataKt.class", "androidx.work:work-runtime-ktx:2.7.0"),
            new Entry("androidx/work/Data.class", "androidx.work:work-runtime:2.7.0"),
            new Entry("com/google/android/gms/common/zaa.class", "com.google.android.gms:play-services-base:18.0.1"),
            new Entry("com/google/android/gms/common/zza.class", "com.google.android.gms:play-services-basement:18.0.0"),
            new Entry("com/google/android/gms/net/zza.class", "com.google.android.gms:play-services-cronet:18.0.1"),
            new Entry("com/google/android/gms/tasks/zza.class", "com.google.android.gms:play-services-tasks:18.0.1"),
            new Entry("com/google/common/util/concurrent/ListenableFuture.class", "com.google.guava:listenablefuture:1.0"),
            new Entry("com/google/protobuf/Any.class", "com.google.protobuf:protobuf-javalite:3.25.6"),
            new Entry("com/google/protobuf/AnyKt.class", "com.google.protobuf:protobuf-kotlin-lite:3.25.6"),
            new Entry("okhttp3/Dns.class", "com.squareup.okhttp3:okhttp:3.12.13"),
            new Entry("okio/Okio.class", "com.squareup.okio:okio-jvm:3.18.2"),
            new Entry("com/unity3d/coherence/Coherence.class", "com.unity3d.coherence:coherencelib:0.2.0"),
            new Entry("org/chromium/net/ApiVersion.class", "org.chromium.net:cronet-api:72.3626.96"),
            new Entry("kotlin/Lazy.class", "org.jetbrains.kotlin:kotlin-stdlib:2.1.21"),
            new Entry("kotlinx/coroutines/android/HandlerContext.class", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"),
            new Entry("_COROUTINE/_BOUNDARY.class", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2"),
            new Entry("org/jetbrains/annotations/Nls.class", "org.jetbrains:annotations:23.0.0"),
            new Entry("com/unity3d/ads/AdFormat.class", "com.unity3d.ads:unity-ads:4.21.0")));

    private UnityAdsKnownLibraries() {
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static List<String> classFiles() {
        List<String> files = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            files.add(entry.classFile);
        }
        return files;
    }

    /** @param available class files found in libraries the build will include (with compiled code) */
    public static List<Entry> missing(Collection<String> available) {
        List<Entry> missing = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (!available.contains(entry.classFile)) {
                missing.add(entry);
            }
        }
        return missing;
    }
}
