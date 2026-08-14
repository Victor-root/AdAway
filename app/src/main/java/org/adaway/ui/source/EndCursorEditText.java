package org.adaway.ui.source;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;

/**
 * A {@link TextInputEditText} that always keeps D-pad up/down as plain focus navigation, with the
 * cursor parked at the end of the text whenever the field is focused.
 * <p>
 * Reported on the TV source edit screen's location field, pre-filled with "https://": a single
 * line of text still has a start and an end for the field's own arrow-key handling to move a
 * cursor between, so it claims a D-pad press before focus search ever sees it as long as the
 * cursor has anywhere left to move. Parking the cursor at the end on focus (onFocusChanged) fixed
 * arriving from above and continuing down, but arriving from below left the cursor at the end
 * too, so a second press up moved it to the start instead of leaving the field; only a third
 * press, once the cursor had nowhere left to go, actually left. Overriding onKeyDown to hand up
 * and down straight to focus search, unconditionally, removes the field's own handling from the
 * picture entirely: every up or down press is a field change, in one press, every time, from
 * either direction. Left/right are untouched and still move the cursor, needed to edit the URL.
 */
public class EndCursorEditText extends TextInputEditText {
    public EndCursorEditText(Context context) {
        super(context);
    }

    public EndCursorEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EndCursorEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) {
            setSelection(length());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        int direction;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            direction = View.FOCUS_UP;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            direction = View.FOCUS_DOWN;
        } else {
            return super.onKeyDown(keyCode, event);
        }
        View next = focusSearch(direction);
        if (next != null) {
            return next.requestFocus(direction);
        }
        return super.onKeyDown(keyCode, event);
    }
}
