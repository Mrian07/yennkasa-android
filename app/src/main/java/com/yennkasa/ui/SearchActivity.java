package com.yennkasa.ui;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.rey.material.widget.SnackBar;
import com.yennkasa.R;
import com.yennkasa.adapter.YennkasaBaseAdapter;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.PhoneNumberNormaliser;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import io.realm.Realm;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class SearchActivity extends PairAppActivity {

    public static final String EXTRA_TEXT = "text";
    @Bind(R.id.clear_search)
    View clearSearch;

    @Bind(R.id.et_filter)
    EditText filterEt;

    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;
    @Bind(R.id.empty_view)
    View searchProgress;

    private Observable<CharSequence> textChangeEvents;
    private Subscription textchangesSubscription;
    private SearchResultsAdapter adapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ButterKnife.bind(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        filterEt.requestFocus();
        registerForEvent(EventBus.getDefault(), UserManager.EVENT_SEARCH_RESULTS, this);
        textChangeEvents = RxTextView.textChanges(filterEt).skip(1);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultsAdapter(delegate, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        textchangesSubscription = textChangeEvents
                .map(new Func1<CharSequence, String>() {
                    @Override
                    public String call(CharSequence charSequence) {
                        return charSequence.toString().trim();
                    }
                })
                .debounce(1, TimeUnit.SECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(final String query) {
                        if (query.length() < 1) {
                            searchResults = null;
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshDisplay(query.length() > 0);
                            }
                        });
                        if (query.length() > 0) {
                            userManager.search(query);
                        }
                    }
                });

        String text = getIntent().getStringExtra(EXTRA_TEXT);
        if (!text.isEmpty()) {
            filterEt.setText(text);
            filterEt.setSelection(text.length());
        }
        refreshDisplay(false);
    }

    @OnTextChanged(R.id.et_filter)
    void textChanged(Editable editable) {
        String o = editable.toString();
        if (o.isEmpty()) {
            ViewUtils.hideViews(clearSearch);
        } else {
            ViewUtils.showViews(clearSearch);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!textchangesSubscription.isUnsubscribed()) {
            textchangesSubscription.unsubscribe();
        }
    }

    @Nullable
    List<User> searchResults = null;

    @Override
    protected void handleEvent(Event event) {
        if (event.getTag().equals(UserManager.EVENT_SEARCH_RESULTS)) {
            //noinspection unchecked
            searchResults = ((List<User>) event.getData());
            //noinspection ThrowableResultOfMethodCallIgnored
            Exception error = event.getError();
            if (error != null) {
                UiHelpers.showToast(error.getMessage());
            }
            refreshDisplay(false);
        } else {
            super.handleEvent(event);
        }
    }

    private void refreshDisplay(boolean searching) {
        if (searchResults == null || searchResults.isEmpty()) {
            //clear adapter and hide recyclerView
            ViewUtils.hideViews(recyclerView);
        } else {
            ViewUtils.showViews(recyclerView);
            adapter.notifyDataChanged();
            //populate list
        }
        if (searching) {
            ViewUtils.showViews(searchProgress);
        } else {
            ViewUtils.hideViews(searchProgress);
        }
    }

    @Override
    protected void onDestroy() {
        unRegister(EventBus.getDefault(), UserManager.EVENT_SEARCH_RESULTS, this);
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        super.onDestroy();
    }

    @Bind(R.id.notification_bar)
    SnackBar snackbar;

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return snackbar;
    }

    @OnClick(R.id.back)
    void goBack() {
        finish();
    }

    @OnClick(R.id.clear_search)
    void clearSearch() {
        filterEt.setText("");
    }


    static class SearchResultsAdapter extends YennkasaBaseAdapter<User> {

        private final int width;
        private final int height;
        private final Drawable cityDrawable;
        private final String currentUserCountry;

        public SearchResultsAdapter(Delegate delegate, Context context) {
            super(delegate);
            width = (int) context.getResources().getDimension(R.dimen.thumbnail_width);
            height = (int) context.getResources().getDimension(R.dimen.thumbnail_height);
            this.cityDrawable = ContextCompat.getDrawable(context, R.drawable.ic_place_black_24dp);
            currentUserCountry = delegate.currentUserCountry();
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void doBindHolder(Holder holder, int position) {
            User item = getItem(position);
            ((VH) holder).userName.setText(item.getName());
            if (item.getInContacts()) {
                ((VH) holder).location.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                ((VH) holder).location.setText(PhoneNumberNormaliser.toLocalFormat(item.getUserId(), currentUserCountry));
            } else {
                ((VH) holder).location.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                ((VH) holder).location.setCompoundDrawablesWithIntrinsicBounds(cityDrawable, null, null, null);
                ((VH) holder).location.setText("  " + item.getCityName());
            }
            Context context = ((VH) holder).itemView.getContext();
            ImageLoader.load(context, item.getDP())
                    .error(R.drawable.user_avartar)
                    .placeholder(R.drawable.user_avartar)
                    .resize(width,
                            height)
                    .onlyScaleDown().into(((VH) holder).dp);
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.user_search_item, parent, false));
        }

        static class VH extends Holder {
            @Bind(R.id.tv_user_name)
            TextView userName;
            @Bind(R.id.iv_display_picture)
            ImageView dp;
            @Bind(R.id.tv_user_city)
            TextView location;

            public VH(View v) {
                super(v);
            }
        }

        interface Delegate extends YennkasaBaseAdapter.Delegate<User> {
            String currentUserCountry();
        }

    }

    private void checkUserAvailability(final User user) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.st_please_wait));
        dialog.setCancelable(false);
        dialog.show();
        subscription = UserManager.getInstance().isRegistered(user.getUserId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<User>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        dialog.dismiss();
                        suggestInvitationToUser(user);
                    }

                    @Override
                    public void onNext(User user) {
                        dialog.dismiss();
                        UiHelpers.gotoProfileActivity(SearchActivity.this, user.getUserId(), user.getInContacts());
                        finish();
                    }
                });
    }

    @Nullable
    private Subscription subscription;

    private void suggestInvitationToUser(final User user) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.invite_user, user.getName()))
                .setMessage(getString(R.string.invite_prompt, user.getName(), getString(R.string.app_name)))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.invite), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        UiHelpers.doInvite(SearchActivity.this, user.getUserId());
                    }
                });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.create().show();
            }
        });
    }

    private final SearchResultsAdapter.Delegate delegate = new SearchResultsAdapter.Delegate() {
        @Override
        public String currentUserCountry() {
            return getCurrentUser().getCountry();
        }

        @Override
        public void onItemClick(YennkasaBaseAdapter<User> adapter, View view, int position, long id) {
            User user = adapter.getItem(position);
            User tmp = userRealm.where(User.class).equalTo(User.FIELD_ID, user.getUserId()).findFirst();
            if (tmp == null && user.getCityName().equals(getString(R.string.unknown))) {
                checkUserAvailability(user);
            } else {
                UiHelpers.gotoProfileActivity(SearchActivity.this, user.getUserId(), user.getInContacts());
                finish();
            }

        }

        @Override
        public boolean onItemLongClick(YennkasaBaseAdapter<User> adapter, View view, int position, long id) {
            return false;
        }

        @NonNull
        @Override
        public List<User> dataSet() {
            return searchResults == null ? Collections.<User>emptyList() : searchResults;
        }

        @Override
        public Realm userRealm() {
            return userRealm;
        }
    };
}
