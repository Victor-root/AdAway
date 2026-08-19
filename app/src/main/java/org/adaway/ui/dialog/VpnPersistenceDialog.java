package org.adaway.ui.dialog;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;

import timber.log.Timber;

/**
 * The "VPN persistence" dialog, shared by the mobile home screen and the TV home screen.
 * <p>
 * Android blocks every recovery path an app owns (sticky service restart, scheduled work,
 * broadcasts) once that app has been force-stopped, so nothing in AdAway can bring the VPN back
 * on its own from that state. Pinning AdAway as the system Always-on VPN is the one mechanism
 * that survives it, because the system's own VPN subsystem does the restarting - which is what
 * this dialog exists to explain and to link to.
 * <p>
 * The dialog's own strings are named {@code vpn_persistence_*}, being shared by both platforms.
 * The {@code tv_} ones it still reads are genuinely TV-specific: their wording names the TV
 * explicitly, and they are only ever used when {@code withAdbFallback} is set (see {@link #show}).
 *
 * @author AdAway Community
 */
public final class VpnPersistenceDialog {
    private VpnPersistenceDialog() {

    }

    /**
     * Check whether the Android system has AdAway pinned as the Always-on VPN app
     * ({@code Settings.Secure.ALWAYS_ON_VPN_APP}, with a fallback to the Global namespace for
     * older or customized builds).
     *
     * @param context The context to read the system setting with.
     * @return {@code true} when AdAway is the always-on VPN app, {@code false} in every other
     * case, including when the setting cannot be read at all.
     */
    public static boolean isAlwaysOnVpnEnabled(Context context) {
        String packageName = context.getPackageName();
        ContentResolver contentResolver = context.getContentResolver();
        try {
            String alwaysOnApp = Settings.Secure.getString(contentResolver, "always_on_vpn_app");
            if (alwaysOnApp == null || alwaysOnApp.isEmpty()) {
                alwaysOnApp = Settings.Global.getString(contentResolver, "always_on_vpn_app");
            }
            return packageName.equals(alwaysOnApp);
        } catch (SecurityException e) {
            Timber.w(e, "Cannot read always-on VPN setting; treating as not configured.");
            return false;
        }
    }

    /**
     * Show the dialog: the current always-on detection status, what AdAway already handles on
     * its own, and a shortcut to the system VPN settings.
     *
     * @param activity                   The activity to show the dialog from.
     * @param withAdbFallback            Whether to also print the ADB commands that set
     *                                   always-on without the system UI. Only useful on Android
     *                                   TV, where that settings screen is often hidden
     *                                   outright; a phone always has it.
     * @param settingsUnavailableMessage The message to show if the system VPN settings cannot
     *                                   be opened, which differs per platform.
     */
    public static void show(Activity activity, boolean withAdbFallback,
                            @StringRes int settingsUnavailableMessage) {
        boolean configured = isAlwaysOnVpnEnabled(activity);
        StringBuilder message = new StringBuilder()
                .append(activity.getString(configured
                        ? R.string.vpn_persistence_status_enabled
                        : R.string.vpn_persistence_status_disabled))
                .append("\n\n")
                .append(activity.getString(R.string.vpn_persistence_message));
        if (withAdbFallback) {
            message.append("\n\n")
                    .append(activity.getString(R.string.tv_persistence_adb_commands,
                            activity.getPackageName()));
        }
        // Built with null click listeners so the auto-dismiss-on-click is wired, then the
        // positive button is overridden after show(): when the ADB commands are on screen and
        // the intent fails, the dialog has to stay up so they remain readable instead of the
        // user having to re-open it.
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.vpn_persistence_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.vpn_persistence_open_settings, null)
                .setNegativeButton(R.string.vpn_persistence_dismiss, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    boolean opened = openVpnSettings(activity, settingsUnavailableMessage);
                    if (opened || !withAdbFallback) {
                        dialog.dismiss();
                    }
                }));
        dialog.show();
    }

    /**
     * Try to open the system VPN settings via {@link Settings#ACTION_VPN_SETTINGS}.
     * Non-blocking: if the activity is unresolved or {@code startActivity} throws, a Toast is
     * shown instead.
     *
     * @return {@code true} when the system activity actually started, {@code false} otherwise,
     * so the caller can decide whether to keep its own UI on screen.
     */
    private static boolean openVpnSettings(Activity activity,
                                           @StringRes int settingsUnavailableMessage) {
        Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            try {
                activity.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                Timber.w(e, "Failed to open VPN settings.");
            }
        }
        Toast.makeText(activity, settingsUnavailableMessage, Toast.LENGTH_LONG).show();
        return false;
    }
}
