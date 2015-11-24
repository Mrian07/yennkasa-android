package com.idea.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.idea.data.User;
import com.idea.pairapp.R;
import com.idea.ui.ImageLoader;
import com.idea.util.PLog;
import com.idea.util.TypeFaceUtil;
import com.idea.util.ViewUtils;

import java.util.List;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/28/2015.
 */
public class GroupsAdapter extends RealmBaseAdapter<User> {
    private static final String TAG = GroupsAdapter.class.getSimpleName();

    public GroupsAdapter(Context context, RealmResults<User> realmResults) {
        super(context, realmResults, true);
    }

    public static String join(CharSequence delimiter, List<User> users) {
        //copied from android.text.TextUtils#join
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (User token : users) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token.getName());
        }
        return sb.toString();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final User group = getItem(position);
        if (convertView == null) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_list_item, parent, false);
            holder = new ViewHolder();
            holder.groupName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            holder.groupMembers = ((TextView) convertView.findViewById(R.id.tv_group_members));
            holder.groupIcon = (ImageView) convertView.findViewById(R.id.iv_group_dp);
            ViewUtils.setTypeface(holder.groupName, TypeFaceUtil.DROID_SERIF_BOLD_TTF);
            ViewUtils.setTypeface(holder.groupMembers, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }
        holder.groupName.setText(group.getName());
        TargetOnclick targetOnclick = new TargetOnclick(holder.groupIcon, group.getUserId());
        ImageLoader.load(context, group.getDP())
                .placeholder(R.drawable.group_avatar)
                .error(R.drawable.group_avatar)
                .resize((int) context.getResources().getDimension(R.dimen.thumbnail_width), (int) context.getResources().getDimension(R.dimen.thumbnail_height))
                .onlyScaleDown().into(targetOnclick);

        String users = join(",", group.getMembers());
        holder.groupMembers.setText(users);
        holder.groupIcon.setOnClickListener(targetOnclick);
        holder.groupName.setOnClickListener(targetOnclick);
        PLog.i(TAG, "Display Picture: " + group.getDP());
        return convertView;
    }

    public class ViewHolder {
        private TextView groupName,
                groupMembers;
        private ImageView groupIcon;
    }
}