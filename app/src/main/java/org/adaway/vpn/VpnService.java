/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.vpn;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.broadcast.CommandReceiver.SEND_COMMAND_ACTION;
import static org.adaway.helper.NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_RUNNING_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL;
import static org.adaway.vpn.VpnService.NetworkType.CELLULAR;
import static org.adaway.vpn.VpnService.NetworkType.WIFI;
import static org.adaway.vpn.VpnStatus.RECONNECTING;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.WAITING_FOR_NETWORK;
import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.adaway.R;
import org.adaway.broadcast.Command;
import org.adaway.broadcast.CommandReceiver;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.home.HomeActivity;
import org.adaway.vpn.worker.VpnWorker;

import java.lang.ref.WeakReference;

import timber.log.Timber;

/**
 * This class is the VPN platform service implementation.
 * <p>
 * it is in charge of:
 * <ul>
 * <li>Accepting service commands,</li>
 * <li>Starting / stopping the {@link VpnWorker} thread,</li>
 * <li>Publishing notifications and intent about the VPN state,</li>
 * <li>Reacting to network connectivity changes.</li>
 * </ul>
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnService extends android.net.VpnService implements Handler.Callback {
    public static final String VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";
    /*
     * Notification intent related.
     */
    private static final int REQUEST_CODE_START = 43;
    private static final int REQUEST_CODE_PAUSE = 42;
    /*
     * Handler related.
     */
    private static final int VPN_STATUS_UPDATE_MESSAGE_TYPE = 0;

    private final MyHandler handler;
    private final NetworkTypeCallback wifiNetworkCallback;
    private final NetworkTypeCallback cellularNetworkCallback;
    /**
     * Whether a Wi-Fi network is currently available on the device.
     */
    private boolean wifiAvailable;
    /**
     * Whether the available Wi-Fi network has validated internet connectivity
     * ({@link android.net.NetworkCapabilities#NET_CAPABILITY_VALIDATED}). Android only promotes
     * Wi-Fi to the default (active) network — the one that actually carries the tunnel's traffic
     * and whose DNS the {@link org.adaway.vpn.dns.DnsServerMapper} resolves — once it is
     * validated. Switching the tunnel to Wi-Fi before then would bind it to a DNS server that is
     * not yet reachable.
     */
    private boolean wifiValidated;
    /**
     * Whether a cellular network is currently available on the device.
     */
    private boolean cellularAvailable;
    /**
     * The transport the running tunnel is currently built for, or <code>null</code> when the VPN
     * is stopped (no network). The tunnel is rebuilt only when the transport Android would route
     * through actually changes — not when a secondary network merely appears or disappears (e.g.
     * the cellular radio flickering on Oppo / ColorOS power-saving devices while Wi-Fi stays the
     * default). This is the single source of truth that keeps the tunnel's DNS in sync with the
     * network carrying its traffic.
     */
    private NetworkType currentTransport;
    private final VpnWorker vpnWorker;

    /**
     * Constructor.
     */
    public VpnService() {
        this.handler = new MyHandler(this);
        this.wifiNetworkCallback = new NetworkTypeCallback(WIFI);
        this.cellularNetworkCallback = new NetworkTypeCallback(CELLULAR);
        this.wifiAvailable = false;
        this.wifiValidated = false;
        this.cellularAvailable = false;
        this.currentTransport = null;
        this.vpnWorker = new VpnWorker(this);
    }

    /*
     * VPN Service.
     */

    @Override
    public void onCreate() {
        Timber.d("Creating VPN service…");
        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Timber.d("onStartCommand %s", intent == null ? "null intent" : intent);
        // Null intent means the system is resurrecting the service via START_STICKY.
        // https://developer.android.com/reference/android/app/Service#START_STICKY
        // If the user has explicitly disabled the VPN since we last ran, refuse the
        // resurrection — otherwise Android can silently bring the VPN back on its own
        // (see issues #4022 / #4234).
        if (intent == null) {
            boolean userEnabled = PreferenceHelper.getVpnServiceUserEnabled(this);
            if (!userEnabled) {
                Timber.i("Refusing sticky resurrection: user has disabled the VPN.");
                stopSelf(startId);
                return START_NOT_STICKY;
            }
        }
        Command command = intent == null ?
                START :
                Command.readFromIntent(intent);
        switch (command) {
            case START:
                startVpn();
                return START_STICKY;
            case STOP:
                stopVpn();
                return START_NOT_STICKY;
            default:
                Timber.w("Unknown command: %s", command);
                return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        Timber.d("Destroying VPN service…");
        unregisterNetworkCallback();
        Timber.d("Destroyed VPN service.");
    }

    /*
     * Handler callback.
     */

    @Override
    public boolean handleMessage(@NonNull Message message) {
        if (message.what == VPN_STATUS_UPDATE_MESSAGE_TYPE) {
            updateVpnStatus(VpnStatus.fromCode(message.arg1));
        }
        return true;
    }

    /**
     * Notify a of the new VPN status.
     *
     * @param status The new VPN status.
     */
    public void notifyVpnStatus(VpnStatus status) {
        Message statusMessage = this.handler.obtainMessage(VPN_STATUS_UPDATE_MESSAGE_TYPE, status.toCode(), 0);
        this.handler.sendMessage(statusMessage);
    }

    private void startVpn() {
        Timber.d("Starting VPN service…");
        PreferenceHelper.setVpnServiceStatus(this, RUNNING);
        updateVpnStatus(STARTING);
        // This path is reached only via an explicit START intent (user click, notif
        // action, autostart, sticky resurrection that survived the user-intent gate).
        // The throttler is meant to dampen reconnection storms, NOT to delay user
        // actions — reset it so the tunnel comes up immediately.
        this.vpnWorker.resetThrottle();
        // Record the transport the tunnel is being built for so the network reconciler only
        // rebuilds it when the default network actually changes transport.
        this.currentTransport = computeDesiredTransport();
        this.vpnWorker.start();
        Timber.i("VPN service started.");
    }

    private void stopVpn() {
        Timber.d("Stopping VPN service…");
        PreferenceHelper.setVpnServiceStatus(this, STOPPED);
        this.currentTransport = null;
        this.vpnWorker.stop();
        stopForeground(true);
        stopSelf();
        updateVpnStatus(STOPPED);
        Timber.i("VPN service stopped.");
    }

    private void waitForNetVpn() {
        this.vpnWorker.stop();
        updateVpnStatus(WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(RECONNECTING);
        this.vpnWorker.start();
    }

    private void updateVpnStatus(VpnStatus status) {
        Notification notification = getNotification(status);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        switch (status) {
            case STARTING:
            case RUNNING:
                notificationManager.cancel(VPN_RESUME_SERVICE_NOTIFICATION_ID);
                try {
                    startForeground(VPN_RUNNING_SERVICE_NOTIFICATION_ID, notification);
                } catch (Exception e) {
                    // startForeground() can be denied by AppOps when the process is killed
                    // and restarted by the OEM system (e.g. battery manager). Force a clean
                    // stop so the UI reflects the real state instead of staying stuck on "pause".
                    Timber.e(e, "startForeground denied — forcing VPN stop.");
                    PreferenceHelper.setVpnServiceStatus(this, STOPPED);
                    Intent stoppedIntent = new Intent(VPN_UPDATE_STATUS_INTENT);
                    stoppedIntent.putExtra(VPN_UPDATE_STATUS_EXTRA, STOPPED);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(stoppedIntent);
                    stopSelf();
                    return;
                }
                break;
            default:
                if (checkSelfPermission(POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    notificationManager.notify(VPN_RESUME_SERVICE_NOTIFICATION_ID, notification);
                }
        }

        // TODO BUG - Nobody is listening to this intent
        // TODO BUG - VpnModel can lister to it to update the MainActivity according its current state
        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private Notification getNotification(VpnStatus status) {
        String title = getString(R.string.vpn_notification_title, getString(status.getTextResource()));

        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, VPN_SERVICE_NOTIFICATION_CHANNEL)
                .setPriority(IMPORTANCE_LOW)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(getColor(R.color.notification))
                .setContentTitle(title);
        switch (status) {
            case RUNNING:
                Intent stopIntent = new Intent(this, CommandReceiver.class)
                        .setAction(SEND_COMMAND_ACTION);
                STOP.appendToIntent(stopIntent);
                PendingIntent stopActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_PAUSE, stopIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        R.drawable.ic_pause_24dp,
                        getString(R.string.vpn_notification_action_pause),
                        stopActionIntent
                ).setOngoing(true);
                break;
            case STOPPED:
                Intent startIntent = new Intent(this, CommandReceiver.class)
                        .setAction(SEND_COMMAND_ACTION);
                START.appendToIntent(startIntent);
                PendingIntent startActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_START, startIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        0,
                        getString(R.string.vpn_notification_action_resume),
                        startActionIntent
                );
                break;
        }
        return builder.build();
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkRequest wifiNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        NetworkRequest cellularNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();
        initializeNetworkState(connectivityManager);
        connectivityManager.registerNetworkCallback(wifiNetworkRequest, this.wifiNetworkCallback, this.handler);
        connectivityManager.registerNetworkCallback(cellularNetworkRequest, this.cellularNetworkCallback, this.handler);

    }

    private void unregisterNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        connectivityManager.unregisterNetworkCallback(this.wifiNetworkCallback);
        connectivityManager.unregisterNetworkCallback(this.cellularNetworkCallback);
    }

    private void initializeNetworkState(ConnectivityManager connectivityManager) {
        this.wifiAvailable = false;
        this.wifiValidated = false;
        this.cellularAvailable = false;
        this.currentTransport = null;
        // Seed the state synchronously from the current networks. The per-transport callbacks
        // fire right after registration and will refine this, but seeding first means startVpn()
        // (which runs before the callbacks get a chance) already knows the transport to build for.
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null || capabilities.hasTransport(TRANSPORT_VPN)) {
                continue;
            }
            if (capabilities.hasTransport(TRANSPORT_WIFI)) {
                this.wifiAvailable = true;
                this.wifiValidated = capabilities.hasCapability(NET_CAPABILITY_VALIDATED);
            } else if (capabilities.hasTransport(TRANSPORT_CELLULAR)) {
                this.cellularAvailable = true;
            }
        }
        Timber.i("Initial network state: wifi=%s (validated=%s), cellular=%s.",
                this.wifiAvailable, this.wifiValidated, this.cellularAvailable);
    }

    /**
     * Determine which transport the tunnel should be built for, mirroring how Android chooses the
     * default (active) network: Wi-Fi is preferred over cellular, but only once it has validated
     * internet connectivity. Before Wi-Fi validates, Android keeps routing through cellular, so
     * the tunnel must too — otherwise its DNS (resolved from the active network) would not match
     * the network actually carrying the traffic.
     *
     * @return The transport to build the tunnel for, or <code>null</code> if no network is available.
     */
    private NetworkType computeDesiredTransport() {
        if (this.wifiAvailable && this.cellularAvailable) {
            // Both present: follow Android, which only promotes Wi-Fi to the default once it is
            // validated. Until then cellular remains the network carrying the traffic.
            return this.wifiValidated ? WIFI : CELLULAR;
        }
        if (this.wifiAvailable) {
            return WIFI;
        }
        if (this.cellularAvailable) {
            return CELLULAR;
        }
        return null;
    }

    /**
     * Reconcile the tunnel with the current default network.
     * <p>
     * This is the single decision point for every network change. It rebuilds the tunnel only
     * when the transport Android routes through actually changes — never on a mere secondary
     * network appearing or disappearing (e.g. the cellular radio flickering on ColorOS / Oppo
     * power-saving devices while Wi-Fi stays the default), which keeps the tunnel stable and the
     * status-bar VPN icon from blinking.
     */
    private void reconcile() {
        NetworkType desired = computeDesiredTransport();
        if (desired == null) {
            // No usable network at all: stop the tunnel and wait. A later reconcile() restarts it
            // when connectivity returns.
            if (this.currentTransport != null) {
                this.currentTransport = null;
                Timber.i("No network available, waiting for network…");
                waitForNetVpn();
            }
        } else if (this.currentTransport == null) {
            // Connectivity (re)gained while the tunnel was down: bring it up now. Reset the
            // throttler as this is a real connectivity-restored event, not a reconnection storm.
            this.currentTransport = desired;
            Timber.i("Network available (%s), connecting VPN.", desired);
            this.vpnWorker.resetThrottle();
            reconnect();
        } else if (desired != this.currentTransport) {
            // The default network switched transport (e.g. cellular ↔ Wi-Fi). The tunnel's DNS is
            // resolved from the active network, so rebuild it to match — otherwise it keeps
            // forwarding DNS to the old network and every query fails with ENETUNREACH. No
            // throttler reset here so rapid flapping is still damped.
            Timber.i("Default network changed from %s to %s, reconnecting VPN.", this.currentTransport, desired);
            this.currentTransport = desired;
            reconnect();
        }
        // else: the tunnel is already on the right transport — nothing to do.
    }

    private void setNetworkAvailable(NetworkType type, boolean available) {
        if (type == WIFI) {
            this.wifiAvailable = available;
            if (!available) {
                // A gone Wi-Fi network is no longer validated.
                this.wifiValidated = false;
            }
        } else {
            this.cellularAvailable = available;
        }
        reconcile();
    }

    private void setWifiValidated(boolean validated) {
        if (validated != this.wifiValidated) {
            this.wifiValidated = validated;
            Timber.d("Wi-Fi validation changed: %s", validated);
            reconcile();
        }
    }

    /**
     * This class receives network change events to monitor network type available.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     * @see <a href="https://developer.android.com/training/basics/network-ops/reading-network-state#listening-events">Android Developer Documentation</a>
     */
    private class NetworkTypeCallback extends NetworkCallback {
        private final NetworkType monitoredType;

        NetworkTypeCallback(NetworkType monitoredType) {
            this.monitoredType = monitoredType;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            Timber.d("Network available: %s", this.monitoredType);
            setNetworkAvailable(this.monitoredType, true);
        }

        @Override
        public void onLost(@NonNull Network network) {
            Timber.d("Network lost: %s", this.monitoredType);
            setNetworkAvailable(this.monitoredType, false);
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            // Only Wi-Fi validation drives the tunnel decision: Android promotes Wi-Fi to the
            // default network once it validates. Cellular validation is irrelevant — cellular is
            // only ever the fallback when Wi-Fi is not usable.
            if (this.monitoredType == WIFI) {
                setWifiValidated(networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED));
            }
        }
    }

    enum NetworkType {
        CELLULAR,
        WIFI,
    }

    /* The handler may only keep a weak reference around, otherwise it leaks */
    private static class MyHandler extends Handler {

        private final WeakReference<Callback> callback;

        MyHandler(Callback callback) {
            super(requireNonNull(Looper.myLooper()));
            this.callback = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Callback callback = this.callback.get();
            if (callback != null) {
                callback.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
