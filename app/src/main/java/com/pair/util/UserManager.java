package com.pair.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.ContactsManager;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.HttpResponse;
import com.pair.net.api.UserApi;
import com.pair.pairapp.BuildConfig;

import org.apache.http.HttpStatus;

import java.io.EOFException;
import java.io.File;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;
import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
@SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "TryFinallyCanBeTryWithResources", "unused"})
public class UserManager {

    private static final String TAG = UserManager.class.getSimpleName();
    private static final String KEY_SESSION_ID = "lfl/-90-09=klvj8ejf"; //don't give a clue what this is for security reasons
    private static final String KEY_USER_PASSWORD = "klfiielklaklier"; //and this one too
    private static final String KEY_USER_CCC = "USER_ccc";
    public static final String KEY_USER_VERIFIED = "vvlaikkljhf";
    private String VERIFICATION_TOKEN;
    private static final UserManager INSTANCE = new UserManager();

    private volatile int loginAttempts = 0,
            signUpAttempts = 0;
    private volatile boolean loginSignUpBusy = false; //login or sign up never should run in parallel
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private final Exception NO_CONNECTION_ERROR = new Exception("not connected to the internet");
    private final BaseJsonAdapter<User> adapter = new UserJsonAdapter();

    private final UserApi userApi = new RestAdapter.Builder()
            .setEndpoint(Config.PAIRAPP_ENDPOINT)
            .setRequestInterceptor(Config.INTERCEPTOR)
            .setLogLevel((BuildConfig.DEBUG) ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
            .setLog(new AndroidLog(TAG))
            .build()
            .create(UserApi.class);

    @Deprecated
    public static UserManager getInstance(@SuppressWarnings("UnusedParameters") @NonNull Context context) {
        return INSTANCE;
    }

    public static UserManager getInstance() {
        return INSTANCE;
    }

    private UserManager() {
    }

    private void saveMainUser(User user, String userCCC) {
        final Context context = Config.getApplicationContext();
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(user);
        realm.commitTransaction();
        // TODO: 6/25/2015 encrypt the id and password before storing it
        getSettings()
                .edit()
                .putString(KEY_SESSION_ID, user.get_id())
                .putString(KEY_USER_PASSWORD, user.getPassword())
                .putString(KEY_USER_CCC, userCCC)
                .commit();
    }

    public User getMainUser() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User user = getMainUser(realm);
        if (user != null) {
            //returning {@link RealmObject} from methods will leak resources since
            // that will prevent us from closing the realm instance. hence we do a shallow copy.
            // downside is changes to this object will not be persisted which is just what we want
            user = User.copy(user);
        }
        realm.close();
        return user;
    }

    public boolean isUserLoggedIn() {
        return getMainUser() != null;
    }

    public boolean isUserVerified() {
        return isUserLoggedIn() && getSettings().getBoolean(KEY_USER_VERIFIED, false);
    }

    private SharedPreferences getSettings() {
        return Config.getApplicationWidePrefs();
    }

    private User getMainUser(Realm realm) {
        String currUserId = getSettings().getString(KEY_SESSION_ID, null);
        if (currUserId == null) {
            Config.disableComponents();
            return null;
        }
        return realm.where(User.class).equalTo(User.FIELD_ID, currUserId).findFirst();
    }

    private String getUserPassword() {
        String password = getSettings().getString(KEY_USER_PASSWORD, null);
        if (password == null) {
            // TODO: 7/19/2015 logout user and clean up realm as we suspect intruders
            throw new IllegalStateException("session data tampered with");
        }
        return password;
    }

    public boolean isMainUser(String userId) {
        User thisUser = getMainUser();
        return ((!(userId == null || thisUser == null)) && thisUser.get_id().equals(userId));
    }

    public void createGroup(final String groupName, final CallBack callBack) {
        //noinspection unchecked
        createGroup(groupName, Collections.EMPTY_LIST, callBack);
    }

    public void createGroup(final String groupName, final List<String> membersId, final CallBack callBack) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR);
            return;
        }
        if (isUser(User.generateGroupId(groupName))) {
            //already exist[
            callBack.done(new Exception("group already exist"));
            return;
        }
        userApi.createGroup(getMainUser().get_id(), groupName, membersId, new Callback<User>() {
            @Override
            public void success(final User group, Response response) {
                final Handler handler = new Handler();
                new Thread() {
                    @Override
                    public void run() {
                        completeGroupCreation(group, membersId);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                callBack.done(null);
                            }
                        });
                    }
                }.start();
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    createGroup(groupName, callBack);
                } else {
                    Log.i(TAG, "failed to create group");
                    callBack.done(e);
                }
            }
        });
    }

    private void completeGroupCreation(User group, List<String> membersId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        group.setMembers(new RealmList<User>());//required for realm to behave
        User mainUser = getMainUser(realm);
        if (mainUser == null) {
            throw new IllegalStateException("no user logged in");
        }
        RealmList<User> members = User.aggregateUsers(realm, membersId, null);
        group.getMembers().addAll(members);
        group.getMembers().add(mainUser);
        group.setAdmin(mainUser);
        group.setType(User.TYPE_GROUP);
        realm.copyToRealmOrUpdate(group);
        realm.commitTransaction();
        realm.close();
    }

    public void removeMembers(final String groupId, final List<String> members, final CallBack callBack) {
        final Exception e = checkPermission(groupId);
        if (e != null) { //unauthorised
            callBack.done(e);
            return;
        }
        if (members.contains(getMainUser().get_id())) {
            throw new IllegalArgumentException("admin cannot remove him/herself");
        }

        userApi.removeMembersFromGroup(groupId, getMainUser().get_id(), members, new Callback<Response>() {
            @Override
            public void success(Response o, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                    @Override
                    public boolean accept(User user) {
                        return (user != null && group.getMembers().contains(user));
                    }
                };
                RealmList<User> membersToDelete = aggregateUsers(realm, members, filter);
                group.getMembers().removeAll(membersToDelete);
                realm.commitTransaction();
                realm.close();
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    removeMembers(groupId, members, callBack);
                } else {
                    callBack.done(e);
                }
            }
        });
    }

    private boolean isUser(String id) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        boolean isUser = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst() != null;
        realm.close();
        return isUser;
    }

    public boolean isAdmin(String groupId, String userId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
        if (group == null) {
            realm.close();
            throw new IllegalArgumentException("no group with such id");
        }
        String adminId = group.getAdmin().get_id();
        realm.close();
        return adminId.equals(userId);
    }

    private Exception checkPermission(String groupId) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            return (NO_CONNECTION_ERROR);
        }
        if (!isUser(groupId)) {
            return new IllegalArgumentException("no group with such id");
        }
        if (!isAdmin(groupId, getMainUser().get_id())) {
            return new IllegalAccessException("you don't have the authority to add/remove a member");
        }
        return null;
    }

    public void addMembers(final String groupId, final List<String> membersId, final CallBack callBack) {
        Exception e = checkPermission(groupId);
        if (e != null) {
            callBack.done(e);
            return;
        }
        userApi.addMembersToGroup(groupId, getMainUser().get_id(), membersId, new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                    @Override
                    public boolean accept(User user) {
                        return (user != null && !group.getMembers().contains(user));
                    }
                };
                RealmList<User> newMembers = aggregateUsers(realm, membersId, filter);
                group.getMembers().addAll(newMembers);
                realm.commitTransaction();
                realm.close();
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    addMembers(groupId, membersId, callBack);
                } else {
                    callBack.done(e);
                }
            }
        });
    }

    private void getGroupMembers(final String id) {
        userApi.getGroupMembers(id, new Callback<List<User>>() {
            @Override
            public void success(final List<User> freshMembers, final Response response) {
                new Thread() {
                    @Override
                    public void run() {
                        updateLocalGroupMembers(freshMembers, id);
                    }
                }.start();
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (handleError(retrofitError) == null) {
                    getGroupMembers(id);
                }
            }
        });
    }

    private void updateLocalGroupMembers(List<User> freshMembers, String id) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User group = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
        group.getMembers().clear();
        for (User freshMember : freshMembers) {
            freshMember.setType(User.TYPE_NORMAL_USER);
        }
        group.getMembers().addAll(realm.copyToRealmOrUpdate(freshMembers));
        realm.commitTransaction();
        realm.close();
    }

    public void refreshGroup(final String id) {
        if (!isUser(id)) {
            throw new IllegalArgumentException("passed id is invalid");
        }
        doRefreshGroup(id);
    }

    private void doRefreshGroup(String id) {
        getGroupInfo(id); //async
    }

    private void getGroupInfo(final String id) {
        userApi.getGroup(id, new Callback<User>() {
            @Override
            public void success(User group, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
                realm.beginTransaction();
                if (staleGroup != null) {
                    staleGroup.setName(group.getName());
                } else {
                    group.setType(User.TYPE_GROUP);
                    group.setMembers(new RealmList<User>());
                    group.getMembers().add(group.getAdmin());
                    realm.copyToRealm(group);
                }
                realm.commitTransaction();
                realm.close();
                realm = Realm.getInstance(Config.getApplicationContext());
                User g = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
                Log.i(TAG, "members of " + g.getName() + " are: " + g.getMembers().size());
                realm.close();
                getGroupMembers(id); //async
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (handleError(retrofitError) == null) {
                    getGroupInfo(id);
                }
            }
        });
    }

    @Nullable
    private RealmList<User> aggregateUsers(Realm realm, List<String> membersId, ContactsManager.Filter<User> filter) {
        RealmList<User> members = new RealmList<>();
        for (String id : membersId) {
            User user = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
            if (filter.accept(user)) {
                members.add(user);
            }
        }
        return members;
    }

    public void refreshGroups() {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            return;
        }
        getGroups();
    }

    public void refreshUserDetails(final String userId) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            return;
        }
        //update user here
        if (isGroup(userId)) {
            doRefreshGroup(userId);
        } else {
            userApi.getUser(userId, new Callback<User>() {
                @Override
                public void success(User onlineUser, Response response) {
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    realm.beginTransaction();
                    User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
                    user.setLastActivity(onlineUser.getLastActivity());
                    user.setStatus(onlineUser.getStatus());
                    user.setName(onlineUser.getName());
                    realm.commitTransaction();
                    realm.close();
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    Exception e = handleError(retrofitError);
                    if (e == null) {
                        refreshUserDetails(userId);
                    } else {
                        Log.i(TAG, "failed to refresh after 3 attempts");
                    }
                }
            });
        }
    }

    public boolean isGroup(String userId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        try {
            User potentiallyGroup = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            return potentiallyGroup != null && (potentiallyGroup.getType() == User.TYPE_GROUP);
        } finally {
            realm.close();
        }
    }

    private void getGroups() {
        User mainUser = getMainUser();
        if (mainUser == null) {
            throw new IllegalStateException("this operation can only continue only when user is logged in");
        }

        userApi.getGroups(mainUser.get_id(), new Callback<List<User>>() {
            @Override
            public void success(List<User> groups, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                User mainUser = getMainUser(realm);
                if (mainUser == null) {
                    throw new IllegalStateException("no user logged in");
                }
                for (User group : groups) {
                    User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, group.get_id()).findFirst();
                    if (staleGroup != null) { //already exist just update
                        staleGroup.setName(group.getName()); //admin might have changed name
                        staleGroup.setType(User.TYPE_GROUP);
                    } else { //new group
                        // because the json returned from our backend is not compatible with our schema here
                        // the backend always clears the members and type field so we have to set it up down here manually
                        group.setType(User.TYPE_GROUP);
                        group.setMembers(new RealmList<User>());
                        group.getMembers().add(group.getAdmin());
                        if (!group.getAdmin().get_id().equals(mainUser.get_id())) {
                            group.getMembers().add(mainUser);
                        }
                        realm.copyToRealmOrUpdate(group);
                    }
                }
                realm.commitTransaction();
                realm.close();
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (handleError(retrofitError) == null) {
                    getGroups();
                }
            }
        });
    }

    public void changeDp(String imagePath, CallBack callBack) {
        this.changeDp(getMainUser().get_id(), imagePath, callBack);
    }

    public void changeDp(final String userId, final String imagePath, final CallBack callback) {
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            callback.done(new Exception("file " + imagePath + " does not exist"));
            return;
        }
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        final User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user == null) {
            throw new IllegalArgumentException("user does not exist");
        }

        String placeHolder = user.getType() == User.TYPE_GROUP ? "groups" : "users";

        realm.close();
        userApi.changeDp(placeHolder, userId, new TypedFile("image/*", imageFile), new Callback<HttpResponse>() {
            @Override
            public void success(HttpResponse response, Response response2) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    realm.beginTransaction();
                    //noinspection ConstantConditions
                    User user = realm.where(User.class).equalTo("_id", userId).findFirst();
                    if (user != null) {
                        user.setDP(response.getMessage());
                    }
                    realm.commitTransaction();
                } finally {
                    realm.close();
                }
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    changeDp(imagePath, callback); //retry
                } else {
                    callback.done(e); //may be our fault but we have reach maximum retries
                }
            }
        });
    }

    public void logIn(String phoneNumber, String password, String gcmRegId, String userCCC, final CallBack callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        if (TextUtils.isEmpty(phoneNumber)) {
            callback.done(new Exception("invalid phone number"));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            callback.done(new Exception("invalid password"));
            return;
        }
        if (TextUtils.isEmpty(userCCC)) {
            callback.done(new Exception("CCC cannot be null"));
            return;
        }
        if (TextUtils.isEmpty(gcmRegId)) {
            callback.done(new Exception("GCM registration id cannot be null"));
            return;
        }

        User user = new User();
        user.setPassword(password);
        try {
            phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, userCCC);
        } catch (NumberParseException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            callback.done(e);
            return;
        }
        user.set_id(phoneNumber);
        //FIXME: 7/26/2015 use country instead of ccc.
        user.setCountry(userCCC);
        user.setGcmRegId(gcmRegId);
        doLogIn(user, userCCC, callback);
    }

    //this method must be called on the main thread
    private void doLogIn(final User user, final String userCCC, final CallBack callback) {

        if (loginSignUpBusy) {
            return;
        }
        loginSignUpBusy = true;
        loginAttempts++;
        userApi.logIn(adapter.toJson(user), new Callback<User>() {
            @Override
            public void success(User backendUser, Response response) {
                loginSignUpBusy = false;
                loginAttempts = 0;
                //our backend deletes password fields so we got to use our copy here
                backendUser.setPassword(user.getPassword());
                saveMainUser(backendUser, userCCC);
                getGroups(); //async
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                loginSignUpBusy = false;
                Exception e = handleError(retrofitError);
                if (e == null && loginAttempts < 3) {
                    //not our problem lets try again
                    doLogIn(user, userCCC, callback);
                } else {
                    loginAttempts = 0;
                    callback.done(e); //may be our fault but we have ran out of resources
                }
            }
        });
    }


    public void signUp(final String name, final String phoneNumber, final String password, final String gcmRegId, final String userCCC, final CallBack callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        if (loginSignUpBusy) {
            return;
        }
        loginSignUpBusy = true;
        signUpAttempts++;
        if (TextUtils.isEmpty(name)) {
            callback.done(new Exception("name is invalid"));
        } else if (TextUtils.isEmpty(phoneNumber)) {
            callback.done(new Exception("phone number is invalid"));
        } else if (TextUtils.isEmpty(password)) {
            callback.done(new Exception("password is invalid"));
        } else if (TextUtils.isEmpty(userCCC)) {
            callback.done(new Exception("ccc is invalid"));
        } else {
            final User user = new User();
            try {
                user.set_id(PhoneNumberNormaliser.toIEE(phoneNumber, userCCC));
            } catch (NumberParseException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
                }
                callback.done(e);
                return;
            }
            user.setPassword(password);
            user.setName(name);
            user.setCountry(userCCC); // TODO: 7/26/2015 use actual country instead of ccc
            user.setGcmRegId(gcmRegId);
            userApi.registerUser(adapter.toJson(user), new Callback<User>() {
                @Override
                public void success(User backEndUser, Response response) {
                    loginSignUpBusy = false;
                    signUpAttempts = 0;
                    backEndUser.setPassword(user.getPassword());
                    saveMainUser(backEndUser, userCCC);
                    callback.done(null);
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    loginSignUpBusy = false;
                    // TODO: 6/25/2015 handle error
                    Exception e = handleError(retrofitError);
                    if (e == null && signUpAttempts < 3) {
                        //not our fault and we have more chance lets try again
                        signUp(name, phoneNumber, password, gcmRegId, userCCC, callback);
                    } else {
                        signUpAttempts = 0;
                        callback.done(e); //may not be our fault but we have ran out of retries
                    }
                }
            });
        }
    }

    public void generateAndSendVerificationToken(final String number) {
        new Thread() {
            @Override
            public void run() {
                SecureRandom random = new SecureRandom();
                int num = random.nextInt() / 10000;
                num = (num > 0) ? num : num * -1; //convert negative ints to positive ones
                synchronized (this) {
                    VERIFICATION_TOKEN = String.valueOf(num);
                }
                Log.d(TAG, VERIFICATION_TOKEN);
                SmsManager.getDefault().sendTextMessage(number, null, VERIFICATION_TOKEN, null, null);
            }
        }.start();
    }

    private synchronized String getVERIFICATION_TOKEN() {
        return VERIFICATION_TOKEN;
    }

    public void LogOut(Context context, final CallBack logOutCallback) {
        //TODO logout user from backend
        String userId = getSettings().getString(KEY_SESSION_ID, null);
        if ((userId == null)) {
            throw new AssertionError("calling logout when no user is logged in"); //security hole!
        }

        getSettings()
                .edit()
                .remove(KEY_SESSION_ID)
                .apply();
        Realm realm = Realm.getInstance(context);
        // TODO: 6/14/2015 remove this in production code.
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user == null) {
            throw new IllegalStateException("existing session id with no corresponding User in the database");
        }
        realm.close();
        GcmHelper.unRegister(context, new GcmHelper.UnregisterCallback() {
            @Override
            public void done(Exception e) {
                //we don't care about gcm regid
                cleanUpRealm();
                logOutCallback.done(null);
            }
        });
    }

    public void fetchFriends(final List<String> array, final FriendsFetchCallback callback) {
        if (!ConnectionHelper.isConnected()) {
            callback.done(NO_CONNECTION_ERROR, null);
            return;
        }
        userApi.fetchFriends(array, new Callback<List<User>>() {
            @Override
            public void success(List<User> users, Response response) {
                callback.done(null, users);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)
                        || retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)
                        ) {
                    callback.done(retrofitError, null);
                } else if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                    if (ConnectionHelper.isConnectedOrConnecting()) {
                        //try again
                        fetchFriends(array, callback);
                    } else {
                        callback.done(retrofitError, null);
                    }
                } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) {
                    throw new AssertionError(retrofitError);
                } else {
                    callback.done(retrofitError, null);
                }
            }
        });
    }

    private void cleanUpRealm() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        realm.clear(User.class);
        realm.clear(Message.class);
        realm.clear(Conversation.class);
        realm.commitTransaction();
    }


    // FIXME: 6/25/2015 find a sensible place to keep this error handler so that message dispatcher and others can share it
    private Exception handleError(RetrofitError retrofitError) {
        if (retrofitError.getCause() instanceof SocketTimeoutException) { //likely that  user turned on data but no plan
            return NO_CONNECTION_ERROR;
        } else if (retrofitError.getCause() instanceof EOFException) { //usual error when we try to connect first time after server startup
            Log.w(TAG, "EOF_EXCEPTION trying again");
            return null;
        }
        if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
            Log.w(TAG, "unexpected error, trying again");
            return null;
        } else if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)) {
            int statusCode = retrofitError.getResponse().getStatus();
            if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                Log.w(TAG, "internal server error, trying again");
                return null;
            }
            //crash early
            // as far as we know, our backend will only return other status code if its is our fault and that normally should not happen
            Log.wtf(TAG, "internal error, exiting");
            throw new RuntimeException("An unknown internal error occurred");
        } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) { //crash early
            Log.wtf(TAG, "internal error ");
            throw new RuntimeException("poorly encoded json data");
        } else if (retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)) {
            if (ConnectionHelper.isConnectedOrConnecting()) {
                return null;
            }
            //bubble up error and empty
            Log.w(TAG, "no network connection, aborting");
            return NO_CONNECTION_ERROR;
        }

        //may be retrofit added some error kinds in a new version we are not aware of so lets crash to ensure that
        //we find out
        throw new AssertionError("unknown error kind");
    }

    public void leaveGroup(final String id, final CallBack callBack) {
        if (!isGroup(id) || isAdmin(id)) {
            throw new IllegalArgumentException("not group or you are the admin");
        }
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR);
        }
        userApi.leaveGroup(id, getMainUser().get_id(), getUserPassword(), new Callback<HttpResponse>() {
            @Override
            public void success(HttpResponse httpResponse, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    User group = realm.where(User.class).equalTo("_id", id).findFirst();
                    if (group != null) {
                        realm.beginTransaction();
                        group.removeFromRealm();
                        realm.commitTransaction();
                        callBack.done(null);
                    }
                } finally {
                    realm.close();
                }

            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    leaveGroup(id, callBack);
                } else {
                    callBack.done(e);
                }
            }
        });
    }

    public boolean isAdmin(String id) {
        return isAdmin(id, getMainUser().get_id());
    }

    public String getDefaultCCC() {
        String ccc = getSettings()
                .getString(KEY_USER_CCC, null);
        if (ccc == null) {
            throw new IllegalStateException("user cc is null");
        }

        return ccc;
    }


    public interface FriendsFetchCallback {
        void done(Exception e, List<User> users);
    }

    public interface CallBack {
        void done(Exception e);
    }

    @SuppressWarnings("unused")
    private class GroupFilter implements ContactsManager.Filter<User> {
        private final User group;

        public GroupFilter(User user) {
            this.group = user;
        }

        @Override
        public boolean accept(User user) {
            return (user != null && !group.getMembers().contains(user) && !isAdmin(group.get_id(), user.get_id()));
        }
    }
}
