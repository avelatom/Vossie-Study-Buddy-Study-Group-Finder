package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.v2v.app.R;
import com.v2v.app.model.GroupItem;
import com.v2v.app.util.ApiCallback;
import com.v2v.app.util.Json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

// FR12 (view and join a study group) and US07 (leave a study group)
public class GroupDetailFragment extends BaseFragment {

    private int groupId;
    private View content, progress, memberNote, aboutCard, membersCard;
    private TextView name, module, creator, capacity, description, members;
    private LinearProgressIndicator fill;
    private MaterialButton actionButton;

    public GroupDetailFragment() {
        super(R.layout.fragment_group_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupId = getArguments() == null ? 0 : getArguments().getInt("group_id");

        content = view.findViewById(R.id.group_content);
        progress = view.findViewById(R.id.group_progress);
        memberNote = view.findViewById(R.id.group_member_note);
        name = view.findViewById(R.id.group_name);
        module = view.findViewById(R.id.group_module);
        creator = view.findViewById(R.id.group_creator);
        capacity = view.findViewById(R.id.group_capacity);
        description = view.findViewById(R.id.group_description);
        aboutCard = view.findViewById(R.id.group_about_card);
        membersCard = view.findViewById(R.id.group_members_card);
        fill = view.findViewById(R.id.group_fill);
        members = view.findViewById(R.id.group_members);
        actionButton = view.findViewById(R.id.group_action_button);

        load();
    }

    // GET groups/get.php - details + members if I'm in the group
    private void load() {
        show(progress, true);
        Map<String, String> params = new HashMap<>();
        params.put("group_id", String.valueOf(groupId));
        api().get("groups/get.php", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);
                JSONObject g = json.optJSONObject("group");
                if (g != null)
                    render(GroupItem.fromJson(g), json.optJSONArray("members"));
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                show(progress, false);
                toast(message);
            }
        });
    }

    // Fill in the screen + set the Join / Leave button
    private void render(final GroupItem g, @Nullable JSONArray memberArray) {
        main().setToolbarTitle(g.name);
        name.setText(g.name);
        module.setText(g.moduleCode + " \u2013 " + g.moduleName);
        creator.setText("Created by " + g.creatorName);

        // "3 of 5 members" + how full bar
        int spaces = Math.max(0, g.maxMembers - g.memberCount);
        capacity.setText(g.memberCount + " of " + g.maxMembers + " members"
                + (spaces > 0 ? "  \u00b7  " + spaces + (spaces == 1 ? " space left" : " spaces left") : ""));
        fill.setMax(Math.max(1, g.maxMembers));
        fill.setProgress(g.memberCount);

        // Only show About if there's a description
        if (g.description != null && !g.description.isEmpty()) {
            description.setText(g.description);
            show(aboutCard, true);
        } else {
            show(aboutCard, false);
        }

        if (g.isMember) {
            StringBuilder sb = new StringBuilder();
            if (memberArray != null) {
                for (int i = 0; i < memberArray.length(); i++) {
                    JSONObject m = memberArray.optJSONObject(i);
                    if (m == null)
                        continue;
                    if (sb.length() > 0)
                        sb.append("\n");
                    sb.append("\u2022  ").append(Json.str(m, "full_name", ""))
                            .append(" (").append(Json.str(m, "campus_location", "")).append(")");
                    if ("creator".equals(Json.str(m, "member_role"))) {
                        sb.append(" \u2013 creator");
                    }
                }
            }
            // Members can see who's in the group
            members.setText(sb.toString());
            show(membersCard, true);
            show(memberNote, false);

            actionButton.setEnabled(true);
            actionButton.setText("Leave group");
            actionButton.setOnClickListener(v -> confirmLeave());
        } else {
            // Not a member - show a note instead of the list
            show(membersCard, false);
            show(memberNote, true);

            if (g.isFull) {
                actionButton.setText("Group is full");
                actionButton.setEnabled(false);
            } else {
                actionButton.setText("Join group");
                actionButton.setEnabled(true);
                actionButton.setOnClickListener(v -> join());
            }
        }
        show(content, true);
    }

    // POST groups/join.php (server checks if it's full)
    private void join() {
        actionButton.setEnabled(false);
        api().post("groups/join.php", body("group_id", groupId), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                toast(json.optString("message"));
                load();
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                toast(message);
                load(); // e.g. the group just became full
            }
        });
    }

    // Asks "Leave this group?" before leaving.
    private void confirmLeave() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Leave this group?")
                .setMessage("You will no longer see the group's members.")
                .setNegativeButton("Stay", null)
                .setPositiveButton("Leave", (d, which) -> leave())
                .show();
    }

    // POST groups/leave.php, then go back to the group list.
    private void leave() {
        actionButton.setEnabled(false);
        api().post("groups/leave.php", body("group_id", groupId), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                toast(json.optString("message"));
                nav().popBackStack();
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                actionButton.setEnabled(true);
                toast(message);
            }
        });
    }
}
