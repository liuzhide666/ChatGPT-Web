package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AndroidPermissionCoordinatorTest {
    private static final class FakeHost implements AndroidPermissionCoordinator.Host {
        final Set<String> granted = new HashSet<>();
        final List<String[]> requests = new ArrayList<>();
        final List<Integer> requestCodes = new ArrayList<>();
        @Override public boolean isGranted(String permission) { return granted.contains(permission); }
        @Override public void requestPermissions(String[] permissions, int requestCode) {
            requests.add(permissions.clone());
            requestCodes.add(requestCode);
        }
    }

    private static final class FakeCallback implements AndroidPermissionCoordinator.Callback {
        int grants;
        int rejects;
        @Override public void grant() { grants++; }
        @Override public void reject() { rejects++; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String mic = "android.permission.RECORD_AUDIO";
        String camera = "android.permission.CAMERA";

        {
            FakeHost host = new FakeHost(); host.granted.add(mic);
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(host, flows);
            FakeCallback cb = new FakeCallback();
            c.request(new String[]{mic}, cb);
            check(cb.grants == 1 && cb.rejects == 0, "already-granted request grants immediately");
            check(host.requests.isEmpty(), "already-granted request shows no dialog");
            check(!flows.isAnyActive(), "already-granted request is not external flow");
        }

        {
            FakeHost host = new FakeHost();
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(host, flows);
            FakeCallback cb = new FakeCallback();
            c.request(new String[]{mic, camera}, cb);
            check(host.requests.size() == 1, "dialog requested once");
            check(Arrays.equals(new String[]{mic, camera}, host.requests.get(0)), "missing permissions preserve order");
            check(flows.isActive(ExternalFlowTracker.Flow.ANDROID_PERMISSION), "permission dialog marked external");
            check(c.onPermissionsResult(AndroidPermissionCoordinator.REQUEST_CODE, new boolean[]{true, true}), "result consumed");
            check(cb.grants == 1 && cb.rejects == 0, "all granted callback");
            check(!flows.isAnyActive(), "flow ends on grant");
        }

        {
            FakeHost host = new FakeHost();
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(host, flows);
            FakeCallback cb = new FakeCallback();
            c.request(new String[]{mic}, cb);
            check(c.onPermissionsResult(AndroidPermissionCoordinator.REQUEST_CODE, new boolean[]{false}), "denial consumed");
            check(cb.grants == 0 && cb.rejects == 1, "denial rejects");
            check(!flows.isAnyActive(), "flow ends on denial");
        }

        {
            FakeHost host = new FakeHost();
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(host, flows);
            FakeCallback cb = new FakeCallback();
            c.request(new String[]{"android.permission.READ_CONTACTS"}, cb);
            check(cb.rejects == 1 && cb.grants == 0, "unknown permission rejected");
            check(host.requests.isEmpty(), "unknown permission never reaches Android dialog");
        }

        {
            FakeHost host = new FakeHost();
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(host, flows);
            FakeCallback first = new FakeCallback();
            FakeCallback second = new FakeCallback();
            c.request(new String[]{mic}, first);
            c.request(new String[]{camera}, second);
            check(first.rejects == 1, "superseded callback rejected");
            check(host.requests.size() == 2, "second request starts its own dialog");
            check(flows.isActive(ExternalFlowTracker.Flow.ANDROID_PERMISSION), "second request keeps flow active");
            c.cancelPending();
            check(second.rejects == 1, "lifecycle cancellation rejects pending callback");
            check(!flows.isAnyActive(), "cancel clears flow");
        }

        {
            FakeHost host = new FakeHost();
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(host, flows);
            FakeCallback cb = new FakeCallback();
            c.request(new String[]{mic}, cb);
            check(!c.onPermissionsResult(999, new boolean[]{true}), "wrong request code ignored");
            check(cb.grants == 0 && cb.rejects == 0, "wrong request code leaves pending request intact");
            check(c.onPermissionsResult(AndroidPermissionCoordinator.REQUEST_CODE, new boolean[]{}), "mismatched result consumed");
            check(cb.rejects == 1, "mismatched result rejects safely");
            check(!flows.isAnyActive(), "mismatched result clears flow");
        }

        {
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator.Host throwingHost = new AndroidPermissionCoordinator.Host() {
                @Override public boolean isGranted(String permission) { return false; }
                @Override public void requestPermissions(String[] permissions, int requestCode) {
                    throw new IllegalStateException("platform permission bridge failed");
                }
            };
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(throwingHost, flows);
            FakeCallback cb = new FakeCallback();
            c.request(new String[]{mic}, cb);
            check(cb.rejects == 1 && cb.grants == 0, "platform permission exception rejects instead of escaping");
            check(!flows.isAnyActive(), "platform permission exception clears external flow");
            check(!c.onPermissionsResult(AndroidPermissionCoordinator.REQUEST_CODE, new boolean[]{true}),
                    "failed platform request leaves no pending callback");
        }

        {
            FakeHost host = new FakeHost();
            ExternalFlowTracker flows = new ExternalFlowTracker();
            AndroidPermissionCoordinator c = new AndroidPermissionCoordinator(host, flows);
            FakeCallback cb = new FakeCallback();
            c.request(new String[]{mic}, cb);
            c.abandonPending();
            check(cb.grants == 0 && cb.rejects == 0, "dead-session cleanup drops callback without touching Gecko");
            check(!flows.isAnyActive(), "dead-session cleanup clears permission flow");
            check(!c.onPermissionsResult(AndroidPermissionCoordinator.REQUEST_CODE, new boolean[]{true}),
                    "abandoned callback cannot receive a late Android result");
        }

        System.out.println("AndroidPermissionCoordinatorTest OK");
    }
}
