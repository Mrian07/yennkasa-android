package com.idea.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.idea.pairapp.R;
import com.idea.util.TypeFaceUtil;
import com.idea.util.ViewUtils;

/**
 * @author Null-Pointer on 11/9/2015.
 */
public class SimpleAdapter extends BaseAdapter {

    private final Drawable[] imgRes;
    private final CharSequence[] data;

    public SimpleAdapter(Context context, int[] imgRes, CharSequence[] data) {
        this.imgRes = new Drawable[imgRes.length];
        this.data = data;
        for (int i = 0; i < imgRes.length; i++) {
            this.imgRes[i] = context.getResources().getDrawable(imgRes[i]);
        }
    }

    public SimpleAdapter(Drawable[] imgRes, CharSequence[] data) {
        this.imgRes = imgRes;
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.length;
    }

    @Override
    public Object getItem(int position) {
        return data[position];
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @SuppressLint("ViewHolder")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.img_title_list_item, parent, false);
        }
        TextView textView = (TextView) convertView;
        textView.setText("    " + data[position]);
        ViewUtils.setTypeface(textView, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        textView.setCompoundDrawablesWithIntrinsicBounds(imgRes[position], null, null, null);
        return convertView;
    }
}
