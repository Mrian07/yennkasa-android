package com.pair.pairapp.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.UserManager;
import com.squareup.picasso.Picasso;

import java.io.File;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class SideBarFragment extends ListFragment {
    private MenuCallback callback;
    private String[] items;
    public SideBarFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        if(!(activity instanceof MenuCallback)){
            throw new ClassCastException(activity.getClass().getName() + " must implement interface" + MenuCallback.class.getSimpleName());
        }
        super.onAttach(activity);
        callback = (MenuCallback) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.side_bar_fragment, container, false);
        ImageView iv = ((ImageView) view.findViewById(R.id.iv_display_picture));
        User user = UserManager.INSTANCE.getMainUser();
        Picasso.with(getActivity()).load(new File(user.getDP())).resize(150, 150).centerCrop().error(R.drawable.avatar_empty).into(iv);
        items = getResources().getStringArray(R.array.menuItems);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, items);
        setListAdapter(adapter);
        return view;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        callback.onItemSelected(position,items[position]);
    }

    public interface MenuCallback{
        void onItemSelected(int position,String recommendedTitle);
    }
}
