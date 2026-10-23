package com.v2v.app.model;

import com.v2v.app.util.Json;

import org.json.JSONObject;

public class ChatMessage {
    public int id;
    public int senderId;
    public String text;
    public String createdAt;
    public boolean mine;

    public static ChatMessage fromJson(JSONObject o) {
        ChatMessage m = new ChatMessage();
        m.id = o.optInt("message_id");
        m.senderId = o.optInt("sender_id");
        m.text = Json.str(o, "message_text", "");
        m.createdAt = Json.str(o, "created_at", "");
        m.mine = o.optBoolean("is_mine", false);
        return m;
    }
}
