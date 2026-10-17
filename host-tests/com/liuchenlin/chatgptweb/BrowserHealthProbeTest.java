package com.liuchenlin.chatgptweb;

public final class BrowserHealthProbeTest {
    private static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
    private static BrowserHealthSupervisor.Snapshot s(long now, boolean connected, boolean compatible, boolean paint) {
        return new BrowserHealthSupervisor.Snapshot(now, true, false, true,
                false,false,false,false,false,false,true,connected,compatible,paint);
    }
    public static void main(String[] args) {
        BrowserHealthSupervisor h = new BrowserHealthSupervisor();
        h.onBridgeDisconnected(1L);
        check(h.evaluate(s(1L,true,true,true)) == BrowserHealthSupervisor.Decision.PROBE,
                "probe is on-demand from a concrete bridge incident");
        h.onBridgeHealthy(2L);
        check(h.evaluate(s(2L,true,true,true)) == BrowserHealthSupervisor.Decision.NONE,
                "PONG/READY ends probe incident");
        check(h.state() == BrowserHealthSupervisor.State.STABLE,
                "bridge response returns to stable");
        check(h.nextDelayMs(s(2L,true,true,true)) == Long.MAX_VALUE,
                "successful probe leaves no timer behind");
        System.out.println("BrowserHealthProbeTest OK");
    }
}
