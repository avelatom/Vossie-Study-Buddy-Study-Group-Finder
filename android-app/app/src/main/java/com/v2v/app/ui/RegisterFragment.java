package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavOptions;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.v2v.app.R;
import com.v2v.app.util.ApiCallback;

import org.json.JSONObject;

import java.util.Locale;

// FR01 - Registration
public class RegisterFragment extends BaseFragment {

    private TextInputEditText studentNumber, fullName, email, phone, password, confirm;
    private Spinner campus;
    private MaterialButton registerButton;
    private View progress;

    public RegisterFragment() {
        super(R.layout.fragment_register);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        studentNumber = view.findViewById(R.id.reg_student_number);
        fullName = view.findViewById(R.id.reg_full_name);
        email = view.findViewById(R.id.reg_email);
        phone = view.findViewById(R.id.reg_phone);
        password = view.findViewById(R.id.reg_password);
        confirm = view.findViewById(R.id.reg_confirm);
        campus = view.findViewById(R.id.reg_campus);
        registerButton = view.findViewById(R.id.reg_button);
        progress = view.findViewById(R.id.reg_progress);

        ArrayAdapter<String> campusAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"Tygervalley", "Mowbray"});
        campusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        campus.setAdapter(campusAdapter);

        registerButton.setOnClickListener(v -> attemptRegister());
        // Back to login
        view.findViewById(R.id.reg_login_link).setOnClickListener(v -> nav().navigateUp());
    }

    // Disable the button + show progress while loading
    private void setBusy(boolean busy) {
        registerButton.setEnabled(!busy);
        show(progress, busy);
    }

    // Check the form, POST auth/register.php, then open the verify screen
    private void attemptRegister() {
        hideKeyboard();

        final String sn = text(studentNumber).toUpperCase(Locale.ROOT);
        final String name = text(fullName);
        final String mail = text(email).toLowerCase(Locale.ROOT);
        final String phoneNumber = text(phone);
        final String pass = raw(password);
        final String pass2 = raw(confirm);
        final String campusValue = (String) campus.getSelectedItem();

        if (sn.isEmpty() || name.isEmpty() || mail.isEmpty() || phoneNumber.isEmpty() || pass.isEmpty()) {
            toast("Please fill in all fields");
            return;
        }
        if (!mail.endsWith("@vossie.net")) {
            toast("Please use your @vossie.net email address");
            return;
        }
        if (pass.length() < 8) {
            toast("Password must be at least 8 characters long");
            return;
        }
        if (!pass.equals(pass2)) {
            toast("Passwords do not match");
            return;
        }

        setBusy(true);
        api().post("auth/register.php",
                body("student_number", sn, "full_name", name, "email", mail, "phone_number", phoneNumber,
                        "password", pass, "campus_location", campusValue),
                new ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        if (!alive()) return;
                        setBusy(false);
                        toast(json.optString("message"));

                        if ("auto".equals(json.optString("verification_mode"))) {
                            nav().popBackStack();                // straight back to the login screen
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putString("email", mail);
                        if (json.has("verification_code")) {
                            args.putString("verification_code", json.optString("verification_code"));
                        }
                        NavOptions opts = new NavOptions.Builder().setPopUpTo(R.id.registerFragment, true).build();
                        nav().navigate(R.id.verifyFragment, args, opts);
                    }

                    @Override
                    public void onError(String message) {
                        if (!alive()) return;
                        setBusy(false);
                        toast(message);
                    }
                });
    }
}
