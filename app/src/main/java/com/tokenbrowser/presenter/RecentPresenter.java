package com.tokenbrowser.presenter;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.tokenbrowser.model.local.Conversation;
import com.tokenbrowser.token.R;
import com.tokenbrowser.view.BaseApplication;
import com.tokenbrowser.view.activity.ChatActivity;
import com.tokenbrowser.view.adapter.RecentAdapter;
import com.tokenbrowser.view.adapter.listeners.OnItemClickListener;
import com.tokenbrowser.view.custom.HorizontalLineDivider;
import com.tokenbrowser.view.fragment.toplevel.RecentFragment;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public final class RecentPresenter implements
        Presenter<RecentFragment>,
        OnItemClickListener<Conversation> {

    private RecentFragment fragment;
    private boolean firstTimeAttaching = true;
    private RecentAdapter adapter;
    private CompositeSubscription subscriptions;

    @Override
    public void onViewAttached(final RecentFragment fragment) {
        this.fragment = fragment;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }
        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.adapter = new RecentAdapter()
                .setOnItemClickListener(this);
    }

    private void initShortLivingObjects() {
        this.subscriptions = new CompositeSubscription();
        initRecentsAdapter();
        populateRecentsAdapter();
        updateEmptyState();
        attachSubscriber();
    }

    private void initRecentsAdapter() {
        final RecyclerView recyclerView = this.fragment.getBinding().recents;
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this.fragment.getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(this.adapter);

        final int dividerLeftPadding = this.fragment.getResources().getDimensionPixelSize(R.dimen.avatar_size_small)
                                     + this.fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.fragment.getContext(), R.color.divider))
                .setLeftPadding(dividerLeftPadding);
        recyclerView.addItemDecoration(lineDivider);
    }

    private void populateRecentsAdapter() {
        final Subscription sub =
                BaseApplication
                .get()
                .getTokenManager()
                .getSofaMessageManager()
                .loadAllConversations()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((conversations) -> {
                    this.adapter.setConversations(conversations);
                    updateEmptyState();
                });
        this.subscriptions.add(sub);
    }

    private void attachSubscriber() {
        final Subscription sub =
                BaseApplication
                        .get()
                        .getTokenManager()
                        .getSofaMessageManager()
                        .registerForAllConversationChanges()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((updatedConversation) -> {
                            this.adapter.updateConversation(updatedConversation);
                            updateEmptyState();
                        });
        this.subscriptions.add(sub);
    }

    private void updateEmptyState() {
        if (this.fragment == null) {
            return;
        }
        // Hide empty state if we have some content
        final boolean showingEmptyState = this.fragment.getBinding().emptyStateSwitcher.getCurrentView().getId() == this.fragment.getBinding().emptyState.getId();
        final boolean shouldShowEmptyState = this.adapter.getItemCount() == 0;

        if (shouldShowEmptyState && !showingEmptyState) {
            this.fragment.getBinding().emptyStateSwitcher.showPrevious();
        } else if (!shouldShowEmptyState && showingEmptyState) {
            this.fragment.getBinding().emptyStateSwitcher.showNext();
        }
    }

    @Override
    public void onItemClick(final Conversation clickedConversation) {
        final Intent intent = new Intent(this.fragment.getActivity(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA__REMOTE_USER_ADDRESS, clickedConversation.getMember().getTokenId());
        this.fragment.startActivity(intent);
    }

    @Override
    public void onViewDetached() {
        this.fragment = null;
    }

    @Override
    public void onViewDestroyed() {
        this.subscriptions.clear();
        this.fragment = null;
    }
}