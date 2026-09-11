package org.adaway.ui.welcome;

import androidx.fragment.app.Fragment;

/**
 * This class is the base fragment to all setup fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class WelcomeFragment extends Fragment {
    private boolean goNext = false;

    protected void allowNext() {
        this.goNext = true;
        getNavigable().allowNext();
    }

    protected void blockNext() {
        this.goNext = false;
        getNavigable().blockNext();
    }

    protected boolean canGoNext() {
        return this.goNext;
    }

    /**
     * Whether the "Next" button should stay hidden while this page hasn't called
     * {@link #allowNext()} yet. Most pages need the user to actually complete a required
     * choice or action, so hiding it is the right default; a page whose step can safely be
     * left to finish in the background (see {@link WelcomeSyncFragment}) overrides this to
     * keep the button visible and handle an early tap itself via {@link #requestLeave}.
     */
    protected boolean shouldHideNextWhileBlocked() {
        return true;
    }

    /**
     * Called when the user taps "Next" while this page is the current one. The default just
     * proceeds immediately; a page that isn't done yet but can be safely left anyway (again,
     * {@link WelcomeSyncFragment}) can override this to ask for confirmation first and only
     * run {@code proceed} once the user actually agrees to move on.
     *
     * @param proceed Advances to the next page; call it to actually leave.
     */
    protected void requestLeave(Runnable proceed) {
        proceed.run();
    }

    private WelcomeNavigable getNavigable() {
        return (WelcomeNavigable) getActivity();
    }
}
