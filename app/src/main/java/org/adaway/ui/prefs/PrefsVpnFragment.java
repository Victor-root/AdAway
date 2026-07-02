package org.adaway.ui.prefs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsActivity;
import org.adaway.util.AppExecutors;
import org.adaway.util.log.DiagnosticLog;
import org.adaway.vpn.VpnServiceControls;

import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for VPN ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsVpnFragment extends PreferenceFragmentCompat {
    private ActivityResultLauncher<Intent> startActivityLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_vpn);
        // Register for activity
        registerForStartActivity();
        // Bind pref actions
        bindExcludedSystemApps();
        bindExcludedUserApps();
        bindResetVpn();
        bindDiagnosticLog();
        bindClearDiagnosticLog();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_vpn_title);
    }

    private void registerForStartActivity() {
        this.startActivityLauncher = registerForActivityResult(
                new StartActivityForResult(),
                result -> restartVpn()
        );
    }

    private void bindExcludedSystemApps() {
        ListPreference excludeUserAppsPreferences = findPreference(getString(R.string.pref_vpn_excluded_system_apps_key));
        assert excludeUserAppsPreferences != null : PREFERENCE_NOT_FOUND;
        excludeUserAppsPreferences.setOnPreferenceChangeListener((preference, newValue) -> {
            restartVpn();
            return true;
        });
    }

    private void bindExcludedUserApps() {
        Context context = requireContext();
        Preference excludeUserAppsPreferences = findPreference(getString(R.string.pref_vpn_excluded_user_apps_key));
        assert excludeUserAppsPreferences != null : PREFERENCE_NOT_FOUND;
        excludeUserAppsPreferences.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, PrefsVpnExcludedAppsActivity.class);
            this.startActivityLauncher.launch(intent);
            return true;
        });
    }

    private void bindResetVpn() {
        Context context = requireContext();
        Preference resetPreference = findPreference(getString(R.string.pref_vpn_reset_key));
        assert resetPreference != null : PREFERENCE_NOT_FOUND;
        resetPreference.setOnPreferenceClickListener(preference -> {
            // Tear down and re-establish the tunnel so the DNS configuration is read again.
            // This recovers from a tunnel stuck in a broken state (e.g. established with no
            // resolver after a network transition) without needing to recreate the VPN profile.
            if (VpnServiceControls.isRunning(context)) {
                restartVpn();
                Toast.makeText(context, R.string.pref_vpn_reset_done, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.pref_vpn_reset_not_running, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void restartVpn() {
        Context context = requireContext();
        if (VpnServiceControls.isRunning(context)) {
            VpnServiceControls.stop(context);
            VpnServiceControls.start(context);
        }
    }

    private void bindDiagnosticLog() {
        Context context = requireContext();
        Preference preference = findPreference(getString(R.string.pref_vpn_diagnostic_log_key));
        assert preference != null : PREFERENCE_NOT_FOUND;
        preference.setOnPreferenceClickListener(p -> {
            // Reading the log touches disk and blocks until pending writes flush, so do it off
            // the main thread, then come back to show the dialog.
            AppExecutors executors = AppExecutors.getInstance();
            executors.diskIO().execute(() -> {
                String log = DiagnosticLog.getInstance(context).read();
                executors.mainThread().execute(() -> showDiagnosticLog(log));
            });
            return true;
        });
    }

    private void showDiagnosticLog(String log) {
        // The read is asynchronous: the fragment may have been detached in the meantime.
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        boolean empty = log == null || log.trim().isEmpty();
        CharSequence message = empty ? getString(R.string.pref_vpn_diagnostic_log_empty) : previewOf(log);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_vpn_diagnostic_log)
                .setMessage(message)
                .setNegativeButton(android.R.string.ok, null);
        if (!empty) {
            builder.setPositiveButton(R.string.pref_vpn_diagnostic_log_share, (dialog, which) -> shareLog(log));
            builder.setNeutralButton(R.string.pref_vpn_diagnostic_log_copy, (dialog, which) -> copyLog(log));
        }
        builder.show();
    }

    /**
     * Keep only the tail of the log for the on-screen preview so a large log does not make the
     * dialog sluggish; the full log is still what gets copied or shared.
     */
    private CharSequence previewOf(String log) {
        int maxChars = 20_000;
        if (log.length() <= maxChars) {
            return log;
        }
        return "…\n" + log.substring(log.length() - maxChars);
    }

    private void copyLog(String log) {
        Context context = requireContext();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.pref_vpn_diagnostic_log), log));
        Toast.makeText(context, R.string.pref_vpn_diagnostic_log_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareLog(String log) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.pref_vpn_diagnostic_log));
        intent.putExtra(Intent.EXTRA_TEXT, log);
        startActivity(Intent.createChooser(intent, getString(R.string.pref_vpn_diagnostic_log_share)));
    }

    private void bindClearDiagnosticLog() {
        Context context = requireContext();
        Preference preference = findPreference(getString(R.string.pref_vpn_diagnostic_log_clear_key));
        assert preference != null : PREFERENCE_NOT_FOUND;
        preference.setOnPreferenceClickListener(p -> {
            DiagnosticLog.getInstance(context).clear();
            Toast.makeText(context, R.string.pref_vpn_diagnostic_log_cleared, Toast.LENGTH_SHORT).show();
            return true;
        });
    }
}
