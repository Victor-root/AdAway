package org.adaway.ui.help;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.adaway.R;

public class AboutFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.about_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView versionTextView = view.findViewById(R.id.about_version);
        versionTextView.setText(getString(R.string.about_version, getVersionName(requireContext())));

        setLink(view, R.id.about_source_link, "https://github.com/Victor-root/AdAway-Community");
        setLink(view, R.id.about_issues_link, "https://github.com/Victor-root/AdAway-Community/issues");
        setLink(view, R.id.about_releases_link, "https://github.com/Victor-root/AdAway-Community/releases");
        setLink(view, R.id.about_upstream_link, "https://github.com/AdAway/AdAway");
    }

    @Override
    public void onResume() {
        super.onResume();
        // Requesting focus here, not in onViewCreated(): this fragment's view can be
        // created early while its tab is merely adjacent to the selected one (the
        // ViewPager2 pre-lays-out neighbours for smooth swiping), so a one-time
        // request at creation either fires before the tab is actually visible or
        // gets stolen by a later-created neighbour. FragmentStateAdapter only
        // resumes the fragment behind the currently selected tab, so onResume()
        // fires exactly when this tab becomes the visible one, every time,
        // including on swipe-back. Posted so it runs after layout settles.
        if (isTv(requireContext())) {
            View view = getView();
            if (view != null) {
                view.post(view::requestFocus);
            }
        }
    }

    private void setLink(View parent, int viewId, String url) {
        View link = parent.findViewById(viewId);
        link.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        );
        if (isTv(link.getContext())) {
            // A click listener makes this row D-pad-focusable by default, so D-pad
            // up/down jumps link-to-link instead of scrolling; there is no
            // D-pad-friendly way to open these links anyway, so skip them on TV.
            link.setFocusable(false);
        }
    }

    private static boolean isTv(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private static String getVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
