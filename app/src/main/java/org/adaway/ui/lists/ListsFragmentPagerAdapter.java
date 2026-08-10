package org.adaway.ui.lists;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import org.adaway.ui.lists.type.AbstractListFragment;
import org.adaway.ui.lists.type.AllowedHostsFragment;
import org.adaway.ui.lists.type.BlockedHostsFragment;
import org.adaway.ui.lists.type.RedirectedHostsFragment;

import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;

/**
 * This class is a {@link FragmentStateAdapter} to store lists tab fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class ListsFragmentPagerAdapter extends FragmentStateAdapter {
    /**
     * The number of fragment.
     */
    private static final int FRAGMENT_COUNT = 3;
    /**
     * The blacklist fragment (<code>null</code> until first retrieval).
     */
    private final AbstractListFragment blacklistFragment;
    /**
     * The whitelist fragment (<code>null</code> until first retrieval).
     */
    private final AbstractListFragment whitelistFragment;
    /**
     * The redirection list fragment (<code>null</code> until first retrieval).
     */
    private final AbstractListFragment redirectionListFragment;

    /**
     * Constructor.
     *
     */
    ListsFragmentPagerAdapter(FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        this.blacklistFragment = new BlockedHostsFragment();
        this.whitelistFragment = new AllowedHostsFragment();
        this.redirectionListFragment = new RedirectedHostsFragment();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case BLOCKED_HOSTS_TAB:
                return this.blacklistFragment;
            case ALLOWED_HOSTS_TAB:
                return this.whitelistFragment;
            case REDIRECTED_HOSTS_TAB:
                return this.redirectionListFragment;
            default:
                throw new IllegalStateException("Position " + position + " is not supported.");
        }
    }

    @Override
    public int getItemCount() {
        return FRAGMENT_COUNT;
    }

    /**
     * Ensure action mode is cancelled.
     */
    void ensureActionModeCanceled() {
        if (this.blacklistFragment != null) {
            this.blacklistFragment.ensureActionModeCanceled();
        }
        if (this.whitelistFragment != null) {
            this.whitelistFragment.ensureActionModeCanceled();
        }
        if (this.redirectionListFragment != null) {
            this.redirectionListFragment.ensureActionModeCanceled();
        }
    }

    /**
     * Get the fragment for a tab position.
     * <p>
     * Fields, not {@link #createFragment}: the three fragments are created once in the
     * constructor and kept alive for the activity's lifetime (see the fields above), so this is
     * a plain lookup, not a fetch from the {@link androidx.viewpager2.widget.ViewPager2}'s own
     * (possibly not yet created, and per adjacent-tab preload not reliably "the selected one")
     * page views.
     *
     * @param position The tab position.
     * @return The fragment at that position, or <code>null</code> if the position is out of range.
     */
    @Nullable
    AbstractListFragment getFragment(int position) {
        switch (position) {
            case BLOCKED_HOSTS_TAB:
                return this.blacklistFragment;
            case ALLOWED_HOSTS_TAB:
                return this.whitelistFragment;
            case REDIRECTED_HOSTS_TAB:
                return this.redirectionListFragment;
            default:
                return null;
        }
    }

    /**
     * Add an item into the requested fragment.
     *
     * @param position The fragment position.
     */
    void addItem(int position) {
        switch (position) {
            case BLOCKED_HOSTS_TAB:
                if (this.blacklistFragment != null) {
                    this.blacklistFragment.addItem();
                }
                break;
            case ALLOWED_HOSTS_TAB:
                if (this.whitelistFragment != null) {
                    this.whitelistFragment.addItem();
                }
                break;
            case REDIRECTED_HOSTS_TAB:
                if (this.redirectionListFragment != null) {
                    this.redirectionListFragment.addItem();
                }
                break;
        }
    }
}
