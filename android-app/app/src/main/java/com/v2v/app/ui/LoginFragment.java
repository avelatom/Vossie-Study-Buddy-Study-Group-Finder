package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.v2v.app.R;
import com.v2v.app.util.ApiCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

// FR02 - Login
public class LoginFragment extends BaseFragment {

    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private View progress;

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emailInput = view.findViewById(R.id.login_email);
        passwordInput = view.findViewById(R.id.login_password);
        loginButton = view.findViewById(R.id.login_button);
        progress = view.findViewById(R.id.login_progress);

        loginButton.setOnClickListener(v -> attemptLogin());
        view.findViewById(R.id.login_register_link).setOnClickListener(v -> nav().navigate(R.id.registerFragment));
    }

    // Disable the button + show progress while loading
    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        show(progress, busy);
    }

    // POST auth/login.php, save the token and go to Matches
    private void attemptLogin() {
        hideKeyboard();
        // Email or student number - server works out which
        final String login = text(emailInput);
        final String password = raw(passwordInput);

        if (login.isEmpty() || password.isEmpty()) {
            toast("Please enter your email or student number, and your password");
            return;
        }

        setBusy(true);
        api().post("auth/login.php", body("login", login, "password", password), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                setBusy(false);
                try {
                    JSONObject user = json.getJSONObject("user");
                    session().saveSession(json.getString("token"), user.getInt("user_id"), user.getString("full_name"));
                    main().goToHome();
                } catch (JSONException e) {
                    toast("Unexpected response from the server");
                }
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                setBusy(false);
                toast(message);
            }

            @Override
            public void onErrorDetail(String message, int status, JSONObject body) {
                if (alive() && body != null && body.optBoolean("needs_verification", false)) {
                    setBusy(false);
                    toast(message);
                    Bundle args = new Bundle();
                    args.putString("email", body.optString("email", login.toLowerCase(Locale.ROOT)));
                    nav().navigate(R.id.verifyFragment, args);
                    return;
                }
                super.onErrorDetail(message, status, body);
            }
        });
    }
}
