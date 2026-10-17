package pro.sketchware.evolve;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.auth.AuthGate;
import pro.sketchware.evolve.EvolveFeed.Section;
import pro.sketchware.evolve.EvolveFeed.Tab;
import pro.sketchware.evolve.EvolveSocialRepository.Registration;

/**
 * The state of the community page. It owns the live listeners (the newest posts, the user's likes, saves and follows,
 * their notifications, the connection), keeps what they report in an {@link EvolveFeed}, and tells the screen what
 * to show through LiveData, so a rotation or a trip to a post and back costs nothing. The screens never touch Firebase.
 */
public class EvolveViewModel extends AndroidViewModel {

    public static final int PAGE_SIZE = 30;
    private static final int POPULAR_LIMIT = 50;
    private static final int FEW_ROWS = 6;
    private static final int MAX_AUTO_PAGES = 10;
    private static final String CACHE_FILE = "evolve_feed.json";

    private final EvolveFeed feed = new EvolveFeed();
    private final File cacheFile;
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Registration> registrations = new ArrayList<>();

    private final MutableLiveData<List<SharedBlock>> shown = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> offline = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> fromCopy = new MutableLiveData<>(false);
    private final MutableLiveData<Set<String>> liked = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Set<String>> savedIds = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Set<String>> following = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<List<EvolveNotification>> notifications = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> moderator = new MutableLiveData<>(false);

    private Section section = Section.EXPLORE;
    private Tab tab = Tab.RECENT;
    @Nullable
    private SharedKind kind;
    private String query = "";
    private List<String> savedOrder = new ArrayList<>();
    private boolean hasMore = true;
    private boolean paging;
    private boolean started;
    private int autoPages;
    @Nullable
    private EvolveProfile me;

    public EvolveViewModel(@NonNull Application application) {
        super(application);
        cacheFile = new File(application.getCacheDir(), CACHE_FILE);
    }

    // ---------------------------------------------------------------------------------------------- what the screen reads

    public LiveData<List<SharedBlock>> shown() {
        return shown;
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    /** The reason the feed can't be read, or null. */
    public LiveData<String> error() {
        return error;
    }

    public LiveData<Boolean> offline() {
        return offline;
    }

    /** Whether what is shown is the copy kept on the device (the live data hasn't arrived). */
    public LiveData<Boolean> fromCopy() {
        return fromCopy;
    }

    public LiveData<Set<String>> liked() {
        return liked;
    }

    public LiveData<Set<String>> savedIds() {
        return savedIds;
    }

    public LiveData<Set<String>> following() {
        return following;
    }

    public LiveData<List<EvolveNotification>> notifications() {
        return notifications;
    }

    public LiveData<Boolean> moderator() {
        return moderator;
    }

    public Section section() {
        return section;
    }

    public Tab tab() {
        return tab;
    }

    @Nullable
    public SharedKind kind() {
        return kind;
    }

    public String query() {
        return query;
    }

    /** @return the signed-in user as the community sees them, or null when nobody is signed in or Firebase is off */
    @Nullable
    public EvolveProfile me() {
        return me;
    }

    public boolean available() {
        return me != null;
    }

    public String myUid() {
        return me == null ? "" : me.uid();
    }

    // ----------------------------------------------------------------------------------------------------- the inputs

    public void setSection(Section section) {
        if (this.section == section) return;
        this.section = section;
        autoPages = 0;
        loadSectionData();
        recompute();
    }

    public void setTab(Tab tab) {
        if (this.tab == tab) return;
        this.tab = tab;
        autoPages = 0;
        loadSectionData();
        recompute();
    }

    public void setKind(@Nullable SharedKind kind) {
        this.kind = kind;
        autoPages = 0;
        recompute();
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query;
        autoPages = 0;
        recompute();
    }

    // ------------------------------------------------------------------------------------------------------ the data

    /** Starts listening. Safe to call again: it only does anything the first time. */
    public void start() {
        if (started) return;
        started = true;
        FirebaseUser user = AuthGate.currentUser(getApplication());
        if (user == null) {
            error.setValue(getApplication().getString(R.string.evolve_unavailable));
            return;
        }
        String name = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? getApplication().getString(R.string.evolve_anonymous) : user.getDisplayName();
        me = new EvolveProfile(user.getUid(), name, user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString(), "", 0);
        // What was shown last time, until the live data arrives
        disk.execute(() -> {
            List<SharedBlock> copy = EvolveDiskCache.load(cacheFile);
            if (!copy.isEmpty()) {
                main.post(() -> {
                    if (feed.latest().isEmpty()) {
                        feed.setLive(copy);
                        fromCopy.setValue(true);
                        recompute();
                    }
                });
            }
        });
        EvolveSocialRepository.saveProfile(me, false, timeout(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
            }

            @Override
            public void onError(@NonNull String message) {
            }
        });
        EvolveRepository.isModerator(user.getUid(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Boolean value) {
                moderator.setValue(value);
            }

            @Override
            public void onError(@NonNull String message) {
            }
        });
        listenToLatest();
        registrations.add(EvolveSocialRepository.observeConnected(new Quiet<>(connected -> offline.setValue(!connected))));
        registrations.add(EvolveSocialRepository.observeLiked(user.getUid(), new Quiet<>(liked::setValue)));
        registrations.add(EvolveSocialRepository.observeSaved(user.getUid(), new Quiet<>(ids -> {
            savedOrder = ids;
            savedIds.setValue(new HashSet<>(ids));
            if (section == Section.SAVED) loadSectionData();
            recompute();
        })));
        registrations.add(EvolveSocialRepository.observeFollowing(user.getUid(), new Quiet<>(uids -> {
            following.setValue(uids);
            if (section == Section.EXPLORE && tab == Tab.FOLLOWING) loadSectionData();
            recompute();
        })));
        registrations.add(EvolveSocialRepository.observeNotifications(user.getUid(), new Quiet<>(notifications::setValue)));
        loadSectionData();
    }

    private String timeout() {
        return getApplication().getString(R.string.evolve_timeout);
    }

    private void listenToLatest() {
        loading.setValue(true);
        registrations.add(EvolveSocialRepository.observeLatest(PAGE_SIZE, new EvolveSocialRepository.Live<>() {
            @Override
            public void onChange(List<SharedBlock> posts) {
                loading.setValue(false);
                error.setValue(null);
                fromCopy.setValue(false);
                feed.setLive(posts);
                hasMore = posts.size() >= PAGE_SIZE;
                recompute();
                List<SharedBlock> latest = feed.latest();
                disk.execute(() -> EvolveDiskCache.save(cacheFile, latest));
            }

            @Override
            public void onError(@NonNull String message) {
                loading.setValue(false);
                if (feed.isEmpty()) {
                    error.setValue(getApplication().getString(R.string.evolve_error, message));
                }
            }
        }));
    }

    /** Loads what the open section or tab needs beyond the live newest posts. */
    private void loadSectionData() {
        if (me == null) return;
        if (section == Section.EXPLORE && tab == Tab.POPULAR) {
            EvolveSocialRepository.loadPopular(POPULAR_LIMIT, timeout(), forFeed(feed::setPopular));
        } else if (section == Section.EXPLORE && tab == Tab.FOLLOWING) {
            Set<String> uids = following.getValue() == null ? Set.of() : following.getValue();
            EvolveSocialRepository.loadByAuthors(uids, 20, timeout(), forFeed(feed::setFollowing));
        } else if (section == Section.SAVED) {
            EvolveSocialRepository.loadSaved(savedOrder, timeout(), forFeed(feed::setSaved));
        } else if (section == Section.PROFILE) {
            EvolveSocialRepository.loadByAuthors(List.of(me.uid()), 50, timeout(), forFeed(feed::setMine));
        }
    }

    private EvolveRepository.Callback<List<SharedBlock>> forFeed(java.util.function.Consumer<List<SharedBlock>> store) {
        loading.setValue(true);
        return new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(List<SharedBlock> value) {
                loading.setValue(false);
                store.accept(value);
                recompute();
            }

            @Override
            public void onError(@NonNull String message) {
                loading.setValue(false);
                if (feed.isEmpty()) {
                    error.setValue(getApplication().getString(R.string.evolve_error, message));
                }
            }
        };
    }

    /** Starts over: the live listener, the older pages and the section's own data. */
    public void refresh() {
        if (me == null) return;
        feed.clearOlder();
        hasMore = true;
        autoPages = 0;
        error.setValue(null);
        loadSectionData();
        recompute();
    }

    /** Appends the next, older page of the newest posts. */
    public void loadMore() {
        if (me == null || paging || !hasMore || section != Section.EXPLORE) return;
        long oldest = feed.oldestCreatedAt();
        if (oldest <= 0) return;
        paging = true;
        loading.setValue(true);
        EvolveRepository.load(PAGE_SIZE, oldest, timeout(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(EvolveRepository.Page page) {
                paging = false;
                loading.setValue(false);
                feed.addOlder(page.items());
                hasMore = page.rawCount() >= PAGE_SIZE;
                recompute();
            }

            @Override
            public void onError(@NonNull String message) {
                paging = false;
                loading.setValue(false);
                hasMore = false;
            }
        });
    }

    /** What the sections show, from what is loaded; asks for more pages when a search or filter leaves few rows. */
    private void recompute() {
        List<SharedBlock> visible = feed.visible(section, tab, kind, query, myUid(), following.getValue() == null ? Set.of() : following.getValue(),
                savedOrder, System.currentTimeMillis());
        shown.setValue(visible);
        boolean pagedSection = section == Section.EXPLORE && tab != Tab.POPULAR;
        if (pagedSection && hasMore && !paging && visible.size() < FEW_ROWS && autoPages < MAX_AUTO_PAGES && !feed.latest().isEmpty()) {
            autoPages++;
            loadMore();
        }
    }

    // ---------------------------------------------------------------------------------------------- the user's actions

    public void toggleLike(SharedBlock post, EvolveRepository.Callback<Void> callback) {
        if (me == null) return;
        boolean now = liked.getValue() == null || !liked.getValue().contains(post.id());
        EvolveSocialRepository.setLiked(post, me, now, timeout(), callback);
    }

    public void toggleSave(SharedBlock post, EvolveRepository.Callback<Void> callback) {
        if (me == null) return;
        boolean now = savedIds.getValue() == null || !savedIds.getValue().contains(post.id());
        EvolveSocialRepository.setSaved(me.uid(), post.id(), now, timeout(), callback);
    }

    public void toggleFollow(String authorUid, String authorName, EvolveRepository.Callback<Void> callback) {
        if (me == null) return;
        boolean now = following.getValue() == null || !following.getValue().contains(authorUid);
        EvolveSocialRepository.setFollowing(me, authorUid, authorName, now, timeout(), callback);
    }

    public void delete(SharedBlock post, EvolveRepository.Callback<Void> callback) {
        EvolveRepository.delete(post.id(), timeout(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                EvolveCache.clear();
                EvolveStorage.delete(post.meta().screenshots());
                feed.remove(post.id());
                recompute();
                callback.onSuccess(null);
            }

            @Override
            public void onError(@NonNull String message) {
                callback.onError(message);
            }
        });
    }

    public void markNotificationsRead() {
        if (me == null) return;
        List<String> unread = new ArrayList<>();
        List<EvolveNotification> current = notifications.getValue();
        if (current != null) {
            for (EvolveNotification notification : current) {
                if (!notification.read()) unread.add(notification.id());
            }
        }
        EvolveSocialRepository.markRead(me.uid(), unread, timeout(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
            }

            @Override
            public void onError(@NonNull String message) {
            }
        });
    }

    public int unreadCount() {
        int count = 0;
        List<EvolveNotification> current = notifications.getValue();
        if (current != null) {
            for (EvolveNotification notification : current) {
                if (!notification.read()) count++;
            }
        }
        return count;
    }

    @Override
    protected void onCleared() {
        for (Registration registration : registrations) {
            registration.remove();
        }
        registrations.clear();
        disk.shutdown();
        super.onCleared();
    }

    /** A live listener whose errors are ignored: the feed's own errors are the ones worth showing. */
    private static final class Quiet<T> implements EvolveSocialRepository.Live<T> {
        private final java.util.function.Consumer<T> onChange;

        Quiet(java.util.function.Consumer<T> onChange) {
            this.onChange = onChange;
        }

        @Override
        public void onChange(T value) {
            onChange.accept(value);
        }

        @Override
        public void onError(@NonNull String message) {
        }
    }
}
