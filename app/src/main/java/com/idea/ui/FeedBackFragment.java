package com.idea.ui;


import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import android.net.Uri;
import com.idea.data.UserManager;
import com.idea.messenger.PairAppClient;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.GcmUtils;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.rey.material.widget.CheckBox;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

/**
 * A simple {@link Fragment} subclass.
 */
public class FeedBackFragment extends Fragment {

    EditText subject;
    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //noinspection ConstantConditions
            CheckBox checkBox = ((CheckBox) getView().findViewById(R.id.cb_checked));

            String feedbackBody = subject.getText().toString().trim();
            subject.setText("");
            if (TextUtils.isEmpty(feedbackBody)) {
                UiHelpers.showErrorDialog(((PairAppBaseActivity) getActivity()), getString(R.string.error_feedback_body_empty));
                return;
            }

            String title = titleEt.getText().toString().trim();
            if (TextUtils.isEmpty(title)) {
                title = "no subject";
            } else if (title.length() > 40) {
                UiHelpers.showErrorDialog(getActivity(), getString(R.string.error_feedback_subject_too_lonng));
                return;
            }
            titleEt.setText("");
            JSONObject reportObject = new JSONObject();
            try {
                reportObject.put("body", feedbackBody);
                if (checkBox.isChecked()) {
                    reportObject.put("device", Build.DEVICE);
                    reportObject.put("model", Build.MODEL);
                    reportObject.put("manufacturer", Build.MANUFACTURER);
                    reportObject.put("reportedBy", UserManager.getMainUserId());
                    reportObject.put("time", new Date());
                    reportObject.put("pairapVersion", BuildConfig.VERSION_NAME);
                    reportObject.put("pairapVersionCode", BuildConfig.VERSION_CODE);
                    reportObject.put("apiVersion", Build.VERSION.SDK_INT);
                    reportObject.put("hasGooglePlayServices", GcmUtils.hasGcm());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        reportObject.put("arc", TextUtils.join(",", Build.SUPPORTED_ABIS));
                    } else {
                        //noinspection deprecation
                        String[] abi = {
                                Build.CPU_ABI,
                                Build.CPU_ABI2
                        };
                        reportObject.put("arc", TextUtils.join(",", abi));
                    }
                    reportObject.put("locale", Locale.getDefault().getDisplayCountry());
                }

                Intent intent = new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:team.pairapp.android@gmail.com"));
                intent.putExtra(Intent.EXTRA_TEXT, reportObject.toString());
                intent.putExtra(Intent.EXTRA_SUBJECT, title);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    reportObject.put("title", title);
                    PairAppClient.sendFeedBack(reportObject);
                    UiHelpers.showToast(R.string.feedback_sent_successfully);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    };
    private com.rey.material.widget.EditText titleEt;

    public FeedBackFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //noinspection ConstantConditions
        View view = inflater.inflate(R.layout.fragment_feed_back, container, false);

        Button submit = (Button) view.findViewById(R.id.bt_submit);
        ViewUtils.setTypeface(submit, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        submit.setOnClickListener(onClickListener);
        titleEt = ((com.rey.material.widget.EditText) view.findViewById(R.id.et_feedback_subject));
        subject = ((EditText) view.findViewById(R.id.et_feedback_body));
        ViewUtils.setTypeface(submit, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface(titleEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        TextView addDeviceInfo = ((TextView) view.findViewById(R.id.tv_add_device_info));
        ViewUtils.setTypeface(addDeviceInfo, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        return view;
    }


}
