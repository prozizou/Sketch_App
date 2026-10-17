package pro.sketchware.evolve;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import pro.sketchware.evolve.EvolveRepository.Callback;
import pro.sketchware.evolve.EvolveRepository.Once;

/**
 * The community side of Evolve in the Firebase Realtime Database: likes, saved posts, follows, comments and replies,
 * notifications and profiles, plus the live views of the feed. Together with {@link EvolveRepository} it is the whole
 * backend; the screens only talk to these two.
 *
 * <p>Nodes (all readable by signed-in users unless noted, see docs/EVOLVE.md for the rules):
 * <pre>
 * Evolve_likes/&lt;postId&gt;/&lt;uid&gt;               true (who liked a post, for the counter's rule)
 * Evolve_liked/&lt;uid&gt;/&lt;postId&gt;               true (the posts a user liked, private to the user)
 * Evolve_saved/&lt;uid&gt;/&lt;postId&gt;               time (private to the user)
 * Evolve_follows/&lt;uid&gt;/&lt;authorUid&gt;          true (private to the user)
 * Evolve_followers/&lt;authorUid&gt;/&lt;uid&gt;        true
 * Evolve_comments/&lt;postId&gt;/&lt;commentId&gt;      {@link Comment}
 * Evolve_notifications/&lt;uid&gt;/&lt;id&gt;          {@link EvolveNotification} (private to the user)
 * Evolve_profiles/&lt;uid&gt;                      {@link EvolveProfile}
 * </pre>
 * Every write that has a counter (likes, comments) is one atomic multi-location update, and the rules check that the
 * counter moves by one together with the record it counts.
 */
public final class EvolveSocialRepository {

    public static final String LIKES = "Evolve_likes";
    public static final String LIKED = "Evolve_liked";
    public static final String SAVED = "Evolve_saved";
    public static final String FOLLOWS = "Evolve_follows";
    public static final String FOLLOWERS = "Evolve_followers";
    public static final String COMMENTS = "Evolve_comments";
    public static final String NOTIFICATIONS = "Evolve_notifications";
    public static final String PROFILES = "Evolve_profiles";

    /** Comments loaded per page; older ones are asked for with {@code limit} raised. */
    public static final int COMMENT_PAGE = 50;
    public static final int NOTIFICATION_LIMIT = 50;
    /** Most followed authors whose posts the Following tab loads (one query each). */
    public static final int MAX_FOLLOWED_QUERIES = 20;

    private EvolveSocialRepository() {
    }

    /** A live listener; call {@link #remove()} when the screen stops showing what it feeds. */
    public interface Registration {
        void remove();
    }

    /** What a live view reports: a fresh value each time the data changes, or the reason it can't be read. */
    public interface Live<T> {
        void onChange(T value);

        void onError(@NonNull String message);
    }

    private static DatabaseReference root() {
        return FirebaseDatabase.getInstance().getReference();
    }

    @Nullable
    private static Map<String, Object> asMap(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        if (!(value instanceof Map<?, ?> fields)) return null;
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    private static Registration listen(Query query, ValueEventListener listener) {
        query.addValueEventListener(listener);
        return () -> query.removeEventListener(listener);
    }

    private static <T> void write(Map<String, Object> updates, String timeoutMessage, Callback<T> callback, T result) {
        Once<T> once = new Once<>(callback, timeoutMessage);
        try {
            root().updateChildren(updates, (error, ref) -> {
                if (error != null) {
                    once.error(EvolveRepository.describe(error));
                } else {
                    once.success(result);
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------------------------------------ the feed

    /** The newest {@code limit} posts, kept up to date: the listener hears of every new, edited and removed post. */
    public static Registration observeLatest(int limit, Live<List<SharedBlock>> live) {
        Query query = EvolveRepository.node().orderByChild(SharedBlock.CREATED_AT).limitToLast(limit);
        return listen(query, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                live.onChange(posts(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onError(EvolveRepository.describe(error));
            }
        });
    }

    /** The most picked {@code limit} posts, to be ordered by {@link EvolveFilter#popularity} by the caller. */
    public static void loadPopular(int limit, String timeoutMessage, Callback<List<SharedBlock>> callback) {
        Once<List<SharedBlock>> once = new Once<>(callback, timeoutMessage);
        try {
            EvolveRepository.node().orderByChild(SharedBlock.DOWNLOADS).limitToLast(limit)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            once.success(posts(snapshot));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            once.error(EvolveRepository.describe(error));
                        }
                    });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    /** The newest posts of each of {@code authorUids} (at most {@link #MAX_FOLLOWED_QUERIES} authors), merged, newest first. */
    public static void loadByAuthors(Collection<String> authorUids, int perAuthor, String timeoutMessage, Callback<List<SharedBlock>> callback) {
        List<String> authors = new ArrayList<>(authorUids);
        if (authors.size() > MAX_FOLLOWED_QUERIES) authors = authors.subList(0, MAX_FOLLOWED_QUERIES);
        if (authors.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        Once<List<SharedBlock>> once = new Once<>(callback, timeoutMessage);
        List<SharedBlock> merged = new ArrayList<>();
        AtomicInteger pending = new AtomicInteger(authors.size());
        for (String author : authors) {
            try {
                EvolveRepository.node().orderByChild(SharedBlock.AUTHOR_UID).equalTo(author).limitToLast(perAuthor)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                synchronized (merged) {
                                    merged.addAll(posts(snapshot));
                                }
                                if (pending.decrementAndGet() == 0) finish();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                // One author that can't be read must not hide all the others
                                if (pending.decrementAndGet() == 0) finish();
                            }

                            private void finish() {
                                synchronized (merged) {
                                    merged.sort(Comparator.comparingLong(SharedBlock::createdAt).reversed());
                                    once.success(new ArrayList<>(merged));
                                }
                            }
                        });
            } catch (RuntimeException e) {
                if (pending.decrementAndGet() == 0) once.success(new ArrayList<>(merged));
            }
        }
    }

    /** One post by id, or null in the callback when it doesn't exist (any more). */
    public static void loadPost(String id, String timeoutMessage, Callback<SharedBlock> callback) {
        Once<SharedBlock> once = new Once<>(callback, timeoutMessage);
        try {
            EvolveRepository.node().child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Map<String, Object> map = asMap(snapshot);
                    once.success(map == null ? null : SharedBlock.fromMap(snapshot.getKey(), map));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    once.error(EvolveRepository.describe(error));
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    /** One post kept up to date: its counters and text change on the screen while it is open. Null when it is removed. */
    public static Registration observePost(String id, Live<SharedBlock> live) {
        return listen(EvolveRepository.node().child(id), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Object> map = asMap(snapshot);
                live.onChange(map == null ? null : SharedBlock.fromMap(snapshot.getKey(), map));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onError(EvolveRepository.describe(error));
            }
        });
    }

    /** The posts the user saved, in the order they were saved (newest first), skipping those since deleted. */
    public static void loadSaved(Collection<String> ids, String timeoutMessage, Callback<List<SharedBlock>> callback) {
        List<String> wanted = new ArrayList<>(ids);
        if (wanted.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        Once<List<SharedBlock>> once = new Once<>(callback, timeoutMessage);
        Map<String, SharedBlock> found = new HashMap<>();
        AtomicInteger pending = new AtomicInteger(wanted.size());
        for (String id : wanted) {
            try {
                EvolveRepository.node().child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Map<String, Object> map = asMap(snapshot);
                        SharedBlock post = map == null ? null : SharedBlock.fromMap(snapshot.getKey(), map);
                        synchronized (found) {
                            if (post != null) found.put(id, post);
                        }
                        if (pending.decrementAndGet() == 0) finish();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (pending.decrementAndGet() == 0) finish();
                    }

                    private void finish() {
                        List<SharedBlock> ordered = new ArrayList<>();
                        synchronized (found) {
                            for (String wantedId : wanted) {
                                SharedBlock post = found.get(wantedId);
                                if (post != null) ordered.add(post);
                            }
                        }
                        once.success(ordered);
                    }
                });
            } catch (RuntimeException e) {
                if (pending.decrementAndGet() == 0) once.success(new ArrayList<>(found.values()));
            }
        }
    }

    private static List<SharedBlock> posts(DataSnapshot snapshot) {
        List<SharedBlock> posts = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            Map<String, Object> map = asMap(child);
            SharedBlock post = map == null ? null : SharedBlock.fromMap(child.getKey(), map);
            if (post != null) posts.add(post);
        }
        posts.sort(Comparator.comparingLong(SharedBlock::createdAt).reversed());
        return posts;
    }

    // ------------------------------------------------------------------------------------------ likes and saves

    /** The ids of the posts the user likes, kept up to date: one listener tells every card and the detail page. */
    public static Registration observeLiked(String uid, Live<Set<String>> live) {
        return listen(root().child(LIKED).child(uid), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> ids = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) ids.add(child.getKey());
                live.onChange(ids);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onError(EvolveRepository.describe(error));
            }
        });
    }

    /** Whether the device is connected to the database, kept up to date (false offline: the page shows its copy). */
    public static Registration observeConnected(Live<Boolean> live) {
        return listen(FirebaseDatabase.getInstance().getReference(".info/connected"), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                live.onChange(Boolean.TRUE.equals(snapshot.getValue(Boolean.class)));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onChange(false);
            }
        });
    }

    /**
     * Likes or unlikes {@code post} as {@code user}: the like record and the post's counter change together, and a new
     * like tells the author (unless they liked their own post).
     */
    public static void setLiked(SharedBlock post, EvolveProfile user, boolean liked, String timeoutMessage, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(LIKES + "/" + post.id() + "/" + user.uid(), liked ? Boolean.TRUE : null);
        updates.put(LIKED + "/" + user.uid() + "/" + post.id(), liked ? Boolean.TRUE : null);
        updates.put(EvolveRepository.NODE + "/" + post.id() + "/" + PostMeta.LIKES, ServerValue.increment(liked ? 1 : -1));
        if (liked && !post.authorUid().equals(user.uid())) {
            addNotification(updates, post.authorUid(), new EvolveNotification("", EvolveNotification.Type.LIKE, user.uid(), user.name(),
                    post.id(), post.name(), 0, false));
        }
        write(updates, timeoutMessage, callback, null);
    }

    /** The ids of the posts the user saved, newest first, kept up to date. */
    public static Registration observeSaved(String uid, Live<List<String>> live) {
        return listen(root().child(SAVED).child(uid).orderByValue(), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> children = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) children.add(child);
                children.sort(Comparator.comparingLong((DataSnapshot c) -> c.getValue() instanceof Number n ? n.longValue() : 0L).reversed());
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : children) ids.add(child.getKey());
                live.onChange(ids);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onError(EvolveRepository.describe(error));
            }
        });
    }

    public static void setSaved(String uid, String postId, boolean saved, String timeoutMessage, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(SAVED + "/" + uid + "/" + postId, saved ? ServerValue.TIMESTAMP : null);
        write(updates, timeoutMessage, callback, null);
    }

    // ------------------------------------------------------------------------------------------------- follows

    /** The uids of the authors the user follows, kept up to date. */
    public static Registration observeFollowing(String uid, Live<Set<String>> live) {
        return listen(root().child(FOLLOWS).child(uid), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> uids = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) uids.add(child.getKey());
                live.onChange(uids);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onError(EvolveRepository.describe(error));
            }
        });
    }

    public static void setFollowing(EvolveProfile user, String authorUid, String authorName, boolean follow, String timeoutMessage, Callback<Void> callback) {
        if (user.uid().equals(authorUid)) {
            callback.onError("cannot follow yourself");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put(FOLLOWS + "/" + user.uid() + "/" + authorUid, follow ? Boolean.TRUE : null);
        updates.put(FOLLOWERS + "/" + authorUid + "/" + user.uid(), follow ? Boolean.TRUE : null);
        if (follow) {
            addNotification(updates, authorUid, new EvolveNotification("", EvolveNotification.Type.FOLLOW, user.uid(), user.name(), "", "", 0, false));
        }
        write(updates, timeoutMessage, callback, null);
    }

    /** How many users follow {@code uid}. */
    public static void followerCount(String uid, String timeoutMessage, Callback<Long> callback) {
        Once<Long> once = new Once<>(callback, timeoutMessage);
        try {
            root().child(FOLLOWERS).child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    once.success(snapshot.getChildrenCount());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    once.error(EvolveRepository.describe(error));
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    // ----------------------------------------------------------------------------------------------- comments

    /** The newest {@code limit} comments of a post, oldest first, kept up to date. */
    public static Registration observeComments(String postId, int limit, Live<List<Comment>> live) {
        Query query = root().child(COMMENTS).child(postId).orderByChild(Comment.CREATED_AT).limitToLast(limit);
        return listen(query, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Comment> comments = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, Object> map = asMap(child);
                    Comment comment = map == null ? null : Comment.fromMap(postId, child.getKey(), map);
                    if (comment != null) comments.add(comment);
                }
                comments.sort(Comparator.comparingLong(Comment::createdAt).thenComparing(Comment::id));
                live.onChange(comments);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onError(EvolveRepository.describe(error));
            }
        });
    }

    /**
     * Adds a comment (or a reply, when {@code parent} is not null). The comment and the post's counter are one update.
     * Tells the post's author, the author of the comment answered, and the people mentioned, each once and never the writer.
     *
     * @param mentions the uids of the people named in the text, with {@link Mentions#extract}
     */
    public static void addComment(SharedBlock post, @Nullable Comment parent, EvolveProfile user, String text, List<String> mentions,
                                  String timeoutMessage, Callback<String> callback) {
        String clean = PostRules.clean(text);
        if (PostRules.checkComment(clean) != PostRules.CommentProblem.NONE) {
            callback.onError("invalid comment");
            return;
        }
        DatabaseReference ref = root().child(COMMENTS).child(post.id()).push();
        String commentId = ref.getKey();
        if (commentId == null) {
            callback.onError("no id");
            return;
        }
        Comment comment = new Comment(commentId, post.id(), parent == null ? "" : parent.id(), user.uid(), user.name(), clean, 0, mentions);
        Map<String, Object> value = new HashMap<>(comment.toPublishMap());
        value.put(Comment.CREATED_AT, ServerValue.TIMESTAMP);
        Map<String, Object> updates = new HashMap<>();
        updates.put(COMMENTS + "/" + post.id() + "/" + commentId, value);
        updates.put(EvolveRepository.NODE + "/" + post.id() + "/" + PostMeta.COMMENT_COUNT, ServerValue.increment(1));

        Set<String> told = new HashSet<>();
        told.add(user.uid());
        for (String mentioned : mentions) {
            if (told.add(mentioned)) {
                addNotification(updates, mentioned, new EvolveNotification("", EvolveNotification.Type.MENTION, user.uid(), user.name(), post.id(), post.name(), 0, false));
            }
        }
        if (parent != null && told.add(parent.authorUid())) {
            addNotification(updates, parent.authorUid(), new EvolveNotification("", EvolveNotification.Type.REPLY, user.uid(), user.name(), post.id(), post.name(), 0, false));
        }
        if (told.add(post.authorUid())) {
            addNotification(updates, post.authorUid(), new EvolveNotification("", EvolveNotification.Type.COMMENT, user.uid(), user.name(), post.id(), post.name(), 0, false));
        }
        write(updates, timeoutMessage, callback, commentId);
    }

    /** Removes a comment (its writer, the post's author or a moderator may; the rules decide) and lowers the counter. */
    public static void deleteComment(Comment comment, String timeoutMessage, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(COMMENTS + "/" + comment.postId() + "/" + comment.id(), null);
        updates.put(EvolveRepository.NODE + "/" + comment.postId() + "/" + PostMeta.COMMENT_COUNT, ServerValue.increment(-1));
        write(updates, timeoutMessage, callback, null);
    }

    // ------------------------------------------------------------------------------------------ notifications

    /** Adds a notification for {@code toUid} to a multi-location update; its id and time are the database's. */
    private static void addNotification(Map<String, Object> updates, String toUid, EvolveNotification notification) {
        String id = root().child(NOTIFICATIONS).child(toUid).push().getKey();
        if (id == null) return;
        Map<String, Object> value = new HashMap<>(notification.toPublishMap());
        value.put(EvolveNotification.CREATED_AT, ServerValue.TIMESTAMP);
        updates.put(NOTIFICATIONS + "/" + toUid + "/" + id, value);
    }

    /** The user's newest notifications, newest first, kept up to date. */
    public static Registration observeNotifications(String uid, Live<List<EvolveNotification>> live) {
        Query query = root().child(NOTIFICATIONS).child(uid).orderByChild(EvolveNotification.CREATED_AT).limitToLast(NOTIFICATION_LIMIT);
        return listen(query, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<EvolveNotification> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, Object> map = asMap(child);
                    EvolveNotification notification = map == null ? null : EvolveNotification.fromMap(child.getKey(), map);
                    if (notification != null) list.add(notification);
                }
                list.sort(Comparator.comparingLong(EvolveNotification::createdAt).reversed());
                live.onChange(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                live.onError(EvolveRepository.describe(error));
            }
        });
    }

    /** Marks the given notifications as seen. */
    public static void markRead(String uid, Collection<String> ids, String timeoutMessage, Callback<Void> callback) {
        if (ids.isEmpty()) {
            callback.onSuccess(null);
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        for (String id : ids) {
            updates.put(NOTIFICATIONS + "/" + uid + "/" + id + "/" + EvolveNotification.READ, Boolean.TRUE);
        }
        write(updates, timeoutMessage, callback, null);
    }

    // -------------------------------------------------------------------------------------------------- profiles

    /** Writes the user's profile, so others see their name and picture; the first write also stamps the join time. */
    public static void saveProfile(EvolveProfile profile, boolean first, String timeoutMessage, Callback<Void> callback) {
        Map<String, Object> value = new HashMap<>(profile.toMap());
        if (first) value.put(EvolveProfile.CREATED_AT, ServerValue.TIMESTAMP);
        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, Object> field : value.entrySet()) {
            updates.put(PROFILES + "/" + profile.uid() + "/" + field.getKey(), field.getValue());
        }
        write(updates, timeoutMessage, callback, null);
    }

    /** A profile, or null in the callback when the user hasn't opened the community page yet. */
    public static void loadProfile(String uid, String timeoutMessage, Callback<EvolveProfile> callback) {
        Once<EvolveProfile> once = new Once<>(callback, timeoutMessage);
        try {
            root().child(PROFILES).child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Map<String, Object> map = asMap(snapshot);
                    once.success(map == null ? null : EvolveProfile.fromMap(uid, map));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    once.error(EvolveRepository.describe(error));
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    // ----------------------------------------------------------------------------------------- author's own posts

    /**
     * Updates a post as its author: only the fields given change, and the update time is the server's. The rules refuse
     * anybody else, a change of author, and a change to the counters.
     */
    public static void updatePost(String postId, Map<String, Object> changes, String timeoutMessage, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            updates.put(EvolveRepository.NODE + "/" + postId + "/" + change.getKey(), change.getValue());
        }
        updates.put(EvolveRepository.NODE + "/" + postId + "/" + PostMeta.UPDATED_AT, ServerValue.TIMESTAMP);
        write(updates, timeoutMessage, callback, null);
    }
}
