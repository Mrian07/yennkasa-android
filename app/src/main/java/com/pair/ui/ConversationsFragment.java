package com.pair.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.pair.Config;
import com.pair.adapter.ConversationAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.pair.view.SwipeDismissListViewTouchListener;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

/**
 * @author by Null-Pointer on 5/29/2015.
 */
public class ConversationsFragment extends ListFragment implements RealmChangeListener {

    private static final String TAG = ConversationsFragment.class.getSimpleName();
    private Realm realm;
    private RealmResults<Conversation> conversations;
    private ConversationAdapter adapter;
    private static long currentTimeOut = 0L;
    private static Timer timer;

    public ConversationsFragment() {
    } //required no-arg constructor

    @Override
    public void onAttach(Activity activity) {
        setHasOptionsMenu(true);
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);
        cleanUp();
        realm = Realm.getInstance(Config.getApplicationContext());
        conversations = realm.allObjectsSorted(Conversation.class, Conversation.FIELD_LAST_ACTIVE_TIME, false);
        adapter = new ConversationAdapter(getActivity(), conversations, true);
        setListAdapter(adapter);
        return view;
    }

    private void cleanUp() {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                Realm realm = Realm.getInstance(getActivity());
//                realm.beginTransaction();
//                RealmResults<Conversation> conversations = realm.allObjectsSorted(Conversation.class, Conversation.FIELD_LAST_ACTIVE_TIME, false);
//                for (int i = 0; i < conversations.size(); i++) {
//                    Conversation conversation = conversations.get(i);
//                    if (conversation.getLastMessage() == null) {
//                        conversation.removeFromRealm();
//                    }
//                }
//                realm.commitTransaction();
//                realm.close();
//            }
//        }).start();
    }

    private void startTimer() {
        if (timer == null) {
            Log.i(TAG, "starting timer");
            timer = newTimer(null);
            currentTimeOut = AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15;
            timer.scheduleAtFixedRate(task, 0L, currentTimeOut);
        }
    }

    private void scheduleTimer(long interval) {
        try {
            timer.purge();
            timer = newTimer("uiRefresher");
            timer.scheduleAtFixedRate(task, 0L, interval);
            currentTimeOut = interval;
        } catch (Exception ignored) { //timer is already scheduled!

        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        SwipeDismissListViewTouchListener swipeDismissListViewTouchListener = new SwipeDismissListViewTouchListener(getListView(), new SwipeDismissListViewTouchListener.OnDismissCallback() {
            @Override
            public void onDismiss(ListView listView, final int[] reverseSortedPositions) {
                UiHelpers.showErrorDialog(getActivity(), R.string.sure_you_want_to_delete_conversation, R.string.yes, R.string.no, new UiHelpers.Listener() {
                    @Override
                    public void onClick() {
                        deleteConversation(reverseSortedPositions);
                    }
                }, null);
            }
        });
        getListView().setOnTouchListener(swipeDismissListViewTouchListener);
        getListView().setOnScrollListener(swipeDismissListViewTouchListener.makeScrollListener());
    }

    private void deleteConversation(int[] reverseSortedPositions) {
        realm.beginTransaction();
        for (int position : reverseSortedPositions) {
            try {
                Conversation conversation = conversations.get(position);
                final String peerId = conversation.getPeerId();
                realm.where(Message.class).equalTo(Message.FIELD_FROM, peerId)
                        .or()
                        .equalTo(Message.FIELD_TO, peerId)
                        .findAll().clear();
                conversation.removeFromRealm();
            } catch (Exception e) {
                realm.cancelTransaction();
                Log.e(TAG, e.getMessage(), e.getCause());
            }
        }
        realm.commitTransaction();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String peerId = ((Conversation) l.getAdapter().getItem(position)).getPeerId();
        UiHelpers.enterChatRoom(getActivity(), peerId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.new_message_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.new_message) {
            UiHelpers.pickRecipient(getActivity());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onResume() {
        realm.addChangeListener(this);
        startTimer();
        super.onResume();
    }

    @Override
    public void onPause() {
        task.cancel();
        realm.removeChangeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            getActivity().runOnUiThread(doRefreshDisplay);
        }
    };

    @Override
    public void onChange() {
        doRefreshDisplay.run();
    }

    private Runnable doRefreshDisplay = new Runnable() {
        @Override
        public void run() {
            refreshDisplay();
            setUpTimerIfPossible();
        }
    };

    private void setUpTimerIfPossible() {
        Date then = (realm.where(Conversation.class).maximumDate(Conversation.FIELD_LAST_ACTIVE_TIME));
        if (then != null) {
            long elapsed = new Date().getTime() - then.getTime();
            if (elapsed < AlarmManager.INTERVAL_HOUR) {
                if (currentTimeOut != AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15 /*one minute*/) {
                    //reset timer.
                    Log.i(TAG, "rescheduling time to one minute");
                    scheduleTimer(AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15);
                }
            } else if (elapsed < AlarmManager.INTERVAL_DAY) {
                //reschedule timer
                if (currentTimeOut != AlarmManager.INTERVAL_HOUR) {
                    Log.i(TAG, "rescheduling time to one hour");
                    scheduleTimer(AlarmManager.INTERVAL_HOUR);
                }
            } else {//one day or more interval is too long
                Log.i(TAG, "canceling timer");
                if (timer != null) timer.cancel();
            }
        }
    }

    private void refreshDisplay() {
        adapter.notifyDataSetChanged();
        Log.i(TAG, "refreshing");
    }

    private Timer newTimer(String id) {
        id = (id == null) ? "defaultName" : id;
        return new Timer(id, true);
    }
}
