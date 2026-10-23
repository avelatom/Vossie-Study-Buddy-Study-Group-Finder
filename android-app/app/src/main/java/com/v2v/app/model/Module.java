package com.v2v.app.model;

import com.v2v.app.util.Json;

import org.json.JSONObject;

public class Module {
    public int id;
    public String code;
    public String name;

    public static Module fromJson(JSONObject o) {
        Module m = new Module();
        m.id = o.optInt("module_id");
        m.code = Json.str(o, "module_code", "");
        m.name = Json.str(o, "module_name", "");
        return m;
    }

    // Shown in drop-down lists.
    @Override
    public String toString() {
        return code + " \u2013 " + name;
    }
}
