package com.v2v.app.util;

// Turns database values into friendly text.
public class Labels {

    public static String skillType(String type) {
        if ("studying".equals(type))
            return "Studying";
        if ("offering".equals(type))
            return "Can help with";
        if ("requiring".equals(type))
            return "Needs help with";
        return type == null ? "" : type;
    }

    public static String proficiency(String level) {
        if (level == null || level.isEmpty() || "null".equals(level))
            return "";
        return level.substring(0, 1).toUpperCase() + level.substring(1);
    }

    public static String visibility(String v) {
        if ("public".equals(v))
            return "Public \u2013 everyone can see my profile";
        if ("connections_only".equals(v))
            return "Connections only";
        if ("private".equals(v))
            return "Private \u2013 only my name is visible";
        return v == null ? "" : v;
    }
}
