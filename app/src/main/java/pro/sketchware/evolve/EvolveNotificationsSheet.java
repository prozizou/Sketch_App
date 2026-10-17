package pro.sketchware.evolve;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.databinding.EvolveNotificationItemBinding;
import pro.sketchware.databinding.EvolveNotificationsSheetBinding;

/** What happened to the user's posts and comments: likes, comments, replies, mentions and new followers, newest first. */
public class EvolveNotificationsSheet extends BottomSheetDialogFragment {

    private EvolveNotificationsSheetBinding binding;
    private EvolveViewModel model;

    public static void show(FragmentManager manager) {
        if (manager.findFragmentByTag("evolve_notifications") == null) {
            new EvolveNotificationsSheet().show(manager, "evolve_notifications");
        }
    }

    /** The sentence of a notification, in the user's language. */
    public static String text(Context context, EvolveNotification notification) {
        String who = notification.fromName().isEmpty() ? context.getString(R.string.evolve_anonymous) : notification.fromName();
        return switch (notification.type()) {
            case LIKE -> context.getString(R.string.evolve_notification_like, who, notification.postName());
            case COMMENT -> context.getString(R.string.evolve_notification_comment, who, notification.postName());
            case REPLY -> context.getString(R.string.evolve_notification_reply, who, notification.postName());
            case MENTION -> context.getString(R.string.evolve_notification_mention, who, notification.postName());
            case FOLLOW -> context.getString(R.string.evolve_notification_follow, who);
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = EvolveNotificationsSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = new ViewModelProvider(requireActivity()).get(EvolveViewModel.class);
        Adapter adapter = new Adapter();
        binding.notificationsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.notificationsList.setAdapter(adapter);
        model.notifications().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(new ArrayList<>(list));
            binding.notificationsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        // Marked as seen once the user has looked at them, so the unread dots are there while they read
        if (model != null) model.markNotificationsRead();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void open(EvolveNotification notification) {
        if (notification.postId().isEmpty()) {
            // A follow: nothing to open
            return;
        }
        dismiss();
        startActivity(EvolvePostActivity.intent(requireContext(), notification.postId(),
                notification.type() != EvolveNotification.Type.LIKE));
    }

    private final class Adapter extends ListAdapter<EvolveNotification, Adapter.Holder> {
        Adapter() {
            super(new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull EvolveNotification a, @NonNull EvolveNotification b) {
                    return a.id().equals(b.id());
                }

                @Override
                public boolean areContentsTheSame(@NonNull EvolveNotification a, @NonNull EvolveNotification b) {
                    return a.equals(b);
                }
            });
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(EvolveNotificationItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            EvolveNotification notification = getItem(position);
            Context context = holder.item.getRoot().getContext();
            holder.item.notificationText.setText(text(context, notification));
            holder.item.notificationTime.setText(notification.createdAt() <= 0 ? "" : DateUtils.getRelativeTimeSpanString(
                    notification.createdAt(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            holder.item.notificationUnread.setVisibility(notification.read() ? View.INVISIBLE : View.VISIBLE);
            holder.item.getRoot().setOnClickListener(v -> open(notification));
        }

        final class Holder extends RecyclerView.ViewHolder {
            final EvolveNotificationItemBinding item;

            Holder(EvolveNotificationItemBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }
    }
}
