package org.adaway.util.log;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import timber.log.Timber;

/**
 * A {@link Timber.Tree} that persists {@code INFO} and above to the exportable {@link
 * DiagnosticLog}. Lower priorities (the high-frequency {@code DEBUG}/{@code VERBOSE} packet trace)
 * are dropped by the priority gate, and the per-query DNS result trace, which is logged at
 * {@code INFO} but is just as noisy, is dropped by message prefix (see
 * {@link #DNS_QUERY_TRACE_PREFIX}), so the on-disk log stays small and meaningful.
 *
 * @author AdAway Community
 */
public final class DiagnosticLogTree extends Timber.Tree {
    /**
     * {@link DateTimeFormatter} is immutable and thread-safe, unlike {@code SimpleDateFormat}:
     * Timber invokes {@link #log} on the calling thread, so entries may be formatted from several
     * threads at once (VPN worker, main thread, …).
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.US);
    /**
     * Prefix of the per-DNS-query result trace ("… DNS Name x blocked!/allowed/redirected"),
     * which the DNS proxies log at {@code INFO} for <em>every single query</em>, many per
     * second. Persisting those would flood the bounded diagnostic file and push the meaningful
     * lifecycle/error events out of it. The blocked/allowed domains are already inspectable via
     * the dedicated DNS log ("Journal DNS"), so they are dropped here. DNS <em>error</em> traces
     * ("Discarding invalid packet", "Failed to query…") do not match this prefix and are kept.
     */
    private static final String DNS_QUERY_TRACE_PREFIX = "handleDnsRequest: DNS Name ";
    /**
     * The other per-query traces that carry the resolved host name. The prefix above only caught
     * the three result traces, so a failing query still wrote the host name into a log built to
     * be shared, contradicting what the preference promises the user. Failures are still visible
     * in the log, just without naming the host.
     */
    private static final String[] HOST_NAME_TRACE_PREFIXES = {
            "Failed to get inet address for host ",
            "Failed to query DNS Name ",
            "No address was found for DNS Name ",
    };

    private final DiagnosticLog log;

    /**
     * Constructor.
     *
     * @param log The diagnostic log to persist entries to.
     */
    public DiagnosticLogTree(DiagnosticLog log) {
        this.log = log;
    }

    @Override
    protected boolean isLoggable(@Nullable String tag, int priority) {
        // Recording is opt-in (off by default): skip everything, cheaply, until the user turns
        // it on in Preferences > VPN > Record diagnostic log.
        return priority >= Log.INFO && this.log.isEnabled();
    }

    @Override
    protected void log(int priority, @Nullable String tag, @NonNull String message, @Nullable Throwable t) {
        // Drop the high-frequency per-query DNS trace so it does not flood the bounded log.
        if (message.startsWith(DNS_QUERY_TRACE_PREFIX)) {
            return;
        }
        // Drop the remaining traces naming a resolved host: this log is meant to be shared, and
        // the preference states it never records browsing history.
        for (String prefix : HOST_NAME_TRACE_PREFIXES) {
            if (message.startsWith(prefix)) {
                this.log.append(redactedLine(priority, tag, prefix));
                return;
            }
        }
        try {
            String line = LocalDateTime.now().format(TIMESTAMP_FORMAT) +
                    ' ' + priorityChar(priority) +
                    '/' + (tag == null ? "" : tag) +
                    ": " + message;
            this.log.append(line);
            if (t != null) {
                this.log.append(Log.getStackTraceString(t));
            }
        } catch (RuntimeException e) {
            // Logging must never crash the app.
        }
    }

    /**
     * Keep the fact that a query failed, drop the host it was for.
     *
     * @param priority The log priority.
     * @param tag      The log tag.
     * @param prefix   The matched prefix, kept as is.
     * @return The log line with the host name replaced by a placeholder.
     */
    private static String redactedLine(int priority, @Nullable String tag, String prefix) {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT) +
                ' ' + priorityChar(priority) +
                '/' + (tag == null ? "" : tag) +
                ": " + prefix + "(host hidden)";
    }

    private static char priorityChar(int priority) {
        switch (priority) {
            case Log.INFO:
                return 'I';
            case Log.WARN:
                return 'W';
            case Log.ERROR:
                return 'E';
            case Log.ASSERT:
                return 'A';
            default:
                return 'D';
        }
    }
}
