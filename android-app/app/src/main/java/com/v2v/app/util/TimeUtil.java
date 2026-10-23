package com.v2v.app.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TimeUtil {

    private static final SimpleDateFormat SERVER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static String shortTime(String serverTime) {
        Date d = parse(serverTime);
        if (d == null) {
            return "";
        }
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTime(d);
        boolean today = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        SimpleDateFormat out = new SimpleDateFormat(today ? "HH:mm" : "d MMM", Locale.getDefault());
        return out.format(d);
    }

    private static Date parse(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) {
            return null;
        }
        try {
            synchronized (SERVER) {
                return SERVER.parse(s);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
