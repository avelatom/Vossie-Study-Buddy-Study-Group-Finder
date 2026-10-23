package com.v2v.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.v2v.app.MainActivity;
import com.v2v.app.R;
import com.v2v.app.util.ApiCallback;
import com.v2v.app.util.ApiClient;
import com.v2v.app.util.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

// Base class with shared helpers for every screen
public abstract class BaseFragment extends Fragment {

    protected BaseFragment(@LayoutRes int layoutRes) {
        super(layoutRes);
    }

    // Callbacks can come back after the user left the screen, so check this first
    protected boolean alive() {
        return isAdded() && getView() != null;
    }

    protected ApiClient api() {
        return ApiClient.get(requireContext());
    }

    protected SessionManager session() {
        return SessionManager.get(requireContext());
    }

    protected NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    protected MainActivity main() {
        return (MainActivity) requireActivity();
    }

    protected void toast(String message) {
        Context c = getContext();
        if (c != null && message != null) {
            Toast.makeText(c, message, Toast.LENGTH_LONG).show();
        }
    }

    protected void show(View v, boolean visible) {
        if (v != null) {
            v.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    // Trimmed text of an EditText / TextInputEditText.
    protected static String text(TextView t) {
        return t.getText() == null ? "" : t.getText().toString().trim();
    }

    // Un-trimmed text (use for passwords).
    protected static String raw(TextView t) {
        return t.getText() == null ? "" : t.getText().toString();
    }

    protected static JSONObject body(Object... keyValues) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < keyValues.length; i += 2) {
                o.put((String) keyValues[i], keyValues[i + 1]);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    protected void hideKeyboard() {
        View v = getView();
        Context c = getContext();
        if (v != null && c != null) {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
    }

    // Opens (or creates) the chat with a connected student
    protected void openChatWith(final int userId, final String name) {
        api().post("messages/open.php", body("user_id", userId), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                Bundle args = new Bundle();
                args.putInt("conversation_id", json.optInt("conversation_id"));
                args.putString("title", name);
                nav().navigate(R.id.chatFragment, args);
            }

            @Override
            public void onError(String message) {
                if (alive())
                    toast(message);
            }
        });
    }

    protected void openPeerProfile(int userId) {
        Bundle args = new Bundle();
        args.putInt("user_id", userId);
        nav().navigate(R.id.peerProfileFragment, args);
    }
}
