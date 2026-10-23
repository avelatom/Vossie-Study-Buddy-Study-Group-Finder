package com.v2v.app.model;

import com.v2v.app.util.Json;

import org.json.JSONObject;

public class GroupItem {
    public int groupId;
    public String name;
    public String description;
    public int moduleId;
    public String moduleCode;
    public String moduleName;
    public int creatorId;
    public String creatorName;
    public int memberCount;
    public int maxMembers;
    public boolean isFull;
    public boolean isMember;

    public static GroupItem fromJson(JSONObject o) {
        GroupItem g = new GroupItem();
        g.groupId = o.optInt("group_id");
        g.name = Json.str(o, "group_name", "");
        g.description = Json.str(o, "description");
        g.moduleId = o.optInt("module_id");
        g.moduleCode = Json.str(o, "module_code", "");
        g.moduleName = Json.str(o, "module_name", "");
        g.creatorId = o.optInt("creator_id");
        g.creatorName = Json.str(o, "creator_name", "");
        g.memberCount = o.optInt("member_count");
        g.maxMembers = o.optInt("max_members");
        g.isFull = o.optBoolean("is_full", false);
        g.isMember = o.optBoolean("is_member", false);
        return g;
    }
}
