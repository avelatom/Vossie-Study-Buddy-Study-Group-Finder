package com.v2v.app.model;

import com.v2v.app.util.Json;

import org.json.JSONObject;

public class ConnectionItem {
    public int connectionId;
    public int userId;
    public String fullName;
    public String campus;

    public static ConnectionItem fromJson(JSONObject o) {
        ConnectionItem c = new ConnectionItem();
        c.connectionId = o.optInt("connection_id");
        c.userId = o.optInt("user_id");
        c.fullName = Json.str(o, "full_name", "");
        c.campus = Json.str(o, "campus_location", "");
        return c;
    }
}
