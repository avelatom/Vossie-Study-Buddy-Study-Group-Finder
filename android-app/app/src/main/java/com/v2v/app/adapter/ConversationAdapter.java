package com.v2v.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.v2v.app.R;
import com.v2v.app.model.ConversationItem;
import com.v2v.app.util.Avatar;
import com.v2v.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

// FR13 - my chats on the Messages tab
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {

    public interface Listener {
        void onOpen(ConversationItem item);
    }

    private final List<ConversationItem> items = new ArrayList<>();
    private final Listener listener;

    public ConversationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<ConversationItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final ConversationItem c = items.get(position);
        Avatar.bind(h.avatar, c.otherName, c.otherUserId);
        h.name.setText(c.otherName);
        h.last.setText(c.lastMessage == null ? "No messages yet \u2013 say hello!" : c.lastMessage);
        h.time.setText(TimeUtil.shortTime(c.lastMessageAt));

        // Unread - blue badge + bold preview
        boolean hasUnread = c.unread > 0;
        h.unread.setText(c.unread > 99 ? "99+" : String.valueOf(c.unread));
        h.unread.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
        h.last.setTypeface(null, hasUnread ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        h.itemView.setOnClickListener(v -> listener.onOpen(c));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView avatar, name, last, time, unread;

        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.conv_avatar);
            name = v.findViewById(R.id.conv_name);
            last = v.findViewById(R.id.conv_last);
            time = v.findViewById(R.id.conv_time);
            unread = v.findViewById(R.id.conv_unread);
        }
    }
}
