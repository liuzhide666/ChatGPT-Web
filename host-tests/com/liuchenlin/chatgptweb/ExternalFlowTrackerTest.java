package com.liuchenlin.chatgptweb;

public final class ExternalFlowTrackerTest {
    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ExternalFlowTracker tracker = new ExternalFlowTracker();
        final int[] changes = {0};
        tracker.setChangeListener(() -> changes[0]++);
        assertFalse(tracker.isAnyActive(), "initially inactive");
        tracker.begin(ExternalFlowTracker.Flow.ANDROID_PERMISSION);
        assertTrue(tracker.isActive(ExternalFlowTracker.Flow.ANDROID_PERMISSION), "permission active");
        assertTrue(tracker.isAnyActive(), "any active");
        assertThrows(() -> tracker.begin(ExternalFlowTracker.Flow.ANDROID_PERMISSION), "same flow cannot begin twice");
        tracker.begin(ExternalFlowTracker.Flow.FILE_PICKER);
        assertTrue(tracker.isActive(ExternalFlowTracker.Flow.FILE_PICKER), "distinct flow may coexist");
        tracker.end(ExternalFlowTracker.Flow.ANDROID_PERMISSION);
        assertFalse(tracker.isActive(ExternalFlowTracker.Flow.ANDROID_PERMISSION), "permission ended");
        assertTrue(tracker.isAnyActive(), "picker remains active");
        tracker.end(ExternalFlowTracker.Flow.FILE_PICKER);
        assertFalse(tracker.isAnyActive(), "all ended");
        tracker.end(ExternalFlowTracker.Flow.FILE_PICKER);
        assertFalse(tracker.isAnyActive(), "duplicate terminal cleanup is harmless");
        tracker.begin(ExternalFlowTracker.Flow.FILE_PICKER);
        tracker.begin(ExternalFlowTracker.Flow.DOWNLOAD_SAVE);
        assertTrue(tracker.isActive(ExternalFlowTracker.Flow.DOWNLOAD_SAVE), "download save flow active");
        tracker.clearAll();
        assertFalse(tracker.isAnyActive(), "clearAll cancels external state");
        assertTrue(changes[0] >= 6, "begin/end/clear state changes notify health scheduler");
        System.out.println("ExternalFlowTrackerTest OK");
    }
}
