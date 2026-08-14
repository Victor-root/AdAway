package org.adaway.ui.prefs;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;

/**
 * A {@link PreferenceGroupAdapter} whose disabled rows are not focusable.
 * <p>
 * The stock adapter disables a row's click handling when its {@link Preference} is disabled (a
 * whole section like the root ad blocker settings here, when VPN mode is active), but leaves its
 * row view focusable regardless, since disabling a preference and disabling its view are two
 * different things the library does not tie together. A disabled row is still clickable-inert but
 * remains an individual D-pad stop, so reaching whatever comes after a several-row disabled
 * section (e.g. after "Clear diagnostic log", the whole root ad blocker category) takes one D-pad
 * press per disabled row instead of jumping straight past them, and every one of those presses
 * looks like nothing happened. This ties the two back together: disabled means unfocusable, the
 * same relationship every other disabled control (a plain disabled {@code Button}, for one)
 * already has by default.
 * <p>
 * Used by every {@code PreferenceFragmentCompat} in this package via {@code onCreateAdapter}, not
 * only the one this was reported on: the same stock behaviour, and the same fix, apply wherever a
 * preference is conditionally disabled (a category here, individual rows behind
 * {@code app:dependency} or a manual {@code setEnabled(false)} in {@link PrefsUpdateFragment}).
 * Touch is unaffected either way: focusability only changes what a D-pad or keyboard can land on,
 * never what a tap does.
 */
class FocusSkippingPreferenceGroupAdapter extends PreferenceGroupAdapter {
    FocusSkippingPreferenceGroupAdapter(PreferenceGroup preferenceGroup) {
        super(preferenceGroup);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        holder.itemView.setFocusable(getItem(position).isEnabled());
    }
}
