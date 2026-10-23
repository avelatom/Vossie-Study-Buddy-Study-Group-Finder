package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.v2v.app.R;
import com.v2v.app.adapter.PeerAdapter;
import com.v2v.app.model.PeerCard;
import com.v2v.app.util.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// FR08 - Suggested peers, strongest match first
public class HomeFragment extends BaseFragment {

    private PeerAdapter adapter;
    private RecyclerView list;
    private View emptyBox, progress;
    private TextView emptyText, subtitle;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        list = view.findViewById(R.id.home_list);
        emptyBox = view.findViewById(R.id.home_empty_box);
        emptyText = view.findViewById(R.id.home_empty_text);
        progress = view.findViewById(R.id.home_progress);
        subtitle = view.findViewById(R.id.home_subtitle);

        // "Hi, ....." from the saved session
        String name = session().getName();
        String first = (name == null || name.trim().isEmpty()) ? "there" : name.trim().split("\\s+")[0];
        ((TextView) view.findViewById(R.id.home_greeting)).setText("Hi, " + first);

        adapter = new PeerAdapter(peer -> openPeerProfile(peer.userId));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.home_setup_button).setOnClickListener(v -> nav().navigate(R.id.skillsFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        load(); // reload every time so changes to my modules show up straight away
    }

    // GET peers/matches.php - best match first
    private void load() {
        show(progress, adapter.getItemCount() == 0);
        api().get("peers/matches.php", null, new ApiCallback() {
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

                // Show how many students matched
                subtitle.setText(peers.isEmpty() ? "Students who match your modules and skills"
                        : peers.size() + (peers.size() == 1 ? " student matches" : " students match")
                                + " your modules and skills");

                boolean empty = peers.isEmpty();
                show(list, !empty);
                show(emptyBox, empty);
                if (empty) {
                    emptyText.setText(json.optString("message", "No matches yet."));
                }
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
}
