package org.adaway.ui.welcome;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.error.HostError;
import org.adaway.ui.home.HomeViewModel;
import org.adaway.ui.home.TvHomeActivity;

/**
 * Android TV's own first-run wizard: two screens (welcome, then activate) reached only from
 * {@link TvHomeActivity} when the ad-block method is still undefined, playing the role {@link
 * WelcomeActivity} plays for a fresh mobile install. See tv_activity_welcome.xml for why this is
 * not just that Activity adapted for D-pad: the method choice (TV always uses VPN) and the
 * phone-OS/phone-brand battery guidance mobile shows do not apply to a TV box at all.
 * <p>
 * Reuses the exact {@link HomeViewModel#enable()} mobile's own {@link WelcomeSyncFragment} calls
 * for this same first activation: sources are synced before the VPN starts, and it does not start
 * at all if that sync fails, rather than starting immediately against an empty database the way
 * {@link HomeViewModel#toggleAdBlocking()} would.
 *
 * @author AdAway Community
 */
public class TvWelcomeActivity extends AppCompatActivity {
    private HomeViewModel homeViewModel;
    private View introPage;
    private View activatePage;
    private View promptGroup;
    private View progressGroup;
    private View successGroup;
    private View errorGroup;
    private TextView progressDetailText;
    private TextView errorText;
    private MaterialButton activateButton;
    private MaterialButton finishButton;
    private MaterialButton retryButton;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_welcome);
        bindViews();

        // Fixed before anything touches HomeViewModel: AdAwayApplication.getAdBlockModel()
        // only re-resolves the method when asked again, and HomeViewModel reads it once, in
        // its own constructor - so this has to happen first, or enable() below would run
        // against an UndefinedBlockModel that does nothing at all.
        PreferenceHelper.setAbBlockMethod(this, VPN);
        this.homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                showProgress();
                this.homeViewModel.enable();
            }
            // Cancelled: whatever this screen was already showing (the prompt, or an error
            // from a previous attempt) stays exactly as it was; the button is still there.
        });

        this.homeViewModel.getState().observe(this, state -> this.progressDetailText.setText(state));
        this.homeViewModel.getError().observe(this, this::showError);
        this.homeViewModel.isAdBlocked().observe(this, adBlocked -> {
            if (adBlocked) {
                showSuccess();
            }
        });
    }

    private void bindViews() {
        this.introPage = findViewById(R.id.tv_welcome_intro_page);
        this.activatePage = findViewById(R.id.tv_welcome_activate_page);
        this.promptGroup = findViewById(R.id.tv_welcome_prompt_group);
        this.progressGroup = findViewById(R.id.tv_welcome_progress_group);
        this.successGroup = findViewById(R.id.tv_welcome_success_group);
        this.errorGroup = findViewById(R.id.tv_welcome_error_group);
        this.progressDetailText = findViewById(R.id.tv_welcome_progress_detail);
        this.errorText = findViewById(R.id.tv_welcome_error_text);

        MaterialButton startButton = findViewById(R.id.tv_welcome_start_button);
        this.activateButton = findViewById(R.id.tv_welcome_activate_button);
        this.finishButton = findViewById(R.id.tv_welcome_finish_button);
        this.retryButton = findViewById(R.id.tv_welcome_retry_button);

        startButton.setOnClickListener(v -> showActivatePage());
        this.activateButton.setOnClickListener(v -> activate());
        this.retryButton.setOnClickListener(v -> activate());
        this.finishButton.setOnClickListener(v -> finishWizard());
    }

    /**
     * Toggling a sibling's visibility to VISIBLE does not move D-pad focus there on its own -
     * {@code focusedByDefault} only applies to an Activity's very first layout pass, not to
     * later visibility changes. Posting the request runs it after the layout pass the
     * visibility change above just triggered, once the view is actually focusable; requesting
     * it immediately, before that pass runs, is what left the wizard almost never focused on
     * its own buttons.
     */
    private void focusWhenLaidOut(View view) {
        view.post(view::requestFocus);
    }

    private void showActivatePage() {
        this.introPage.setVisibility(View.GONE);
        this.activatePage.setVisibility(View.VISIBLE);
        focusWhenLaidOut(this.activateButton);
    }

    /**
     * Used both for the first attempt and for a retry after an error: {@link VpnService#prepare}
     * only returns a non-null intent when consent is actually still needed, so a retry after the
     * user already granted it the first time skips straight to re-syncing, no repeat dialog.
     */
    private void activate() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent == null) {
            showProgress();
            this.homeViewModel.enable();
        } else {
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void showProgress() {
        this.promptGroup.setVisibility(View.GONE);
        this.errorGroup.setVisibility(View.GONE);
        this.progressGroup.setVisibility(View.VISIBLE);
    }

    private void showSuccess() {
        this.promptGroup.setVisibility(View.GONE);
        this.progressGroup.setVisibility(View.GONE);
        this.errorGroup.setVisibility(View.GONE);
        this.successGroup.setVisibility(View.VISIBLE);
        focusWhenLaidOut(this.finishButton);
    }

    private void showError(@Nullable HostError error) {
        if (error == null) {
            return;
        }
        this.errorText.setText(getString(R.string.welcome_sync_error, getString(error.getMessageKey())));
        this.progressGroup.setVisibility(View.GONE);
        this.errorGroup.setVisibility(View.VISIBLE);
        focusWhenLaidOut(this.retryButton);
    }

    private void finishWizard() {
        PreferenceHelper.setTvWelcomeDone(this, true);
        startActivity(new Intent(this, TvHomeActivity.class));
        finish();
    }
}
