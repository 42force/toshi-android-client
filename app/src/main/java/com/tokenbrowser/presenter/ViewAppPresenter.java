package com.tokenbrowser.presenter;

import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Toast;

import com.tokenbrowser.manager.network.DirectoryService;
import com.tokenbrowser.model.local.User;
import com.tokenbrowser.model.network.App;
import com.tokenbrowser.model.network.ReputationScore;
import com.tokenbrowser.token.R;
import com.tokenbrowser.token.databinding.ActivityViewAppBinding;
import com.tokenbrowser.util.ImageUtil;
import com.tokenbrowser.util.LogUtil;
import com.tokenbrowser.view.BaseApplication;
import com.tokenbrowser.view.activity.ChatActivity;
import com.tokenbrowser.view.activity.ViewAppActivity;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class ViewAppPresenter implements Presenter<ViewAppActivity> {

    private ViewAppActivity activity;
    private App app;
    private String appOwnerAddress;
    private User user;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;

    @Override
    public void onViewAttached(ViewAppActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        getIntentData();
        fetchUserFromApp();
        fetchUserReputation();
        initView();
        initClickListeners();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void getIntentData() {
        this.appOwnerAddress = this.activity.getIntent().getStringExtra(ViewAppActivity.APP_OWNER_ADDRESS);
        this.app = DirectoryService.getCachedApp(appOwnerAddress);
        if (this.app == null) {
            Toast.makeText(this.activity, R.string.error__app_loading, Toast.LENGTH_LONG).show();
            this.activity.finish();
        }
    }

    private void fetchUserFromApp() {
        final Subscription sub = BaseApplication
                .get()
                .getTokenManager()
                .getUserManager()
                .getUserFromAddress(this.appOwnerAddress)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleAppLoaded, this::handleAppLoadingFailed);

        this.subscriptions.add(sub);
    }

    private void fetchUserReputation() {
        final Subscription reputationSub = BaseApplication
                .get()
                .getTokenManager()
                .getReputationManager()
                .getReputationScore(this.appOwnerAddress)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleReputationResponse, this::handleReputationError);

        this.subscriptions.add(reputationSub);
    }

    private void handleReputationResponse(final ReputationScore reputationScore) {
        final String reviewCount = String.valueOf(reputationScore.getCount());
        final double score = reputationScore.getScore() != null ? reputationScore.getScore() : 0;
        final String stringScore = String.valueOf(score);
        this.activity.getBinding().reviewCount.setText(reviewCount);
        this.activity.getBinding().ratingView.setStars(score);
        this.activity.getBinding().reputationScore.setText(stringScore);
        this.activity.getBinding().ratingInfo.setRatingInfo(reputationScore);
    }

    private void handleReputationError(final Throwable throwable) {
        LogUtil.e(getClass(), "Error during reputation fetching " + throwable.getMessage());
    }

    private void initView() {
        final ActivityViewAppBinding binding = this.activity.getBinding();
        binding.title.setText(app.getDisplayName());
        binding.name.setText(app.getDisplayName());
        binding.username.setText(app.getDisplayName());
        generateQrCode(this.app.getOwnerAddress());
    }

    private void generateQrCode(final String address) {
        final Subscription sub = ImageUtil
                .generateQrCodeForWalletAddress(address)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::renderQrCode);

        this.subscriptions.add(sub);
    }

    private void renderQrCode(final Bitmap qrCodeBitmap) {
        if (this.activity == null || this.activity.getBinding() == null) {
            return;
        }
        this.activity.getBinding().qrCodeImage.setAlpha(0.0f);
        this.activity.getBinding().qrCodeImage.setImageBitmap(qrCodeBitmap);
        this.activity.getBinding().qrCodeImage.animate().alpha(1f).setDuration(200).start();
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(this::handleClosedClicked);
        this.activity.getBinding().messageContactButton.setOnClickListener(this::handleOnMessageClicked);
    }

    private void handleClosedClicked(final View view) {
        this.activity.finish();
    }

    private void handleOnMessageClicked(final View v) {
        if (this.user == null) {
            return;
        }
        final Intent intent = new Intent(this.activity, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA__REMOTE_USER_ADDRESS, this.user.getTokenId());
        this.activity.startActivity(intent);
    }

    private void handleAppLoaded(final User user) {
        this.user = user;
        if (this.user == null || this.activity == null) {
            return;
        }
        this.activity.getBinding().username.setText(user.getDisplayName());
        this.activity.getBinding().username.setText(user.getUsername());
    }

    private void handleAppLoadingFailed(final Throwable throwable) {
        LogUtil.print(getClass(), throwable.toString());
        if (this.activity != null) {
            this.activity.finish();
            Toast.makeText(this.activity, R.string.error__app_loading, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onViewDetached() {
        this.activity = null;
    }

    @Override
    public void onViewDestroyed() {
        this.subscriptions.clear();
        this.activity = null;
    }
}