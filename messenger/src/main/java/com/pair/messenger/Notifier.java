package com.pair.messenger;

import android.content.Context;

import com.pair.data.Message;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public interface Notifier {
    enum location {
        BACKGROUND,
        FORE_GROUND
    }

    void notifyUser(Context context, Message message, String sender);

    location where();
}