package com.pair.messenger;

import android.content.Context;

import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.TaskManager;
import com.pair.util.ThreadUtils;

import java.lang.ref.WeakReference;

import io.realm.Realm;


/**
 * @author Null-Pointer on 6/14/2015.
 */
final class NotificationManager {
    static final NotificationManager INSTANCE = new NotificationManager();
    private static final String TAG = NotificationManager.class.getSimpleName();
    private final Notifier BACKGROUND_NOTIFIER = new StatusBarNotifier();
    private volatile WeakReference<Notifier> UI_NOTIFIER;

    static CharSequence messageTypeToString(int type) {
        switch (type) {
            case Message.TYPE_PICTURE_MESSAGE:
                return Config.getApplicationContext().getString(R.string.picture);
            case Message.TYPE_VIDEO_MESSAGE:
                return Config.getApplicationContext().getString(R.string.video);
            case Message.TYPE_BIN_MESSAGE:
                return Config.getApplicationContext().getString(R.string.file);
            default:
                if (com.pair.pairapp.BuildConfig.DEBUG) {
                    throw new AssertionError("Unknown message type");
                }
                return "";
        }
    }

    void onNewMessage(final Context context, final Message message) {
        if (ThreadUtils.isMainThread()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    notifyUser(context, message, retrieveSendersName(message));
                }
            });
        } else {
            notifyUser(context, message, retrieveSendersName(message));
        }
    }

    private void notifyUser(final Context context, final Message message, final String sendersName) {
        if (Config.isAppOpen() && UserManager.getInstance().getBoolPref(UserManager.IN_APP_NOTIFICATIONS, false)) {
            //Toast.makeText(Config.getApplicationContext(), message.getFrom() + " : " + message.getMessageBody(), Toast.LENGTH_LONG).show();
            if (UI_NOTIFIER != null) {
                final Notifier notifier = UI_NOTIFIER.get();
                if (notifier != null) {
                    TaskManager.executeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            notifier.notifyUser(context, message, sendersName);
                        }
                    });
                    return;
                }
            }
            BACKGROUND_NOTIFIER.notifyUser(context, message, sendersName);
        }
    }

    private String retrieveSendersName(Message message) {
        ThreadUtils.ensureNotMain();
        final Realm realm = Realm.getInstance(Config.getApplicationContext());
        String sendersName;
        try {
            User user = realm.
                    where(User.class).equalTo(User.FIELD_ID, message.getFrom())
                    .findFirst();

            if (user == null) {
                user = UserManager.getInstance().fetchUserIfRequired(
                        Message.isGroupMessage(message)
                                ? message.getTo()
                                : message.getFrom());
                sendersName = user.getName();
            } else {
                sendersName = user.getName();
            }
        } finally {
            realm.close();
        }
        return sendersName;
    }

    synchronized void registerUI_Notifier(Notifier notifier) {
        if (notifier == null) throw new IllegalArgumentException("notifier is null");
        if (UI_NOTIFIER != null && UI_NOTIFIER.get() == notifier) {
            return;
        }
        UI_NOTIFIER = new WeakReference<>(notifier);
    }

    synchronized void unRegisterUI_Notifier(Notifier notifier) {
        if (UI_NOTIFIER != null && UI_NOTIFIER.get() == notifier) {
            UI_NOTIFIER.clear();
            UI_NOTIFIER = null;
        }
    }

}
