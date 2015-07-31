package com.pair.pairapp.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.pair.adapter.CountriesListAdapter;
import com.pair.data.Country;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.SetUpActivity;
import com.pair.util.Config;
import com.pair.util.GcmHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;
import com.pair.workers.ContactSyncService;

import io.realm.Realm;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
@SuppressWarnings("ConstantConditions FieldCanBeLocal")
public class LoginFragment extends Fragment {
    private Button loginButton;
    public static final String TAG = LoginFragment.class.getSimpleName();
    private EditText passwordEt;
    private AutoCompleteTextView phoneNumberEt;
    private boolean busy = false;
    private ProgressDialog progressDialog;
    private Realm realm;
    public LoginFragment(){}

    @Override
    public void onAttach(Activity activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        realm = Realm.getInstance(getActivity());
        View view = inflater.inflate(R.layout.login_fragment, container, false);
        phoneNumberEt = (AutoCompleteTextView) view.findViewById(R.id.et_phone_number_field);
        new UiHelpers.AutoCompleter(getActivity(), phoneNumberEt).execute();//enable autocompletion
        passwordEt = (EditText) view.findViewById(R.id.et_passwordField);
        loginButton = (Button) view.findViewById(R.id.bt_loginButton);
        Spinner spinner = ((Spinner) view.findViewById(R.id.sp_ccc));
        final CountriesListAdapter adapter = new CountriesListAdapter(getActivity(),realm.where(Country.class).findAllSorted("name"));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        TextView tv = (TextView) view.findViewById(R.id.tv_signup);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy)
                    return;
                Fragment fragment = getFragmentManager().findFragmentByTag(SetUpActivity.SIGNUP_FRAG);
                if (fragment == null) {
                    fragment = new SignupFragment();
                }
                getFragmentManager().beginTransaction().replace(R.id.container, new SignupFragment(), SetUpActivity.SIGNUP_FRAG).commit();
            }
        });
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(TextUtils.isEmpty(passwordEt.getText().toString())
                        ||TextUtils.isEmpty(phoneNumberEt.getText().toString()))
                {
                    return;
                }
                attemptLogin();
            }
        });
        return view;
    }

    private void attemptLogin() {
        if (busy) {
            return;
        }
        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setMessage(getString(R.string.st_please_wait));
        progressDialog.setCancelable(false);
        progressDialog.show();
        busy = true;
        GcmHelper.register(getActivity(), new GcmHelper.GCMRegCallback() {
            @Override
            public void done(Exception e, String gcmRegId) {
                if (e == null) {
                    String phoneNumber = phoneNumberEt.getText().toString().trim();
                    String password = passwordEt.getText().toString().trim();
                    String ccc = ((Country) ((Spinner) getView().findViewById(R.id.sp_ccc)).getSelectedItem()).getIso2letterCode();
                    UserManager.getInstance().logIn(phoneNumber, password, gcmRegId, ccc, new UserManager.CallBack() {
                        @Override
                        public void done(Exception e) {
                            progressDialog.dismiss();
                            if (e == null) {
                                Config.enableComponents();
                                ContactSyncService.start(Config.getApplicationContext());
                                startActivity(new Intent(getActivity(), MainActivity.class));
                                getActivity().finish();
                            } else {
                                String message = e.getMessage();
                                if ((message == null) || (message.isEmpty())) {
                                    message = "an unknown error occurred";
                                }
                                UiHelpers.showErrorDialog(getActivity(),message);
                            }
                        }
                    });
                } else {
                    progressDialog.dismiss();
                    busy = false;
                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                }
            }
        });
    }

    private UserManager.CallBack loginOrSignUpCallback = new UserManager.CallBack() {
        public void done(Exception e) {

        }
    };
}
