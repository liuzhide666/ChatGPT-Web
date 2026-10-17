package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AndroidPermissionCoordinator {
    public static final int REQUEST_CODE = 41040;

    public static final String RECORD_AUDIO = "android.permission.RECORD_AUDIO";
    public static final String CAMERA = "android.permission.CAMERA";
    public static final String ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
    public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";

    private static final Set<String> ALLOWED = Set.of(
            RECORD_AUDIO,
            CAMERA,
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION
    );

    public interface Host {
        boolean isGranted(String permission);
        void requestPermissions(String[] permissions, int requestCode);
    }

    public interface Callback {
        void grant();
        void reject();
    }

    private static final class Pending {
        final Callback callback;
        final int expectedResultCount;

        Pending(Callback callback, int expectedResultCount) {
            this.callback = callback;
            this.expectedResultCount = expectedResultCount;
        }
    }

    private final Host host;
    private final ExternalFlowTracker flows;
    private Pending pending;

    public AndroidPermissionCoordinator(Host host, ExternalFlowTracker flows) {
        this.host = Objects.requireNonNull(host, "host");
        this.flows = Objects.requireNonNull(flows, "flows");
    }


    public synchronized boolean isGranted(String permission) {
        return permission != null && ALLOWED.contains(permission) && host.isGranted(permission);
    }

    public synchronized void request(String[] permissions, Callback callback) {
        Objects.requireNonNull(callback, "callback");
        if (permissions == null) {
            callback.reject();
            return;
        }
        for (String permission : permissions) {
            if (permission == null || !ALLOWED.contains(permission)) {
                callback.reject();
                return;
            }
        }

        List<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (!host.isGranted(permission)) {
                missing.add(permission);
            }
        }
        if (missing.isEmpty()) {
            callback.grant();
            return;
        }

        rejectPendingLocked();
        flows.begin(ExternalFlowTracker.Flow.ANDROID_PERMISSION);
        pending = new Pending(callback, missing.size());
        try {
            host.requestPermissions(missing.toArray(new String[0]), REQUEST_CODE);
        } catch (RuntimeException error) {
            Pending failed = pending;
            pending = null;
            flows.end(ExternalFlowTracker.Flow.ANDROID_PERMISSION);
            failed.callback.reject();
        }
    }

    public synchronized boolean onPermissionsResult(int requestCode, boolean[] grantedResults) {
        if (requestCode != REQUEST_CODE || pending == null) {
            return false;
        }
        Pending current = pending;
        pending = null;
        flows.end(ExternalFlowTracker.Flow.ANDROID_PERMISSION);

        if (grantedResults == null || grantedResults.length != current.expectedResultCount) {
            current.callback.reject();
            return true;
        }
        for (boolean granted : grantedResults) {
            if (!granted) {
                current.callback.reject();
                return true;
            }
        }
        current.callback.grant();
        return true;
    }

    public synchronized void cancelPending() {
        rejectPendingLocked();
    }

    /** Drops a callback owned by a dead GeckoSession without calling back into that session. */
    public synchronized void abandonPending() {
        if (pending == null) {
            return;
        }
        pending = null;
        flows.end(ExternalFlowTracker.Flow.ANDROID_PERMISSION);
    }

    private void rejectPendingLocked() {
        if (pending == null) {
            return;
        }
        Pending current = pending;
        pending = null;
        flows.end(ExternalFlowTracker.Flow.ANDROID_PERMISSION);
        current.callback.reject();
    }
}
