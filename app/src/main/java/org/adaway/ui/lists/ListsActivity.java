package org.adaway.ui.lists;

import static android.content.Intent.ACTION_SEARCH;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.SearchView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.lists.type.AbstractListFragment;

/**
 * This activity display hosts list items.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsActivity extends AppCompatActivity {
    /**
     * The tab to display argument.
     */
    public static final String TAB = "org.adaway.lists.tab";
    /**
     * The blocked hosts tab index.
     */
    public static final int BLOCKED_HOSTS_TAB = 0;
    /**
     * The allowed hosts tab index.
     */
    public static final int ALLOWED_HOSTS_TAB = 1;
    /**
     * The redirected hosts tab index.
     */
    public static final int REDIRECTED_HOSTS_TAB = 2;
    /**
     * The view model.
     */
    private ListsViewModel listsViewModel;
    /**
     * The back press callback.
     */
    private OnBackPressedCallback onBackPressedCallback;
    /**
     * The tab view pager, and the adapter giving access to its (permanently kept alive, see
     * {@link ListsFragmentPagerAdapter}) fragments. Kept as fields, rather than the local
     * variables they used to be, so {@link #dispatchKeyEvent} can reach the fragment behind the
     * currently selected tab.
     */
    private ViewPager2 viewPager;
    private ListsFragmentPagerAdapter pagerAdapter;
    /**
     * The bottom tab bar and the add button, both needed by {@link #dispatchKeyEvent} to
     * recognise the D-pad crossings it redirects.
     */
    private BottomNavigationView navigationView;
    private FloatingActionButton addActionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        /*
         * Set view content.
         */
        setContentView(R.layout.lists_fragment);
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /*
         * Configure back press callback.
         */
        this.onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                ListsActivity.this.listsViewModel.clearSearch();
                ListsActivity.this.onBackPressedCallback.setEnabled(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this.onBackPressedCallback);
        /*
         * Configure tabs.
         */
        // Get view pager
        this.viewPager = findViewById(R.id.lists_view_pager);
        ViewPager2 viewPager = this.viewPager;
        // Create pager adapter
        this.pagerAdapter = new ListsFragmentPagerAdapter(this);
        ListsFragmentPagerAdapter pagerAdapter = this.pagerAdapter;
        // Set view pager adapter
        viewPager.setAdapter(pagerAdapter);
        // Get navigation view
        this.navigationView = findViewById(R.id.navigation);
        BottomNavigationView navigationView = this.navigationView;
        // White indicator that sits on the bottom edge of the active tab (like a tab indicator).
        View navIndicator = findViewById(R.id.nav_indicator);
        // Position it under the initial tab once the bar has been measured, and keep it aligned
        // if the bar is re-laid out (e.g. on rotation).
        navigationView.post(() -> positionIndicator(navIndicator, navigationView, viewPager.getCurrentItem(), false));
        navigationView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                positionIndicator(navIndicator, navigationView, viewPager.getCurrentItem(), false));
        // Add view pager on page listener to set selected tab according the selected page
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                navigationView.getMenu().getItem(position).setChecked(true);
                positionIndicator(navIndicator, navigationView, position, true);
                pagerAdapter.ensureActionModeCanceled();
            }
        });
        // Add navigation view item selected listener to change view pager current item
        navigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.lists_navigation_blocked) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (item.getItemId() == R.id.lists_navigation_allowed) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (item.getItemId() == R.id.lists_navigation_redirected) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });
        // Display requested tab
        Intent intent = getIntent();
        int tab = intent.getIntExtra(TAB, BLOCKED_HOSTS_TAB);
        viewPager.setCurrentItem(tab);
        /*
         * Configure add action button.
         */
        // Get the add action button
        this.addActionButton = findViewById(R.id.lists_add);
        FloatingActionButton addActionButton = this.addActionButton;
        // Set add action button listener
        addActionButton.setOnClickListener(clickedView -> {
            // Get current fragment position
            int currentItemPosition = viewPager.getCurrentItem();
            // Add item to the current fragment
            pagerAdapter.addItem(currentItemPosition);
        });
        /*
         * Configure snackbar.
         */
        // Get lists layout to attached snackbar to
        CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator);
        // Create apply snackbar
        ApplyConfigurationSnackbar applySnackbar = new ApplyConfigurationSnackbar(coordinatorLayout, false, false);
        // Bind snackbar to view models
        this.listsViewModel = new ViewModelProvider(this).get(ListsViewModel.class);
        this.listsViewModel.getModelChanged().observe(this, applySnackbar.createObserver());
        // Get the intent, verify the action and get the query
        handleQuery(intent);
    }

    /**
     * Place the white active-tab indicator on the bottom edge of the given tab, centred under
     * its actual rendered bounds.
     * <p>
     * This deliberately does not assume the bar's width is divided evenly between items
     * (bar width / item count): BottomNavigationView caps each item's width and centres the
     * item row when the bar is wider than the items need, which barely ever happens on a phone
     * but is the common case on a TV screen, and an even-split assumption would then land the
     * indicator under empty bar space instead of under the tab.
     *
     * @param indicator The indicator view.
     * @param nav       The bottom navigation bar.
     * @param position  The selected tab position.
     * @param animate   Whether to animate the move (true on user selection, false on initial/layout).
     */
    private void positionIndicator(View indicator, BottomNavigationView nav, int position, boolean animate) {
        if (nav.getWidth() == 0) {
            return;
        }
        View itemView = findItemView(nav, position);
        float targetX;
        if (itemView != null) {
            View indicatorParent = (View) indicator.getParent();
            float itemLeft = getLeftRelativeTo(itemView, indicatorParent);
            float itemCenterX = itemLeft + itemView.getWidth() / 2f;
            targetX = itemCenterX - indicator.getWidth() / 2f;
        } else {
            // Fallback if BottomNavigationView's internal view hierarchy ever changes shape.
            int itemCount = nav.getMenu().size();
            if (itemCount == 0) {
                return;
            }
            int itemWidth = nav.getWidth() / itemCount;
            targetX = (float) position * itemWidth + (itemWidth - indicator.getWidth()) / 2f;
        }
        if (animate) {
            indicator.animate().translationX(targetX).setDuration(200).start();
        } else {
            indicator.setTranslationX(targetX);
        }
    }

    /**
     * Find the actual rendered view for the tab at the given menu position, walking
     * BottomNavigationView's internal item container (its sole child). Material does not expose
     * per-item bounds via public API.
     */
    @Nullable
    private static View findItemView(BottomNavigationView nav, int position) {
        if (nav.getChildCount() == 0) {
            return null;
        }
        View menuView = nav.getChildAt(0);
        if (!(menuView instanceof ViewGroup)) {
            return null;
        }
        ViewGroup menuViewGroup = (ViewGroup) menuView;
        if (position < 0 || position >= menuViewGroup.getChildCount()) {
            return null;
        }
        return menuViewGroup.getChildAt(position);
    }

    /**
     * Sum {@code getLeft()} from {@code view} up through its ancestors to {@code ancestor}
     * (exclusive), giving view's horizontal position in ancestor's coordinate space regardless
     * of how many view groups sit in between.
     */
    private static float getLeftRelativeTo(View view, View ancestor) {
        float left = 0;
        View current = view;
        while (current != null && current != ancestor) {
            left += current.getLeft();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return left;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleQuery(intent);
    }

    private void handleQuery(Intent intent) {
        if (ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            this.listsViewModel.search(query);
            this.onBackPressedCallback.setEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_menu, menu);
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        if (searchManager != null) {
            SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            searchView.setIconifiedByDefault(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_toggle_source) {
            this.listsViewModel.toggleSources();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Redirect the D-pad crossings this screen's layout leads the platform's default focus
     * search astray on.
     * <p>
     * The add button floats over the list via a {@code CoordinatorLayout} anchor, and the list
     * itself is a {@code RecyclerView} inside a tab of {@link #viewPager}: geometric search has no
     * plain, stable neighbour to line either of them up with the header or the bottom tab bar, so
     * without this, up from the tab bar jumped straight past both the list and the add button, and
     * the add button was not reachable by D-pad at all. This only covers the crossings that were
     * actually broken; movement within the list, and between it and the header, is left to the
     * platform's default search.
     *
     * @param event The key event being dispatched.
     * @return <code>true</code> if this consumed the event, the superclass's result otherwise.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && this.addActionButton != null) {
            View focused = getCurrentFocus();
            int keyCode = event.getKeyCode();
            if (focused != null) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                        && isOrIsDescendantOf(focused, this.navigationView)
                        && this.addActionButton.requestFocus()) {
                    return true;
                }
                if (focused == this.addActionButton
                        && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP)
                        && focusCurrentTabLastItem()) {
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        && isCurrentTabListRow(focused)
                        && this.addActionButton.requestFocus()) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean focusCurrentTabLastItem() {
        AbstractListFragment fragment = this.pagerAdapter.getFragment(this.viewPager.getCurrentItem());
        return fragment != null && fragment.focusLastItem();
    }

    private boolean isCurrentTabListRow(View view) {
        AbstractListFragment fragment = this.pagerAdapter.getFragment(this.viewPager.getCurrentItem());
        return fragment != null && view.getParent() == fragment.getRecyclerView();
    }

    private static boolean isOrIsDescendantOf(View view, View ancestor) {
        for (View current = view; current != null; ) {
            if (current == ancestor) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
}
