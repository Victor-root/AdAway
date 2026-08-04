package org.adaway.ui.welcome;

import static android.content.Context.POWER_SERVICE;
import static android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS;
import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.R;
import org.adaway.databinding.WelcomeBatteryLayoutBinding;

/**
 * This class is a fragment to request the standard Android battery optimization exemption
 * (the "Doze" allowlist) for AdAway, so its VPN service is not paused in the background.
 * <p>
 * This is the official, documented Android mechanism. A separate, undocumented layer that many
 * phone brands add on top of it is covered by {@link WelcomeOemBatteryFragment} next.
 * <p>
 * This step is informational only: whether the user grants the request or not, setup can always
 * continue, since there is nothing to enforce or verify beyond what the system already reports.
 *
 * @author AdAway Community
 */
public class WelcomeBatteryFragment extends WelcomeFragment {
    private WelcomeBatteryLayoutBinding binding;
    private ActivityResultLauncher<Intent> requestLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = WelcomeBatteryLayoutBinding.inflate(inflater, container, false);
        this.requestLauncher = registerForActivityResult(new StartActivityForResult(), result -> refreshState());
        this.binding.batteryActionButton.setOnClickListener(this::requestIgnoreBatteryOptimizations);
        bindFootnote();
        refreshState();
        allowNext();
        return this.binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The user may grant this from Android Settings directly and come back. Reflect it.
        refreshState();
    }

    private void bindFootnote() {
        String[] path = getResources().getStringArray(R.array.welcome_battery_footnote_path);
        this.binding.footnoteTextView.setText(BreadcrumbText.build(requireContext(), 12, path));
    }

    private void refreshState() {
        boolean granted = isIgnoringBatteryOptimizations();
        this.binding.batteryActionButton.setEnabled(!granted);
        this.binding.batteryActionButton.setText(granted ? R.string.welcome_battery_granted : R.string.welcome_battery_action);
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager powerManager = (PowerManager) requireContext().getSystemService(POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(requireContext().getPackageName());
    }

    private void requestIgnoreBatteryOptimizations(@Nullable View view) {
        String packageName = requireContext().getPackageName();
        Intent intent = new Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName));
        try {
            this.requestLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            // Some OEMs strip the direct-request action; fall back to the general list, where
            // the user can still find AdAway and grant it manually.
            try {
                this.requestLauncher.launch(new Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                // Nothing more we can do here; the step stays informational either way.
            }
        }
    }
}
