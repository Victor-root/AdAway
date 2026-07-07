package org.adaway.util.log;

import android.content.Context;

import androidx.annotation.NonNull;

import org.adaway.helper.PreferenceHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A tiny, self-bounding diagnostic log that persists important events to a file on disk so the
 * user can export them later — even after the VPN service has been killed by the system (which
 * would otherwise take any in-memory log down with it).
 * <p>
 * Only {@code INFO} and above are captured (see {@link DiagnosticLogTree}), so the file stays
 * small and readable: no per-packet DNS spam, just lifecycle events, network transitions,
 * warnings and errors — exactly what is needed to diagnose why the VPN stopped.
 * <p>
 * Recording is opt-in and off by default (see {@link #isEnabled()}): nothing is written to disk
 * until the user turns on "Record diagnostic log" in the VPN preferences.
 * <p>
 * The log rotates into a single backup once the active file exceeds {@link #MAX_FILE_SIZE}, so at
 * most {@code 2 × MAX_FILE_SIZE} of history is ever kept on disk. All file access happens on a
 * dedicated single-threaded executor: {@link #append(String)} never blocks the caller (VPN worker,
 * main thread, …) on disk I/O, and {@link #read()} / {@link #clear()} are serialized behind any
 * pending writes so a read always reflects everything logged so far.
 *
 * @author AdAway Community
 */
public final class DiagnosticLog {
    /**
     * Maximum size of the active log file before it rotates into the backup, in bytes.
     * Kept small on purpose: the whole point is a lightweight, exportable trail.
     */
    private static final int MAX_FILE_SIZE = 100 * 1024;
    private static final String LOG_FILE_NAME = "diagnostic.log";
    private static final String BACKUP_FILE_NAME = "diagnostic.log.1";

    private static volatile DiagnosticLog instance;

    private final File logFile;
    private final File backupFile;
    private final ExecutorService executor;
    private final AtomicBoolean enabled;

    private DiagnosticLog(Context context) {
        File directory = context.getFilesDir();
        this.logFile = new File(directory, LOG_FILE_NAME);
        this.backupFile = new File(directory, BACKUP_FILE_NAME);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "diagnostic-log");
            thread.setDaemon(true);
            return thread;
        });
        // Seed from the persisted preference so recording resumes across app restarts if the
        // user previously turned it on.
        this.enabled = new AtomicBoolean(PreferenceHelper.getVpnDiagnosticLogEnabled(context));
    }

    /**
     * Whether recording is currently turned on. Checked by {@link DiagnosticLogTree} before every
     * write, so nothing touches disk while the user has not opted in.
     */
    public boolean isEnabled() {
        return this.enabled.get();
    }

    /**
     * Turn recording on or off immediately (in-memory). The Preference widget itself is
     * responsible for persisting the choice to disk; this only updates the cached flag so the
     * change takes effect on the very next log line, without needing an app restart.
     *
     * @param enabled The new recording state.
     */
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    /**
     * Get the singleton instance, bound to the application context.
     *
     * @param context Any context (the application context is used internally).
     * @return The diagnostic log instance.
     */
    public static DiagnosticLog getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (DiagnosticLog.class) {
                if (instance == null) {
                    instance = new DiagnosticLog(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Append a formatted line to the log. Non-blocking: the write is queued on the log executor.
     *
     * @param line The already-formatted line (without trailing newline).
     */
    void append(String line) {
        this.executor.execute(() -> writeLine(line));
    }

    /**
     * Read the whole captured log (backup followed by the active file, oldest events first).
     * <p>
     * This blocks until every previously queued write has been flushed, so it must be called
     * from a background thread — never the main thread.
     *
     * @return The captured log, or an empty string if nothing has been captured (or on error).
     */
    @NonNull
    public String read() {
        try {
            // Cast to Callable so the submit(Callable) overload is chosen unambiguously.
            return this.executor.submit((Callable<String>) this::readAll).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException e) {
            return "";
        }
    }

    /**
     * Clear the whole captured log. Non-blocking: the deletion is queued on the log executor.
     */
    public void clear() {
        this.executor.execute(() -> {
            //noinspection ResultOfMethodCallIgnored
            this.logFile.delete();
            //noinspection ResultOfMethodCallIgnored
            this.backupFile.delete();
        });
    }

    private void writeLine(String line) {
        try {
            if (this.logFile.length() >= MAX_FILE_SIZE) {
                rotate();
            }
            Files.write(
                    this.logFile.toPath(),
                    (line + '\n').getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException | RuntimeException e) {
            // Logging must never crash the app: swallow any failure to persist a line.
        }
    }

    private void rotate() {
        //noinspection ResultOfMethodCallIgnored
        this.backupFile.delete();
        //noinspection ResultOfMethodCallIgnored
        this.logFile.renameTo(this.backupFile);
    }

    private String readAll() {
        StringBuilder builder = new StringBuilder();
        appendFileContent(builder, this.backupFile);
        appendFileContent(builder, this.logFile);
        return builder.toString();
    }

    private void appendFileContent(StringBuilder builder, File file) {
        if (!file.exists()) {
            return;
        }
        try {
            builder.append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // Ignore an unreadable file: return whatever could be read.
        }
    }
}
