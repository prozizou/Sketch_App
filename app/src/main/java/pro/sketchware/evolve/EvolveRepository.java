package pro.sketchware.evolve;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads and writes the {@code Blocks_share} node of the Firebase Realtime Database.
 *
 * <p>Every call answers exactly once, on the main thread, with a result or an error text; a call the server never
 * answers (offline, rules waiting) ends with a timeout error instead of a spinner that never stops.
 */
public final class EvolveRepository {

    public static final String NODE = "Blocks_share";
    /** Reports, one child per entry and reporter. Nobody reads them from the app: they are for the console. */
    public static final String REPORTS = "Blocks_share_reports";
    /** Uids that may delete any entry, set by hand in the console: {@code Evolve_moderators/<uid>: true}. */
    public static final String MODERATORS = "Evolve_moderators";
    private static final long TIMEOUT_MS = 15_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private EvolveRepository() {
    }

    public interface Callback<T> {
        void onSuccess(T value);

        void onError(@NonNull String message);
    }

    private static DatabaseReference node() {
        return FirebaseDatabase.getInstance().getReference(NODE);
    }

    /** Answers once: the first of the real result and the timeout wins. */
    private static final class Once<T> {
        private final AtomicBoolean done = new AtomicBoolean();
        private final Callback<T> callback;
        private final Runnable timeout;
        private final String timeoutMessage;

        Once(Callback<T> callback, String timeoutMessage) {
            this.callback = callback;
            this.timeoutMessage = timeoutMessage;
            this.timeout = () -> error(timeoutMessage);
            MAIN.postDelayed(timeout, TIMEOUT_MS);
        }

        void success(T value) {
            if (done.compareAndSet(false, true)) {
                MAIN.removeCallbacks(timeout);
                MAIN.post(() -> callback.onSuccess(value));
            }
        }

        void error(String message) {
            if (done.compareAndSet(false, true)) {
                MAIN.removeCallbacks(timeout);
                MAIN.post(() -> callback.onError(message));
            }
        }
    }

    private static String describe(@Nullable DatabaseError error) {
        if (error == null) {
            return "unknown error";
        }
        // PERMISSION_DENIED is the database rules saying no: say so instead of the library's long sentence.
        return error.getCode() == DatabaseError.PERMISSION_DENIED ? "permission denied by the database rules" : error.getMessage();
    }

    /** @param items the valid entries of one page, newest first; @param rawCount nodes the page held, valid or not */
    public record Page(List<SharedBlock> items, int rawCount) {
    }

    /**
     * Loads one page of shared entries, newest first.
     *
     * @param limit            page size
     * @param beforeCreatedAt  0 for the newest page, else only entries older than this time (the oldest one already shown)
     * @param timeoutMessage   what to report when the server doesn't answer
     */
    public static void load(int limit, long beforeCreatedAt, String timeoutMessage, Callback<Page> callback) {
        Once<Page> once = new Once<>(callback, timeoutMessage);
        try {
            Query query = node().orderByChild(SharedBlock.CREATED_AT);
            if (beforeCreatedAt > 0) {
                query = query.endBefore((double) beforeCreatedAt);
            }
            query.limitToLast(limit).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<SharedBlock> blocks = new ArrayList<>();
                    int raw = 0;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        raw++;
                        Object value = child.getValue();
                        if (value instanceof Map<?, ?> fields) {
                            Map<String, Object> map = new HashMap<>();
                            for (Map.Entry<?, ?> entry : fields.entrySet()) {
                                map.put(String.valueOf(entry.getKey()), entry.getValue());
                            }
                            SharedBlock block = SharedBlock.fromMap(child.getKey(), map);
                            if (block != null) {
                                blocks.add(block);
                            }
                        }
                    }
                    blocks.sort(Comparator.comparingLong(SharedBlock::createdAt).reversed());
                    once.success(new Page(blocks, raw));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    once.error(describe(error));
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    /** Tells whether {@code uid} is listed under {@code Evolve_moderators}: moderators may delete any entry. */
    public static void isModerator(String uid, Callback<Boolean> callback) {
        Once<Boolean> once = new Once<>(callback, "");
        try {
            FirebaseDatabase.getInstance().getReference(MODERATORS).child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            once.success(Boolean.TRUE.equals(snapshot.getValue(Boolean.class)));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            once.success(false);
                        }
                    });
        } catch (RuntimeException e) {
            once.success(false);
        }
    }

    /** Files a report on entry {@code id}; one per user and entry (reporting again replaces the reason). */
    public static void report(String id, String uid, String reason, String timeoutMessage, Callback<Void> callback) {
        Once<Void> once = new Once<>(callback, timeoutMessage);
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("reason", reason);
            map.put(SharedBlock.CREATED_AT, ServerValue.TIMESTAMP);
            FirebaseDatabase.getInstance().getReference(REPORTS).child(id).child(uid).setValue(map, (error, ref) -> {
                if (error != null) {
                    once.error(describe(error));
                } else {
                    once.success(null);
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    /**
     * Publishes {@code block} as a new child; the server stamps {@code createdAt}.
     *
     * @return through the callback, the new node's id
     */
    public static void publish(SharedBlock block, String timeoutMessage, Callback<String> callback) {
        Once<String> once = new Once<>(callback, timeoutMessage);
        try {
            DatabaseReference child = node().push();
            Map<String, Object> map = new HashMap<>(block.toPublishMap());
            map.put(SharedBlock.CREATED_AT, ServerValue.TIMESTAMP);
            child.setValue(map, (error, ref) -> {
                if (error != null) {
                    once.error(describe(error));
                } else {
                    once.success(child.getKey());
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    /** Removes a block the signed-in user published; the database rules refuse anybody else. */
    public static void delete(String id, String timeoutMessage, Callback<Void> callback) {
        Once<Void> once = new Once<>(callback, timeoutMessage);
        try {
            node().child(id).removeValue((error, ref) -> {
                if (error != null) {
                    once.error(describe(error));
                } else {
                    once.success(null);
                }
            });
        } catch (RuntimeException e) {
            once.error(e.getClass().getSimpleName());
        }
    }

    /** Adds one to the block's download counter. Fire and forget: a failure only leaves the counter as it was. */
    public static void countDownload(String id) {
        try {
            node().child(id).child(SharedBlock.DOWNLOADS).runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData data) {
                    Long current = data.getValue(Long.class);
                    data.setValue(current == null ? 1L : current + 1L);
                    return Transaction.success(data);
                }

                @Override
                public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                }
            });
        } catch (RuntimeException ignored) {
            // The pick itself already succeeded locally.
        }
    }
}
