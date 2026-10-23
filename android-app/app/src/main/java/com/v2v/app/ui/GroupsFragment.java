package com.v2v.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.v2v.app.R;
import com.v2v.app.adapter.GroupAdapter;
import com.v2v.app.model.GroupItem;
import com.v2v.app.model.Module;
import com.v2v.app.util.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// FR11 (create a study group) and FR12 (search for study groups)
public class GroupsFragment extends BaseFragment {

    private TextInputEditText query;
    private Spinner moduleSpinner;
    private CheckBox mineOnly;
    private RecyclerView list;
    private View empty, progress;
    private GroupAdapter adapter;
    private final List<Module> modules = new ArrayList<>();

    public GroupsFragment() {
        super(R.layout.fragment_groups);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        query = view.findViewById(R.id.groups_query);
        moduleSpinner = view.findViewById(R.id.groups_module);
        mineOnly = view.findViewById(R.id.groups_mine_only);
        list = view.findViewById(R.id.groups_list);
        empty = view.findViewById(R.id.groups_empty);
        progress = view.findViewById(R.id.groups_progress);

        adapter = new GroupAdapter(group -> openGroup(group.groupId));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        fillModuleSpinner();
        loadModules();

        view.findViewById(R.id.groups_search_button).setOnClickListener(v -> search());
        view.findViewById(R.id.groups_create_button).setOnClickListener(v -> showCreateDialog());
        query.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        search(); // refresh every time (e.g. after joining or leaving a group)
    }

    // Opens the detail screen for one group (GroupDetailFragment).
    private void openGroup(int groupId) {
        Bundle args = new Bundle();
        args.putInt("group_id", groupId);
        nav().navigate(R.id.groupDetailFragment, args);
    }

    // "Any module" + every module from the server
    private void fillModuleSpinner() {
        List<String> labels = new ArrayList<>();
        labels.add("Any module");
        for (Module m : modules) {
            labels.add(m.toString());
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        moduleSpinner.setAdapter(a);
    }

    // GET modules/list.php for the filter + New group form
    private void loadModules() {
        api().get("modules/list.php", null, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                modules.clear();
                JSONArray arr = json.optJSONArray("modules");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null)
                            modules.add(Module.fromJson(o));
                    }
                }
                fillModuleSpinner();
            }

            @Override
            public void onError(String message) {
                if (alive())
                    toast(message);
            }
        });
    }

    // GET groups/list.php with the current filters
    private void search() {
        hideKeyboard();
        Map<String, String> params = new HashMap<>();
        String q = text(query);
        if (!q.isEmpty())
            params.put("q", q);
        int pos = moduleSpinner.getSelectedItemPosition();
        if (pos > 0 && pos - 1 < modules.size()) {
            params.put("module_id", String.valueOf(modules.get(pos - 1).id));
        }
        if (mineOnly.isChecked())
            params.put("scope", "mine");

        show(progress, adapter.getItemCount() == 0);
        api().get("groups/list.php", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);
                List<GroupItem> groups = new ArrayList<>();
                JSONArray arr = json.optJSONArray("groups");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null)
                            groups.add(GroupItem.fromJson(o));
                    }
                }
                adapter.setItems(groups);
                show(empty, groups.isEmpty());
                show(list, !groups.isEmpty());
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

    // FR11 - the "New group" form
    private void showCreateDialog() {
        if (modules.isEmpty()) {
            toast("Modules are still loading (or none exist yet). Please try again in a moment.");
            return;
        }
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_group, null);
        final TextInputEditText nameInput = dialogView.findViewById(R.id.dlg_group_name);
        final Spinner moduleInput = dialogView.findViewById(R.id.dlg_group_module);
        final TextInputEditText descInput = dialogView.findViewById(R.id.dlg_group_description);
        final TextInputEditText maxInput = dialogView.findViewById(R.id.dlg_group_max);

        ArrayAdapter<Module> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, modules);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        moduleInput.setAdapter(a);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New study group")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = text(nameInput);
            if (name.length() < 3) {
                toast("Group name must be at least 3 characters");
                return;
            }
            int max;
            try {
                max = Integer.parseInt(text(maxInput));
            } catch (NumberFormatException e) {
                toast("Maximum members must be a number between 2 and 30");
                return;
            }
            if (max < 2 || max > 30) {
                toast("Maximum members must be between 2 and 30");
                return;
            }
            Module module = modules.get(moduleInput.getSelectedItemPosition());

            api().post("groups/create.php",
                    body("group_name", name, "module_id", module.id, "description", text(descInput), "max_members",
                            max),
                    new ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject json) {
                            dialog.dismiss();
                            if (!alive())
                                return;
                            toast(json.optString("message"));
                            JSONObject g = json.optJSONObject("group");
                            if (g != null) {
                                openGroup(g.optInt("group_id"));
                            } else {
                                search();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (alive())
                                toast(message);
                        }
                    });
        });
    }
}
