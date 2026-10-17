package com.liuchenlin.chatgptweb;

public final class BrowserHealthSupervisorNetworkTest {
    private static void check(boolean v,String m){ if(!v) throw new AssertionError(m); }
    private static BrowserHealthSupervisor.Snapshot s(long now, boolean network) {
        return new BrowserHealthSupervisor.Snapshot(now,true,false,true,
                false,false,false,false,false,false,network,true,true,true);
    }
    public static void main(String[] args) {
        BrowserHealthSupervisor h = new BrowserHealthSupervisor();
        h.onTopLevelLoadFailed(1L);
        check(h.evaluate(s(1L,false)) == BrowserHealthSupervisor.Decision.NONE,
                "offline never performs destructive recovery");
        check(h.nextDelayMs(s(1L,false)) == Long.MAX_VALUE,
                "offline incident waits for an event instead of polling");
        check(h.evaluate(s(2L,true)) == BrowserHealthSupervisor.Decision.SOFT_RELOAD,
                "connectivity return may resume an existing proven failure only");

        BrowserHealthSupervisor healthy = new BrowserHealthSupervisor();
        check(healthy.evaluate(s(999_999L,true)) == BrowserHealthSupervisor.Decision.NONE,
                "connectivity/elapsed time without an incident does nothing");
        check(healthy.nextDelayMs(s(999_999L,true)) == Long.MAX_VALUE,
                "healthy online page has no timer");
        System.out.println("BrowserHealthSupervisorNetworkTest OK");
    }
}
