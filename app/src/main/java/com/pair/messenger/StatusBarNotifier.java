package com.pair.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.app.NotificationCompat;

import com.pair.data.Message;
import com.pair.pairapp.R;
import com.pair.ui.ChatActivity;
import com.pair.util.Config;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public class StatusBarNotifier implements Notifier {
    private final static int MESSAGE_NOTIFICATION_ID = 1001;
    private final static int MESSAGE_PENDING_INTENT_REQUEST_CODE = 1002;
    private AtomicBoolean shouldPlayTone = new AtomicBoolean(true);
    Timer timer = new Timer();

    @Override
    public void notifyUser(Context context, Message message, String sender) {
        Intent action = new Intent(context, ChatActivity.class);
        action.putExtra(ChatActivity.EXTRA_PEER_ID, message.getFrom());

        PendingIntent pendingIntent = PendingIntent.getActivity(Config.getApplicationContext(),
                MESSAGE_PENDING_INTENT_REQUEST_CODE,
                action,
                PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(Config.getApplication());
        builder.setTicker(context.getString(R.string.new_message))
                .setContentTitle(String.format(context.getString(R.string.message_from), sender));

        if (Message.isTextMessage(message)) {
            builder.setContentText(message.getMessageBody());
        } else {
            builder.setContentText(NotificationManager.messageTypeToString(message.getType()));
        }

        builder.setSmallIcon(android.R.drawable.stat_notify_chat)
                .setLights(Color.GREEN, 1500, 3000)
                .setContentIntent(pendingIntent);
        Notification notification = builder.build();
        doNotify(context, notification);
    }

    private void doNotify(Context context, Notification notification) {
        android.app.NotificationManager notMgr = ((android.app.NotificationManager) Config.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE));
        notMgr.notify(MESSAGE_NOTIFICATION_ID, notification);
        if (shouldPlayTone.getAndSet(false)) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!shouldPlayTone.get())
                        shouldPlayTone.set(true);
                }
            }, 15000);
            NotificationManager.playTone(context);
        }

    }

    @Override
    public location where() {
        return location.BACKGROUND;
    }
}
