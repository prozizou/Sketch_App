package pro.sketchware.evolve;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityEvolveBinding;
import pro.sketchware.databinding.EvolveProfileHeaderBinding;
import pro.sketchware.evolve.EvolveFeed.Section;
import pro.sketchware.evolve.EvolveFeed.Tab;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;

/**
 * The community page of Evolve: a feed of what other developers published (blocks, more blocks, XML, Java, Kotlin,
 * components), with search, filters, Popular/Recent/Following, Saved and a Profile. Everything it shows comes from
 * {@link EvolveViewModel}; opening a post, publishing and the notifications are screens of their own.
 */
public class EvolveActivity extends AppCompatActivity implements EvolvePostCard.Listener {

    private ActivityEvolveBinding binding;
    private EvolveViewModel model;
    private EvolvePostAdapter adapter;
    private ProfileAdapter profile;
    private GridLayoutManager layoutManager;

    public static Intent intent(Context context) {
        return new Intent(context, EvolveActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityEvolveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        model = new ViewModelProvider(this).get(EvolveViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(binding.evolveRoot, (root, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        adapter = new EvolvePostAdapter(this);
        profile = new ProfileAdapter();
        layoutManager = new GridLayoutManager(this, spanCount());
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return profile.visible && position == 0 ? layoutManager.getSpanCount() : 1;
            }
        });
        binding.evolveFeed.setLayoutManager(layoutManager);
        binding.evolveFeed.setAdapter(new ConcatAdapter(profile, adapter));
        binding.evolveFeed.setItemAnimator(null);
        binding.evolveFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView list, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= layoutManager.getItemCount() - 5) {
                    model.loadMore();
                }
                // The Publish button gets out of the way of what is being read
                if (dy > 4) binding.evolvePublish.shrink();
                else if (dy < -4) binding.evolvePublish.extend();
            }
        });
        binding.evolveRefresh.setColorSchemeResources(R.color.evolve_accent);
        binding.evolveRefresh.setOnRefreshListener(() -> {
            model.refresh();
            binding.evolveRefresh.postDelayed(() -> binding.evolveRefresh.setRefreshing(false), 800);
        });

        setupHeader();
        setupFilters();
        setupNavigation();
        observe();
        model.start();
        showSection(model.section());
    }

    private int spanCount() {
        int width = getResources().getConfiguration().screenWidthDp;
        return width >= 840 ? 3 : width >= 600 ? 2 : 1;
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (layoutManager != null) layoutManager.setSpanCount(spanCount());
    }

    // ---------------------------------------------------------------------------------------------------- the header

    private void setupHeader() {
        binding.evolveBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.evolveNotifications.setOnClickListener(v -> EvolveNotificationsSheet.show(getSupportFragmentManager()));
        binding.evolveAvatar.setOnClickListener(v -> binding.evolveBottomNav.setSelectedItemId(R.id.evolve_nav_profile));
        binding.evolvePublish.setOnClickListener(v -> {
            if (model.available()) {
                startActivity(EvolvePublishActivity.intent(this, null));
            } else {
                SketchwareUtil.toastError(getString(R.string.evolve_unavailable));
            }
        });
        binding.evolveSearch.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                binding.evolveSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                model.setQuery(s.toString());
            }
        });
        binding.evolveSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(binding.evolveSearch.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        binding.evolveSearchClear.setOnClickListener(v -> binding.evolveSearch.setText(""));
        binding.evolveRetry.setOnClickListener(v -> model.refresh());
    }

    private void setupFilters() {
        addKindChip(R.string.evolve_filter_all, null, true);
        addKindChip(R.string.evolve_kind_blocks, SharedKind.BLOCKS, false);
        addKindChip(R.string.evolve_chip_xml, SharedKind.VIEW, false);
        addKindChip(R.string.evolve_kind_java, SharedKind.JAVA, false);
        addKindChip(R.string.evolve_chip_moreblocks, SharedKind.MOREBLOCK, false);
        addKindChip(R.string.evolve_kind_kotlin, SharedKind.KOTLIN, false);
        addKindChip(R.string.evolve_kind_component, SharedKind.COMPONENT, false);
        binding.evolveKinds.setOnCheckedStateChangeListener((group, ids) -> {
            SharedKind kind = null;
            if (!ids.isEmpty()) {
                Chip chip = group.findViewById(ids.get(0));
                if (chip != null && chip.getTag() instanceof SharedKind tag) kind = tag;
            }
            model.setKind(kind);
        });
        binding.evolveTabs.addTab(binding.evolveTabs.newTab().setText(R.string.evolve_tab_popular).setTag(Tab.POPULAR));
        binding.evolveTabs.addTab(binding.evolveTabs.newTab().setText(R.string.evolve_tab_recent).setTag(Tab.RECENT), true);
        binding.evolveTabs.addTab(binding.evolveTabs.newTab().setText(R.string.evolve_tab_following).setTag(Tab.FOLLOWING));
        binding.evolveTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getTag() instanceof Tab value) model.setTab(value);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                binding.evolveFeed.smoothScrollToPosition(0);
            }
        });
    }

    private void addKindChip(@StringRes int label, @Nullable SharedKind kind, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.evolve_kind_chip, binding.evolveKinds, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(kind == null ? "all" : kind);
        chip.setChecked(checked);
        binding.evolveKinds.addView(chip);
    }

    private void setupNavigation() {
        binding.evolveBottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Section section = id == R.id.evolve_nav_saved ? Section.SAVED : id == R.id.evolve_nav_profile ? Section.PROFILE : Section.EXPLORE;
            showSection(section);
            return true;
        });
        binding.evolveBottomNav.setOnItemReselectedListener(item -> binding.evolveFeed.smoothScrollToPosition(0));
    }

    private void showSection(Section section) {
        model.setSection(section);
        boolean explore = section == Section.EXPLORE;
        binding.evolveTabs.setVisibility(explore ? View.VISIBLE : View.GONE);
        binding.evolvePageTitle.setText(switch (section) {
            case SAVED -> R.string.evolve_nav_saved;
            case PROFILE -> R.string.evolve_nav_profile;
            default -> R.string.evolve_title;
        });
        profile.setVisible(section == Section.PROFILE);
        if (section == Section.PROFILE) profile.load();
        binding.evolveFeed.scrollToPosition(0);
        render();
    }

    // ----------------------------------------------------------------------------------------------------- the state

    private void observe() {
        model.shown().observe(this, posts -> {
            adapter.submitList(new ArrayList<>(posts));
            render();
        });
        model.liked().observe(this, liked -> adapter.setMarks(liked, model.savedIds().getValue() == null ? new java.util.HashSet<>() : model.savedIds().getValue()));
        model.savedIds().observe(this, saved -> adapter.setMarks(model.liked().getValue() == null ? new java.util.HashSet<>() : model.liked().getValue(), saved));
        model.loading().observe(this, loading -> render());
        model.error().observe(this, error -> render());
        model.offline().observe(this, offline -> binding.evolveOffline.setVisibility(offline ? View.VISIBLE : View.GONE));
        model.notifications().observe(this, list -> {
            int unread = model.unreadCount();
            binding.evolveNotificationsBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            binding.evolveNotificationsBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
        });
    }

    /** Chooses between the list, the loading skeleton, the empty text and the error with Retry. */
    private void render() {
        List<SharedBlock> posts = model.shown().getValue();
        boolean empty = posts == null || posts.isEmpty();
        boolean loading = Boolean.TRUE.equals(model.loading().getValue());
        String error = model.error().getValue();
        binding.evolveSkeleton.setVisibility(empty && loading && error == null ? View.VISIBLE : View.GONE);
        String text = null;
        boolean retry = false;
        if (empty && !loading) {
            if (error != null) {
                text = error;
                retry = model.available();
            } else {
                text = getString(emptyText());
            }
        }
        binding.evolveState.setText(text);
        binding.evolveStateLayout.setVisibility(text == null ? View.GONE : View.VISIBLE);
        binding.evolveRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        binding.evolvePublish.setVisibility(model.section() == Section.SAVED ? View.GONE : View.VISIBLE);
        // The profile card stays above the empty text of "no posts yet"
        binding.evolveStateLayout.setTranslationY(profile.visible ? binding.getRoot().getResources().getDisplayMetrics().density * 120 : 0);
    }

    @StringRes
    private int emptyText() {
        if (!model.query().isEmpty() || model.kind() != null) return R.string.evolve_no_results;
        return switch (model.section()) {
            case SAVED -> R.string.evolve_empty_saved;
            case PROFILE -> R.string.evolve_empty_profile;
            default -> model.tab() == Tab.FOLLOWING ? R.string.evolve_empty_following : R.string.evolve_empty_explore;
        };
    }

    // --------------------------------------------------------------------------------------------------- the actions

    @Override
    public void onOpen(SharedBlock post) {
        startActivity(EvolvePostActivity.intent(this, post.id(), false));
    }

    @Override
    public void onComments(SharedBlock post) {
        startActivity(EvolvePostActivity.intent(this, post.id(), true));
    }

    @Override
    public void onLike(SharedBlock post) {
        model.toggleLike(post, failure(R.string.evolve_action_failed));
    }

    @Override
    public void onSave(SharedBlock post) {
        boolean wasSaved = model.savedIds().getValue() != null && model.savedIds().getValue().contains(post.id());
        model.toggleSave(post, new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                SketchwareUtil.toast(getString(wasSaved ? R.string.evolve_unsaved_done : R.string.evolve_saved_done));
            }

            @Override
            public void onError(@NonNull String message) {
                SketchwareUtil.toastError(getString(R.string.evolve_action_failed, message));
            }
        });
    }

    private EvolveRepository.Callback<Void> failure(@StringRes int format) {
        return new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
            }

            @Override
            public void onError(@NonNull String message) {
                SketchwareUtil.toastError(getString(format, message));
            }
        };
    }

    @Override
    public void onMenu(View anchor, SharedBlock post) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 1, R.string.evolve_menu_share);
        menu.getMenu().add(0, 2, 2, R.string.evolve_menu_report);
        boolean mine = post.authorUid().equals(model.myUid());
        if (mine) menu.getMenu().add(0, 3, 3, R.string.evolve_menu_edit);
        if (mine || Boolean.TRUE.equals(model.moderator().getValue())) menu.getMenu().add(0, 4, 4, R.string.evolve_menu_delete);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> EvolveActions.share(this, post);
                case 2 -> EvolveActions.report(this, model.myUid(), post);
                case 3 -> startActivity(EvolvePublishActivity.intent(this, post.id()));
                case 4 -> EvolveActions.confirmDelete(this, post, () -> model.delete(post, new EvolveRepository.Callback<>() {
                    @Override
                    public void onSuccess(Void value) {
                        SketchwareUtil.toast(getString(R.string.evolve_deleted));
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        SketchwareUtil.toastError(getString(R.string.evolve_delete_failed, message));
                    }
                }));
                default -> {
                    return false;
                }
            }
            return true;
        });
        menu.show();
    }

    // ------------------------------------------------------------------------------------------------ the profile card

    /** One row above the posts, shown in the Profile section. */
    private final class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.Holder> {
        boolean visible;
        private String bio = "";
        private long followers = -1;
        private EvolveProfileCard card;

        void setVisible(boolean visible) {
            if (this.visible == visible) return;
            this.visible = visible;
            notifyDataSetChanged();
        }

        void load() {
            EvolveProfile me = model.me();
            if (me == null) return;
            EvolveSocialRepository.loadProfile(me.uid(), getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
                @Override
                public void onSuccess(EvolveProfile value) {
                    bio = value == null ? "" : value.bio();
                    bindCard();
                }

                @Override
                public void onError(@NonNull String message) {
                }
            });
            EvolveSocialRepository.followerCount(me.uid(), getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
                @Override
                public void onSuccess(Long value) {
                    followers = value;
                    bindCard();
                }

                @Override
                public void onError(@NonNull String message) {
                }
            });
        }

        void bindCard() {
            if (card != null) card.bind();
        }

        @Override
        public int getItemCount() {
            return visible ? 1 : 0;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(EvolveProfileHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            card = new EvolveProfileCard(holder.item);
            card.bind();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final EvolveProfileHeaderBinding item;

            Holder(EvolveProfileHeaderBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }

        /** Binds the card's texts and the bio editor. */
        final class EvolveProfileCard {
            private final EvolveProfileHeaderBinding item;

            EvolveProfileCard(EvolveProfileHeaderBinding item) {
                this.item = item;
            }

            void bind() {
                EvolveProfile me = model.me();
                if (me == null) return;
                item.profileName.setText(me.name());
                if (!me.photoUrl().isEmpty()) {
                    Glide.with(item.getRoot()).load(me.photoUrl()).override(160, 160).into(item.profileAvatar);
                }
                item.profileBio.setText(bio.isEmpty() ? getString(R.string.evolve_profile_bio_empty) : bio);
                List<SharedBlock> mine = model.shown().getValue();
                int posts = mine == null ? 0 : mine.size();
                String postsText = getResources().getQuantityString(R.plurals.evolve_posts_count, posts, EvolvePostCard.count(posts));
                String followersText = followers < 0 ? "" : getResources().getQuantityString(R.plurals.evolve_followers_count,
                        (int) Math.min(followers, Integer.MAX_VALUE), EvolvePostCard.count(followers));
                item.profileNumbers.setText(followersText.isEmpty() ? postsText : getString(R.string.evolve_profile_numbers, postsText, followersText));
                item.profileEdit.setOnClickListener(v -> editBio(me));
            }

            private void editBio(EvolveProfile me) {
                EditText input = new EditText(EvolveActivity.this);
                input.setText(bio);
                input.setHint(R.string.evolve_profile_bio_hint);
                input.setSingleLine(false);
                input.setMaxLines(3);
                input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(EvolveProfile.MAX_BIO_LENGTH)});
                int pad = (int) (20 * getResources().getDisplayMetrics().density);
                android.widget.FrameLayout holder = new android.widget.FrameLayout(EvolveActivity.this);
                holder.setPadding(pad, pad / 2, pad, 0);
                holder.addView(input);
                new MaterialAlertDialogBuilder(EvolveActivity.this)
                        .setTitle(R.string.evolve_profile_edit_bio)
                        .setView(holder)
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton(R.string.common_word_save, (d, w) -> {
                            String clean = PostRules.clean(input.getText().toString());
                            EvolveSocialRepository.saveProfile(new EvolveProfile(me.uid(), me.name(), me.photoUrl(), clean, 0), false,
                                    getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
                                        @Override
                                        public void onSuccess(Void value) {
                                            bio = clean;
                                            bind();
                                            SketchwareUtil.toast(getString(R.string.evolve_profile_saved));
                                        }

                                        @Override
                                        public void onError(@NonNull String message) {
                                            SketchwareUtil.toastError(getString(R.string.evolve_action_failed, message));
                                        }
                                    });
                        })
                        .show();
            }
        }
    }
}
