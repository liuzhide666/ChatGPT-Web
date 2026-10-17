package com.liuchenlin.chatgptweb;

public final class CrashRecoveryPolicyTest {
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        CrashRecoveryPolicy policy = new CrashRecoveryPolicy();
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, policy.recordFailure(1_000L), "first failure");
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, policy.recordFailure(20_000L), "second failure");
        assertEquals(CrashRecoveryPolicy.Decision.SHOW_RECOVERY_UI, policy.recordFailure(59_999L), "third within 60 seconds");
        assertEquals(CrashRecoveryPolicy.Decision.SHOW_RECOVERY_UI, policy.recordFailure(60_000L), "guard stays active until reset");

        policy.reset();
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, policy.recordFailure(0L), "reset starts new cycle");
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, policy.recordFailure(60_001L), "old failure falls outside rolling window");
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, policy.recordFailure(120_002L), "window keeps pruning old failures");

        CrashRecoveryPolicy boundary = new CrashRecoveryPolicy();
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, boundary.recordFailure(10_000L), "boundary first");
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, boundary.recordFailure(20_000L), "boundary second");
        assertEquals(CrashRecoveryPolicy.Decision.AUTO_RECOVER, boundary.recordFailure(70_001L), "exactly older than 60 seconds is pruned");
        System.out.println("CrashRecoveryPolicyTest OK");
    }
}
