package com.pair.data;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.pair.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * @author Null-Pointer on 5/26/2015.
 */
@SuppressWarnings("unused")
@RealmClass
public class Message extends RealmObject {

    public static final int STATE_PENDING = 0x3e9,
            STATE_SENT = 0x3ea,
            STATE_RECEIVED = 0x3eb,
            STATE_SEEN = 0x3ec,
            STATE_SEND_FAILED = 0x3ed;

    public static final int TYPE_TEXT_MESSAGE = 0x3ee,
            TYPE_BIN_MESSAGE = 0x3ef,
            TYPE_PICTURE_MESSAGE = 0x3f0,
            TYPE_VIDEO_MESSAGE = 0x3f1,
            TYPE_DATE_MESSAGE = 0x3f2,
            TYPE_TYPING_MESSAGE = 0x3f3;
    private static final String TAG = Message.class.getSimpleName();

    public static final String FIELD_ID = "id",
            FIELD_FROM = "from",
            FIELD_TO = "to",
            FIELD_TYPE = "type",
            FIELD_STATE = "state",
            FIELD_DATE_COMPOSED = "dateComposed",
            FIELD_MESSAGE_BODY = "messageBody";
    @PrimaryKey
    private String id;

    private String from; //sender's id
    private String to; //recipient's id
    private String messageBody;
    private Date dateComposed;
    private int state;
    private int type;

    public Message() {
    } //required no-arg c'tor;

    @Deprecated
    public Message(Message message) {
        this.from = message.getFrom();
        this.to = message.getTo();
        this.id = message.getId();
        this.type = message.getType();
        this.dateComposed = message.getDateComposed();
        this.messageBody = message.getMessageBody();
        this.state = message.getState();
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public Date getDateComposed() {
        return dateComposed;
    }

    public void setDateComposed(Date dateComposed) {
        this.dateComposed = dateComposed;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    private final static Object idLock = new Object();

    public static String generateIdPossiblyUnique() {

        Application appContext = Config.getApplication();
        Realm realm = Realm.getInstance(appContext);
        long count = realm.where(Message.class).count() + 1;
        String id;
        synchronized (idLock) {
            id = count + "@" + UserManager.getInstance().getCurrentUser().getUserId() + "@" + System.nanoTime();
        }
        Log.i(TAG, "generated message id: " + id);
        realm.close();
        return id;
    }

    public static Realm REALM(Context context) {
        return Realm.getInstance(context);
    }

    public static boolean isTextMessage(Message message) {
        return message.getType() == TYPE_TEXT_MESSAGE;
    }

    public static boolean isBinMessage(Message message) {
        return message.getType() == TYPE_BIN_MESSAGE;
    }

    public static boolean isPictureMessage(Message message) {
        return message.getType() == TYPE_PICTURE_MESSAGE;
    }

    public static boolean isVideoMessage(Message message) {
        return message.getType() == TYPE_VIDEO_MESSAGE;
    }

    public static boolean isDateMessage(Message message) {
        return message.getType() == TYPE_DATE_MESSAGE;
    }

    public static boolean isTypingMessage(Message message) {
        return message.getType() == TYPE_TYPING_MESSAGE;
    }

    public static boolean isIncoming(Message message) {
        return !isOutGoing(message);
    }

    public static boolean isOutGoing(Message message) {
        return UserManager.getInstance().isCurrentUser(message.getFrom());
    }

    public static String state(Context context, int status) {
        switch (status) {
            case Message.STATE_PENDING:
                return context.getString(com.pair.pairapp.R.string.st_message_state_pending);
            case Message.STATE_SEND_FAILED:
                return context.getString(com.pair.pairapp.R.string.st_message_state_failed);
            case Message.STATE_RECEIVED:
                return context.getString(com.pair.pairapp.R.string.st_message_state_delivered);
            case Message.STATE_SEEN:
                return context.getString(com.pair.pairapp.R.string.st_message_state_seen);
            case Message.STATE_SENT:
                return context.getString(com.pair.pairapp.R.string.st_message_state_sent);
            default:
                throw new AssertionError("new on unknown message status");
        }
    }

    public static Message copy(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message is null");
        }

        Message clone = new Message();

        clone.setId(message.getId());
        clone.setFrom(message.getFrom());
        clone.setTo(message.getTo());
        clone.setDateComposed(message.getDateComposed());
        clone.setType(message.getType());
        clone.setMessageBody(message.getMessageBody());
        clone.setState(message.getState());
        return clone;
    }

    public static List<Message> copy(Collection<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Message> copy = new ArrayList<>(messages.size());
        for (Message message : messages) {
            copy.add(copy(message));
        }
        return copy;
    }

    public static String typeToString(Context context, int type) {
        switch (type) {
            case Message.TYPE_PICTURE_MESSAGE:
                return context.getString(com.pair.pairapp.R.string.picture);
            case Message.TYPE_VIDEO_MESSAGE:
                return context.getString(com.pair.pairapp.R.string.video);
            case Message.TYPE_BIN_MESSAGE:
                return context.getString(com.pair.pairapp.R.string.file);
            case Message.TYPE_TEXT_MESSAGE:
                return context.getString(com.pair.pairapp.R.string.message);
            default:
                throw new AssertionError("Unknown message type");
        }
    }
}
