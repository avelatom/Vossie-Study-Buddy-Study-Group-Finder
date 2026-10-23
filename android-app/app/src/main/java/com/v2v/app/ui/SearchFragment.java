package com.v2v.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.v2v.app.R;
import com.v2v.app.adapter.PeerAdapter;
import com.v2v.app.model.Module;
import com.v2v.app.model.PeerCard;
import com.v2v.app.util.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// FR07 - Search for peers by module or skill
public class SearchFragment extends BaseFragment {

    private static final String[] TYPE_LABELS = { "Any role", "Can help with it", "Studying it", "Needs help with it" };
    private static final String[] TYPE_VALUES = { "", "offering", "studying", "requiring" };

    // Remember the last search
    private static final String PREFS = "search_preferences";
    private static final String KEY_LAST_QUERY = "last_query";

    private TextInputEditText query;
    private Spinner moduleSpinner, typeSpinner;
    private RecyclerView list;
    private View empty, progress;
    private PeerAdapter adapter;
    private final List<Module> modules = new ArrayList<>();

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        query = view.findViewById(R.id.search_query);
        moduleSpinner = view.findViewById(R.id.search_module);
        typeSpinner = view.findViewById(R.id.search_type);
        list = view.findViewById(R.id.search_list);
        empty = view.findViewById(R.id.search_empty);
        progress = view.findViewById(R.id.search_progress);

        adapter = new PeerAdapter(peer -> openPeerProfile(peer.userId));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, TYPE_LABELS);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        fillModuleSpinner(); // shows "Any module" until the real list arrives
        loadModules();

        view.findViewById(R.id.search_button).setOnClickListener(v -> search());
        query.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });

        query.setText(prefs().getString(KEY_LAST_QUERY, "")); // bring back the last search
        search(); // start by listing students
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

    // GET modules/list.php for the module filter.
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

    // GET peers/search.php with the current filters.
    private void search() {
        hideKeyboard();
        Map<String, String> params = new HashMap<>();

        String q = text(query);
        prefs().edit().putString(KEY_LAST_QUERY, q).apply();
        if (!q.isEmpty()) {
            params.put("q", q);
        }
        int modulePos = moduleSpinner.getSelectedItemPosition(); // 0 = any module
        if (modulePos > 0 && modulePos - 1 < modules.size()) {
            params.put("module_id", String.valueOf(modules.get(modulePos - 1).id));
        }
        String type = TYPE_VALUES[typeSpinner.getSelectedItemPosition()];
        if (!type.isEmpty()) {
            params.put("type", type);
        }

        show(progress, true);
        show(empty, false);
        api().get("peers/search.php", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);
                List<PeerCard> peers = new ArrayList<>();
                JSONArray arr = json.optJSONArray("peers");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null)
                            peers.add(PeerCard.fromJson(o));
                    }
                }
                adapter.setItems(peers);
                show(empty, peers.isEmpty());
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

    // Normal SharedPreferences, only stores the search text
    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
