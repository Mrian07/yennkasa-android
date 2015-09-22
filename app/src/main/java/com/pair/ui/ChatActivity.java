package com.pair.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.pair.Errors.ErrorCenter;
import com.pair.Errors.PairappException;
import com.pair.adapter.MessagesAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.FileUtils;
import com.pair.util.LiveCenter;
import com.pair.util.SimpleDateUtil;
import com.pair.util.UiHelpers;
import com.pair.util.ViewUtils;
import com.pair.view.SwipeDismissListViewTouchListener;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.FloatingActionButton;
import com.rey.material.widget.SnackBar;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

import static com.pair.data.Message.TYPE_DATE_MESSAGE;
import static com.pair.data.Message.TYPE_TEXT_MESSAGE;


@SuppressWarnings({"ConstantConditions", "FieldCanBeLocal"})
public class ChatActivity extends MessageActivity implements View.OnClickListener,
        AbsListView.OnScrollListener, TextWatcher, RealmChangeListener, LiveCenter.TypingListener {
    private static final String TAG = ChatActivity.class.getSimpleName();
    public static final String EXTRA_PEER_ID = "peer id";
    private static final int ADD_USERS_REQUEST = 0x5;

    private RealmResults<Message> messages;
    private User peer;
    private Conversation currConversation;
    private Realm realm;
    private ListView messagesListView;
    private EditText messageEt;
    private View sendButton, dateHeaderViewParent;
    private TextView dateHeader;
    private MessagesAdapter adapter;
    private static Message selectedMessage;

    private Toolbar toolBar;
    private ToolbarManager toolbarManager;
    private AbsListView.OnScrollListener swipeScrollListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolBar.setOnClickListener(this);
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        realm = Realm.getInstance(this);
        messageEt = ((EditText) findViewById(R.id.et_message));
        sendButton = findViewById(R.id.iv_send);
        ((FloatingActionButton) sendButton).setIcon(getResources().getDrawable(R.drawable.ic_action_send_now), false);
        messagesListView = ((ListView) findViewById(R.id.lv_messages));
        dateHeader = ((TextView) findViewById(R.id.tv_header_date));
        dateHeaderViewParent = findViewById(R.id.cv_date_header_parent);
        Bundle bundle = getIntent().getExtras();
        String peerId = bundle.getString(EXTRA_PEER_ID);
        peer = realm.where(User.class).equalTo(User.FIELD_ID, peerId).findFirst();
        if (peer == null) {
            realm.beginTransaction();
            peer = realm.createObject(User.class);
            peer.setUserId(peerId);
            String[] parts = peerId.split("@"); //in case the peer is a group
            peer.setType(parts.length > 1 ? User.TYPE_GROUP : User.TYPE_NORMAL_USER);
            peer.setHasCall(false); //we cannot tell for now
            peer.setDP(peerId);
            peer.setName(parts[0]);
            realm.commitTransaction();
            UserManager.getInstance().refreshUserDetails(peerId); //async
        }
        String peerName = peer.getName();
        //noinspection ConstantConditions
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(peerName);
        actionBar.setDisplayHomeAsUpEnabled(true);
        messages = realm.where(Message.class).equalTo(Message.FIELD_FROM, peer.getUserId())
                .or()
                .equalTo(Message.FIELD_TO, peer.getUserId())
                .findAllSorted(Message.FIELD_DATE_COMPOSED, true, Message.FIELD_TYPE, false);
        setUpCurrentConversation();
        sendButton.setOnClickListener(this);
        messageEt.addTextChangedListener(this);
        setUpListView();
        // TODO: 8/22/2015 in future we will move to the last  un seen message if any
    }

    private void setUpListView() {
        adapter = new MessagesAdapter(this, messages, true);
        messagesListView.setAdapter(adapter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            SwipeDismissListViewTouchListener touchListener = new SwipeDismissListViewTouchListener(messagesListView, new SwipeDismissListViewTouchListener.OnDismissCallback() {
                @Override
                public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                    deleteMessage(reverseSortedPositions[0]);
                }
            });
            messagesListView.setOnTouchListener(touchListener);
            swipeScrollListener = touchListener.makeScrollListener();
        }

        messagesListView.setOnScrollListener(this);
        registerForContextMenu(messagesListView);
        messagesListView.setSelection(messages.size()); //move to last
    }

    private void setUpCurrentConversation() {
        String peerId = peer.getUserId();
        currConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        // FIXME: 8/4/2015 move this to a background thread
        if (currConversation == null) { //first time
            currConversation = Conversation.newConversationWithoutSession(realm, peerId, true);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        toolbarManager.onPrepareMenu();
        menu = toolBar.getMenu();
        if (menu != null && menu.size() > 0) { //required for toolbar to behave on older platforms <=10
            User mainUser = UserManager.getInstance().getCurrentUser();
            menu.findItem(R.id.action_invite_friends)
                    .setVisible(peer.getType() == User.TYPE_GROUP && peer.getAdmin().getUserId().equals(mainUser.getUserId()));
            menu.findItem(R.id.action_view_profile).setTitle((peer.getType() == User.TYPE_GROUP) ? R.string.st_group_info : R.string.st_view_profile);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        toolbarManager.createMenu(R.menu.chat_menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_invite_friends) {
            Intent intent = new Intent(this, InviteActivity.class);
            intent.putExtra(InviteActivity.EXTRA_GROUP_ID, peer.getUserId());
            startActivityForResult(intent, ADD_USERS_REQUEST);
            return true;
        } else if (id == R.id.action_view_profile) {
            UiHelpers.gotoProfileActivity(this, peer.getUserId());
            return true;
        } else if (id == R.id.action_attach) {
            UiHelpers.attach(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        Config.appOpen(true);
        clearRecentChat();
        if (!User.isGroup(peer)) {
            updateUserStatus(LiveCenter.isOnline(peer.getUserId()));
            LiveCenter.trackUser(peer.getUserId());
            LiveCenter.notifyInChatRoom(peer.getUserId());
            LiveCenter.registerTypingListener(this);
        } else {
            getSupportActionBar().setSubtitle(R.string.group);
        }
    }

    @Override
    protected void onPause() {
        if (currConversation != null) {
            realm.beginTransaction();
            currConversation.setActive(false);
            realm.commitTransaction();
        }
        Config.appOpen(false);
        if (!UserManager.getInstance().isGroup(peer.getUserId())) {
            LiveCenter.notifyNotTyping(peer.getUserId());
            LiveCenter.notifyLeftChatRoom(peer.getUserId());
            LiveCenter.doNotTrackUser(peer.getUserId());
            LiveCenter.unRegisterTypingListener(this);
        }
        super.onPause();
    }

    @Override
    protected SnackBar getSnackBar() {
        return (SnackBar) findViewById(R.id.notification_bar);
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.iv_send:
                sendTextMessage();
                break;
            case R.id.main_toolbar:
                UiHelpers.gotoProfileActivity(this, peer.getUserId());
                break;
            default:
                throw new AssertionError();
        }
    }

    private void sendTextMessage() {
        String content = messageEt.getText().toString().trim();
        messageEt.setText(""); //clear the text field
        //TODO use a regular expression to validate the message body
        if (!TextUtils.isEmpty(content)) {
            super.sendMessage(content, peer.getUserId(), Message.TYPE_TEXT_MESSAGE, true);
            messagesListView.setSelection(messages.size());
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        try {
            Pair<String, Integer> pathAndType = UiHelpers.completeAttachIntent(requestCode, data);
            sendMessage(pathAndType.first, peer.getUserId(), pathAndType.second);
            messagesListView.setSelection(messages.size());
        } catch (PairappException e) {
            ErrorCenter.reportError(TAG, e.getMessage());
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }

    @Override
    public void onScroll(AbsListView view, final int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (swipeScrollListener != null) {
            swipeScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
        if (firstVisibleItem == 0) { //first/second item
            dateHeaderViewParent.setVisibility(View.GONE);// TODO: 8/7/2015 fade instead of hiding right away
            return;
        }
        if (visibleItemCount != 0 && visibleItemCount < totalItemCount) {
            dateHeaderViewParent.setVisibility(View.VISIBLE);
            for (int i = firstVisibleItem; i >= 0; i--) { //loop backwards
                final Message message = messages.get(i);
                if (message.getType() == TYPE_DATE_MESSAGE) {
                    dateHeader.setText(SimpleDateUtil.formatDateRage(this, message.getDateComposed()));
                    return;
                }
            }
            //if we've got here then somehow a  session was not set up correctly.
            // do we have to clean that mess or
            //do this: throw new IllegalStateException("impossible");
        }
    }
    private static int cursor = -1; //static so that it can resist activity restarts.

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        User user = realm.where(User.class).beginGroup().notEqualTo(User.FIELD_ID, UserManager.getMainUserId()).notEqualTo(User.FIELD_ID, peer.getUserId()).endGroup().findFirst();
        boolean can4ward = user != null; //no other user apart from peer. cannot forward so lets hide it


        AdapterView.AdapterContextMenuInfo info = ((AdapterView.AdapterContextMenuInfo) menuInfo);
        selectedMessage = messages.get(info.position);
        cursor = info.position;
        if (cursor <= 0)
            return; //thanks to realm long is not precise if a message managed to be first before any date lets return
        if (selectedMessage.getType() != Message.TYPE_DATE_MESSAGE && selectedMessage.getType() != Message.TYPE_TYPING_MESSAGE) {
            getMenuInflater().inflate(R.menu.message_context_menu, menu);
            menu.findItem(R.id.action_copy).setVisible(selectedMessage.getType() == TYPE_TEXT_MESSAGE);
            menu.findItem(R.id.action_forward).setVisible(can4ward);
            if (selectedMessage.getType() != TYPE_TEXT_MESSAGE) {
                menu.findItem(R.id.action_forward).setVisible(new File(selectedMessage.getMessageBody()).exists());
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_copy) {
            ClipboardManager manager = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE));
            manager.setText(selectedMessage.getMessageBody());
            return true;
        } else if (itemId == R.id.action_delete) {
            deleteMessage(cursor);
            return true;
        } else if (itemId == R.id.action_forward) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setComponent(new ComponentName(this, CreateMessageActivity.class));
            intent.putExtra(MainActivity.ARG_TITLE, getString(R.string.forward_to));
            intent.putExtra(CreateMessageActivity.EXTRA_FORWARDED_FROM, peer.getUserId());
            if (Message.isTextMessage(selectedMessage)) {
                intent.putExtra(Intent.EXTRA_TEXT, selectedMessage.getMessageBody());
                intent.setType("text/*");
                startActivity(intent);
            } else {
                final File file = new File(selectedMessage.getMessageBody());
                if (file.exists()) {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                    intent.setType(FileUtils.getMimeType(file.getAbsolutePath()));
                    startActivity(intent);
                } else {
                    ErrorCenter.reportError(TAG, getString(R.string.file_not_found));
                }
            }
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void deleteMessage(int position) {
        realm.beginTransaction(); //beginning transaction earlier to force realm to prevent other realms from changing the data set
        //hook up message to remove.
        //if it is the only message for the day remove the date message
        //if it is the last message
        //if there are other messages set the newest to the just removed message as the last message of the conversation
        Message currMessage = messages.get(position),
                previousToCurrMessage = messages.get(position - 1), //at least there will be a date message
                nextToCurrMessage = (messages.size() - 1 > position ? messages.get(position + 1) : null);

        final boolean wasLastForTheDay = nextToCurrMessage == null || Message.isDateMessage(nextToCurrMessage);
        currMessage.removeFromRealm();
        if (Message.isDateMessage(previousToCurrMessage) &&
                wasLastForTheDay) {
            previousToCurrMessage.removeFromRealm(); //this will be a date message
        }
        if (currConversation.getLastMessage() == null) {
            int allMessages = messages.size() - 1;
            for (int i = allMessages; i > 0/*0th is the date*/; i--) {
                final Message cursor = messages.get(i);
                if (!Message.isDateMessage(cursor) && !Message.isTypingMessage(cursor)) {
                    currConversation.setLastMessage(cursor);
                    break;
                }
            }
        }
        realm.commitTransaction();
        messagesListView.requestLayout();
        adapter.notifyDataSetChanged();
    }

    /**
     * code purposely for testing we will take this off in production
     */
    @SuppressWarnings("unused")
    private void testChatActivity() {
//        final String senderId = peer.getUserId();
//        timer = new Timer(true);
//        TimerTask task = new TimerTask() {
//            @Override
//            public void run() {
//                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST);
//                testMessageProcessor(RealmUtils.seedIncomingMessages(senderId, getCurrentUser().getUserId()));
//            }
//        };
//        timer.scheduleAtFixedRate(task, 1000, 45000);
    }

//    Timer timer;
//
//    private void testMessageProcessor(Message messages) {
//        JsonObject object = MessageJsonAdapter.INSTANCE.toJson(messages);
//        Context context = Config.getApplicationContext();
//        Bundle bundle = new Bundle();
//        bundle.putString("message", object.toString());
//        Intent intent = new Intent(context, MessageProcessor.class);
//        intent.putExtras(bundle);
//        context.startService(intent);
//    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    Handler handler = new Handler();
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            wasTyping = false;
            LiveCenter.notifyNotTyping(peer.getUserId());
        }
    };
    boolean wasTyping = false;

    @Override
    public void afterTextChanged(Editable s) {
        handler.removeCallbacks(runnable);
        if (!s.toString().trim().isEmpty()) {
            ViewUtils.showViews(sendButton);
            if (!wasTyping) {
                wasTyping = true;
                LiveCenter.notifyTyping(peer.getUserId());
            }
            //TODO add some deviation to the timeout
            handler.postDelayed(runnable, 10000);
        } else if (wasTyping) {
            wasTyping = false;
            LiveCenter.notifyNotTyping(peer.getUserId());
            ViewUtils.hideViews(sendButton);
        }
    }

    @Override
    public void notifyUser(Context context, final Message message, String sender) {
        if (sender.equals(peer.getName())) {
            // TODO: 8/17/2015 give user a tiny hint of new messages and allow fast scroll
        } else {
            super.notifyUser(this, message, sender);
        }
    }

    @Override
    public void onTyping(String userId) {
        if (peer.getUserId().equals(userId)) {
            getSupportActionBar().setSubtitle("Typing...");
        }
    }

    @Override
    public void onStopTyping(String userId) {
        if (peer.getUserId().equals(userId)) {
            updateUserStatus(LiveCenter.isOnline(userId));
        }
    }

    @Override
    public void onUserStatusChanged(String userId, boolean isOnline) {
        if (userId.equals(peer.getUserId())) {
            updateUserStatus(isOnline);
        }
    }

    private void updateUserStatus(boolean online) {
        getSupportActionBar().setSubtitle(online ? R.string.st_online : R.string.st_offline);
    }
}