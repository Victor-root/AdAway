package org.adaway.util.log;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.SentryLevel.WARNING;

import android.app.Application;

import org.adaway.helper.PreferenceHelper;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;

/**
 * This class is a helper to initialize and configuration Sentry.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class SentryLog {
    /**
     * Private constructor.
     */
    private SentryLog() {

    }

    /**
     * Initialize Sentry logging client according user preferences.
     *
     * @param application The application instance.
     */
    public static void init(Application application) {
        setEnabled(application, PreferenceHelper.getTelemetryEnabled(application));
    }

    /**
     * Initialize Sentry logging client.
     *
     * @param application The application instance.
     * @param enabled Whether the application is allowed to send events to Sentry or not.
     */
    public static void setEnabled(Application application, boolean enabled) {
        if (enabled) {
            // Initialize sentry client manually and bind it to logging.
            // Breadcrumbs start at WARNING, not INFO: the VPN logs every resolved host name at
            // INFO, so an INFO threshold attached the user's recent browsing to every report.
            SentryAndroid.init(application, options -> {
                options.addIntegration(new SentryTimberIntegration(ERROR, WARNING));
                options.addIntegration(new FragmentLifecycleIntegration(application, true, false));
            });
        } else {
            // Turning the preference off used to do nothing at all: an already initialized
            // client kept reporting for the rest of the process life. Close it so the switch
            // takes effect immediately, as the user expects.
            Sentry.close();
        }
    }

    /**
     * Record a breadcrumb.
     *
     * @param message The breadcrumb message.
     */
    public static void recordBreadcrumb(String message) {
        Sentry.configureScope(scope -> {
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage(message);
            breadcrumb.setLevel(INFO);
            scope.addBreadcrumb(breadcrumb);
        });
    }

    /**
     * Check if {@link Sentry} implementation is a stub or not.
     *
     * @return {@code true} if the runtime implementation is a stub, {@code false} otherwise.
     */
    public static boolean isStub() {
        try {
            Sentry.class.getDeclaredField("STUB");
            return true;
        } catch (NoSuchFieldException exception) {
            return false;
        }
    }
}
