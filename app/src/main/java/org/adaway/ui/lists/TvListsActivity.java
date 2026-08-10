package org.adaway.ui.lists;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagingData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.dialog.AlertDialogValidator;
import org.adaway.util.Clipboard;
import org.adaway.util.RegexUtils;

import java.util.function.BiConsumer;

/**
 * The Android TV screen for the Blocked/Allowed/Redirected host lists.
 * <p>
 * Not a reuse of {@link ListsActivity} with D-pad patches, unlike the two previous fixes on that
 * screen: the phone layout's floating add button and swipeable tabs each needed one, and even
 * patched they only redirected the crossings that were actually reported broken, leaving normal
 * D-pad search (a geometric nearest-neighbour heuristic with no real concept of "floats over the
 * list" or "lives inside a horizontally-paged tab") in charge everywhere else. This is a plain,
 * from-scratch layout built the way this app's other TV screens already are ({@link
 * org.adaway.ui.log.TvLogActivity}, {@link org.adaway.ui.home.TvHomeActivity}): every focusable
 * control sits in the normal top-to-bottom, always-visible header instead of floating over
 * content, and a click opens a dialog instead of relying on a checkbox tap plus a long-press,
 * which has no clean D-pad equivalent short of holding the centre button down.
 * <p>
 * The data layer is untouched: this calls the exact same {@link ListsViewModel} the phone screen
 * does, so an entry added, edited, toggled or removed here is the same database row, subject to
 * the same "apply configuration" prompt ({@link org.adaway.ui.adblocking.ApplyConfigurationSnackbar})
 * before it actually changes what is blocked.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TvListsActivity extends AppCompatActivity {
    private ListsViewModel viewModel;
    private TvListsAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    /**
     * The title row: it IS the current tab, not a separate "Vos listes" caption plus a button
     * repeating the tab name underneath. A plain label, not a control: switching tabs goes back
     * out to the TV home screen's Blocked/Allowed/Redirected tiles, not through a picker on this
     * screen, so unlike the rest of the header this row does nothing when clicked.
     */
    private ImageView titleIcon;
    private TextView titleText;
    /**
     * The tab currently shown. Never null after {@link #onCreate}: seeded before the ViewModel's
     * LiveData observers are registered, since those fire as soon as the first page of data is
     * ready and compare against this to decide whether it is their tab's turn to be displayed.
     */
    private ListType currentType = BLOCKED;
    /**
     * The hostname filter currently applied, mirroring {@link ListsViewModel}'s own (private)
     * filter state so {@link #openSearchDialog} can pre-fill the field with whatever is already
     * active. Empty means unfiltered, the same meaning {@link ListsFilter#ALL}'s query gives it.
     */
    private String currentQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_lists);

        this.recyclerView = findViewById(R.id.tv_lists_recycler);
        this.emptyTextView = findViewById(R.id.tv_lists_empty_text);
        this.titleIcon = findViewById(R.id.tv_lists_title_icon);
        this.titleText = findViewById(R.id.tv_lists_title_text);
        MaterialButton addButton = findViewById(R.id.tv_lists_add);
        MaterialButton searchButton = findViewById(R.id.tv_lists_search);
        MaterialButton toggleSourcesButton = findViewById(R.id.tv_lists_toggle_sources);

        this.viewModel = new ViewModelProvider(this).get(ListsViewModel.class);
        this.adapter = new TvListsAdapter(this::onItemClicked);
        this.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        this.recyclerView.setAdapter(this.adapter);
        this.adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateEmptyState();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateEmptyState();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateEmptyState();
            }
        });

        // Each of the three lists stays observed for the activity's lifetime; only the one
        // matching the currently selected tab is actually handed to the (single, shared) adapter.
        // Paging3's own cachedIn(), already applied in ListsViewModel, is what makes re-submitting
        // a previously-collected one safe when the user switches back to a tab.
        this.viewModel.getBlockedListItems().observe(this, data -> submitIfCurrent(BLOCKED, data));
        this.viewModel.getAllowedListItems().observe(this, data -> submitIfCurrent(ALLOWED, data));
        this.viewModel.getRedirectedListItems().observe(this, data -> submitIfCurrent(REDIRECTED, data));

        addButton.setOnClickListener(v -> addItem());
        searchButton.setOnClickListener(v -> openSearchDialog());
        toggleSourcesButton.setOnClickListener(v -> this.viewModel.toggleSources());

        // Same prompt-before-apply flow as ListsActivity: adding, editing, toggling or deleting an
        // entry only changes the database; this is what tells the user a change is pending and
        // lets them apply it, exactly like on mobile. Anchored on the window's content root since
        // this layout has no CoordinatorLayout (no floating action button for one to keep clear of).
        View contentRoot = findViewById(android.R.id.content);
        ApplyConfigurationSnackbar applySnackbar = new ApplyConfigurationSnackbar(contentRoot, false, false);
        this.viewModel.getModelChanged().observe(this, applySnackbar.createObserver());

        int requestedTab = getIntent().getIntExtra(ListsActivity.TAB, ListsActivity.BLOCKED_HOSTS_TAB);
        selectTab(tabToType(requestedTab));
    }

    private void selectTab(ListType type) {
        this.currentType = type;
        updateTitle();
        PagingData<HostListItem> data = dataForType(type).getValue();
        if (data != null) {
            this.adapter.submitData(getLifecycle(), data);
        }
        this.recyclerView.scrollToPosition(0);
    }

    private void submitIfCurrent(ListType type, PagingData<HostListItem> data) {
        if (this.currentType == type) {
            this.adapter.submitData(getLifecycle(), data);
        }
    }

    private LiveData<PagingData<HostListItem>> dataForType(ListType type) {
        switch (type) {
            case ALLOWED:
                return this.viewModel.getAllowedListItems();
            case REDIRECTED:
                return this.viewModel.getRedirectedListItems();
            default:
                return this.viewModel.getBlockedListItems();
        }
    }

    private static ListType tabToType(int tab) {
        if (tab == ListsActivity.ALLOWED_HOSTS_TAB) {
            return ALLOWED;
        }
        if (tab == ListsActivity.REDIRECTED_HOSTS_TAB) {
            return REDIRECTED;
        }
        return BLOCKED;
    }

    /**
     * Reflect the current tab in the title row: its label and its icon (the same one each type
     * already has elsewhere — the mobile bottom tab bar, the TV home screen's stat cards), so it
     * reads correctly from the moment the screen appears, before anything is clicked.
     */
    private void updateTitle() {
        int labelRes;
        int iconRes;
        switch (this.currentType) {
            case ALLOWED:
                labelRes = R.string.lists_tab_allowed;
                iconRes = R.drawable.baseline_check_24;
                break;
            case REDIRECTED:
                labelRes = R.string.lists_tab_redirected;
                iconRes = R.drawable.baseline_compare_arrows_24;
                break;
            default:
                labelRes = R.string.lists_tab_blocked;
                iconRes = R.drawable.baseline_block_24;
                break;
        }
        this.titleText.setText(labelRes);
        this.titleIcon.setImageResource(iconRes);
        this.titleIcon.setContentDescription(getString(labelRes));
    }

    /**
     * Open the hostname filter dialog, mirroring the phone screen's {@code SearchView} (also
     * backed by {@link ListsViewModel#search}) with a plain {@code EditText} dialog instead: a
     * collapsible ActionBar search field is a phone pattern with nowhere to collapse to here,
     * this activity's theme has no ActionBar to hold one in the first place.
     * <p>
     * Pre-filled with whatever filter is already active, so it doubles as the way to see, change
     * or clear it; clearing the field and confirming is what {@code search("")} already treats as
     * "no filter", the same value {@link ListsFilter#ALL} carries.
     */
    private void openSearchDialog() {
        EditText queryEditText = new EditText(this);
        queryEditText.setHint(R.string.lists_menu_filter_hint);
        queryEditText.setSingleLine();
        queryEditText.setText(this.currentQuery);
        queryEditText.setSelection(this.currentQuery.length());
        // A plain padded container, not the multi-arg setView(view, left, top, right, bottom)
        // overload: this dialog's content is built in code rather than inflated from a layout
        // resource like the others on this screen, so it needs to supply its own inset the same
        // way lists_blocked_dialog.xml and its siblings do via their root's android:padding.
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_inner_padding);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding, padding, padding);
        container.addView(queryEditText);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lists_menu_filter)
                .setView(container)
                .setPositiveButton(R.string.tv_dialog_search, (dialog, which) -> applySearch(queryEditText.getText().toString()))
                .setNeutralButton(R.string.tv_dialog_clear_search, (dialog, which) -> applySearch(""))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void applySearch(String query) {
        this.currentQuery = query;
        this.viewModel.search(query);
    }

    private void updateEmptyState() {
        boolean empty = this.adapter.getItemCount() == 0;
        this.emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
        this.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * A row was clicked. Mirrors {@code ListsAdapter}'s touch behaviour exactly, just collapsed
     * into the single gesture a D-pad actually has: a source-provided entry (not the user's own)
     * only offers what mobile's long-press offers it too, a copy; the user's own entry gets the
     * same enable-toggle, edit and delete mobile reaches via its checkbox and long-press.
     */
    private void onItemClicked(HostListItem item) {
        if (!TvListsAdapter.isEditable(item)) {
            Clipboard.copyHostToClipboard(this, item.getHost());
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(item.getHost());
        String[] options = {
                getString(item.isEnabled() ? R.string.tv_dialog_disable_entry : R.string.tv_dialog_enable_entry),
                getString(R.string.checkbox_list_context_edit),
                getString(R.string.checkbox_list_context_delete),
                getString(R.string.tv_dialog_copy_hostname),
        };
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    this.viewModel.toggleItemEnabled(item);
                    break;
                case 1:
                    editItem(item);
                    break;
                case 2:
                    this.viewModel.removeListItem(item);
                    break;
                case 3:
                    Clipboard.copyHostToClipboard(this, item.getHost());
                    break;
                default:
                    break;
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    private void addItem() {
        switch (this.currentType) {
            case ALLOWED:
                showHostnameDialog(R.layout.lists_allowed_dialog, R.string.list_add_dialog_white,
                        R.string.button_add, null, RegexUtils::isValidWildcardHostname,
                        (host, ip) -> this.viewModel.addListItem(ALLOWED, host, null));
                break;
            case REDIRECTED:
                showRedirectedDialog(R.string.list_add_dialog_redirect, R.string.button_add, null, null,
                        (host, ip) -> this.viewModel.addListItem(REDIRECTED, host, ip));
                break;
            default:
                showHostnameDialog(R.layout.lists_blocked_dialog, R.string.list_add_dialog_black,
                        R.string.button_add, null, RegexUtils::isValidHostname,
                        (host, ip) -> this.viewModel.addListItem(BLOCKED, host, null));
                break;
        }
    }

    private void editItem(HostListItem item) {
        switch (this.currentType) {
            case ALLOWED:
                showHostnameDialog(R.layout.lists_allowed_dialog, R.string.list_edit_dialog_white,
                        R.string.button_save, item.getHost(), RegexUtils::isValidWildcardHostname,
                        (host, ip) -> this.viewModel.updateListItem(item, host, null));
                break;
            case REDIRECTED:
                showRedirectedDialog(R.string.list_edit_dialog_redirect, R.string.button_save,
                        item.getHost(), item.getRedirection(),
                        (host, ip) -> this.viewModel.updateListItem(item, host, ip));
                break;
            default:
                showHostnameDialog(R.layout.lists_blocked_dialog, R.string.list_edit_dialog_black,
                        R.string.button_save, item.getHost(), RegexUtils::isValidHostname,
                        (host, ip) -> this.viewModel.updateListItem(item, host, null));
                break;
        }
    }

    /**
     * The add/edit dialog for a Blocked or Allowed entry: one hostname field. Reuses the exact
     * layout and validation the matching phone fragment does; only the shell (a single dialog
     * builder here instead of one per fragment) is different.
     *
     * @param layoutRes   {@code lists_blocked_dialog} or {@code lists_allowed_dialog}.
     * @param titleRes    The dialog title.
     * @param positiveRes The confirm button's label ("Add" or "Save").
     * @param initialHost The current hostname when editing, <code>null</code> when adding.
     * @param validator   The hostname validator (hostnames and wildcard hostnames differ).
     * @param onConfirm   Called with the validated hostname (and a null second argument, kept only
     *                    so this shares a callback shape with {@link #showRedirectedDialog}).
     */
    private void showHostnameDialog(int layoutRes, int titleRes, int positiveRes, @Nullable String initialHost,
                                     Function<String, Boolean> validator, BiConsumer<String, String> onConfirm) {
        View view = LayoutInflater.from(this).inflate(layoutRes, null);
        EditText hostnameEditText = view.findViewById(R.id.list_dialog_hostname);
        if (initialHost != null) {
            hostnameEditText.setText(initialHost);
            hostnameEditText.setSelection(initialHost.length());
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCancelable(true)
                .setTitle(titleRes)
                .setView(view)
                .setPositiveButton(positiveRes, (d, which) -> {
                    d.dismiss();
                    String host = hostnameEditText.getText().toString();
                    if (Boolean.TRUE.equals(validator.apply(host))) {
                        onConfirm.accept(host, null);
                    }
                })
                .setNegativeButton(R.string.button_cancel, (d, which) -> d.dismiss())
                .create();
        dialog.show();
        hostnameEditText.addTextChangedListener(new AlertDialogValidator(dialog, validator, initialHost != null));
    }

    /**
     * The add/edit dialog for a Redirected entry: hostname plus target IP. See
     * {@link #showHostnameDialog}; the two are kept separate rather than parameterised over field
     * count, since the redirected case validates both fields together regardless of which one
     * changed, exactly as {@code RedirectedHostsFragment} does on mobile.
     */
    private void showRedirectedDialog(int titleRes, int positiveRes, @Nullable String initialHost,
                                       @Nullable String initialIp, BiConsumer<String, String> onConfirm) {
        View view = LayoutInflater.from(this).inflate(R.layout.lists_redirected_dialog, null);
        EditText hostnameEditText = view.findViewById(R.id.list_dialog_hostname);
        EditText ipEditText = view.findViewById(R.id.list_dialog_ip);
        if (initialHost != null) {
            hostnameEditText.setText(initialHost);
            hostnameEditText.setSelection(initialHost.length());
        }
        if (initialIp != null) {
            ipEditText.setText(initialIp);
        }
        Function<String, Boolean> validator = ignored ->
                RegexUtils.isValidHostname(hostnameEditText.getText().toString())
                        && RegexUtils.isValidIP(ipEditText.getText().toString());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCancelable(true)
                .setTitle(titleRes)
                .setView(view)
                .setPositiveButton(positiveRes, (d, which) -> {
                    d.dismiss();
                    String host = hostnameEditText.getText().toString();
                    String ip = ipEditText.getText().toString();
                    if (RegexUtils.isValidHostname(host) && RegexUtils.isValidIP(ip)) {
                        onConfirm.accept(host, ip);
                    }
                })
                .setNegativeButton(R.string.button_cancel, (d, which) -> d.dismiss())
                .create();
        dialog.show();
        AlertDialogValidator validatorWatcher = new AlertDialogValidator(dialog, validator, initialHost != null);
        hostnameEditText.addTextChangedListener(validatorWatcher);
        ipEditText.addTextChangedListener(validatorWatcher);
    }
}
