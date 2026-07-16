/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Parsing code derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.vpn.worker;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import timber.log.Timber;

/**
 * Keeps the tunnel healthy by probing the upstream DNS server during idle periods.
 * <p>
 * When {@link #handleTimeout()} fires — meaning {@code poll()} saw no activity for the whole
 * poll timeout — it sends an empty keep-alive datagram to the upstream DNS server. If the
 * underlying network is genuinely gone, that send throws and the worker reconnects; otherwise the
 * probe simply keeps the connection warm and the poll timeout is grown so the next quiet period is
 * checked less often (backing off 1&nbsp;s → 4&nbsp;s → 16&nbsp;s → … up to ~68&nbsp;min).
 * <p>
 * Historically the watchdog also forced a reconnect whenever it went a few seconds without seeing
 * a packet come <em>back</em> after a probe. That was wrong: the keep-alive packet is an empty UDP
 * datagram that DNS servers never answer, and its reply was never read anyway, so the only real
 * signal was "did an app on the phone make a DNS query recently". While the phone is idle no app
 * queries, so the tunnel was torn down and rebuilt every few minutes for no reason (a documented
 * bug). A tunnel with no traffic is not a dead tunnel — genuine failures are caught by the device
 * read path, the forward path, and this probe's own send throwing — so idle no longer reconnects.
 */
class VpnWatchdog {
    // Poll timeout grows on every idle tick so keep-alive probes get rarer the longer the tunnel
    // sits quiet: 1s, then 4s, 16s, … up to ~68m. It is never reset by app traffic — while queries
    // are flowing poll() returns on those events instead of timing out, so no probe is sent at all;
    // probes only happen once the tunnel has actually been quiet for the whole (growing) interval.
    private static final int POLL_TIMEOUT_START = 1000;
    private static final int POLL_TIMEOUT_END = 4096000;
    private static final int POLL_TIMEOUT_GROW = 4;

    private int pollTimeout = POLL_TIMEOUT_START;

    private boolean enabled;
    private DatagramPacket checkAlivePacket;

    VpnWatchdog() {
        // Disabled by default until initialize() is called with the user preference.
        this.enabled = false;
    }

    /**
     * Returns the current poll time out, or {@code -1} (poll forever) when disabled.
     */
    int getPollTimeout() {
        if (!this.enabled) {
            return -1;
        }
        return this.pollTimeout;
    }

    /**
     * Sets the target address keep-alive packets should be sent to.
     */
    void setTarget(InetAddress target) {
        this.checkAlivePacket = new DatagramPacket(new byte[0], 0, 0 /* length */, target, 53);
    }

    /**
     * An initialization method. Resets the poll timeout.
     *
     * @param enabled If the watchdog should be enabled.
     */
    void initialize(boolean enabled) {
        Timber.d("initialize: Initializing watchdog");

        this.pollTimeout = POLL_TIMEOUT_START;
        this.enabled = enabled;

        if (!this.enabled) {
            Timber.d("initialize: Disabled.");
        }
    }

    /**
     * Handles a timeout of poll(): the tunnel has been idle for the whole poll timeout.
     * <p>
     * An idle tunnel is healthy, not dead, so this never reconnects on its own. It only grows the
     * back-off and sends a keep-alive probe; a genuinely unreachable network makes that probe's
     * send throw, which is what triggers a reconnect.
     *
     * @throws VpnNetworkException When the keep-alive packet could not be sent.
     */
    void handleTimeout() throws VpnNetworkException {
        if (!this.enabled) {
            return;
        }
        this.pollTimeout *= POLL_TIMEOUT_GROW;
        if (this.pollTimeout > POLL_TIMEOUT_END) {
            this.pollTimeout = POLL_TIMEOUT_END;
        }
        sendPacket();
    }

    /**
     * Sends an empty keep-alive packet to the configured target address.
     *
     * @throws VpnNetworkException If sending failed and we should reconnect
     */
    void sendPacket() throws VpnNetworkException {
        if (!this.enabled || this.checkAlivePacket == null) {
            return;
        }
        Timber.d("sendPacket: Sending keep-alive, poll timeout is %d.", this.pollTimeout);

        try (DatagramSocket socket = newDatagramSocket()) {
            socket.send(this.checkAlivePacket);
        } catch (IOException e) {
            throw new VpnNetworkException("Failed to send check-alive packet.", e);
        }
    }

    @NonNull
    DatagramSocket newDatagramSocket() throws SocketException {
        return new DatagramSocket();
    }
}
