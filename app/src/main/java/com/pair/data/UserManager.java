package com.pair.data;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.Config;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.net.HttpResponse;
import com.pair.net.UserApiV2;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.parse_client.ParseClient;
import com.pair.util.ConnectionUtils;
import com.pair.util.FileUtils;
import com.pair.util.GcmUtils;
import com.pair.util.PhoneNumberNormaliser;

import org.apache.http.HttpStatus;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmList;
import retrofit.RetrofitError;
import retrofit.mime.TypedFile;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
@SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "TryFinallyCanBeTryWithResources", "unused"})
public class UserManager {

    private static final String TAG = UserManager.class.getSimpleName();
    private static final String KEY_SESSION_ID = "lfl/-90-09=klvj8ejf"; //don't give a clue what this is for security reasons
    private static final String KEY_USER_PASSWORD = "klfiielklaklier"; //and this one too
    public static final String KEY_USER_VERIFIED = "vvlaikkljhf"; // and this


    private volatile User mainUser;
    private final Object mainUserLock = new Object();
    private static final UserManager INSTANCE = new UserManager();

    private final Exception NO_CONNECTION_ERROR;
    private final BaseJsonAdapter<User> adapter = new UserJsonAdapter();


    private final UserApiV2 userApi;
    private final Handler MAIN_THREAD_HANDLER;

    @Deprecated
    public static UserManager getInstance(@SuppressWarnings("UnusedParameters") @NonNull Context context) {
        return INSTANCE;
    }

    public static UserManager getInstance() {
        return INSTANCE;
    }

    private UserManager() {
        NO_CONNECTION_ERROR = new Exception(Config.getApplicationContext().getString(R.string.st_unable_to_connect));
        MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());
        userApi = ParseClient.getInstance();
    }

    private void saveMainUser(User user) {
        final Context context = Config.getApplicationContext();
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(user);
        realm.commitTransaction();
        // TODO: 6/25/2015 encrypt the id and password before storing it
        getSettings()
                .edit()
                .putString(KEY_SESSION_ID, user.getUserId())
                .putString(KEY_USER_PASSWORD, user.getPassword())
                .commit();
    }

    public User getMainUser() {
        synchronized (mainUserLock) {
            if (mainUser != null) {
                return mainUser;
            }
        }
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User user = getMainUser(realm);
        if (user != null) {
            //returning {@link RealmObject} from methods will leak resources since
            // that will prevent us from closing the realm instance. hence we do a shallow copy.
            // downside is changes to this object will not be persisted which is just what we want
            synchronized (mainUserLock) {
                mainUser = User.copy(user);
            }
            return mainUser;
        }
        realm.close();
        //noinspection ConstantConditions
        return user;
    }

    public boolean isUserLoggedIn() {
        return isEveryThingSetup();
    }

    private boolean isEveryThingSetup() {
        final User mainUser = getMainUser();
        if (mainUser == null || mainUser.getUserId().isEmpty() || mainUser.getName().isEmpty() || mainUser.getCountry().isEmpty()) {
            return false;
        } else //noinspection ConstantConditions
            if (getSettings().getString(KEY_SESSION_ID, "").isEmpty()) {
                return false;
            }
        return true;
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
        if (TextUtils.isEmpty(userId)) {
            return false;
        }
        User thisUser = getMainUser();
        return ((thisUser != null)) && thisUser.getUserId().equals(userId);
    }

    public void createGroup(final String groupName, final List<String> membersId, final CreateGroupCallBack callBack) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR, null);
            return;
        }
        if (isUser(User.generateGroupId(groupName))) {
            //already exist[
            callBack.done(new Exception("group with name " + groupName + "already exists"), null);
            return;
        }
        membersId.add(getMainUserId());
        userApi.createGroup(getMainUser().getUserId(), groupName, membersId, new UserApiV2.Callback<User>() {

            @Override
            public void done(Exception e, User group) {
                if (e == null) {
                    completeGroupCreation(group, membersId);
                    doNotify(callBack, null, group.getUserId());
                } else {
                    Log.i(TAG, "failed to create group");
                    doNotify(callBack, e, null);
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
        RealmList<User> members = User.aggregateUsers(realm, membersId, new ContactsManager.Filter<User>() {
            @Override
            public boolean accept(User user) {
                return user != null && !isGroup(user.getUserId());
            }
        });
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
            doNotify(e, callBack);
            return;
        }
        if (members.contains(getMainUser().getUserId())) {
            if (BuildConfig.DEBUG) {
                throw new IllegalArgumentException("admin cannot remove him/herself");
            }
            doNotify(new Exception("admin cannot remove him/herself"), callBack);
        }

        userApi.removeMembersFromGroup(groupId, getMainUser().getUserId(), members, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse response) {
                if (e == null) {
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    realm.beginTransaction();
                    final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                    final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                        @Override
                        public boolean accept(User user) {
                            return (user != null && group.getMembers().contains(user));
                        }
                    };
                    RealmList<User> membersToDelete = User.aggregateUsers(realm, members, filter);
                    group.getMembers().removeAll(membersToDelete);
                    realm.commitTransaction();
                    realm.close();
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);

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
        String adminId = group.getAdmin().getUserId();
        realm.close();
        return adminId.equals(userId);
    }

    private Exception checkPermission(String groupId) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return (NO_CONNECTION_ERROR);
        }
        if (!isUser(groupId)) {
            return new IllegalArgumentException("no group with such id");
        }
        if (!isAdmin(groupId, getMainUser().getUserId())) {
            return new IllegalAccessException("you don't have the authority to add/remove a member");
        }
        return null;
    }

    public void addMembersToGroup(final String groupId, final List<String> membersId, final CallBack callBack) {
        Exception e = checkPermission(groupId);
        if (e != null) {
            doNotify(e, callBack);
            return;
        }
        userApi.addMembersToGroup(groupId, getMainUser().getUserId(), membersId, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse httpResponse) {
                if (e == null) {
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    realm.beginTransaction();
                    final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                    final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                        @Override
                        public boolean accept(User user) {
                            return (user != null && !group.getMembers().contains(user) && !isGroup(user.getUserId()));
                        }
                    };
                    RealmList<User> newMembers = User.aggregateUsers(realm, membersId, filter);
                    group.getMembers().addAll(newMembers);
                    realm.commitTransaction();
                    realm.close();
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);
                }
            }
        });
    }

    private void getGroupMembers(final String id) {
        userApi.getGroupMembers(id, new UserApiV2.Callback<List<User>>() {
            @Override
            public void done(Exception e, final List<User> freshMembers) {
                if (e == null) {
                    WORKER.submit(new Runnable() {
                        @Override
                        public void run() {
                            updateLocalGroupMembers(freshMembers, id);
                        }
                    });
                }
            }
        });
    }

    private void saveFreshUsers(List<User> freshMembers) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        List<User> ret = saveFreshUsers(realm, freshMembers);
        realm.close();
    }

    private List<User> saveFreshUsers(Realm realm, List<User> freshMembers) {
        for (User freshMember : freshMembers) {
            freshMember.setType(User.TYPE_NORMAL_USER);
        }
        realm.beginTransaction();
        List<User> ret = realm.copyToRealmOrUpdate(freshMembers);
        realm.commitTransaction();
        updateUsersLocalNames();
        return ret;
    }

    private void updateLocalGroupMembers(List<User> freshMembers, String id) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        try {
            User group = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
            freshMembers = saveFreshUsers(realm, freshMembers);
            realm.beginTransaction();
            group.getMembers().clear();
            group.getMembers().addAll(freshMembers);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
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
        userApi.getGroup(id, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, final User group) {
                if (e == null) {
                    WORKER.submit(new Runnable() {
                        @Override
                        public void run() {
                            completeGetGroupInfo(group, id);
                        }
                    });
                }
            }
        });
    }

    private void completeGetGroupInfo(User group, String id) {
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

    public void refreshGroups() {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return;
        }
        getGroups();
    }

    public void refreshUserDetails(final String userId) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return;
        }
        //update user here
        if (isGroup(userId)) {
            doRefreshGroup(userId);
        } else {
            userApi.getUser(userId, new UserApiV2.Callback<User>() {
                @Override
                public void done(Exception e, User onlineUser) {
                    if (e == null) {
                        completeRefresh(onlineUser, userId);
                    }
                }
            });
        }
    }

    private void completeRefresh(User onlineUser, String userId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        user.setLastActivity(onlineUser.getLastActivity());
        user.setStatus(onlineUser.getStatus());
        user.setName(onlineUser.getName());
        realm.commitTransaction();
        //commit the changes and then
        //check if user is saved locally
        ContactsManager.Contact contact = ContactsManager.getInstance().findContactByPhoneSync(user.getUserId(), getUserCountryISO());
        if (contact != null) {
            realm.beginTransaction();
            //change remote name to local name
            user.setName(contact.name);
            realm.commitTransaction();
        }
        realm.close();
    }

    private final ExecutorService WORKER = Executors.newCachedThreadPool();

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
        userApi.getGroups(mainUser.getUserId(), new UserApiV2.Callback<List<User>>() {
            @Override
            public void done(Exception e, List<User> users) {
                if (e == null) {
                    completeGetGroups(users);
                }
            }
        });
    }

    private void completeGetGroups(List<User> groups) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User mainUser = getMainUser(realm);
        if (mainUser == null) {
            throw new IllegalStateException("no user logged in");
        }
        for (User group : groups) {
            User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, group.getUserId()).findFirst();
            if (staleGroup != null) { //already exist just update
                staleGroup.setName(group.getName()); //admin might have changed name
                staleGroup.setType(User.TYPE_GROUP);
            } else { //new group
                // because the json returned from our backend is not compatible with our schema here
                // the backend always clears the members and type field so we have to set it up down here manually
                group.setType(User.TYPE_GROUP);
                group.setMembers(new RealmList<User>());
                group.getMembers().add(group.getAdmin());
                if (!group.getAdmin().getUserId().equals(mainUser.getUserId())) {
                    group.getMembers().add(mainUser);
                }
                realm.copyToRealmOrUpdate(group);
            }
        }
        realm.commitTransaction();
        realm.close();
    }

    public void changeDp(String imagePath, CallBack callBack) {
        this.changeDp(getMainUser().getUserId(), imagePath, callBack);
    }

    public void changeDp(final String userId, final String imagePath, final CallBack callback) {
        final File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            doNotify(new Exception("file " + imagePath + " does not exist"), callback);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        final User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user == null) {
            Log.w(TAG, "can't change dp for user with id " + userId + " because no such user exists");
            doNotify(null, callback);
            return;
        }

        String placeHolder = User.isGroup(user) ? "groups" : "users";

        realm.close();
        userApi.changeDp(placeHolder, userId, new TypedFile("image/*", imageFile), new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, final HttpResponse response) {
                if (e == null) {
                    completeDpChangeRequest(response.getMessage(), userId, imageFile);
                    doNotify(null, callback);
                } else {
                    doNotify(e, callback); //may be our fault but we have reach maximum retries
                }
            }
        });
    }

    private void completeDpChangeRequest(String dpPath, String userId, File imageFile) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        try {
            realm.beginTransaction();
            //noinspection ConstantConditions
            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            if (user != null) {
                user.setDP(dpPath);
                FileUtils.copyTo(imageFile, new File(Config.getAppProfilePicsBaseDir(), user.getDP() + ".jpg"));
            }
            realm.commitTransaction();
        } catch (IOException e) {
            //we will not cancel the transaction
            Log.e(TAG, "failed to save user's profile locally: " + e.getMessage());
        } finally {
            realm.close();
        }
    }

    public void logIn(final Activity context, final String phoneNumber, final String userIso2LetterCode, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        GcmUtils.register(context, new GcmUtils.GCMRegCallback() {
            @Override
            public void done(Exception e, final String regId) {
                if (e == null) {
                    completeLogin(phoneNumber, regId, userIso2LetterCode, callback);
                } else {
                    doNotify(e, callback);
                }
            }
        });
    }

    private void completeLogin(String phoneNumber, String gcmRegId, String userIso2LetterCode, CallBack callback) {
        if (TextUtils.isEmpty(phoneNumber)) {
            doNotify(new Exception("invalid phone number"), callback);
            return;
        }

        if (TextUtils.isEmpty(userIso2LetterCode)) {
            doNotify(new Exception("userIso2LetterCode cannot be empty"), callback);
            return;
        }
        if (TextUtils.isEmpty(gcmRegId)) {
            doNotify(new Exception("GCM registration id cannot be empty"), callback);
            return;
        }

        User user = new User();
        try {
            phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, userIso2LetterCode);
        } catch (NumberParseException e) {
            if (com.pair.pairapp.BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            doNotify(new Exception(String.format(Config.getApplicationContext().getString(R.string.invalid_phone_number), phoneNumber)), callback);
            return;
        }
        user.setUserId(phoneNumber);
        user.setCountry(userIso2LetterCode);
        user.setGcmRegId(gcmRegId);
        String password = Base64.encodeToString(phoneNumber.getBytes(), Base64.DEFAULT);
        user.setPassword(password);
        doLogIn(user, userIso2LetterCode, callback);
    }

    //this method must be called on the main thread
    private void doLogIn(final User user, final String countryIso, final CallBack callback) {
        userApi.logIn(user, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User backendUser) {
                if (e == null) {
                    backendUser.setPassword(user.getPassword());
                    saveMainUser(backendUser);
                    getSettings()
                            .edit()
                            .putBoolean(KEY_USER_VERIFIED, true)
                            .commit();
                    getGroups(); //async
                    doNotify(null, callback);
                } else {
                    doNotify(e, callback);
                }
            }
        });
    }


    public void signUp(Activity context, final String name, final String phoneNumber, final String countryIso, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        GcmUtils.register(context, new GcmUtils.GCMRegCallback() {
            @Override
            public void done(Exception e, String regId) {
                if (e == null) {
                    completeSignUp(name, phoneNumber, regId, countryIso, callback);
                } else {
                    doNotify(e, callback);
                }
            }
        });
    }

    private void completeSignUp(final String name, final String phoneNumber, final String gcmRegId, final String countryIso, final CallBack callback) {
        if (TextUtils.isEmpty(name)) {
            doNotify(new Exception("name is invalid"), callback);
        } else if (TextUtils.isEmpty(phoneNumber)) {
            doNotify(new Exception("phone number is invalid"), callback);
        } else if (TextUtils.isEmpty(countryIso)) {
            doNotify(new Exception("ccc is invalid"), callback);
        } else {
            doSignup(name, phoneNumber, gcmRegId, countryIso, callback);
        }
    }

    private void doSignup(final String name,
                          final String phoneNumber,
                          final String gcmRegId,
                          final String countryIso,
                          final CallBack callback) {
        String thePhoneNumber;
        try {
            thePhoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, countryIso);
        } catch (NumberParseException e) {
            Log.e(TAG, e.getMessage());
            doNotify(new Exception(String.format(Config.getApplicationContext().getString(R.string.invalid_phone_number), phoneNumber)), callback);
            return;
        }
        final User user = new User();
        user.setUserId(thePhoneNumber);
        String password = Base64.encodeToString(user.getUserId().getBytes(), Base64.DEFAULT);
        user.setPassword(password);
        user.setName(name);
        user.setCountry(countryIso);
        user.setGcmRegId(gcmRegId);
        userApi.registerUser(user, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User backEndUser) {
                if (e == null) {
                    backEndUser.setPassword(user.getPassword());
                    saveMainUser(backEndUser);
                    doNotify(null, callback);
                } else {
                    doNotify(e, callback);
                }
            }

        });
    }

    public void verifyUser(final String token, final CallBack callBack) {
        if (isUserVerified()) {
            doNotify(null, callBack);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        if (TextUtils.isEmpty(token)) {
            doNotify(new Exception("invalid token"), callBack);
            return;
        }
        if (!isUserLoggedIn()) {
            throw new IllegalStateException(new Exception("no user logged for verification"));
        }
        userApi.verifyUser(getMainUser().getUserId(), token, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse s) {
                if (e == null) {
                    getSettings().edit().putBoolean(KEY_USER_VERIFIED, true).commit();
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);
                }
            }
        });
    }

    public void resendToken(final CallBack callBack) {
        if (!isUserLoggedIn()) {
            throw new IllegalArgumentException(new Exception("no user logged for verification"));
        }
        if (isUserVerified()) {
            doNotify(null, callBack);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        userApi.resendToken(getMainUser().getUserId(), getUserPassword(), new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse response) {
                doNotify(e, callBack);
            }
        });
    }

    private void updateUsersLocalNames() {
        WORKER.submit(new Runnable() {
            @Override
            public void run() {
                doUpdateLocalNames();
            }
        });
    }

    private void doUpdateLocalNames() {
        Context context = Config.getApplicationContext();
        Cursor cursor = ContactsManager.getInstance().findAllContactsCursor(context);
        String phoneNumber, name;
        User user;
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        try {
            while (cursor.moveToNext()) {
                phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract
                        .CommonDataKinds.Phone.NUMBER));
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.i(TAG, "strange!: no phone number for this contact, ignoring");
                    continue;
                }
                try {
                    phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, UserManager.getInstance().getUserCountryISO());
                } catch (NumberParseException e) {
                    Log.e(TAG, "failed to format to IEE number: " + e.getMessage());
                    continue;
                }
                user = realm.where(User.class)
                        .equalTo(User.FIELD_ID, phoneNumber)
                        .findFirst();

                if (user != null) {
                    name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    if (TextUtils.isEmpty(name)) { //some users can store numbers with no name; am a victim :-P
                        name = user.getName();
                    }
                    realm.beginTransaction();
                    user.setName(name);
                    realm.commitTransaction();
                }
            }
        } finally {
            realm.close();
        }
    }

//    public void generateAndSendVerificationToken(final String number) {
//        new Thread() {
//            @Override
//            public void run() {
//                SecureRandom random = new SecureRandom();
//                int num = random.nextInt() / 10000;
//                num = (num > 0) ? num : num * -1; //convert negative ints to positive ones
//                synchronized (this) {
//                    VERIFICATION_TOKEN = String.valueOf(num);
//                }
//                Log.d(TAG, VERIFICATION_TOKEN);
//                SmsManager.getDefault().sendTextMessage(number, null, VERIFICATION_TOKEN, null, null);
//            }
//        }.start();
//    }
//
//    private synchronized String getVERIFICATION_TOKEN() {
//        return VERIFICATION_TOKEN;
//    }

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
        GcmUtils.unRegister(context, new GcmUtils.UnregisterCallback() {
            @Override
            public void done(Exception e) {
                //we don't care about gcm regid
                cleanUpRealm();
                doNotify(null, logOutCallback);
            }
        });
    }

    public void syncContacts(final List<String> array) {
        if (!ConnectionUtils.isConnected()) {
            return;
        }
        userApi.syncContacts(array, new UserApiV2.Callback<List<User>>() {
            @Override
            public void done(Exception e, List<User> users) {
                if (e == null) {
                    saveFreshUsers(users);
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


    // FIXME: 6/25/2015 find a sensible place to keep this error MAIN_THREAD_HANDLER so that message dispatcher and others can share it
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
            if (ConnectionUtils.isConnectedOrConnecting()) {
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
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
        }
        userApi.leaveGroup(id, getMainUser().getUserId(), getUserPassword(), new UserApiV2.Callback<HttpResponse>() {

            @Override
            public void done(Exception e, HttpResponse response) {
                if (e == null) {
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    try {
                        User group = realm.where(User.class).equalTo("_id", id).findFirst();
                        if (group != null) {
                            realm.beginTransaction();
                            group.removeFromRealm();
                            realm.commitTransaction();
                            doNotify(null, callBack);
                        }
                    } finally {
                        realm.close();
                    }
                } else {
                    doNotify(e, callBack);
                }
            }
        });
    }

    public boolean isAdmin(String id) {
        return isAdmin(id, getMainUser().getUserId());
    }

    public String getUserCountryISO() {
        if (!isUserLoggedIn()) {
            throw new IllegalStateException("no user logged in");
        }
        return getMainUser().getCountry();
    }

    public void reset(final CallBack callBack) {
        WORKER.submit(new Runnable() {
            @Override
            public void run() {
                if (isUserVerified()) {
                    throw new RuntimeException("use logout instead");
                }
                if (!ConnectionUtils.isConnected()) {
                    doNotify(NO_CONNECTION_ERROR, callBack);
                    return;
                }
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    realm.beginTransaction();
                    realm.clear(User.class);
                    realm.commitTransaction();
                    cleanUp();
                    doNotify(null, callBack);
                } finally {
                    realm.close();
                }
            }
        });
    }

    private void doNotify(final Exception e, final CallBack callBack) {
        MAIN_THREAD_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                callBack.done(e);
            }
        });
    }

    private void doNotify(final CreateGroupCallBack callBack, final Exception e, final String id) {
        MAIN_THREAD_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                callBack.done(e, id);
            }
        });
    }

    public static String getMainUserId() {
        return getInstance().getMainUser().getUserId();
    }

    private void cleanUp() {
        getSettings().edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_USER_VERIFIED)
                .remove(KEY_USER_PASSWORD)
                .commit();
    }

    public void refreshDp(final String id, final CallBack callBack) {
        if (!ConnectionUtils.isConnected()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        WORKER.submit(new Runnable() {
            @Override
            public void run() {
                Realm realm = User.Realm(Config.getApplicationContext());
                try {
                    User user = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
                    if (user != null) {
                        File dpFile = new File(Config.getAppProfilePicsBaseDir(), user.getDP() + ".jpg");
                        if (!dpFile.exists()) try {
                            FileUtils.save(dpFile, Config.DP_ENDPOINT + "/" + user.getDP());
                            doNotify(null, callBack);
                        } catch (IOException e) {
                            doNotify(new Exception(Config.getApplicationContext().getResources().getString(R.string.error_occurred)), callBack);
                        }
                        else {
                            doNotify(null, callBack);
                        }
                    } else {
                        doNotify(new Exception("No such user!"), callBack);
                    }
                } finally {
                    realm.close();
                }
            }
        });
    }

    public interface CallBack {
        void done(Exception e);
    }

    public interface CreateGroupCallBack {
        void done(Exception e, String groupId);
    }
}
