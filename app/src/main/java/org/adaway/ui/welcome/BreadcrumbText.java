package org.adaway.ui.welcome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.adaway.R;

/**
 * Builds an inline "Settings › Apps › AdAway" style path out of plain text segments separated by
 * a small chevron icon, instead of a bare "→" character (which renders as a thin, easy-to-miss
 * text glyph on the welcome screens' red/dark backgrounds).
 *
 * @author AdAway Community
 */
final class BreadcrumbText {
    private BreadcrumbText() {
    }

    /**
     * @param context     Context to load the chevron drawable from.
     * @param iconSizeDp  Size, in dp, to draw the chevron at (should roughly match the
     *                    surrounding text size).
     * @param segments    Path segments, e.g. {"Settings", "Apps", "AdAway"}.
     */
    @NonNull
    static CharSequence build(@NonNull Context context, int iconSizeDp, @NonNull String... segments) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int iconSizePx = Math.round(iconSizeDp * context.getResources().getDisplayMetrics().density);
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                Drawable chevron = ContextCompat.getDrawable(context, R.drawable.ic_chevron_right_24dp);
                if (chevron != null) {
                    chevron.setBounds(0, 0, iconSizePx, iconSizePx);
                    int start = builder.length();
                    builder.append("   ");
                    builder.setSpan(new ImageSpan(chevron, ImageSpan.ALIGN_BASELINE), start + 1, start + 2,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            builder.append(segments[index]);
        }
        return builder;
    }
}
