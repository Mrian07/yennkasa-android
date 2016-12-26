package com.pairapp.ui;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pairapp.Errors.ErrorCenter;
import com.pairapp.R;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.messenger.MessengerBus;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.FileUtils;
import com.pairapp.util.MediaUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.PhoneNumberNormaliser;
import com.pairapp.util.ScreenUtility;
import com.pairapp.util.SimpleDateUtil;
import com.pairapp.util.TaskManager;
import com.pairapp.util.TypeFaceUtil;
import com.pairapp.util.UiHelpers;
import com.pairapp.util.ViewUtils;
import com.pairapp.view.FrameLayout;
import com.rey.material.widget.FloatingActionButton;
import com.squareup.picasso.Callback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import io.realm.Realm;
import io.realm.RealmChangeListener;


/**
 * A simple {@link Fragment} subclass.
 */
public class ProfileFragment extends Fragment implements RealmChangeListener<Realm> {

    public static final String ARG_USER_ID = ProfileActivity.EXTRA_USER_ID;
    private static final String TAG = ProfileFragment.class.getSimpleName();

    private static final int PICK_PHOTO_REQUEST = 0x3e9;
    private static final int TAKE_PHOTO_REQUEST = 0x3ea;
    private static final int CROP_PHOTO_REQUEST = 0x3eb;

    private boolean dpLoaded = false;
    private ImageView displayPicture;
    private TextView userName, userPhoneOrAdminName;
    private User user;
    private Realm realm;
    private View progressView, changeDpButton, changeDpButton2;
    private android.widget.Button callButton, sendMessageButton;
    private ProgressDialog progressDialog;
    private Uri image_capture_out_put_uri;
    private boolean changingDp = false;
    private final UserManager.CallBack DP_CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            hideProgressView();
            changingDp = false;
            ViewUtils.showViews(changeDpButton, changeDpButton2);
            if (e == null) {
                showDp();
            } else {
                ErrorCenter.reportError(TAG, e.getMessage());
            }
        }
    };
    private TextView phoneOrAdminTitle;
    private UserManager userManager;
    //    private int DP_HEIGHT;
//    private int DP_WIDTH;
    private String phoneInlocalFormat;
    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_message:
                    UiHelpers.enterChatRoom(v.getContext(), user.getUserId());
                    getActivity().finish();
                    break;
                case R.id.bt_call:
                    attemptCall();
                    break;
                case R.id.iv_display_picture:
                    if (dpLoaded) {
                        File dpFile = new File(user.getDP());
                        Intent intent = new Intent(getActivity(), ImageViewer.class);
                        Uri uri;
                        if (dpFile.exists()) {
                            hideProgressView();
                            uri = Uri.fromFile(dpFile);
                            //noinspection ConstantConditions
                            intent.setData(uri);
                            startActivity(intent);
                        } else {//dp from remote server
                            Uri data = Uri.parse(user.getDP());
                            intent.setData(data);
                            startActivity(intent);
                        }
                    } else {
                        UiHelpers.showToast(R.string.sorry_no_dp);
                    }
                    break;
                case R.id.bt_take_photo_change_dp:
                    if (changingDp) {
                        UiHelpers.showToast(getString(R.string.busy));
                        return;
                    }
                    ViewUtils.hideViews(changeDpButton, changeDpButton2);
                    File file = new File(Config.getTempDir(), SimpleDateUtil.timeStampNow() + "_dp" + ".jpg");
                    image_capture_out_put_uri = Uri.fromFile(file);
                    MediaUtils.takePhoto(ProfileFragment.this, image_capture_out_put_uri, TAKE_PHOTO_REQUEST);
                    break;
                case R.id.bt_pick_photo_change_dp:
                    if (changingDp) {
                        UiHelpers.showToast(getString(R.string.busy));
                        return;
                    }
                    ViewUtils.hideViews(changeDpButton, changeDpButton2);
                    choosePicture();
                    break;
                default:
                    throw new AssertionError("unknown view");

            }
        }
    };

    private void attemptCall() {
        new AlertDialog.Builder(getActivity())
                .setItems(getResources().getStringArray(R.array.call_options), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String tag;
                        if (which == 0) {
                            tag = MessengerBus.VOICE_CALL_USER;
                        } else {
                            tag = MessengerBus.VIDEO_CALL_USER;
                        }
                        Event event = Event.create(tag, null, user.getUserId());
                        MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS).post(event);
                    }
                })
                .create().show();
    }


    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @SuppressLint("CutPasteId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        displayPicture = ((android.widget.ImageView) view.findViewById(R.id.iv_display_picture));
        userName = ((TextView) view.findViewById(R.id.tv_user_name));
        ViewUtils.setTypeface(userName, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        changeDpButton = view.findViewById(R.id.bt_pick_photo_change_dp);
        changeDpButton2 = view.findViewById(R.id.bt_take_photo_change_dp);
        progressView = view.findViewById(R.id.pb_progress);
        sendMessageButton = (android.widget.Button) view.findViewById(R.id.bt_message);
        sendMessageButton.setOnClickListener(clickListener);
        ViewUtils.setTypeface(sendMessageButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        callButton = (android.widget.Button) view.findViewById(R.id.bt_call);
        ViewUtils.setTypeface(callButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setCancelable(false);
        progressDialog.setMessage(getString(R.string.st_please_wait));

        ((FloatingActionButton) changeDpButton2).setIcon(getResources().getDrawable(R.drawable.ic_action_camera), true);
        ((FloatingActionButton) changeDpButton).setIcon(getResources().getDrawable(R.drawable.ic_action_picture), true);

        int screenHeight = (int) new ScreenUtility(getActivity()).getPixelsHeight();

        if (!getResources().getBoolean(R.bool.isLandscape)) {
            displayPicture.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, screenHeight / 2));
        }
        View parent = view.findViewById(R.id.tv_user_phone_group_admin);
        phoneOrAdminTitle = ((TextView) parent.findViewById(R.id.tv_title));
        userPhoneOrAdminName = ((TextView) parent.findViewById(R.id.tv_subtitle));
        ViewUtils.setTypeface(phoneOrAdminTitle, TypeFaceUtil.ROBOTO_BOLD_TTF);
        ViewUtils.setTypeface(userPhoneOrAdminName, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        //end view hookup
        realm = User.Realm(getActivity());
        userManager = UserManager.getInstance();

        String id = getArguments().getString(ARG_USER_ID);
        user = UserManager.getInstance().fetchUserIfRequired(realm, id, true);
        //common to all
        userName.setText(user.getName());
        displayPicture.setOnClickListener(clickListener);
        if (user.getType() == User.TYPE_NORMAL_USER) {
            setUpViewSingleUserWay();
        }
        //noinspection deprecation
        ActionBar actionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        if (userManager.isCurrentUser(realm, user.getUserId())) {
            //noinspection ConstantConditions
            actionBar.setTitle(R.string.you);
            view.findViewById(R.id.user_action_panel).setVisibility(View.GONE); //we don't need this
        } else {
            //noinspection ConstantConditions
            actionBar.setTitle(user.getName());
            if (!User.isGroup(user)) {
                // TODO: 7/2/2016 register for user status and set the user status to the actionbar
            }
        }
        pDialog = new ProgressDialog(getActivity());

        pDialog.setCancelable(false);
        pDialog.setMessage(getString(R.string.st_please_wait));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        realm.addChangeListener(this);
        showDp();
    }

    @Override
    public void onPause() {
        realm.removeChangeListener(this);
        super.onPause();
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (user.getType() == User.TYPE_NORMAL_USER
                && !user.getInContacts()
                && !userManager.isCurrentUser(realm, user.getUserId())
                //FIXME fix this issue
                //quick fix for inproperly prcessed groups
                && !user.getUserId().contains("@")) {
            inflater.inflate(R.menu.menu_profile_fragment, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_add_contact);
        if (item != null) {
            item.setVisible(!user.getInContacts() && user.getType() == User.TYPE_NORMAL_USER && !user.getUserId().contains("@"));
        }
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_contact) {
            UiHelpers.addToContact(getActivity(), user);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void setUpViewSingleUserWay() {
        if (userManager.isCurrentUser(realm, user.getUserId())) {
            callButton.setVisibility(View.GONE);
            sendMessageButton.setVisibility(View.GONE);
            changeDpButton.setOnClickListener(clickListener);
            changeDpButton2.setOnClickListener(clickListener);
        } else {
            changeDpButton.setVisibility(View.GONE);
            changeDpButton2.setVisibility(View.GONE);
            callButton.setOnClickListener(clickListener);
        }
        //noinspection ConstantConditions
        phoneOrAdminTitle.setText(R.string.phone);
        phoneInlocalFormat = PhoneNumberNormaliser.toLocalFormat("+" + user.getUserId(), userManager.getUserCountryISO(realm));
        userPhoneOrAdminName.setText(phoneInlocalFormat);
    }


    private void choosePicture() {
        Intent attachIntent;
        attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachIntent.setType("image/*");
        startActivityForResult(attachIntent, PICK_PHOTO_REQUEST);
    }

    @Override
    public void onChange(Realm o) {
        try {
            userName.setText(user.getName());
            if (!userManager.isGroup(realm, user.getUserId())) {
                setUpMutualMembers();
            }
            if (changingDp) {
                showDp();
            }
            //todo probably change status too and last activity
        } catch (Exception lateUpdate) {//fragment no more in layout or maybe user left group
            PLog.e(TAG, lateUpdate.getMessage(), lateUpdate.getCause());
        }
    }

    private void setUpMutualMembers() {
        // TODO: 8/25/2015 implement this method
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_PHOTO_REQUEST) {
                Uri uri = data.getData();
                doChangeDp(uri);
            } else if (requestCode == TAKE_PHOTO_REQUEST) {
                doChangeDp(image_capture_out_put_uri);
            } else if (requestCode == CROP_PHOTO_REQUEST) {
                ViewUtils.hideViews(changeDpButton, changeDpButton2);
                showProgressView();
                String filePath = data.getData().getPath();
                changingDp = true;
                userManager.changeDp(user.getUserId(), filePath, DP_CALLBACK);
            } else if (requestCode == UiHelpers.ADD_TO_CONTACTS_REQUEST) {
                userManager.fetchUserIfRequired(realm, user.getUserId());
                getActivity().supportInvalidateOptionsMenu();
            }
        } else {
            final UserManager instance = UserManager.getInstance();
            if (instance.isGroup(realm, user.getUserId()) || instance.isCurrentUser(realm, user.getUserId()))
                ViewUtils.showViews(changeDpButton, changeDpButton2);
        }
    }

    private void doChangeDp(Uri uri) {
        if (changingDp) {
            return;
        }
        ViewUtils.hideViews(changeDpButton, changeDpButton2);
        String filePath = FileUtils.resolveContentUriToFilePath(uri);
        if (filePath == null) {
            ErrorCenter.reportError(TAG, getString(R.string.error_use_file_manager));
            return;
        }
        File file = new File(filePath);

        if (!file.exists()) {
            UiHelpers.showErrorDialog(getActivity(), getString(R.string.invalid_image));
            ViewUtils.showViews(changeDpButton, changeDpButton2);
        } else if (!MediaUtils.isImage(filePath)) {
            UiHelpers.showErrorDialog(getActivity(), getString(R.string.not_a_bitmap));
            ViewUtils.showViews(changeDpButton, changeDpButton2);
        } else if (file.length() > FileUtils.ONE_MB * 8) {
            UiHelpers.showErrorDialog(getActivity(), getString(R.string.image_size_too_large));
            ViewUtils.showViews(changeDpButton, changeDpButton2);
        } else {
            Intent intent = new Intent(getActivity(), ImageCropper.class);
            intent.putExtra(ImageCropper.IMAGE_TO_CROP, filePath);
            startActivityForResult(intent, CROP_PHOTO_REQUEST);
        }
    }

    private void showDp() {
        if (changingDp) {
            return;
        }
        showProgressView();
        Drawable placeHolderD, errorD;
        final Bitmap placeHolderB = getArguments().getParcelable(ProfileActivity.EXTRA_AVARTAR_PLACEHOLDER), errorB = getArguments().getParcelable(ProfileActivity.EXTRA_AVARTAR_ERROR);
        //  if (placeHolderB == null) {
        //}
        //if (errorB == null) {
        //}
        errorD = errorB == null ? getResources().getDrawable(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar) : new BitmapDrawable(getResources(), errorB);
        placeHolderD = placeHolderB == null ? getResources().getDrawable(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar) : new BitmapDrawable(getResources(), placeHolderB);
        //noinspection ConstantConditions
        ImageLoader.load(getActivity(), user.getDP())
                .placeholder(placeHolderD)
                .error(errorD)
                .noFade()
                .into(displayPicture, dpLoadedCallback);
    }

    @SuppressWarnings("unused")
    private void saveBitmapIfPossible(final Bitmap bitmap) {
        final User copy = User.copy(user);
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                if (!new File(copy.getDP()).exists()) {
                    String originalPath = FileUtils.hash(copy.getDP() + "_" + copy.getUserId().replaceAll("\\s+", "_") + ".jpg");
                    if (new File(Config.getAppProfilePicsBaseDir(), originalPath).exists()) {
                        return;
                    }
                    File file = new File(Config.getTempDir(), copy.getUserId().replaceAll("\\s+", "_") + "display_pic.jpg.tmp");
                    try {
                        FileOutputStream outputStream = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                        //noinspection ResultOfMethodCallIgnored
                        file.renameTo(new File(Config.getAppProfilePicsBaseDir(), originalPath));
                    } catch (FileNotFoundException e) {
                        PLog.d(TAG, "error while saving bitmap");
                    }
                }
            }
        }, false);
    }

    private final Callback dpLoadedCallback = new Callback() {
        @Override
        public void onSuccess() {
            dpLoaded = true;
            hideProgressView();
        }

        @Override
        public void onError() {
            dpLoaded = false;
            ProfileFragment.this.hideProgressView();
            if (new File(user.getDP()).exists()) {
                UiHelpers.showStopAnnoyingMeDialog(getActivity(),
                        "dpLoadError" + TAG,
                        getString(R.string.stop_annoying_me),
                        getString(R.string.device_out_of_ram),
                        getString(android.R.string.ok), getString(R.string.no),
                        new UiHelpers.Listener() {
                            @Override
                            public void onClick() {

                            }
                        }, null);
            }
        }
    };

    @Override
    public void onDestroy() {
        realm.close();
        // TODO: 7/2/2016 stop listing for user status
        super.onDestroy();
    }

    private void showProgressView() {
        if (changingDp) {
            pDialog.show();
        } else {
            progressView.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgressView() {
        try {
            if (changingDp)
                pDialog.dismiss();
            else
                progressView.setVisibility(View.GONE);
        } catch (Exception ignored) {
        }
    }

    private void leaveGroup() {
        progressDialog.show();
        userManager.leaveGroup(realm, user.getUserId(), new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                progressDialog.dismiss();
                if (e != null) {
                    ErrorCenter.reportError(TAG, e.getMessage());
                } else {
                    getActivity().finish();
                }
            }
        });
    }

    ProgressDialog pDialog;
}
