package com.v2v.app.util;

import org.json.JSONObject;

// Result handler for every network call. Always runs on the main (UI) thread.
public abstract class ApiCallback {

    public abstract void onSuccess(JSONObject json);

    public abstract void onError(String message);

    // Override if you need the status code or full response
    public void onErrorDetail(String message, int httpStatus, JSONObject body) {
        onError(message);
    }
}
