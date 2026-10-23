package com.v2v.app.model;

import com.v2v.app.util.Json;

import org.json.JSONObject;

public class ConversationItem {
    public int conversationId;
    public int otherUserId;
    public String otherName;
    public String lastMessage;
    public String lastMessageAt;
    public int unread;

    public static ConversationItem fromJson(JSONObject o) {
        ConversationItem c = new ConversationItem();
        c.conversationId = o.optInt("conversation_id");
        c.otherUserId = o.optInt("other_user_id");
        c.otherName = Json.str(o, "other_name", "");
        c.lastMessage = Json.str(o, "last_message");
        c.lastMessageAt = Json.str(o, "last_message_at");
        c.unread = o.optInt("unread_count");
        return c;
    }
}
