package com.v2v.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

// Keeps track of who is logged in. Token is stored encrypted (NFR04)
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String FILE = "v2v_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "full_name";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    public static synchronized SessionManager get(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    private SessionManager(Context context) {
        SharedPreferences p;
        try {
            String masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            p = EncryptedSharedPreferences.create(
                    FILE + "_secure",
                    masterKey,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Rare (e.g. broken emulator keystore) - fall back so the app still works.
            Log.w(TAG, "Encrypted storage unavailable, using normal private storage", e);
            p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        }
        prefs = p;
    }

    public void saveSession(String token, int userId, String fullName) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_NAME, fullName)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public void setName(String name) {
        prefs.edit().putString(KEY_NAME, name).apply();
    }

    public boolean isLoggedIn() {
        String t = getToken();
        return t != null && !t.isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
