package com.where.tracker.helper;


import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;


public class SpannableHelper {

    public static CharSequence join(CharSequence delimiter, CharSequence first, CharSequence... other) {
        SpannableStringBuilder builder = new SpannableStringBuilder(first);
        if (other.length > 0) {
            for (CharSequence cs : other) {
                builder.append(delimiter).append(cs);
            }
        }

        return builder;
    }

    public static CharSequence coloredString(CharSequence charSequence, int color) {
        return styledString(charSequence, new ForegroundColorSpan(color));
    }

    public static CharSequence boldString(CharSequence charSequence) {
        return styledString(charSequence, new StyleSpan(Typeface.BOLD));
    }

    public static CharSequence styledString(CharSequence charSequence, CharacterStyle... styles) {
        SpannableString spannable = new SpannableString(charSequence);
        for (CharacterStyle style : styles) {
            spannable.setSpan(style, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }
}
