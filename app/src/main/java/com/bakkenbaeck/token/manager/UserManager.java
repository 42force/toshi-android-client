package com.bakkenbaeck.token.manager;


import android.content.Context;
import android.content.SharedPreferences;

import com.bakkenbaeck.token.crypto.HDWallet;
import com.bakkenbaeck.token.manager.network.IdService;
import com.bakkenbaeck.token.manager.store.UserStore;
import com.bakkenbaeck.token.model.local.User;
import com.bakkenbaeck.token.model.network.ServerTime;
import com.bakkenbaeck.token.model.network.UserDetails;
import com.bakkenbaeck.token.util.FileNames;
import com.bakkenbaeck.token.util.LogUtil;
import com.bakkenbaeck.token.view.BaseApplication;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.adapter.rxjava.HttpException;
import rx.Observable;
import rx.Single;
import rx.SingleSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

public class UserManager {

    private final static String USER_ID = "uid";

    private final BehaviorSubject<User> userSubject = BehaviorSubject.create();
    private SharedPreferences prefs;
    private HDWallet wallet;
    private ExecutorService dbThreadExecutor;
    private UserStore userStore;

    public final BehaviorSubject<User> getUserObservable() {
        return this.userSubject;
    }

    public UserManager init(final HDWallet wallet) {
        this.wallet = wallet;
        initDatabase();
        initUser();
        return this;
    }

    private void initDatabase() {
        this.dbThreadExecutor = Executors.newSingleThreadExecutor();
        this.dbThreadExecutor.submit((Runnable) () -> UserManager.this.userStore = new UserStore());
    }

    private void initUser() {
        this.prefs = BaseApplication.get().getSharedPreferences(FileNames.USER_PREFS, Context.MODE_PRIVATE);
        if (!userExistsInPrefs()) {
            registerNewUser();
        } else {
            getExistingUser();
        }
    }

    private boolean userExistsInPrefs() {
        final String userId = this.prefs.getString(USER_ID, null);
        final String expectedAddress = wallet.getOwnerAddress();
        return userId != null && userId.equals(expectedAddress);
    }

    private void registerNewUser() {
        IdService
            .getApi()
            .getTimestamp()
            .subscribe(this::registerNewUserWithTimestamp, this::handleError);
    }

    private void handleError(final Throwable throwable) {
        LogUtil.e(getClass(), "Unable to register user");
        throw new RuntimeException(throwable);
    }

    private void registerNewUserWithTimestamp(final ServerTime serverTime) {
        final UserDetails ud = new UserDetails().setPaymentAddress(this.wallet.getPaymentAddress());

        IdService
                .getApi()
                .registerUser(ud, serverTime.get())
                .subscribe(this::updateCurrentUser, this::handleUserRegistrationFailed);
    }

    private void handleUserRegistrationFailed(final Throwable throwable) {
        LogUtil.error(getClass(), throwable.toString());
        if (throwable instanceof HttpException && ((HttpException)throwable).code() == 400) {
            getExistingUser();
        }
    }

    private void getExistingUser() {
        IdService.getApi()
                .getUser(this.wallet.getOwnerAddress())
                .subscribe(this::updateCurrentUser);
    }

    private void updateCurrentUser(final User user) {
        prefs
            .edit()
            .putString(USER_ID, user.getOwnerAddress())
            .apply();
        this.userSubject.onNext(user);
    }

    public void updateUser(final UserDetails userDetails, final SingleSubscriber<Void> completionCallback) {
        IdService
                .getApi()
                .getTimestamp()
                .subscribe((st) -> updateUserWithTimestamp(userDetails, st, completionCallback), completionCallback::onError);
    }

    private void updateUserWithTimestamp(
            final UserDetails userDetails,
            final ServerTime serverTime,
            final SingleSubscriber<Void> completionCallback) {

        IdService.getApi()
                .updateUser(this.wallet.getOwnerAddress(), userDetails, serverTime.get())
                .subscribe(this::updateCurrentUser, completionCallback::onError);
    }

    public Observable<User> getUserFromAddress(final String userAddress) {
        return Observable
                .concat(
                        this.userStore.loadForAddress(userAddress),
                        this.fetchAndCacheFromNetwork(userAddress))
                .subscribeOn(Schedulers.from(this.dbThreadExecutor))
                .observeOn(Schedulers.from(this.dbThreadExecutor))
                .first(user -> user != null && !user.needsRefresh());
    }

    private Observable<User> fetchAndCacheFromNetwork(final String userAddress) {
        return IdService
                .getApi()
                .getUser(userAddress)
                .toObservable()
                .subscribeOn(Schedulers.from(this.dbThreadExecutor))
                .observeOn(Schedulers.from(this.dbThreadExecutor))
                .doOnNext(this::cacheUser);
    }

    private void cacheUser(final User user) {
        if (this.userStore == null) {
            return;
        }

        this.userStore.save(user);
    }

    public Single<List<User>> searchByUsername(final String query) {
        return this.userStore
                .queryUsername(query)
                .subscribeOn(Schedulers.from(this.dbThreadExecutor))
                .observeOn(Schedulers.from(this.dbThreadExecutor));
    }

}
