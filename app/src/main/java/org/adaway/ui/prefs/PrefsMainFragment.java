package org.adaway.ui.prefs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.util.log.SentryLog;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences main fragment.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsMainFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_main);
        // Bind pref actions
        bindThemePrefAction();
        bindAdBlockMethod();
        bindTelemetryPrefAction();
        hideDebugCategoryOnReleaseBuild();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_main_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        PrefsActivity.setAppBarTitle(this, R.string.pref_main_title);
    }

    private void bindThemePrefAction() {
        Preference darkThemePref = findPreference(getString(R.string.pref_dark_theme_mode_key));
        assert darkThemePref != null : PREFERENCE_NOT_FOUND;
        darkThemePref.setOnPreferenceChangeListener((preference, newValue) -> {
            requireActivity().recreate();
            // Allow preference change
            return true;
        });
    }

    private void bindAdBlockMethod() {
        Preference rootPreference = findPreference(getString(R.string.pref_root_ad_block_method_key));
        assert rootPreference != null : PREFERENCE_NOT_FOUND;
        Preference vpnPreference = findPreference(getString(R.string.pref_vpn_ad_block_method_key));
        assert vpnPreference != null : PREFERENCE_NOT_FOUND;
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(requireContext());
        rootPreference.setEnabled(adBlockMethod == ROOT);
        vpnPreference.setEnabled(adBlockMethod == VPN);
    }

    /**
     * Hide the whole debug category on a release build. The category only holds
     * developer tooling (telemetry and debug logging), so it is irrelevant to
     * end users. A release APK is not flagged debuggable, which is what we test.
     */
    private void hideDebugCategoryOnReleaseBuild() {
        boolean debuggable = (requireContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable) {
            return;
        }
        PreferenceCategory debugCategory = findPreference(getString(R.string.pref_debug_category_key));
        if (debugCategory != null) {
            debugCategory.setVisible(false);
        }
        // The telemetry switch lives in that category, so hiding it also takes away the only way
        // to turn telemetry back off. It defaults to off, but a restored backup carries the
        // preference across, which would leave a release user uploading reports with no control
        // over it. Force it off whenever the switch is out of reach.
        if (PreferenceHelper.getTelemetryEnabled(requireContext())) {
            PreferenceHelper.setTelemetryEnabled(requireContext(), false);
            SentryLog.setEnabled(requireActivity().getApplication(), false);
        }
    }

    private void bindTelemetryPrefAction() {
        Preference enableTelemetryPref = findPreference(getString(R.string.pref_enable_telemetry_key));
        assert enableTelemetryPref != null : PREFERENCE_NOT_FOUND;
        enableTelemetryPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SentryLog.setEnabled(requireActivity().getApplication(), (boolean) newValue);
            return true;
        });
        if (SentryLog.isStub()) {
            enableTelemetryPref.setEnabled(false);
            enableTelemetryPref.setSummary(R.string.pref_enable_telemetry_disabled_summary);
        }
    }
}
