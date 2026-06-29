package org.adaway.vpn.worker;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

import android.content.Context;

import org.adaway.helper.PreferenceHelper;
import org.adaway.vpn.VpnServiceControls;
import org.adaway.vpn.VpnStartDecision;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * This class monitors the VPN network interface is still up while the VPN is running.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnConnectionMonitor {
    private static final int CONNECTION_CHECK_DELAY_MS = 10_000;
    private static final Pattern TUNNEL_PATTERN = Pattern.compile("tun([0-9]+)");
    /**
     * The application context.
     */
    private final Context context;
    /**
     * Whether the monitor is running (<code>true</code> if running, <code>false</code> if stopped).
     */
    private final AtomicBoolean running;
    /**
     * The network interface to monitor (<code>null</code> if not initialized).
     */
    private NetworkInterface networkInterface;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    VpnConnectionMonitor(Context context) {
        this.context = context;
        this.running = new AtomicBoolean(true);
        this.networkInterface = null;
    }

    private static NetworkInterface findVpnNetworkInterface() {
        try {
            NetworkInterface vpnNetworkInterface = null;
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                vpnNetworkInterface = pickLastVpnNetworkInterface(vpnNetworkInterface, networkInterface);
            }
            if (vpnNetworkInterface == null) {
                throw new IllegalStateException("Failed to find a network interface.");
            }
            return vpnNetworkInterface;
        } catch (SocketException e) {
            throw new IllegalStateException("Failed to find VPN network interface.", e);
        }
    }

    private static NetworkInterface pickLastVpnNetworkInterface(NetworkInterface current, NetworkInterface other) {
        // Ensure other is a tunnel interface, otherwise picks current
        Matcher otherMatcher = TUNNEL_PATTERN.matcher(other.getName());
        if (!otherMatcher.matches()) {
            return current;
        }
        // If other is a tunnel interface and no current interface, picks other.
        if (current == null) {
            return other;
        }
        // Ensure current is still a tunnel interface (it happens to change), otherwise picks other
        Matcher currentMatcher = TUNNEL_PATTERN.matcher(current.getName());
        if (!currentMatcher.matches()) {
            Timber.e("Current interface %s is no more a tunnel interface.", current.getName());
            return other;
        }
        // Compare current and ot then pick the last one
        int currentTunnelNumber = parseInt(requireNonNull(currentMatcher.group(1)));
        int otherTunnelNumber = parseInt(requireNonNull(otherMatcher.group(1)));
        return otherTunnelNumber > currentTunnelNumber ? other : current;
    }

    /**
     * Initialize the monitor once the VPN connection is up.
     */
    void initialize() {
        Timber.d("Initializing connection monitor…");
        this.networkInterface = findVpnNetworkInterface();
        Timber.d("Connection monitor initialized to watch interface %s.", this.networkInterface.getName());
    }

    /**
     * Monitor the VPN network interface is still up while the VPN is running.
     */
    void monitor() {
        while (this.running.get()) {
            if (!isVpnInterfacePresent()) {
                stop();
                if (mayRestart()) {
                    Timber.i("No VPN tunnel interface present. Restarting VPN service…");
                    VpnServiceControls.start(this.context);
                } else {
                    Timber.i("VPN tunnel interface gone but user has stopped the VPN; not restarting.");
                }
                return;
            }
            try {
                Thread.sleep(CONNECTION_CHECK_DELAY_MS);
            } catch (InterruptedException e) {
                Timber.d("Stop monitoring.");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Check whether a VPN tunnel interface ({@code tunX}) is currently present.
     * <p>
     * Returns {@code true} immediately if the tunnel has not been initialized yet for this
     * session (i.e. {@link #initialize()} has not been called or {@link #reset()} was called
     * since the last initialize): the monitor must not fire while the worker is still in the
     * throttle-wait before the new tunnel is established, or it would falsely conclude the
     * tunnel is gone and trigger an extra restart.
     * <p>
     * Once initialized, this deliberately probes for the <em>presence</em> of the interface
     * by re-enumerating the device interfaces on every cycle, instead of calling
     * {@link NetworkInterface#isUp()} on the cached instance: on some devices / Android
     * versions that {@code ioctl} fails with {@code ENODEV} (or the cached instance goes stale)
     * while the tunnel is in fact still up, which made the monitor tear down a perfectly working
     * tunnel every 10s in a restart loop. Checking for presence also transparently handles the
     * interface being renumbered (e.g. {@code tun0} → {@code tun1}) on a rebuild.
     *
     * @return <code>true</code> if a tunnel interface is present — or if the tunnel has not
     * been initialized yet, or if the interfaces could not be probed at all (transient error);
     * <code>false</code> only when the device positively reports no tunnel interface AND the
     * tunnel had been successfully initialized.
     */
    private boolean isVpnInterfacePresent() {
        // Not yet initialized for this VPN session — the worker is still in the throttle
        // wait before establishing the tunnel. Skip the check to avoid a false "no tunnel"
        // detection that would immediately trigger another restart.
        if (this.networkInterface == null) {
            return true;
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                if (TUNNEL_PATTERN.matcher(interfaces.nextElement().getName()).matches()) {
                    return true;
                }
            }
            return false;
        } catch (SocketException e) {
            Timber.w(e, "Failed to probe VPN tunnel interface; assuming it is still up.");
            return true;
        }
    }

    /**
     * The connection monitor must never resurrect a VPN that the user has explicitly
     * stopped. Without this guard, a torn-down tunnel during the stop sequence would
     * race the monitor thread and the service could come back on its own.
     */
    private boolean mayRestart() {
        return VpnStartDecision.mayBackgroundStart(
                PreferenceHelper.getVpnServiceUserEnabled(this.context)
        );
    }

    /**
     * Activate the monitor so the loop runs on the next {@code monitor()} call.
     * <p>
     * Must be called from {@link org.adaway.vpn.worker.VpnWorker#start()} before
     * submitting the monitor task, because a previous self-triggered restart (the monitor
     * detecting a gone tunnel and calling {@link VpnServiceControls#start}) calls
     * {@link #stop()} which sets {@code running} to {@code false}. Without this reset the
     * new monitor task exits immediately on the {@code while (running.get())} check.
     */
    void activate() {
        this.running.set(true);
    }

    /**
     * Reset the connection monitor if the VPN connection changed.
     */
    void reset() {
        this.networkInterface = null;
    }

    /**
     * Stop the monitor.
     */
    void stop() {
        this.running.set(false);
    }
}
