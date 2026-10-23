package com.v2v.app.util;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.v2v.app.R;

// Small UI helpers used by the adapters and screens
public final class Ui {

    private Ui() {
    }

    // Shows a chip in the given colours (hidden if text is null)
    public static void chip(TextView view, String text, @ColorRes int background, @ColorRes int textColour) {
        if (text == null) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(text);
        view.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(view.getContext(), background)));
        view.setTextColor(ContextCompat.getColor(view.getContext(), textColour));
        view.setVisibility(View.VISIBLE);
    }

    // Bold navy label + normal value, e.g. "Can help with ITCNA"
    public static CharSequence labelled(TextView view, String label, String value) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(label);
        int end = sb.length();
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(view.getContext(), R.color.v2v_navy)),
                0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ").append(value);
        return sb;
    }

    // Connection status chip (hidden if not connected)
    public static void relationChip(TextView view, String relation) {
        if ("connected".equals(relation)) {
            chip(view, "Connected", R.color.v2v_green_light, R.color.v2v_green);
        } else if ("pending_sent".equals(relation)) {
            chip(view, "Request sent", R.color.v2v_light, R.color.v2v_navy_dark);
        } else if ("pending_received".equals(relation)) {
            chip(view, "Wants to connect", R.color.v2v_blue, R.color.white);
        } else {
            chip(view, null, 0, 0);
        }
    }
}
