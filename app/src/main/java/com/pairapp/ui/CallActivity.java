package com.pairapp.ui;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.pairapp.BuildConfig;
import com.pairapp.R;
import com.pairapp.call.CallData;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.messenger.MessengerBus;
import com.pairapp.util.Event;
import com.pairapp.util.PLog;
import com.pairapp.util.ViewUtils;
import com.rey.material.widget.SnackBar;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static com.pairapp.messenger.MessengerBus.ANSWER_CALL;

public class CallActivity extends PairAppActivity {

    public static final String EXTRA_CALL_DATA = "callData";
    public static final int REQUEST_CODE = 1001;

    @SuppressWarnings("NullableProblems") //will always be initialised in onCreate.
    @NonNull
    private CallData callData;

    @Bind(R.id.iv_user_avatar)
    ImageView imageView;

    @Bind(R.id.tv_user_name)
    TextView tvUserName;

    @Bind(R.id.tv_call_state)
    TextView tvCallState;

    @Bind(R.id.bt_decline_call)
    Button declineCall;

    @Bind(R.id.bt_end_call)
    Button endCall;

    @Bind(R.id.bt_answer_call)
    Button answerCall;

    @Bind(R.id.bt_speaker)
    Button enableSpeaker;

    @Bind(R.id.bt_mute)
    Button mute;

    @SuppressWarnings("NullableProblems") //will always be initialised in onCreate.
    @NonNull
    private User peer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD |
                FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_call);
        ButterKnife.bind(this);
        handleIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); //getIntent will be the new intent see super implementation
        handleIntent();
    }

    void handleIntent() {
        callData = getIntent().getParcelableExtra(EXTRA_CALL_DATA);
        //noinspection ConstantConditions
        assert callData != null;
        String peerId = callData.getPeer();
        peer = UserManager.getInstance().fetchUserIfRequired(peerId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationManagerCompat.from(this)
                .cancel(peer.getUserId(), MessengerBus.CALL_NOTIFICATION_ID);
        registerForEvent(MessengerBus.ON_CALL_EVENT);
        populateUserData();
        refreshDisplay();
    }

    private void refreshDisplay() {
        switch (callData.getCallState()) {
            case CallData.INITIATING:
                if (callData.isOutGoing()) {
                    tvCallState.setText(R.string.dialing);
                    ViewUtils.hideViews(declineCall, answerCall);
                    ViewUtils.showViews(endCall, mute, enableSpeaker);
                } else {
                    tvCallState.setText(getString(R.string.incoming_call, peer.getName()));
                    ViewUtils.showViews(declineCall, answerCall);
                    ViewUtils.hideViews(endCall, mute, enableSpeaker);
                }
                break;
            case CallData.PROGRESSING:
            case CallData.TRANSFERRING:
                tvCallState.setText(R.string.connecting);
                if (callData.isOutGoing()) {
                    ViewUtils.hideViews(declineCall, answerCall);
                    ViewUtils.showViews(endCall, mute, enableSpeaker);
                } else {
                    ViewUtils.showViews(declineCall, answerCall);
                    ViewUtils.hideViews(endCall, mute, enableSpeaker);
                }
                break;
            case CallData.ESTABLISHED:
                startTimer();
                ViewUtils.hideViews(declineCall, answerCall);
                ViewUtils.showViews(endCall, mute, enableSpeaker);
                break;
            case CallData.ENDED:
                tvCallState.setText(R.string.call_ended);
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        finish();
                    }
                }, 2000);
                ViewUtils.hideViews(endCall, declineCall, answerCall, mute, enableSpeaker);
                break;
            default:
                throw new AssertionError();
        }

        mute.setSelected(callData.isMuted());
        enableSpeaker.setSelected(callData.isLoudSpeaker());
    }


    @NonNull
    private final Subscriber<Long> subscriber = new Subscriber<Long>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            PLog.e(TAG, e.getMessage());
        }

        @Override
        public void onNext(Long o) {
            if (callData.getCallState() == CallData.ESTABLISHED) {
                long duration = System.currentTimeMillis() - callData.getEstablishedTime();
                tvCallState.setText(formatTimespan(duration));
            }
        }
    };

    private void startTimer() {
        Observable.interval(0, 1000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);
    }

    private String formatTimespan(long timespan) {
        long totalSeconds = timespan / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unRegister(MessengerBus.ON_CALL_EVENT);
        if (!subscriber.isUnsubscribed()) {
            subscriber.unsubscribe();
        }
        if (callData.getCallState() != CallData.ENDED) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.putExtra(EXTRA_CALL_DATA, callData);
            String contentText = getString(R.string.ongoing_call_notice, peer.getName());
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_icon)
                    .setTicker(contentText)
                    .setContentTitle(getString(R.string.pairapp_call))
                    .setContentText(contentText)
                    .setOngoing(true)
                    .setContentIntent(PendingIntent.getActivity(this,
                            REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT));
            NotificationManagerCompat.from(this)
                    .notify(peer.getUserId(), MessengerBus.CALL_NOTIFICATION_ID, builder.build());
        }
    }

    private void populateUserData() {
        tvUserName.setText(peer.getName());
        Resources resources = getResources();
        ImageLoader.load(this, peer.getDP())
                .error(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .placeholder(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .resize((int) resources.getDimension(R.dimen.thumbnail_width), (int) resources.getDimension(R.dimen.thumbnail_height))
                .onlyScaleDown().into(imageView);
    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return ButterKnife.findById(this, R.id.notification_bar);
    }

    @Override
    protected void handleEvent(Event event) {
        Object tag = event.getTag();
        if (tag.equals(MessengerBus.ON_CALL_EVENT)) {
            //noinspection ThrowableResultOfMethodCallIgnored
            Exception error = event.getError();
            if (error != null) {
                new AlertDialog.Builder(this)
                        .setMessage(error.getMessage())
                        .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }).setCancelable(false)
                        .create().show();

            } else {
                CallData data = ((CallData) event.getData());
                assert data != null;
                //since call events are sticky, it's possible that a user leave the
                //call screen while a call is ongoing. if the call ends and the user  never
                //comes back to the call screen, an old event might get delivered to us so lets screen them out
                if (data.getEstablishedTime() < callData.getEstablishedTime()) { //an  old call event, not using
                    PLog.d(TAG, "can't replace an old callData with a newer callData");
                } else {
                    callData = data;
                    if (callData.getPeer().equals(peer.getUserId())) {
                        refreshDisplay();
                    } else {
                        if (BuildConfig.DEBUG) {
                            throw new IllegalStateException();
                        }
                        PLog.w(TAG, "unknown call from user with id %s", callData.getPeer());
                    }
                }
            }
        }
    }


    @OnClick(R.id.bt_answer_call)
    public void answerCall(View v) {
        // TODO: 7/16/2016 implement method
        postEvent(Event.create(ANSWER_CALL, null, callData));
    }

    @OnClick(R.id.bt_end_call)
    public void endCall(View v) {
        postEvent(Event.create(MessengerBus.HANG_UP_CALL, null, callData));
    }

    @OnClick(R.id.bt_decline_call)
    public void declineCall(View v) {
        postEvent(Event.create(MessengerBus.HANG_UP_CALL, null, callData));
    }

    @OnClick(R.id.bt_mute)
    public void muteCall(View view) {
        postEvent(Event.create(MessengerBus.MUTE_CALL, null, callData));
    }

    @OnClick(R.id.bt_speaker)
    public void speaker(View view) {
        postEvent(Event.create(MessengerBus.ENABLE_SPEAKER, null, callData));
    }
}

