package com.liuchenlin.chatgptweb;

import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;

/** Coarse default-network liveness only; no SSID, address, transport, or location metadata. */
final class NetworkStateMonitor {
    interface Listener {
        void onNetworkAvailabilityChanged(boolean mayHaveNetwork);
    }

    private final ConnectivityManager connectivityManager;
    private final Handler handler;
    private final Listener listener;
    private boolean registered;
    private volatile boolean known;
    private volatile boolean available = true;

    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    update(true, true);
                }

                @Override public void onLost(Network network) {
                    update(true, false);
                }
            };

    NetworkStateMonitor(ConnectivityManager connectivityManager, Handler handler, Listener listener) {
        this.connectivityManager = connectivityManager;
        this.handler = handler;
        this.listener = listener;
    }

    void start() {
        if (registered || connectivityManager == null || handler == null) return;
        try {
            known = true;
            available = connectivityManager.getActiveNetwork() != null;
            connectivityManager.registerDefaultNetworkCallback(callback, handler);
            registered = true;
        } catch (RuntimeException unavailableApiOrPermission) {
            // Unknown is intentionally treated as "may have network" so monitoring failure never
            // disables recovery permanently.
            known = false;
            available = true;
            registered = false;
        }
    }

    void stop() {
        if (!registered || connectivityManager == null) return;
        registered = false;
        try {
            connectivityManager.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
            // Process/activity teardown must remain best-effort.
        }
    }

    boolean mayHaveNetwork() {
        return !known || available;
    }

    private void update(boolean newKnown, boolean newAvailable) {
        boolean oldMayHaveNetwork = mayHaveNetwork();
        known = newKnown;
        available = newAvailable;
        boolean newMayHaveNetwork = mayHaveNetwork();
        if (listener != null && oldMayHaveNetwork != newMayHaveNetwork) {
            listener.onNetworkAvailabilityChanged(newMayHaveNetwork);
        }
    }
}
