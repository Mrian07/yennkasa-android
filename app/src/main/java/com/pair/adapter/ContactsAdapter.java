package com.pair.adapter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.app.FragmentActivity;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.pairapp.Config;
import com.pair.pairapp.R;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.rey.material.widget.Button;
import com.squareup.picasso.Picasso;

import java.util.List;

import static com.pair.data.ContactsManager.Contact;

/**
 * @author Null-Pointer on 6/12/2015.
 */
public class ContactsAdapter extends BaseAdapter {
    private static final String TAG = ContactsAdapter.class.getSimpleName();
    private List<Contact> contacts;
    private boolean isAddOrRemoveFromGroup;
    private final Picasso PICASSO;
    private FragmentActivity context;

    public ContactsAdapter(FragmentActivity context, List<Contact> contacts, boolean isAddOrRemoveFromGroup) {
        this.contacts = contacts;
        this.isAddOrRemoveFromGroup = isAddOrRemoveFromGroup;
        this.PICASSO = Picasso.with(context);
        this.context = context;
    }

    @Override
    public int getCount() {
        return contacts.size();
    }

    @Override
    public Contact getItem(int position) {
        return contacts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public int getItemViewType(int position) {
        return (isAddOrRemoveFromGroup) ? 2 : getItem(position).isRegisteredUser ? 0 : 1;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final Contact contact = getItem(position);

        final ViewHolder holder;
        int layoutRes = layoutResource[getItemViewType(position)];
        if (convertView == null) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            holder = new ViewHolder();
            if (isAddOrRemoveFromGroup) {
                holder.userName = ((TextView) convertView);
            } else {
                holder.userName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            }
            holder.userStatus = ((TextView) convertView.findViewById(R.id.tv_user_phone));
            holder.inviteButton = (Button) convertView.findViewById(R.id.bt_invite);
            holder.userDp = ((ImageView) convertView.findViewById(R.id.iv_display_picture));
            holder.userPhone = (TextView) convertView.findViewById(R.id.tv_user_phone);
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick(v, contact);
            }
        };
        holder.userName.setText(contact.name);
        if (isAddOrRemoveFromGroup) {
            return convertView;
        }
        if (contact.isRegisteredUser) {
            holder.userStatus.setText(contact.status);
            PICASSO.load(Config.DP_ENDPOINT + "/" + contact.DP)
                    .error(R.drawable.user_avartar)
                    .placeholder(R.drawable.user_avartar)
                    .resize(150, 150)
                    .centerInside()
                    .into(holder.userDp);
            holder.userName.setClickable(true);
            holder.userDp.setClickable(true);
            holder.userDp.setOnClickListener(listener);
            holder.userPhone.setText(PhoneNumberNormaliser.toLocalFormat(getItem(position).numberInIEE_Format));
            holder.userPhone.setClickable(true);
            holder.userPhone.setOnClickListener(listener);
        } else {
            holder.userStatus.setText(PhoneNumberNormaliser.toLocalFormat(contact.numberInIEE_Format));
            holder.userStatus.setOnClickListener(listener);
            holder.userName.setOnClickListener(listener);
            holder.inviteButton.setOnClickListener(listener);
        }

        return convertView;
    }

    private void callContact(View v, Contact contact) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + PhoneNumberNormaliser.toLocalFormat(contact.phoneNumber)));
        v.getContext().startActivity(intent);
    }

    public void refill(List<Contact> contacts) {
        this.contacts = contacts;
        notifyDataSetChanged();
    }

    private void handleClick(View view, Contact contact) {
        int id = view.getId();

        if (contact.isRegisteredUser) {
            if (id == R.id.iv_display_picture) {
                UiHelpers.gotoProfileActivity(view.getContext(), contact.numberInIEE_Format);
            } else if (id == R.id.tv_user_phone) {
                callContact(view, contact);
            }
        } else {
            if (id == R.id.bt_invite) {
                invite(contact.phoneNumber);
            } else if (id == R.id.tv_user_status || id == R.id.tv_user_phone) {
                callContact(view, contact);
            }
        }
    }

    private void invite(String phoneNumber) {
        String message = "Try out PAIRAPP messenger for android . Its free and fast!\\n download here: http://pairapp.com/download";
        SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null);
    }

    private class ViewHolder {
        private TextView userName,
                userStatus;
        private Button inviteButton;
        private ImageView userDp;
        private TextView userPhone;
    }

//    private class InviteContact implements View.OnClickListener {
//        Contact contact;
//
//        public InviteContact(Contact contact) {
//            this.contact = contact;
//        }
//
//        @Override
//        public void onClick(View v) {
//            final UiHelpers.Listener listener = new UiHelpers.Listener() {
//                @Override
//                public void onClick() {
//
//                }
//            };
//            UiHelpers.showErrorDialog(context, R.string.charges_may_apply, android.R.string.ok, android.R.string.cancel, listener, null);
////            Uri uri = Uri.parse("sms:"+contact.phoneNumber);
////            Intent intent = new Intent(Intent.ACTION_SENDTO);
////            intent.setData(uri);
////            intent.putExtra(Intent.EXTRA_TEXT, message);
////            v.getContext().startActivity(intent);
//        }
//
//    }


    private void addToContacts(Context context, Contact contact) {
        Intent intent = new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT);
        intent.setData(Uri.parse("tel:" + PhoneNumberNormaliser.toLocalFormat(contact.phoneNumber)));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // TODO: 8/23/2015 should we tell the user or is it that our intent was wrongly targeted?
        }

    }
    private final int[] layoutResource = {
            R.layout.registered_contact_item,
            R.layout.unregistered_contact_item,
            android.R.layout.simple_list_item_checked
    };

}
