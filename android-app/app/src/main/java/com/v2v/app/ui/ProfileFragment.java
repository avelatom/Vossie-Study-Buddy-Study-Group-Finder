package com.v2v.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.v2v.app.R;
import com.v2v.app.util.ApiCallback;
import com.v2v.app.util.Avatar;
import com.v2v.app.util.Json;
import com.v2v.app.util.Labels;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// FR03 (profile) and FR18 (account management / log out)
public class ProfileFragment extends BaseFragment {

    private static final String[] VISIBILITY_VALUES = { "public", "connections_only", "private" };

    private View content, progress;
    private TextView avatar, headerName, studentNumber, email, campus, connections, statModules, statRating;
    private TextInputEditText fullName, phone, bio;
    private Spinner visibility;
    private MaterialButton saveButton;
    private int myUserId;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // header
        content = view.findViewById(R.id.prof_content);
        progress = view.findViewById(R.id.prof_progress);
        avatar = view.findViewById(R.id.prof_avatar);
        headerName = view.findViewById(R.id.prof_header_name);
        studentNumber = view.findViewById(R.id.prof_student_number);
        connections = view.findViewById(R.id.prof_connections);
        statModules = view.findViewById(R.id.prof_stat_modules);
        statRating = view.findViewById(R.id.prof_stat_rating);

        // personal details card
        email = view.findViewById(R.id.prof_email);
        campus = view.findViewById(R.id.prof_campus);
        fullName = view.findViewById(R.id.prof_full_name);
        phone = view.findViewById(R.id.prof_phone);
        bio = view.findViewById(R.id.prof_bio);
        visibility = view.findViewById(R.id.prof_visibility);
        saveButton = view.findViewById(R.id.prof_save);

        // Visibility drop-down shows friendly text for each database value
        String[] labels = new String[VISIBILITY_VALUES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Labels.visibility(VISIBILITY_VALUES[i]);
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        visibility.setAdapter(a);

        // buttons
        saveButton.setOnClickListener(v -> save());
        view.findViewById(R.id.prof_skills_button).setOnClickListener(v -> nav().navigate(R.id.skillsFragment));
        view.findViewById(R.id.prof_password_button).setOnClickListener(v -> showPasswordDialog());
        view.findViewById(R.id.prof_logout_button).setOnClickListener(v -> logout());

        load();
    }

    // GET profile/get.php (my own profile) and fill in the screen
    private void load() {
        show(progress, true);
        api().get("profile/get.php", null, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);
                JSONObject p = json.optJSONObject("profile");
                if (p == null)
                    return;

                String name = Json.str(p, "full_name", "");
                myUserId = p.optInt("user_id");

                // Header: avatar, name, student number and stats
                Avatar.bind(avatar, name, myUserId);
                headerName.setText(name);
                studentNumber.setText(Json.str(p, "student_number", ""));
                connections.setText(String.valueOf(p.optInt("connections", 0)));
                statModules.setText(String.valueOf(countModules(p.optJSONArray("skills"))));
                double rating = Json.dbl(p, "aggregate_rating");
                statRating.setText(rating >= 0 ? String.format(Locale.US, "%.1f", rating) : "–");

                // Read-only details
                email.setText(Json.str(p, "email", ""));
                campus.setText(Json.str(p, "campus_location", "") + " campus");

                // Editable fields
                fullName.setText(name);
                phone.setText(Json.str(p, "phone_number", ""));
                bio.setText(Json.str(p, "bio", ""));

                String vis = Json.str(p, "profile_visibility", "public");
                for (int i = 0; i < VISIBILITY_VALUES.length; i++) {
                    if (VISIBILITY_VALUES[i].equals(vis)) {
                        visibility.setSelection(i);
                    }
                }
                show(content, true);
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

    // Number of different modules across my 3 lists
    private static int countModules(JSONArray skills) {
        Set<Integer> ids = new HashSet<>();
        if (skills != null) {
            for (int i = 0; i < skills.length(); i++) {
                JSONObject s = skills.optJSONObject(i);
                if (s != null)
                    ids.add(s.optInt("module_id"));
            }
        }
        return ids.size();
    }

    // POST account/update.php action=update_profile
    private void save() {
        hideKeyboard();
        final String name = text(fullName);
        String phoneNumber = text(phone);

        // Quick check, server validates again
        if (name.isEmpty() || phoneNumber.isEmpty()) {
            toast("Name and phone number cannot be empty");
            return;
        }
        saveButton.setEnabled(false);

        api().post("account/update.php", body(
                "action", "update_profile",
                "full_name", name,
                "phone_number", phoneNumber,
                "bio", text(bio),
                "profile_visibility", VISIBILITY_VALUES[visibility.getSelectedItemPosition()]), new ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        if (!alive())
                            return;
                        saveButton.setEnabled(true);
                        session().setName(name); // keeps the "Hi, ..." greeting up to date
                        headerName.setText(name);
                        Avatar.bind(avatar, name, myUserId);
                        toast(json.optString("message"));
                    }

                    @Override
                    public void onError(String message) {
                        if (!alive())
                            return;
                        saveButton.setEnabled(true);
                        toast(message);
                    }
                });
    }

    // FR18 - change password dialog
    private void showPasswordDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null);
        final TextInputEditText current = dialogView.findViewById(R.id.dlg_current);
        final TextInputEditText newPass = dialogView.findViewById(R.id.dlg_new);
        final TextInputEditText confirm = dialogView.findViewById(R.id.dlg_confirm);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change password")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Change", null) // set below so the dialog stays open on errors
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cur = raw(current);
            String np = raw(newPass);
            String cf = raw(confirm);

            if (cur.isEmpty() || np.isEmpty()) {
                toast("Please fill in all fields");
                return;
            }
            if (np.length() < 8) {
                toast("New password must be at least 8 characters long");
                return;
            }
            if (!np.equals(cf)) {
                toast("The new passwords do not match");
                return;
            }
            api().post("account/update.php",
                    body("action", "update_password", "current_password", cur, "new_password", np),
                    new ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject json) {
                            dialog.dismiss();
                            if (alive())
                                toast(json.optString("message"));
                        }

                        @Override
                        public void onError(String message) {
                            if (alive())
                                toast(message);
                        }
                    });
        });
    }

    // FR18 - log out (server revokes the token, then clear the session)
    private void logout() {
        api().post("auth/logout.php", null, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                finishLogout();
            }

            @Override
            public void onError(String message) {
                finishLogout(); // even if the server cannot be reached, leave this device logged out
            }
        });
    }

    private void finishLogout() {
        if (getContext() == null)
            return;
        session().clear();
        main().goToLogin();
    }
}
