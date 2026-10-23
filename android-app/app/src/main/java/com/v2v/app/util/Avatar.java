package com.v2v.app.util;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.v2v.app.R;

import java.util.Locale;

// We don't have profile photos yet so everyone gets a coloured circle with their initials
public final class Avatar {

    // Only our own colours
    private static final int[] COLOURS = {
            R.color.v2v_navy, R.color.v2v_blue, R.color.v2v_indigo, R.color.v2v_royal, R.color.v2v_teal
    };

    private Avatar() {
    }

    // "Avela Tom" -> "AT", "Charmaine" -> "C", "" -> "?"
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    // Sets the initials + circle colour
    public static void bind(TextView view, String name, int userId) {
        view.setText(initials(name));
        int colour = ContextCompat.getColor(view.getContext(), COLOURS[Math.abs(userId) % COLOURS.length]);
        view.setBackgroundTintList(ColorStateList.valueOf(colour));
    }
}
