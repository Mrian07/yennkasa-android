package com.pairapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.pairapp.R;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.SnackBar;

public class ProfileActivity extends PairAppActivity {

    public static final String EXTRA_USER_ID = "user id";
    public static final String EXTRA_AVARTAR_PLACEHOLDER = "profileActivity.placeholder", EXTRA_AVARTAR_ERROR = "profileActivity.errorDrable";
    private ToolbarManager manager;
    private Toolbar toolBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        toolBar = ((Toolbar) findViewById(R.id.main_toolbar));
        manager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    void handleIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        String id = bundle.getString(ProfileActivity.EXTRA_USER_ID);
        if (id == null) {
            throw new IllegalArgumentException("should pass in user id");
        }
        Fragment fragment = new ProfileFragment();
        bundle = new Bundle();
        bundle.putAll(getIntent().getExtras());
        fragment.setArguments(bundle);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }
}