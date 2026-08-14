package org.adaway.ui.source;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.util.AppExecutors;

import java.util.Optional;
import java.util.concurrent.Executor;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static java.util.Objects.requireNonNull;
import static org.adaway.ui.Animations.hideView;
import static org.adaway.ui.Animations.setHidden;
import static org.adaway.ui.Animations.setShown;
import static org.adaway.ui.Animations.showView;
import static org.adaway.ui.source.SourceEditActivity.SOURCE_ID;

/**
 * This is the Android TV activity to create, edit and delete a hosts source.
 * <p>
 * Not {@link SourceEditActivity} reused with patches: that layout puts Save/Delete in the
 * ActionBar's overflow, reachable only by steering D-pad focus up into the toolbar, and lays
 * its fields out with ConstraintLayout bias rather than a plain stack, so default D-pad search
 * has no guaranteed top-to-bottom path through it. Reported after testing on a real TV: it works,
 * but is not practical with a remote. This gives it the same shape as the other TV screens
 * (header buttons instead of a menu, one linear column of fields) while reusing every field
 * widget, string and validation rule as-is.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TvSourceEditActivity extends AppCompatActivity {
    private static final String ANY_MIME_TYPE = "*/*";
    private static final Executor DISK_IO_EXECUTOR = AppExecutors.getInstance().diskIO();
    private static final Executor MAIN_THREAD_EXECUTOR = AppExecutors.getInstance().mainThread();

    private HostsSourceDao hostsSourceDao;
    private ActivityResultLauncher<Intent> startActivityLauncher;
    private boolean editing;
    private HostsSource edited;

    private TextView titleTextView;
    private MaterialButton deleteButton;
    private TextInputEditText labelEditText;
    private MaterialButton blockFormatButton;
    private MaterialButton allowFormatButton;
    private MaterialButtonToggleGroup typeButtonGroup;
    private TextInputLayout urlTextInputLayout;
    private TextInputEditText locationEditText;
    private TextView fileLocationTextView;
    private MaterialCheckBox redirectedHostsCheckbox;
    private TextView redirectedHostsWarningTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_source_edit);
        bindViews();
        /*
         * Get database access.
         */
        AppDatabase database = AppDatabase.getInstance(this);
        this.hostsSourceDao = database.hostsSourceDao();
        // Register for activity
        registerForStartActivity();
        // Set up values
        checkInitialValueFromIntent();
    }

    private void bindViews() {
        this.titleTextView = findViewById(R.id.tv_source_edit_title);
        MaterialButton applyButton = findViewById(R.id.tv_source_edit_apply);
        this.deleteButton = findViewById(R.id.tv_source_edit_delete);
        this.labelEditText = findViewById(R.id.tv_source_label);
        this.blockFormatButton = findViewById(R.id.tv_source_block_button);
        this.allowFormatButton = findViewById(R.id.tv_source_allow_button);
        this.typeButtonGroup = findViewById(R.id.tv_source_type_group);
        this.urlTextInputLayout = findViewById(R.id.tv_source_url_layout);
        this.locationEditText = findViewById(R.id.tv_source_location);
        this.fileLocationTextView = findViewById(R.id.tv_source_file_location);
        this.redirectedHostsCheckbox = findViewById(R.id.tv_source_redirected_checkbox);
        this.redirectedHostsWarningTextView = findViewById(R.id.tv_source_redirected_warning);

        applyButton.setOnClickListener(v -> apply());
        this.deleteButton.setOnClickListener(v -> delete());
    }

    private void registerForStartActivity() {
        this.startActivityLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            Uri uri;
            Intent data = result.getData();
            if (result.getResultCode() == RESULT_OK
                    && data != null
                    && (uri = data.getData()) != null) {
                // Persist read permission
                getContentResolver().takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
                // Update file location
                this.fileLocationTextView.setText(uri.toString());
                this.fileLocationTextView.setError(null);
            }
        });
    }

    private void checkInitialValueFromIntent() {
        Intent intent = getIntent();
        int sourceId = intent.getIntExtra(SOURCE_ID, -1);
        this.editing = sourceId != -1;
        if (this.editing) {
            this.deleteButton.setVisibility(VISIBLE);
            DISK_IO_EXECUTOR.execute(() -> {
                Optional<HostsSource> hostsSource = this.hostsSourceDao.getById(sourceId);
                hostsSource.ifPresent(source -> {
                    this.edited = source;
                    MAIN_THREAD_EXECUTOR.execute(() -> {
                        applyInitialValues(source);
                        bindLocation();
                        bindFormats();
                    });
                });
            });
        } else {
            this.titleTextView.setText(R.string.source_edit_add_title);
            setTitle(R.string.source_edit_add_title);
            bindLocation();
            bindFormats();
        }
    }

    private void applyInitialValues(HostsSource source) {
        this.titleTextView.setText(R.string.source_edit_title);
        setTitle(R.string.source_edit_title);
        this.labelEditText.setText(source.getLabel());
        this.blockFormatButton.setChecked(!source.isAllowEnabled());
        this.allowFormatButton.setChecked(source.isAllowEnabled());
        switch (source.getType()) {
            case URL:
                this.typeButtonGroup.check(R.id.tv_source_url_button);
                this.locationEditText.setText(source.getUrl());
                break;
            case FILE:
                this.typeButtonGroup.check(R.id.tv_source_file_button);
                this.fileLocationTextView.setText(source.getUrl());
                this.fileLocationTextView.setVisibility(VISIBLE);
                this.urlTextInputLayout.setVisibility(GONE);
                break;
        }
        this.redirectedHostsCheckbox.setChecked(source.isRedirectEnabled());
        if (source.isAllowEnabled()) {
            setHidden(this.redirectedHostsCheckbox);
            setHidden(this.redirectedHostsWarningTextView);
        } else {
            setShown(this.redirectedHostsCheckbox);
            setShown(this.redirectedHostsWarningTextView);
        }
    }

    private void bindLocation() {
        this.typeButtonGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            // Keep always one button checked
            if (group.getCheckedButtonId() == View.NO_ID) {
                group.check(checkedId);
                return;
            }
            if (isChecked) {
                boolean isFile = checkedId == R.id.tv_source_file_button;
                this.locationEditText.setText(isFile ? "" : "https://");
                this.locationEditText.setEnabled(!isFile);
                if (isFile) {
                    openDocument();
                }
                this.urlTextInputLayout.setVisibility(isFile ? GONE : VISIBLE);
                this.fileLocationTextView.setVisibility(isFile ? VISIBLE : GONE);
            }
        });
        this.fileLocationTextView.setOnClickListener(view -> openDocument());
    }

    private void bindFormats() {
        this.blockFormatButton.addOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                showView(this.redirectedHostsCheckbox);
                showView(this.redirectedHostsWarningTextView);
            } else {
                hideView(this.redirectedHostsCheckbox);
                hideView(this.redirectedHostsWarningTextView);
            }
        });
    }

    private void apply() {
        HostsSource source = validate();
        if (source == null) {
            return;
        }
        DISK_IO_EXECUTOR.execute(() -> {
            if (this.editing) {
                this.hostsSourceDao.delete(this.edited);
            }
            this.hostsSourceDao.insert(source);
            finish();
        });
    }

    private void delete() {
        DISK_IO_EXECUTOR.execute(() -> this.hostsSourceDao.delete(this.edited));
        finish();
    }

    private HostsSource validate() {
        String label = requireNonNull(this.labelEditText.getText()).toString();
        if (label.isEmpty()) {
            this.labelEditText.setError(getString(R.string.source_edit_label_required));
            return null;
        }
        String url;
        if (this.typeButtonGroup.getCheckedButtonId() == R.id.tv_source_url_button) {
            url = requireNonNull(this.locationEditText.getText()).toString();
            if (url.isEmpty()) {
                this.locationEditText.setError(getString(R.string.source_edit_url_location_required));
                return null;
            }
            if (!HostsSource.isValidUrl(url)) {
                this.locationEditText.setError(getString(R.string.source_edit_location_invalid));
                return null;
            }
        } else {
            url = this.fileLocationTextView.getText().toString();
            if (!HostsSource.isValidUrl(url)) {
                this.fileLocationTextView.setError(getString(R.string.source_edit_location_invalid));
                return null;
            }
        }
        HostsSource source = new HostsSource();
        source.setLabel(label);
        source.setUrl(url);
        boolean allowFormat = this.allowFormatButton.isChecked();
        source.setAllowEnabled(allowFormat);
        source.setRedirectEnabled(!allowFormat && this.redirectedHostsCheckbox.isChecked());
        return source;
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(ANY_MIME_TYPE);
        this.startActivityLauncher.launch(intent);
    }
}
