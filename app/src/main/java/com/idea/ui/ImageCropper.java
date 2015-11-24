
package com.idea.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.view.View;

import com.edmodo.cropper.CropImageView;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.SimpleDateUtil;
import com.idea.util.TaskManager;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.rey.material.widget.SnackBar;
import com.squareup.picasso.Picasso.LoadedFrom;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class ImageCropper extends PairAppActivity {

    // Static final constants
    private static final int DEFAULT_ASPECT_RATIO_VALUES = 10;
    private static final String ASPECT_RATIO_X = "ASPECT_RATIO_X";
    private static final String ASPECT_RATIO_Y = "ASPECT_RATIO_Y";
    public static final String IMAGE_TO_CROP = "imageToCrop";
    // Instance variables
    private int mAspectRatioX = DEFAULT_ASPECT_RATIO_VALUES;
    private int mAspectRatioY = DEFAULT_ASPECT_RATIO_VALUES;
    private CropImageView cropImageView;
    Bitmap croppedImage;
    ProgressDialog dialog;

    // Saves the state upon rotating the screen/restarting the activity
    @Override
    protected void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(ASPECT_RATIO_X, mAspectRatioX);
        bundle.putInt(ASPECT_RATIO_Y, mAspectRatioY);
    }

    // Restores the state upon rotating the screen/restarting the activity
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        mAspectRatioX = bundle.getInt(ASPECT_RATIO_X);
        mAspectRatioY = bundle.getInt(ASPECT_RATIO_Y);
    }

    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_image);
        dialog = new ProgressDialog(ImageCropper.this);
        cropImageView = (CropImageView) findViewById(R.id.CropImageView);
        cropImageView.setAspectRatio(10, 10);
        String path = getIntent().getStringExtra(IMAGE_TO_CROP);
        if (path == null || !new File(path).exists()) {
            throw new IllegalArgumentException("invalid or no path passed");
        }
        ViewUtils.hideViews(findViewById(R.id.ll_bt_panel));
        View cropImage = findViewById(R.id.bt_crop_image);
        cropImage.setOnClickListener(clickListener);
        View cancelCrop = findViewById(R.id.bt_cancel_crop);
        cancelCrop.setOnClickListener(clickListener);
        ImageLoader.load(this, path).into(target);
    }

    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_cancel_crop:
                    setResult(RESULT_CANCELED);
                    finish();
                    break;
                case R.id.bt_crop_image:
                    tryCropAndReturn();
                    break;
                default:
                    throw new AssertionError();
            }
        }
    };

    @Override
    protected SnackBar getSnackBar() {
        return (SnackBar) findViewById(R.id.notification_bar);
    }


    private void tryCropAndReturn() {
        croppedImage = cropImageView.getCroppedImage();
        int height = croppedImage.getHeight(), width = croppedImage.getWidth();
        if (((double) height) / width > 1.5) {
            UiHelpers.showPlainOlDialog(ImageCropper.this, getString(R.string.image_too_narrow));
        } else if (((double) width) / height > 1.5) {
            UiHelpers.showPlainOlDialog(ImageCropper.this, getString(R.string.image_too_short));
        } else {
            TaskManager.executeNow(new Runnable() {
                @Override
                public void run() {
                    final File file = new File(Config.getTempDir(), SimpleDateUtil.timeStampNow() + ".jpg");
                    try {
                        croppedImage.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(file));
                        Intent intent = new Intent();
                        intent.setData(Uri.fromFile(file));
                        setResult(RESULT_OK, intent);
                        finish();
                    } catch (FileNotFoundException e) {
                        runOnUiThread(new Runnable(){
                            public void run(){
                                UiHelpers.showPlainOlDialog(ImageCropper.this, getString(R.string.error_unable_to_crop));
                            }
                        });
                    }
                }
            }, true);
        }
    }

    private final Target target = new Target() {

        @Override
        public void onPrepareLoad(Drawable arg0) {
            dialog.setMessage(getString(R.string.loading));
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        public void onBitmapLoaded(Bitmap arg0, LoadedFrom arg1) {
            dialog.dismiss();
            ViewUtils.showViews(findViewById(R.id.ll_bt_panel));
            cropImageView.setImageBitmap(arg0);
        }

        @Override
        public void onBitmapFailed(Drawable arg0) {
            dialog.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(ImageCropper.this);
            builder.setTitle(R.string.error).setMessage(R.string.error_failed_to_open_image).setPositiveButton(android.R.string.ok, null).create().show();
        }
    };
}
