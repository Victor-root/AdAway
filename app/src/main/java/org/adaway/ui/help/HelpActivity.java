/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.help;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;

public class HelpActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.help_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        ViewPager2 viewPager = findViewById(R.id.pager);
        TabsAdapter pagerAdapter = new TabsAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabLayout);

        new TabLayoutMediator(
                tabLayout,
                viewPager,
                (tab, position) -> tab.setText(getTabName(position))
        ).attach();

        if (isTv()) {
            setUpTvTabFocus(tabLayout, pagerAdapter);
        }
    }

    private @StringRes
    int getTabName(int position) {
        switch (position) {
            case 0:
                return R.string.help_tab_faq;
            case 1:
                return R.string.help_tab_problems;
            case 2:
                return R.string.help_tab_about;
            default:
                throw new IllegalStateException("Position " + position + " is not supported.");
        }
    }

    /**
     * Wire the TabLayout's tabs (FAQ/Problems/About) as the D-pad entry point for this screen:
     * focus lands on the first tab on open, left/right moves between tabs and selects as it
     * goes (a TV tab strip has no separate D-pad-centre confirmation step, unlike a button), up
     * is trapped on the row instead of escaping to the (non-interactive) header, and down moves
     * into the selected tab's content. {@link HelpFragmentHtml} and {@link AboutFragment} send
     * focus back here via {@link #focusSelectedTab(Activity)} when their content is scrolled to
     * the top and up is pressed again.
     */
    private void setUpTvTabFocus(TabLayout tabLayout, TabsAdapter pagerAdapter) {
        int tabCount = tabLayout.getTabCount();
        View[] tabViews = new View[tabCount];
        for (int i = 0; i < tabCount; i++) {
            View tabView = getTabView(tabLayout, i);
            if (tabView == null) {
                return;
            }
            if (tabView.getId() == View.NO_ID) {
                tabView.setId(View.generateViewId());
            }
            tabView.setFocusable(true);
            tabViews[i] = tabView;
        }
        for (int i = 0; i < tabCount; i++) {
            View tabView = tabViews[i];
            tabView.setNextFocusUpId(tabView.getId());
            if (i > 0) {
                tabView.setNextFocusLeftId(tabViews[i - 1].getId());
            }
            if (i < tabCount - 1) {
                tabView.setNextFocusRightId(tabViews[i + 1].getId());
            }
            int position = i;
            tabView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    TabLayout.Tab tab = tabLayout.getTabAt(position);
                    if (tab != null) {
                        tab.select();
                    }
                }
            });
            tabView.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                Fragment fragment = pagerAdapter.createFragment(tabLayout.getSelectedTabPosition());
                View content = fragment.getView();
                if (content != null) {
                    content.requestFocus();
                }
                return true;
            });
        }
        tabLayout.post(() -> tabViews[0].requestFocus());
    }

    /**
     * Find the actual rendered view for the tab at the given position, walking TabLayout's
     * internal tab strip (its sole child). Material does not expose per-tab bounds via public
     * API.
     */
    @Nullable
    static View getTabView(TabLayout tabLayout, int position) {
        if (tabLayout.getChildCount() == 0) {
            return null;
        }
        View stripView = tabLayout.getChildAt(0);
        if (!(stripView instanceof ViewGroup)) {
            return null;
        }
        ViewGroup tabStrip = (ViewGroup) stripView;
        if (position < 0 || position >= tabStrip.getChildCount()) {
            return null;
        }
        return tabStrip.getChildAt(position);
    }

    /**
     * Send D-pad focus back to the currently selected tab. Called by the help fragments when
     * their content is scrolled to the top and up is pressed again.
     */
    static void focusSelectedTab(Activity activity) {
        TabLayout tabLayout = activity.findViewById(R.id.tabLayout);
        if (tabLayout == null) {
            return;
        }
        View tabView = getTabView(tabLayout, tabLayout.getSelectedTabPosition());
        if (tabView != null) {
            tabView.requestFocus();
        }
    }

    private boolean isTv() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private static class TabsAdapter extends FragmentStateAdapter {
        private final Fragment faqFragment = HelpFragmentHtml.newInstance(R.raw.help_faq);
        private final Fragment problemsFragment = HelpFragmentHtml.newInstance(R.raw.help_problems);
        private final Fragment aboutFragment = new AboutFragment();

        TabsAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return this.faqFragment;
                case 1:
                    return this.problemsFragment;
                case 2:
                    return this.aboutFragment;
                default:
                    throw new IllegalStateException("Position " + position + " is not supported.");
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
