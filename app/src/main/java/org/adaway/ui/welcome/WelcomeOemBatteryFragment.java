package org.adaway.ui.welcome;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.adaway.R;
import org.adaway.databinding.WelcomeOemBatteryLayoutBinding;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * This class is a fragment guiding the user to their phone brand's own battery manager — a
 * separate, undocumented layer that many OEMs (ColorOS, MIUI, EMUI, One UI…) add on top of
 * Android's standard battery optimization (see {@link WelcomeBatteryFragment}), and which can
 * still silently stop AdAway's VPN even after that standard exemption is granted.
 * <p>
 * There is no public Android API to request or even read this OEM-specific state — it is entirely
 * proprietary to each manufacturer, so this step opens the app info screen (the one reliably
 * consistent entry point across skins) and shows a single tip for the brand detected from
 * {@link Build#MANUFACTURER}/{@link Build#BRAND}, falling back to generic guidance if the brand
 * isn't one of the ones covered here.
 * <p>
 * The exact submenu names and even the navigation depth genuinely vary by firmware/region/version,
 * so the tip is worded as a pointer ("look for something like…") rather than an exact, guaranteed
 * walkthrough, and the screen carries an explicit disclaimer to that effect.
 * <p>
 * Informational only, same as {@link WelcomeBatteryFragment}: nothing here can be verified or
 * enforced, so setup is never blocked on it.
 *
 * @author AdAway Community
 */
public class WelcomeOemBatteryFragment extends WelcomeFragment {
    private static final List<OemBrand> BRANDS = Arrays.asList(
            new OemBrand(R.string.welcome_oem_battery_label_oneui,
                    R.array.welcome_oem_battery_path_oneui, R.string.welcome_oem_battery_instruction_oneui,
                    R.string.welcome_oem_battery_secondary_oneui, "samsung"),
            new OemBrand(R.string.welcome_oem_battery_label_miui,
                    R.array.welcome_oem_battery_path_miui, R.string.welcome_oem_battery_instruction_miui,
                    R.string.welcome_oem_battery_secondary_miui, "xiaomi", "redmi", "poco"),
            new OemBrand(R.string.welcome_oem_battery_label_coloros,
                    R.array.welcome_oem_battery_path_coloros, R.string.welcome_oem_battery_instruction_coloros,
                    R.string.welcome_oem_battery_secondary_coloros, "oppo", "oneplus", "realme"),
            new OemBrand(R.string.welcome_oem_battery_label_emui,
                    R.array.welcome_oem_battery_path_emui, R.string.welcome_oem_battery_instruction_emui,
                    R.string.welcome_oem_battery_secondary_emui, "huawei", "honor"),
            new OemBrand(R.string.welcome_oem_battery_label_funtouch,
                    R.array.welcome_oem_battery_path_funtouch, R.string.welcome_oem_battery_instruction_funtouch,
                    R.string.welcome_oem_battery_secondary_funtouch, "vivo", "iqoo")
    );
    /** Shown when the device's brand doesn't match any entry in {@link #BRANDS}. */
    private static final OemBrand GENERIC = new OemBrand(R.string.welcome_oem_battery_label_generic,
            0, R.string.welcome_oem_battery_tip_generic, 0);

    private WelcomeOemBatteryLayoutBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = WelcomeOemBatteryLayoutBinding.inflate(inflater, container, false);
        bindOemTip();
        this.binding.openAppInfoButton.setOnClickListener(this::openAppInfo);
        allowNext();
        return this.binding.getRoot();
    }

    private void bindOemTip() {
        OemBrand brand = getDetectedBrand();
        this.binding.oemTipLabelTextView.setText(brand.labelRes);
        if (brand.pathArrayRes == 0) {
            this.binding.oemTipPathTextView.setVisibility(View.GONE);
        } else {
            String[] path = getResources().getStringArray(brand.pathArrayRes);
            this.binding.oemTipPathTextView.setText(BreadcrumbText.build(requireContext(), 12, path));
        }
        this.binding.oemTipInstructionTextView.setText(brand.instructionRes);
        if (brand.secondaryInstructionRes == 0) {
            this.binding.oemTipSecondaryTextView.setVisibility(View.GONE);
        } else {
            this.binding.oemTipSecondaryTextView.setVisibility(View.VISIBLE);
            this.binding.oemTipSecondaryTextView.setText(brand.secondaryInstructionRes);
        }
    }

    private void openAppInfo(@Nullable View view) {
        String packageName = requireContext().getPackageName();
        Intent intent = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.welcome_oem_battery_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Pick the guidance matching the device's manufacturer/brand. Both are checked (a device can
     * report the sub-brand in either field depending on OEM, e.g. Realme/OnePlus devices built on
     * the same ColorOS base) with a generic fallback for unrecognized or unaffected brands.
     */
    @NonNull
    private static OemBrand getDetectedBrand() {
        String manufacturer = safeLower(Build.MANUFACTURER);
        String brand = safeLower(Build.BRAND);
        for (OemBrand candidate : BRANDS) {
            if (candidate.matches(manufacturer, brand)) {
                return candidate;
            }
        }
        return GENERIC;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /** @param pathArrayRes 0 when the brand has no specific navigation path (generic fallback). */
    private static final class OemBrand {
        @StringRes
        final int labelRes;
        @ArrayRes
        final int pathArrayRes;
        @StringRes
        final int instructionRes;
        @StringRes
        final int secondaryInstructionRes;
        final String[] needles;

        OemBrand(@StringRes int labelRes, @ArrayRes int pathArrayRes, @StringRes int instructionRes,
                 @StringRes int secondaryInstructionRes, String... needles) {
            this.labelRes = labelRes;
            this.pathArrayRes = pathArrayRes;
            this.instructionRes = instructionRes;
            this.secondaryInstructionRes = secondaryInstructionRes;
            this.needles = needles;
        }

        boolean matches(String manufacturer, String brand) {
            for (String needle : this.needles) {
                if (manufacturer.contains(needle) || brand.contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }
}
