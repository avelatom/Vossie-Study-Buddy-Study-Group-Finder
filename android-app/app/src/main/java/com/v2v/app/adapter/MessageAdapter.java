package com.v2v.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.v2v.app.R;
import com.v2v.app.model.ChatMessage;
import com.v2v.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

    private static final int TYPE_MINE = 1;
    private static final int TYPE_THEIRS = 2;

    private final List<ChatMessage> items = new ArrayList<>();

    // Replaces everything (first load).
    public void setItems(List<ChatMessage> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    // Only adds messages we don't have yet. Returns how many were added
    public int append(List<ChatMessage> newItems) {
        int added = 0;
        int last = lastId();
        for (ChatMessage m : newItems) {
            if (m.id > last) {
                items.add(m);
                last = m.id;
                added++;
            }
        }
        if (added > 0) {
            notifyItemRangeInserted(items.size() - added, added);
        }
        return added;
    }

    // Newest message id (sent as after_id when polling)
    public int lastId() {
        return items.isEmpty() ? 0 : items.get(items.size() - 1).id;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).mine ? TYPE_MINE : TYPE_THEIRS;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == TYPE_MINE) ? R.layout.item_message_mine : R.layout.item_message_theirs;
        return new VH(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChatMessage m = items.get(position);
        h.text.setText(m.text);
        h.time.setText(TimeUtil.shortTime(m.createdAt));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text, time;

        VH(View v) {
            super(v);
            text = v.findViewById(R.id.message_text);
            time = v.findViewById(R.id.message_time);
        }
    }
}
