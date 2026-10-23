package com.v2v.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.v2v.app.R;
import com.v2v.app.model.GroupItem;
import com.v2v.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

// FR11 / FR12 - group cards on the Groups tab
public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {

    public interface Listener {
        void onGroupClick(GroupItem group);
    }

    private final List<GroupItem> items = new ArrayList<>();
    private final Listener listener;

    public GroupAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<GroupItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final GroupItem g = items.get(position);
        h.name.setText(g.name);
        h.module.setText(g.moduleCode);
        h.moduleName.setText(g.moduleName);

        if (g.description != null && !g.description.isEmpty()) {
            h.description.setText(g.description);
            h.description.setVisibility(View.VISIBLE);
        } else {
            h.description.setVisibility(View.GONE);
        }

        // "3 / 5 members" + a bar showing how full it is
        h.info.setText(g.memberCount + " / " + g.maxMembers + " members");
        h.fill.setMax(Math.max(1, g.maxMembers));
        h.fill.setProgress(g.memberCount);

        // Joined / Full chip
        if (g.isMember) {
            Ui.chip(h.status, "Joined", R.color.v2v_green_light, R.color.v2v_green);
        } else if (g.isFull) {
            Ui.chip(h.status, "Full", R.color.v2v_red_light, R.color.v2v_red);
        } else {
            Ui.chip(h.status, null, 0, 0);
        }

        h.itemView.setOnClickListener(v -> listener.onGroupClick(g));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, module, moduleName, description, info, status;
        final com.google.android.material.progressindicator.LinearProgressIndicator fill;

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.group_item_name);
            module = v.findViewById(R.id.group_item_module);
            description = v.findViewById(R.id.group_item_description);
            info = v.findViewById(R.id.group_item_info);
            moduleName = v.findViewById(R.id.group_item_module_name);
            status = v.findViewById(R.id.group_item_status);
            fill = v.findViewById(R.id.group_item_fill);
        }
    }
}
