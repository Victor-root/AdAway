package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import timber.log.Timber;

/**
 * Receives the {@link DownloadManager#ACTION_DOWNLOAD_COMPLETE} broadcast for the APK
 * download queued by {@link UpdateModel} and hands the result to {@link ApkInstaller},
 * which verifies it before the system package installer ever sees it.
 */
public class ApkDownloadReceiver extends BroadcastReceiver {
    private final long downloadId;

    public ApkDownloadReceiver(long downloadId) {
        this.downloadId = downloadId;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (this.downloadId != id) {
            return;
        }
        // ACTION_DOWNLOAD_COMPLETE also fires for a download that failed or was interrupted,
        // leaving a partial file behind. Without this check that truncated file was handed
        // straight to the installer.
        if (!isDownloadSuccessful(context, id)) {
            Timber.w("Download %d did not complete successfully; discarding it.", id);
            ApkInstaller.clearDownloads(context);
            return;
        }
        ApkInstaller.verifyAndInstall(context, ApkInstaller.getStagedApk(context));
    }

    private boolean isDownloadSuccessful(Context context, long id) {
        DownloadManager downloadManager = context.getSystemService(DownloadManager.class);
        if (downloadManager == null) {
            return false;
        }
        try (Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) {
                return false;
            }
            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusColumn < 0) {
                return false;
            }
            return cursor.getInt(statusColumn) == DownloadManager.STATUS_SUCCESSFUL;
        } catch (Exception e) {
            Timber.w(e, "Could not read the download status.");
            return false;
        }
    }
}
