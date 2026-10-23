package com.v2v.app.model;

import com.v2v.app.util.Json;

import org.json.JSONObject;

public class SkillEntry {
    public int skillId;
    public int moduleId;
    public String moduleCode;
    public String moduleName;
    public String skillType; // studying | offering | requiring
    public String proficiency; // beginner | intermediate | advanced | null

    public static SkillEntry fromJson(JSONObject o) {
        SkillEntry s = new SkillEntry();
        s.skillId = o.optInt("skill_id");
        s.moduleId = o.optInt("module_id");
        s.moduleCode = Json.str(o, "module_code", "");
        s.moduleName = Json.str(o, "module_name", "");
        s.skillType = Json.str(o, "skill_type", "");
        s.proficiency = Json.str(o, "proficiency_level");
        return s;
    }
}
