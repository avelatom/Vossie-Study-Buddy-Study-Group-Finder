package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.v2v.app.R;
import com.v2v.app.adapter.SkillAdapter;
import com.v2v.app.model.SkillEntry;
import com.v2v.app.util.ApiCallback;
import com.v2v.app.util.Avatar;
import com.v2v.app.util.Json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// FR09 (view a peer's profile) and FR10 (connect / accept / decline / message)
public class PeerProfileFragment extends BaseFragment {

    private int userId;
    private String peerName = "";
    private int connectionId;

    private View content, progress, aboutCard, skillsCard;
    private TextView avatar, name, campus, rating, limited, bio;
    private RecyclerView skillsList;
    private MaterialButton primary, secondary, feedback;
    private int myRating; // FR16 - stars I already gave this student (0 = none yet)
    private String myComment = "";
    private SkillAdapter skillAdapter;

    public PeerProfileFragment() {
        super(R.layout.fragment_peer_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userId = getArguments() == null ? 0 : getArguments().getInt("user_id");

        content = view.findViewById(R.id.peer_content);
        progress = view.findViewById(R.id.peer_progress);
        name = view.findViewById(R.id.peer_name);
        campus = view.findViewById(R.id.peer_campus);
        rating = view.findViewById(R.id.peer_rating);
        limited = view.findViewById(R.id.peer_limited);
        bio = view.findViewById(R.id.peer_bio);
        avatar = view.findViewById(R.id.peer_avatar);
        aboutCard = view.findViewById(R.id.peer_about_card);
        skillsCard = view.findViewById(R.id.peer_skills_card);
        skillsList = view.findViewById(R.id.peer_skills);
        primary = view.findViewById(R.id.peer_button_primary);
        secondary = view.findViewById(R.id.peer_button_secondary);
        feedback = view.findViewById(R.id.peer_button_feedback);
        feedback.setOnClickListener(v -> openFeedback());

        skillAdapter = new SkillAdapter(null); // read-only list
        skillsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        skillsList.setAdapter(skillAdapter);

        load();
    }

    // GET profile/get.php?user_id= - what comes back depends on their privacy
    // setting
    private void load() {
        show(progress, true);
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(userId));

        api().get("profile/get.php", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);
                JSONObject p = json.optJSONObject("profile");
                if (p != null)
                    render(p);
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

    // Fill in the screen
    private void render(JSONObject p) {
        peerName = Json.str(p, "full_name", "");
        connectionId = p.optInt("connection_id", 0);
        String relation = Json.str(p, "relation", "none");
        boolean full = p.optBoolean("is_full", false);
        myRating = p.optInt("my_rating", 0);
        myComment = Json.str(p, "my_comment", "");

        // --- header ---
        main().setToolbarTitle(peerName);
        Avatar.bind(avatar, peerName, userId);
        name.setText(peerName);
        campus.setText(Json.str(p, "campus_location", "") + " campus");

        // Average rating (FR16) - full profile only and only if they've been rated
        double r = Json.dbl(p, "aggregate_rating");
        if (full && r >= 0) {
            rating.setText("\u2605 " + String.format(Locale.US, "%.1f", r) + " average rating");
            show(rating, true);
        } else {
            show(rating, false);
        }

        // Private / connections only -> show the note
        show(limited, !full);

        // --- About card (hidden when there is no bio) ---
        String b = Json.str(p, "bio");
        if (full && b != null && !b.isEmpty()) {
            bio.setText(b);
            show(aboutCard, true);
        } else {
            show(aboutCard, false);
        }

        // --- Modules card ---

        List<SkillEntry> skills = new ArrayList<>();
        JSONArray arr = p.optJSONArray("skills");
        if (full && arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null)
                    skills.add(SkillEntry.fromJson(o));
            }
        }
        skillAdapter.setItems(skills);
        show(skillsCard, !skills.isEmpty());

        configureButtons(relation);
        show(content, true);
    }

    // Set the buttons depending on our connection (FR10)
    private void configureButtons(String relation) {
        primary.setEnabled(true);
        primary.setIcon(null);
        show(primary, true);
        show(secondary, false);
        show(feedback, false);

        if ("connected".equals(relation)) {
            primary.setText("Message");
            primary.setIconResource(R.drawable.ic_chat);
            primary.setOnClickListener(v -> openChatWith(userId, peerName));
            // FR16 - connected students can rate each other
            feedback.setText(myRating > 0 ? "Your rating: " + myRating + " \u2605  \u00b7  Change" : "Give feedback");
            show(feedback, true);

        } else if ("pending_sent".equals(relation)) {
            primary.setText("Request sent");
            primary.setEnabled(false);

        } else if ("pending_received".equals(relation)) {
            primary.setText("Accept request");
            primary.setOnClickListener(v -> respond("accept"));
            show(secondary, true);
            secondary.setOnClickListener(v -> respond("decline"));

        } else {
            primary.setText("Send connection request");
            primary.setIconResource(R.drawable.ic_add);
            primary.setOnClickListener(v -> sendRequest());
        }
    }

    // FR16 - open the feedback screen with any old rating filled in
    private void openFeedback() {
        Bundle args = new Bundle();
        args.putInt("user_id", userId);
        args.putString("name", peerName);
        args.putInt("my_rating", myRating);
        args.putString("my_comment", myComment);
        nav().navigate(R.id.feedbackFragment, args);
    }

    // POST connections/send.php (FR10).
    private void sendRequest() {
        primary.setEnabled(false);
        api().post("connections/send.php", body("receiver_id", userId), new ApiCallback() {
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
                primary.setEnabled(true);
                toast(message);
                load(); // the relationship may have changed (e.g. they requested me first)
            }
        });
    }

    // POST connections/respond.php - accept / decline (FR10)
    private void respond(String action) {
        primary.setEnabled(false);
        secondary.setEnabled(false);
        api().post("connections/respond.php", body("connection_id", connectionId, "action", action), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                secondary.setEnabled(true);
                toast(json.optString("message"));
                load();
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                secondary.setEnabled(true);
                toast(message);
                load();
            }
        });
    }
}
