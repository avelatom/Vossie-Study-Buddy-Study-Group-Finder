package com.v2v.app.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.v2v.app.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Talks to the PHP API. Requests run on a background thread,
// results come back to the ApiCallback on the main thread
public class ApiClient {

    private static final int TIMEOUT_MS = 15000;

    private static ApiClient instance;
    private static Runnable unauthorizedListener;

    private final Context appContext;
    private final String baseUrl;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized ApiClient get(Context context) {
        if (instance == null) {
            instance = new ApiClient(context.getApplicationContext());
        }
        return instance;
    }

    // Set by MainActivity so an expired session goes back to login
    public static void setUnauthorizedListener(Runnable listener) {
        unauthorizedListener = listener;
    }

    private ApiClient(Context context) {
        this.appContext = context;
        String url = context.getString(R.string.api_base_url).trim();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        this.baseUrl = url;
    }

    // GET request
    public void get(String path, Map<String, String> params, ApiCallback callback) {
        execute("GET", path, params, null, callback);
    }

    // POST request with a JSON body

    public void post(String path, JSONObject body, ApiCallback callback) {
        execute("POST", path, null, body == null ? new JSONObject() : body, callback);
    }

    private void execute(final String method, final String path, final Map<String, String> params,
            final JSONObject body, final ApiCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    // 1. URL = base address from config.xml + endpoint + ?query for GET
                    StringBuilder url = new StringBuilder(baseUrl).append(path);
                    if (params != null && !params.isEmpty()) {
                        url.append('?');
                        boolean first = true;
                        for (Map.Entry<String, String> e : params.entrySet()) {
                            if (!first) {
                                url.append('&');
                            }
                            first = false;
                            url.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                                    .append('=')
                                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                        }
                    }

                    // 2. Open the connection
                    conn = (HttpURLConnection) new URL(url.toString()).openConnection();
                    conn.setRequestMethod(method);
                    conn.setConnectTimeout(TIMEOUT_MS);
                    conn.setReadTimeout(TIMEOUT_MS);
                    conn.setRequestProperty("Accept", "application/json");

                    // 3. Send the login token (JWT)
                    final String token = SessionManager.get(appContext).getToken();
                    if (token != null) {
                        // sent twice: some hosts strip the standard Authorization header
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                        conn.setRequestProperty("X-Auth-Token", token);
                    }

                    // 4. POST requests send their data as JSON
                    if (body != null) {
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        OutputStream os = conn.getOutputStream();
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                        os.close();
                    }

                    // 5. Read the response (errors use the same success + message JSON)
                    final int status = conn.getResponseCode();
                    InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    String text = readAll(is);

                    final JSONObject json;
                    try {
                        json = new JSONObject(text);
                    } catch (JSONException e) {
                        postError(callback, "The server sent an unexpected response (" + status + ").", status, null);
                        return;
                    }

                    boolean accountBlocked = status == 403 && json.optBoolean("account_inactive", false);
                    if ((status == 401 || accountBlocked) && token != null) {
                        // token not valid anymore (expired, logged out or suspended)
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                SessionManager.get(appContext).clear();
                                if (unauthorizedListener != null) {
                                    unauthorizedListener.run();
                                }
                            }
                        });
                        return;
                    }

                    // 6. Pass the result back on the main thread
                    if (status < 400 && json.optBoolean("success", false)) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(json);
                            }
                        });
                    } else {
                        postError(callback, json.optString("message", "Request failed"), status, json);
                    }

                } catch (IOException e) {
                    postError(callback, "Cannot reach the server. Check your internet connection and try again.", -1,
                            null);
                } catch (Exception e) {
                    postError(callback, "Something went wrong: " + e.getMessage(), -1, null);
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    private void postError(final ApiCallback cb, final String message, final int status, final JSONObject body) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onErrorDetail(message, status, body);
            }
        });
    }

    // Reads the whole response body as text.
    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        is.close();
        return out.toString("UTF-8");
    }
}
