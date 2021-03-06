package com.yennkasa.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import com.google.i18n.phonenumbers.NumberParseException;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.SnackBar;
import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.R;
import com.yennkasa.adapter.MultiChoiceUsersAdapter;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.PhoneNumberNormaliser;
import com.yennkasa.util.TypeFaceUtil;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class CreateGroupActivity extends PairAppActivity implements AdapterView.OnItemClickListener,
        ItemsSelector.OnFragmentInteractionListener, TextWatcher,
        ChooseDisplayPictureFragment.Callbacks {

    public static final String TAG = CreateGroupActivity.class.getSimpleName();
    private Set<String> selectedUsersNames = new TreeSet<>();
    private Set<String> selectedUsers = new HashSet<>();
    private String groupName;
    private CustomUsersAdapter adapter;
    private EditText groupNameEt;
    private int stage = 0;
    private String dp;
    private View menuItemDone, menuItemNext, dpPreview;
    private ProgressDialog progressDialog;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tv_menu_item_done:
                    completeGroupCreation();
                    break;
                case R.id.tv_menu_item_next:
                    proceedToAddMembers();
                    break;
                default:
                    throw new AssertionError();
            }
        }
    };
    private Toolbar toolBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);
        toolBar = ((Toolbar) findViewById(R.id.main_toolbar));
        setSupportActionBar(toolBar);
        menuItemDone = toolBar.findViewById(R.id.tv_menu_item_done);
        menuItemNext = toolBar.findViewById(R.id.tv_menu_item_next);

        menuItemDone.setOnClickListener(listener);
        menuItemNext.setOnClickListener(listener);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        groupNameEt = ((EditText) findViewById(R.id.et_group_name));
        ViewUtils.setTypeface(groupNameEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        dpPreview = findViewById(R.id.rl_group_dp_preview);
        groupNameEt.addTextChangedListener(this);
        RealmResults<User> users = getQuery().findAllSorted(User.FIELD_NAME);
        adapter = new CustomUsersAdapter(userRealm, users);
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.st_please_wait));
        progressDialog.setCancelable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    private RealmQuery<User> getQuery() {
        return userRealm.where(User.class).notEqualTo(User.FIELD_ID, userManager
                .getCurrentUser(userRealm).getUserId()).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP);
    }

    private void proceedToAddMembers() {

        final String proposedName = groupNameEt.getText().toString().trim();

        if (TextUtils.isEmpty(proposedName)) {
            // TODO: 8/5/2015 validate group name
            UiHelpers.showErrorDialog(this, getString(R.string.cant_be_empty));
            groupNameEt.requestFocus();
            return;
        }
        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            ProgressDialog dialog;
            String errorMessage, finalName;

            @Override
            protected void onPreExecute() {
                dialog = new ProgressDialog(CreateGroupActivity.this);
                dialog.setMessage(getString(R.string.st_please_wait));
                dialog.setCancelable(false);
                dialog.show();
                finalName = groupNameEt.getText().toString().trim();
                groupNameEt.setText("");
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                dialog.dismiss();
                if (aBoolean) {
                    proceedToNextStage(finalName);
                } else {
                    UiHelpers.showErrorDialog(CreateGroupActivity.this, errorMessage);
                    groupNameEt.requestFocus();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                Realm userRealm = User.Realm(CreateGroupActivity.this);
                try {
                    android.support.v4.util.Pair<String, String> errorNamePair = userManager.isValidGroupName(userRealm, finalName);
                    finalName = errorNamePair.first;
                    errorMessage = errorNamePair.second;
                    return errorMessage == null;
                } finally {
                    userRealm.close();
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
    }

    //pattern is locale insensitive
//    private static final Pattern groupNamePattern = Pattern.compile("^[\\p{Alpha}][A-Za-z_\\p{Space}]{3,30}[\\p{Alpha}]$");

    private ItemsSelector fragment = null;

    private void proceedToNextStage(String name) {
        fragment = new ItemsSelector();
        groupNameEt.setVisibility(View.GONE);
        dpPreview.setVisibility(View.GONE);
        groupName = name;
        stage = 2;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.rl_main_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
        supportInvalidateOptionsMenu();
    }

    private void promptAndExit() {
        UiHelpers.promptAndExit(this);
    }

    @Override
    public void onBackPressed() {
        promptAndExit();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        switch (stage) {
            case 0:
                menuItemDone.setVisibility(View.GONE);
                menuItemNext.setVisibility(View.GONE);
                break;
            case 1:
                menuItemDone.setVisibility(View.GONE);
                if (groupNameEt.getText().length() >= 0) {
                    menuItemNext.setVisibility(View.VISIBLE);
                } else {
                    menuItemNext.setVisibility(View.GONE);
                }
                break;
            case 2:
                menuItemDone.setVisibility(View.VISIBLE);
                menuItemNext.setVisibility(View.GONE);
                break;
            default:
                throw new AssertionError();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == android.R.id.home) {
            promptAndExit();
            return true;
        }
//
//        if (id == R.id.action_proceed) {
//            proceedToAddMembers();
//            return true;
//        }

//        if (id == R.id.action_done) {
//            completeGroupCreation();
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    private void completeGroupCreation() {
        continueProcess();
    }

    private void continueProcess() {
        if (selectedUsers.size() >= 2) {
            progressDialog.show();
            userManager.createGroup(userRealm, groupName, selectedUsers, new UserManager.CreateGroupCallBack() {
                @Override
                public void done(Exception e, String groupId) {
                    onGroupCreated(e, groupId);
                }
            });
        } else {
            UiHelpers.showErrorDialog(this, getString(R.string.group_minimum_notice));
        }
    }

    private void onGroupCreated(Exception e, final String groupId) {
        if (e != null) {
            progressDialog.dismiss();
            ErrorCenter.reportError(TAG, e.getMessage());
        } else {
            if (dp != null && new File(dp).exists()) {
                userManager.changeDp(groupId, dp, new UserManager.CallBack() {
                    @Override
                    public void done(Exception e) {
                        progressDialog.dismiss();
                        if (e == null) {
                            //hurray we changed dp successfully
                            UiHelpers.enterChatRoom(CreateGroupActivity.this, groupId, false);
                            finish();
                        } else {
                            ErrorCenter.reportError(TAG, e.getMessage());
                        }
                    }
                });
            } else {
                progressDialog.dismiss();
                UiHelpers.enterChatRoom(CreateGroupActivity.this, groupId, false);
                finish();
            }
        }
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        String userId = ((User) parent.getAdapter().getItem(position)).getUserId();
//        delegagte.onItemSelected(parent, view, position, id, !selectedUsers.contains(userId));
    }

    @Override
    public ViewGroup searchBar() {
        return ((ViewGroup) findViewById(R.id.search_bar));
    }

    @Override
    public BaseAdapter getAdapter() {
        return adapter;
    }

    @Override
    public Filterable filter() {
        return adapter;
    }

    @Override
    public ItemsSelector.ContainerType preferredContainer() {
        return ItemsSelector.ContainerType.LIST;
    }

    @Override
    public View emptyView() {
        TextView emptyView = new TextView(this);
        emptyView.setText(R.string.not_content);
        emptyView.setTextSize(R.dimen.standard_text_size);
        return emptyView;
    }

    @Override
    public boolean multiChoice() {
        return true;
    }

    @Override
    public boolean supportAddCustom() {
        return false;
    }

    @Override
    public Set<String> selectedItems() {
        return selectedUsersNames;
    }

    @Override
    public void onCustomAdded(String item) {
        String phoneNumber = item.trim();
        if (!TextUtils.isEmpty(phoneNumber)) {
            String original = phoneNumber; //see usage below
            final String userCountryISO = userManager.getUserCountryISO(userRealm);
            try {
                phoneNumber = PhoneNumberNormaliser.cleanNonDialableChars(phoneNumber);
                phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, userCountryISO);
                if (!PhoneNumberNormaliser.isValidPhoneNumber(phoneNumber, userCountryISO)) {
                    throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "");
                }
                finallyAddMember(phoneNumber);
            } catch (NumberParseException e) {
                // FIXME: 8/5/2015 show a better error message
                UiHelpers.showErrorDialog(CreateGroupActivity.this, getString(R.string.invalid_phone_number, original));
            }
        } else {
            // FIXME: 8/5/2015 show a better error message
            UiHelpers.showToast(R.string.cant_be_empty);
        }
    }

    private void finallyAddMember(String phoneNumber) {
        if (phoneNumber.equals(getMainUserId())) {
            UiHelpers.showErrorDialog(this, getString(R.string.user_adding_him_her_self_notice));
        } else if (selectedUsers.contains(phoneNumber)) {
            UiHelpers.showErrorDialog(this, getString(R.string.duplicate_number_notice));
        } else {
            selectedUsers.add(phoneNumber);
            selectedUsersNames.add("@" + PhoneNumberNormaliser.toLocalFormat(phoneNumber, getCurrentUser().getCountry()).replace("\\D", ""));
            adapter.notifyDataSetChanged();
            fragment.onItemsChanged();
            supportInvalidateOptionsMenu();
            UiHelpers.showToast(getString(R.string.added_custom_notice_toast), Toast.LENGTH_LONG);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (s.length() <= 0 && stage == 1) {
            stage = 0;
        } else if (s.length() >= 1 && stage == 0) {
            stage = 1;
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onDp(String newDp) {
        dp = newDp;
    }

    @Override
    public boolean allowCancelling() {
        return false;
    }

    @Override
    public void onCancelled() {
        //do nothing
    }

    @Override
    public CharSequence noDpNotice() {
        return null;
    }

    @Override
    public String defaultDp() {
        return null;
    }

    @Override
    public int placeHolderDp() {
        return R.drawable.group_avatar;
    }

    private final MultiChoiceUsersAdapter.Delegate delegagte = new MultiChoiceUsersAdapter.Delegate() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id, boolean isSelected) {
            User user = ((User) parent.getAdapter().getItem(position));
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.cb_checked);
            String userName = user.getName();
            if (!userName.startsWith("@")) {
                userName = "@" + userName;
            }
            if (isSelected) {
                selectedUsers.add(user.getUserId());
                selectedUsersNames.add(userName);
                if (!checkBox.isChecked()) {
                    checkBox.setChecked(true);
                }
            } else {
                selectedUsers.remove(user.getUserId());
                selectedUsersNames.remove(userName);
                if (checkBox.isChecked()) {
                    checkBox.setChecked(false);
                }
            }
            fragment.onItemsChanged();
            supportInvalidateOptionsMenu();
        }

        @Override
        public Context getContext() {
            return CreateGroupActivity.this;
        }
    };

    private class CustomUsersAdapter extends MultiChoiceUsersAdapter {
        public CustomUsersAdapter(Realm realm, RealmResults<User> realmResults) {
            super(delegagte, realm, realmResults, selectedUsers, R.id.cb_checked);
        }

        @Override
        protected RealmQuery<User> getOriginalQuery() {
            return getQuery();
        }
    }

    @Override
    public final View getToolBar() {
        return toolBar;
    }
}
