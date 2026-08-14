package org.adaway.ui.hosts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.source.SourceEditActivity;

import static org.adaway.ui.source.SourceEditActivity.SOURCE_ID;

/**
 * The Android TV screen for the hosts sources, built the same way {@link
 * org.adaway.ui.lists.TvListsActivity} rebuilds the Lists screen: a from-scratch layout instead
 * of the phone's ({@link HostsSourcesActivity}/{@link HostsSourcesFragment}) floating add button
 * and checkbox-plus-click rows, both touch patterns with no clean D-pad equivalent.
 * <p>
 * The data layer is untouched: the same {@link HostsSourcesViewModel}, and adding or editing a
 * source still goes through the exact same {@link SourceEditActivity} the phone screen uses — its
 * form (text fields, a couple of button groups) has no floating or overlapping elements, so
 * unlike the list screens it did not need a TV-specific rebuild to be D-pad usable.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TvHostsSourcesActivity extends AppCompatActivity {
    private HostsSourcesViewModel viewModel;
    private TvHostsSourcesAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_hosts_sources);

        this.recyclerView = findViewById(R.id.tv_sources_recycler);
        this.emptyTextView = findViewById(R.id.tv_sources_empty_text);
        MaterialButton addButton = findViewById(R.id.tv_sources_add);

        this.viewModel = new ViewModelProvider(this).get(HostsSourcesViewModel.class);
        this.adapter = new TvHostsSourcesAdapter(this, this::onItemClicked);
        this.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        this.recyclerView.setAdapter(this.adapter);

        // Same prompt-before-apply flow as HostsSourcesFragment: syncSources=true and
        // ignoreEventDuringInstall=true match that fragment's own instantiation exactly, since
        // toggling or editing a source (unlike a Lists entry) can change what gets fetched, not
        // just what gets kept.
        View contentRoot = findViewById(android.R.id.content);
        ApplyConfigurationSnackbar applySnackbar = new ApplyConfigurationSnackbar(contentRoot, true, true);
        this.viewModel.getHostsSources().observe(this, applySnackbar.createObserver());
        this.viewModel.getHostsSources().observe(this, sources -> {
            this.adapter.submitList(sources);
            updateEmptyState(sources.isEmpty());
        });

        addButton.setOnClickListener(v -> startSourceEdition(null));
    }

    private void updateEmptyState(boolean empty) {
        this.emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
        this.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onItemClicked(HostsSource source) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(source.getLabel());
        String[] options = {
                getString(source.isEnabled() ? R.string.tv_dialog_disable_entry : R.string.tv_dialog_enable_entry),
                getString(R.string.checkbox_list_context_edit),
        };
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                this.viewModel.toggleSourceEnabled(source);
            } else if (which == 1) {
                startSourceEdition(source);
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    private void startSourceEdition(@Nullable HostsSource source) {
        Intent intent = new Intent(this, SourceEditActivity.class);
        if (source != null) {
            intent.putExtra(SOURCE_ID, source.getId());
        }
        startActivity(intent);
    }
}
