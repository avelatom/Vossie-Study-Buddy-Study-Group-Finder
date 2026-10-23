package com.v2v.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.v2v.app.R;
import com.v2v.app.model.SkillEntry;
import com.v2v.app.util.Labels;
import com.v2v.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

// Modules list. Remove button only shows on my own list (when a listener is passed in)
public class SkillAdapter extends RecyclerView.Adapter<SkillAdapter.VH> {

    public interface RemoveListener {
        void onRemove(SkillEntry entry);
    }

    private final List<SkillEntry> items = new ArrayList<>();
    private final RemoveListener removeListener;   // null = read-only (viewing someone else's profile)

    public SkillAdapter(RemoveListener removeListener) {
        this.removeListener = removeListener;
    }

    public void setItems(List<SkillEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_skill, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final SkillEntry s = items.get(position);
        h.title.setText(s.moduleCode + " – " + s.moduleName);

        // Different chip colour per list type
        String type = Labels.skillType(s.skillType);
        if ("offering".equals(s.skillType)) {
            Ui.chip(h.type, type, R.color.v2v_green_light, R.color.v2v_green);       // FR05 can help with
        } else if ("requiring".equals(s.skillType)) {
            Ui.chip(h.type, type, R.color.v2v_red_light, R.color.v2v_red);           // FR06 needs help with
        } else {
            Ui.chip(h.type, type, R.color.v2v_light, R.color.v2v_navy_dark);         // FR04 studying
        }

        // Proficiency only for "can help with"
        h.detail.setText(Labels.proficiency(s.proficiency));

        if (removeListener == null) {
            h.remove.setVisibility(View.GONE);
        } else {
            h.remove.setVisibility(View.VISIBLE);
            h.remove.setOnClickListener(view -> removeListener.onRemove(s));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, type, detail;
        final MaterialButton remove;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.skill_item_title);
            type = v.findViewById(R.id.skill_item_type);
            detail = v.findViewById(R.id.skill_item_detail);
            remove = v.findViewById(R.id.skill_item_remove);
        }
    }
}
