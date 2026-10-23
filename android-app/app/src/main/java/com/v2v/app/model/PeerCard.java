package com.v2v.app.model;

import com.v2v.app.util.Json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// One student shown in search results or suggested matches.
public class PeerCard {
    public int userId;
    public String fullName;
    public String campus;
    public double rating; // -1 = no rating yet
    public String relation; // none | pending_sent | pending_received | connected
    public boolean skillSwap;
    public List<String> reasons = new ArrayList<>();
    public List<SkillEntry> skills = new ArrayList<>();

    public static PeerCard fromJson(JSONObject o) {
        PeerCard p = new PeerCard();
        p.userId = o.optInt("user_id");
        p.fullName = Json.str(o, "full_name", "");
        p.campus = Json.str(o, "campus_location", "");
        p.rating = Json.dbl(o, "aggregate_rating");
        p.relation = Json.str(o, "relation", "none");
        p.skillSwap = o.optBoolean("skill_swap", false);

        JSONArray r = o.optJSONArray("reasons");
        if (r != null) {
            for (int i = 0; i < r.length(); i++) {
                p.reasons.add(r.optString(i));
            }
        }
        JSONArray s = o.optJSONArray("skills");
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                JSONObject so = s.optJSONObject(i);
                if (so != null) {
                    p.skills.add(SkillEntry.fromJson(so));
                }
            }
        }
        return p;
    }

    // Module codes for one skill type
    public String summary(String type) {
        StringBuilder sb = new StringBuilder();
        for (SkillEntry s : skills) {
            if (type.equals(s.skillType)) {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(s.moduleCode);
            }
        }
        return sb.toString();
    }
}
