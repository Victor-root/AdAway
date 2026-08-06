package org.adaway.ui.help;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
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

        if (isTv(requireContext())) {
            // This screen's tab strip (HelpActivity) sends focus down into this
            // ScrollView on D-pad down; mirror that back once scrolled to the top,
            // instead of leaving up to fall through to default focus search.
            view.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN
                        && !view.canScrollVertically(-1)) {
                    HelpActivity.focusSelectedTab(requireActivity());
                    return true;
                }
                return false;
            });
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

    // getPackageInfo(String, int) is deprecated in favour of a PackageInfoFlags overload that
    // only exists from Android 13 on, and the minimum supported here is Android 8.
    @SuppressWarnings("deprecation")
    private static String getVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
