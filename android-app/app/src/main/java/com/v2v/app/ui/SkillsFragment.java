package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.v2v.app.R;
import com.v2v.app.adapter.SkillAdapter;
import com.v2v.app.model.Module;
import com.v2v.app.model.SkillEntry;
import com.v2v.app.util.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// FR04 / FR05 / FR06 - modules I study, can help with, need help with
public class SkillsFragment extends BaseFragment {

    private static final String[] LEVEL_LABELS = { "Beginner", "Intermediate", "Advanced" };
    private static final String[] LEVEL_VALUES = { "beginner", "intermediate", "advanced" };

    private Spinner moduleSpinner, levelSpinner;
    private RadioGroup typeGroup;
    private View levelLabel, emptyText, progress;
    private MaterialButton addButton;
    private RecyclerView list;
    private SkillAdapter adapter;
    private final List<Module> modules = new ArrayList<>();

    public SkillsFragment() {
        super(R.layout.fragment_skills);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        moduleSpinner = view.findViewById(R.id.skills_module);
        levelSpinner = view.findViewById(R.id.skills_level);
        typeGroup = view.findViewById(R.id.skills_type_group);
        levelLabel = view.findViewById(R.id.skills_level_label);
        emptyText = view.findViewById(R.id.skills_empty);
        progress = view.findViewById(R.id.skills_progress);
        addButton = view.findViewById(R.id.skills_add_button);
        list = view.findViewById(R.id.skills_list);

        ArrayAdapter<String> levels = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item,
                LEVEL_LABELS);
        levels.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        levelSpinner.setAdapter(levels);

        adapter = new SkillAdapter(this::confirmRemove);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        typeGroup.setOnCheckedChangeListener((group, checkedId) -> updateLevelVisibility());
        addButton.setOnClickListener(v -> add());
        updateLevelVisibility();

        loadModules();
        loadMine();
    }

    // Proficiency only for "I can help others" (FR05)
    private void updateLevelVisibility() {
        boolean offering = typeGroup.getCheckedRadioButtonId() == R.id.skills_type_offering;
        show(levelLabel, offering);
        show(levelSpinner, offering);
    }

    // GET modules/list.php
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
                ArrayAdapter<Module> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item,
                        modules);
                a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                moduleSpinner.setAdapter(a);
                if (modules.isEmpty()) {
                    toast("No modules have been added to the system yet.");
                }
            }

            @Override
            public void onError(String message) {
                if (alive())
                    toast(message);
            }
        });
    }

    // GET skills/list.php - my 3 lists
    private void loadMine() {
        show(progress, true);
        api().get("skills/list.php", null, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);
                List<SkillEntry> mine = new ArrayList<>();
                JSONArray arr = json.optJSONArray("skills");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null)
                            mine.add(SkillEntry.fromJson(o));
                    }
                }
                adapter.setItems(mine);
                show(emptyText, mine.isEmpty());
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

    // POST skills/add.php
    private void add() {
        if (modules.isEmpty() || moduleSpinner.getSelectedItemPosition() < 0) {
            toast("Please choose a module");
            return;
        }
        Module m = modules.get(moduleSpinner.getSelectedItemPosition());

        String type;
        int checked = typeGroup.getCheckedRadioButtonId();
        if (checked == R.id.skills_type_offering) {
            type = "offering";
        } else if (checked == R.id.skills_type_requiring) {
            type = "requiring";
        } else {
            type = "studying";
        }

        JSONObject payload = body("module_id", m.id, "skill_type", type);
        if ("offering".equals(type)) {
            try {
                payload.put("proficiency_level", LEVEL_VALUES[levelSpinner.getSelectedItemPosition()]);
            } catch (org.json.JSONException e) {
                throw new IllegalStateException(e);
            }
        }

        addButton.setEnabled(false);
        api().post("skills/add.php", payload, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                addButton.setEnabled(true);
                toast("Saved");
                loadMine();
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                addButton.setEnabled(true);
                toast(message);
            }
        });
    }

    // Confirm before removing
    private void confirmRemove(final SkillEntry entry) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove module?")
                .setMessage(entry.moduleCode + " will be removed from your profile.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, which) -> remove(entry))
                .show();
    }

    // POST skills/remove.php, then reload the list.
    private void remove(SkillEntry entry) {
        api().post("skills/remove.php", body("skill_id", entry.skillId), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                loadMine();
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                toast(message);
                loadMine();
            }
        });
    }
}
