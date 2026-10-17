package pro.sketchware.evolve;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.auth.AuthGate;
import pro.sketchware.evolve.EvolveSocialRepository.Registration;

/**
 * One open post: the post itself, its discussion, and whether the user likes, saved and follows it, all live. The
 * comments come in pages of {@link EvolveSocialRepository#COMMENT_PAGE}; "load older" widens the window.
 */
public class EvolvePostViewModel extends AndroidViewModel {

    private final MutableLiveData<SharedBlock> post = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gone = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<List<CommentThreads.Row>> rows = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> liked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> followingAuthor = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> moderator = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canLoadOlder = new MutableLiveData<>(false);
    private final List<Registration> registrations = new ArrayList<>();
    private final List<Comment> comments = new ArrayList<>();

    private String postId = "";
    private int commentLimit = EvolveSocialRepository.COMMENT_PAGE;
    @Nullable
    private Registration commentRegistration;
    @Nullable
    private EvolveProfile me;

    public EvolvePostViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<SharedBlock> post() {
        return post;
    }

    /** True once the post turned out not to exist (any more). */
    public LiveData<Boolean> gone() {
        return gone;
    }

    public LiveData<String> error() {
        return error;
    }

    public LiveData<List<CommentThreads.Row>> rows() {
        return rows;
    }

    public LiveData<Boolean> liked() {
        return liked;
    }

    public LiveData<Boolean> saved() {
        return saved;
    }

    public LiveData<Boolean> followingAuthor() {
        return followingAuthor;
    }

    public LiveData<Boolean> moderator() {
        return moderator;
    }

    public LiveData<Boolean> canLoadOlder() {
        return canLoadOlder;
    }

    @Nullable
    public EvolveProfile me() {
        return me;
    }

    public String myUid() {
        return me == null ? "" : me.uid();
    }

    private String timeout() {
        return getApplication().getString(R.string.evolve_timeout);
    }

    /** Starts listening to {@code id}; later calls with the same id do nothing. */
    public void open(String id) {
        if (!postId.isEmpty()) return;
        postId = id;
        FirebaseUser user = AuthGate.currentUser(getApplication());
        if (user == null) {
            error.setValue(getApplication().getString(R.string.evolve_unavailable));
            return;
        }
        String name = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? getApplication().getString(R.string.evolve_anonymous) : user.getDisplayName();
        me = new EvolveProfile(user.getUid(), name, user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString(), "", 0);
        registrations.add(EvolveSocialRepository.observePost(id, new EvolveSocialRepository.Live<>() {
            @Override
            public void onChange(SharedBlock value) {
                if (value == null) {
                    gone.setValue(true);
                } else {
                    post.setValue(value);
                    followingAuthor.setValue(lastFollowing.contains(value.authorUid()));
                }
            }

            @Override
            public void onError(@NonNull String message) {
                error.setValue(getApplication().getString(R.string.evolve_error, message));
            }
        }));
        registrations.add(EvolveSocialRepository.observeLiked(user.getUid(), quiet(ids -> liked.setValue(ids.contains(postId)))));
        registrations.add(EvolveSocialRepository.observeSaved(user.getUid(), quiet(ids -> saved.setValue(ids.contains(postId)))));
        registrations.add(EvolveSocialRepository.observeFollowing(user.getUid(), quiet(uids -> {
            SharedBlock current = post.getValue();
            followingAuthor.setValue(current != null && uids.contains(current.authorUid()));
            lastFollowing = uids;
        })));
        EvolveRepository.isModerator(user.getUid(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Boolean value) {
                moderator.setValue(value);
            }

            @Override
            public void onError(@NonNull String message) {
            }
        });
        listenToComments();
    }

    private Set<String> lastFollowing = new HashSet<>();

    private void listenToComments() {
        if (commentRegistration != null) commentRegistration.remove();
        commentRegistration = EvolveSocialRepository.observeComments(postId, commentLimit, new EvolveSocialRepository.Live<>() {
            @Override
            public void onChange(List<Comment> value) {
                comments.clear();
                comments.addAll(value);
                canLoadOlder.setValue(value.size() >= commentLimit);
                rows.setValue(CommentThreads.rows(comments));
            }

            @Override
            public void onError(@NonNull String message) {
                error.setValue(getApplication().getString(R.string.evolve_error, message));
            }
        });
    }

    public void loadOlderComments() {
        commentLimit += EvolveSocialRepository.COMMENT_PAGE;
        listenToComments();
    }

    /** People that can be mentioned: the post's author and everyone who wrote in the discussion, by display name. */
    public Map<String, String> mentionable() {
        Map<String, String> people = new HashMap<>();
        SharedBlock current = post.getValue();
        if (current != null && !current.authorName().isEmpty()) people.put(current.authorName(), current.authorUid());
        for (Comment comment : comments) {
            if (!comment.authorName().isEmpty()) people.put(comment.authorName(), comment.authorUid());
        }
        if (me != null) people.remove(me.name());
        return people;
    }

    public void toggleLike(EvolveRepository.Callback<Void> callback) {
        SharedBlock current = post.getValue();
        if (me == null || current == null) return;
        EvolveSocialRepository.setLiked(current, me, !Boolean.TRUE.equals(liked.getValue()), timeout(), callback);
    }

    public void toggleSave(EvolveRepository.Callback<Void> callback) {
        if (me == null) return;
        EvolveSocialRepository.setSaved(me.uid(), postId, !Boolean.TRUE.equals(saved.getValue()), timeout(), callback);
    }

    public void toggleFollow(EvolveRepository.Callback<Void> callback) {
        SharedBlock current = post.getValue();
        if (me == null || current == null) return;
        EvolveSocialRepository.setFollowing(me, current.authorUid(), current.authorName(), !Boolean.TRUE.equals(followingAuthor.getValue()),
                timeout(), callback);
    }

    public void comment(@Nullable Comment parent, String text, EvolveRepository.Callback<String> callback) {
        SharedBlock current = post.getValue();
        if (me == null || current == null) return;
        List<String> mentions = Mentions.extract(PostRules.clean(text), mentionable());
        EvolveSocialRepository.addComment(current, parent, me, text, mentions, timeout(), callback);
    }

    public void deleteComment(Comment comment, EvolveRepository.Callback<Void> callback) {
        EvolveSocialRepository.deleteComment(comment, timeout(), callback);
    }

    public void delete(EvolveRepository.Callback<Void> callback) {
        SharedBlock deleted = post.getValue();
        EvolveRepository.delete(postId, timeout(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                EvolveCache.clear();
                if (deleted != null) EvolveStorage.delete(deleted.meta().screenshots());
                callback.onSuccess(null);
            }

            @Override
            public void onError(@NonNull String message) {
                callback.onError(message);
            }
        });
    }

    private static <T> EvolveSocialRepository.Live<T> quiet(java.util.function.Consumer<T> onChange) {
        return new EvolveSocialRepository.Live<>() {
            @Override
            public void onChange(T value) {
                onChange.accept(value);
            }

            @Override
            public void onError(@NonNull String message) {
            }
        };
    }

    @Override
    protected void onCleared() {
        for (Registration registration : registrations) registration.remove();
        registrations.clear();
        if (commentRegistration != null) commentRegistration.remove();
        super.onCleared();
    }
}
