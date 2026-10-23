package com.v2v.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.v2v.app.R;
import com.v2v.app.model.ConnectionItem;
import com.v2v.app.util.Avatar;
import com.v2v.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

// One list with 3 sections: requests received, requests sent, connected
public class ConnectionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Click handlers, implemented in ConnectionsFragment
    public interface Listener {
        void onOpenProfile(ConnectionItem item);

        void onAccept(ConnectionItem item);

        void onDecline(ConnectionItem item);

        void onMessage(ConnectionItem item);
    }

    // Row types: section header or a student card
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_INCOMING = 1;
    private static final int TYPE_OUTGOING = 2;
    private static final int TYPE_CONNECTED = 3;

    // One row - either a header or a student
    private static class Row {
        int type;
        String title;
        ConnectionItem item;
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public ConnectionsAdapter(Listener listener) {
        this.listener = listener;
    }

    // Builds the rows: header, students, header, students... (empty sections
    // skipped)
    public void setData(List<ConnectionItem> incoming, List<ConnectionItem> outgoing, List<ConnectionItem> connected) {
        rows.clear();
        addSection("Requests received", TYPE_INCOMING, incoming);
        addSection("Requests sent", TYPE_OUTGOING, outgoing);
        addSection("My connections", TYPE_CONNECTED, connected);
        notifyDataSetChanged();
    }

    private void addSection(String title, int type, List<ConnectionItem> list) {
        if (list.isEmpty()) {
            return;
        }
        Row h = new Row();
        h.type = TYPE_HEADER;
        h.title = title + "  \u00b7  " + list.size();
        rows.add(h);
        for (ConnectionItem c : list) {
            Row r = new Row();
            r.type = type;
            r.item = c;
            rows.add(r);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_section_header, parent, false));
        }
        return new ConnVH(inf.inflate(R.layout.item_connection, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);

        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).title.setText(row.title);
            return;
        }

        ConnVH h = (ConnVH) holder;
        final ConnectionItem c = row.item;
        Avatar.bind(h.avatar, c.fullName, c.userId);
        h.name.setText(c.fullName);
        h.campus.setText(c.campus);

        // Only show the buttons that fit this section
        h.status.setVisibility(View.GONE);
        h.buttons.setVisibility(View.GONE);
        h.message.setVisibility(View.GONE);

        if (row.type == TYPE_INCOMING) {
            h.buttons.setVisibility(View.VISIBLE);
        } else if (row.type == TYPE_OUTGOING) {
            Ui.chip(h.status, "Pending", R.color.v2v_light, R.color.v2v_navy_dark);
        } else if (row.type == TYPE_CONNECTED) {
            h.message.setVisibility(View.VISIBLE);
        }

        h.itemView.setOnClickListener(v -> listener.onOpenProfile(c));
        h.accept.setOnClickListener(v -> listener.onAccept(c));
        h.decline.setOnClickListener(v -> listener.onDecline(c));
        h.message.setOnClickListener(v -> listener.onMessage(c));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderVH(View v) {
            super(v);
            title = v.findViewById(R.id.section_title);
        }
    }

    static class ConnVH extends RecyclerView.ViewHolder {
        final TextView avatar, name, campus, status;
        final LinearLayout buttons;
        final MaterialButton accept, decline, message;

        ConnVH(View v) {
            super(v);
            avatar = v.findViewById(R.id.conn_item_avatar);
            name = v.findViewById(R.id.conn_item_name);
            campus = v.findViewById(R.id.conn_item_campus);
            status = v.findViewById(R.id.conn_item_status);
            buttons = v.findViewById(R.id.conn_item_buttons);
            accept = v.findViewById(R.id.conn_item_accept);
            decline = v.findViewById(R.id.conn_item_decline);
            message = v.findViewById(R.id.conn_item_message);
        }
    }
}
