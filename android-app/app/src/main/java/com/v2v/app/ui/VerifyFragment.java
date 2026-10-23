package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.v2v.app.R;
import com.v2v.app.util.ApiCallback;

import org.json.JSONObject;

// FR01 - Email verification
public class VerifyFragment extends BaseFragment {

    private String email = "";
    private TextView info, hint;
    private TextInputEditText codeInput;
    private MaterialButton verifyButton, resendButton;
    private View progress;

    public VerifyFragment() {
        super(R.layout.fragment_verify);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        info = view.findViewById(R.id.verify_info);
        hint = view.findViewById(R.id.verify_hint);
        codeInput = view.findViewById(R.id.verify_code);
        verifyButton = view.findViewById(R.id.verify_button);
        resendButton = view.findViewById(R.id.verify_resend);
        progress = view.findViewById(R.id.verify_progress);

        Bundle args = getArguments();
        if (args != null) {
            email = args.getString("email", "");
            showTestCode(args.getString("verification_code"));
        }
        info.setText("Enter the 6-digit code for " + email + ".");

        verifyButton.setOnClickListener(v -> verify());
        resendButton.setOnClickListener(v -> resend());
    }

    // Only sent back when the server is in "response" test mode
    private void showTestCode(@Nullable String code) {
        if (code == null || code.isEmpty()) {
            show(hint, false);
            return;
        }
        hint.setText("Test mode: no email is sent. Your code is " + code);
        show(hint, true);
        codeInput.setText(code);
    }

    // Disable the buttons + show progress while loading
    private void setBusy(boolean busy) {
        verifyButton.setEnabled(!busy);
        resendButton.setEnabled(!busy);
        show(progress, busy);
    }

    // POST auth/verify.php
    private void verify() {
        hideKeyboard();
        String code = text(codeInput);
        if (code.isEmpty()) {
            toast("Please enter the verification code");
            return;
        }
        setBusy(true);
        api().post("auth/verify.php", body("email", email, "code", code), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                setBusy(false);
                toast(json.optString("message"));
                if (!nav().popBackStack(R.id.loginFragment, false)) {
                    nav().navigate(R.id.loginFragment);
                }
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                setBusy(false);
                toast(message);
            }
        });
    }

    // POST auth/resend_code.php (max one a minute)
    private void resend() {
        hideKeyboard();
        setBusy(true);
        api().post("auth/resend_code.php", body("email", email), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                setBusy(false);
                toast(json.optString("message"));
                showTestCode(json.has("verification_code") ? json.optString("verification_code") : null);
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                setBusy(false);
                toast(message);
            }
        });
    }
}
