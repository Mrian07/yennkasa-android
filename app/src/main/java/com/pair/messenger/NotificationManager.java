package com.pair.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import com.pair.data.Message;
import com.pair.util.Config;

/**
 * @author Null-Pointer on 6/14/2015.
 */
public class NotificationManager {
    private final static int MESSAGE_NOTIFICATION_ID = 1001;
    private final static int MESSAGE_PENDING_INTENT_REQUEST_CODE = 1002;
    public static final NotificationManager INSTANCE = new NotificationManager();

    public void onNewMessage(Message message, Intent action) {
        if (Config.isChatRoomOpen()) {
            // TODO: 6/14/2015 give title and description of notification based on type of message

            //TODO use a croutin style notification, for now we show a toast
            Toast.makeText(Config.getApplicationContext(), message.getFrom() + " : " + message.getMessageBody(), Toast.LENGTH_LONG).show();
        } else {
            PendingIntent pendingIntent = PendingIntent.getActivity(Config.getApplicationContext(),
                    MESSAGE_PENDING_INTENT_REQUEST_CODE,
                    action,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            STATUS_BAR_NOTIFIER.notifyUser("New message from " + message.getFrom(),
                    message.getMessageBody(),
                    message.getMessageBody(),
                    pendingIntent);
        }
    }

    public interface Notifier {
        void notifyUser(String title, String tickerText, String message, PendingIntent intent);
    }

    private final Notifier STATUS_BAR_NOTIFIER = new Notifier() {
        @Override
        public void notifyUser(String title, String tickerText, String message, PendingIntent intent) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(Config.getApplication());
            builder.setTicker(tickerText)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setLights(android.R.color.holo_green_dark, 1500, 3000)
                    .setContentIntent(intent);
            Notification notification = builder.build();
            android.app.NotificationManager notMgr = ((android.app.NotificationManager) Config.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE));
            notMgr.notify(MESSAGE_NOTIFICATION_ID, notification);
            playBeep();
        }
    };

    private void playBeep() {
        // TODO: 6/14/2015 fetch url from preferences
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone ringtone = RingtoneManager.getRingtone(Config.getApplicationContext(), uri);
        ringtone.play();
    }

}
