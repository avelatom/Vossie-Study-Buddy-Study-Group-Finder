package com.v2v.app.util;

import org.json.JSONObject;

// Null safe JSON getters
public class Json {

    public static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) {
            return null;
        }
        return o.optString(key, null);
    }

    public static String str(JSONObject o, String key, String fallback) {
        String s = str(o, key);
        return s == null ? fallback : s;
    }

    // -1 when missing/null.
    public static double dbl(JSONObject o, String key) {
        if (o == null || o.isNull(key)) {
            return -1;
        }
        return o.optDouble(key, -1);
    }
}
