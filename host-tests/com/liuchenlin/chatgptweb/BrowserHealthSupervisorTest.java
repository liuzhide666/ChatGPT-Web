package com.liuchenlin.chatgptweb;

public final class BrowserHealthSupervisorTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static BrowserHealthSupervisor.Snapshot s(long now, boolean focused, boolean dirty,
            boolean composing, boolean media, boolean external, boolean network,
            boolean bridgeConnected, boolean protocolCompatible, boolean paintValid) {
        return new BrowserHealthSupervisor.Snapshot(now, true, false, true,
                focused, dirty, composing, false, media, external, network,
                bridgeConnected, protocolCompatible, paintValid);
    }

    private static BrowserHealthSupervisor.Snapshot healthy(long now) {
        return s(now, false, false, false, false, false, true, true, true, true);
    }

    public static void main(String[] args) {
        BrowserHealthSupervisor h = new BrowserHealthSupervisor();
        check(h.state() == BrowserHealthSupervisor.State.STABLE, "starts stable");
        check(h.evaluate(healthy(10_000_000L)) == BrowserHealthSupervisor.Decision.NONE,
                "long idle never creates a recovery incident");
        check(h.nextDelayMs(healthy(10_000_000L)) == Long.MAX_VALUE,
                "STABLE has no watchdog deadline");

        h.onBridgeDisconnected(10L);
        check(h.state() == BrowserHealthSupervisor.State.SUSPECT, "bridge event enters suspect");
        check(h.evaluate(healthy(10L)) == BrowserHealthSupervisor.Decision.PROBE,
                "connected compatible bridge is probed before any rebind");
        check(h.state() == BrowserHealthSupervisor.State.PROBING, "probe state active");
        check(h.nextDelayMs(healthy(10L)) == BrowserHealthSupervisor.PROBE_GRACE_MS,
                "only active probe owns a deadline");
        check(h.evaluate(healthy(10L + BrowserHealthSupervisor.PROBE_GRACE_MS - 1))
                        == BrowserHealthSupervisor.Decision.NONE,
                "probe cannot timeout early");
        check(h.evaluate(healthy(10L + BrowserHealthSupervisor.PROBE_GRACE_MS))
                        == BrowserHealthSupervisor.Decision.REBIND_BRIDGE,
                "probe timeout escalates only to bridge rebind");
        long rebindDeadline = 10L + BrowserHealthSupervisor.PROBE_GRACE_MS
                + BrowserHealthSupervisor.REBIND_GRACE_MS;
        check(h.evaluate(healthy(rebindDeadline)) == BrowserHealthSupervisor.Decision.NONE,
                "bridge-only failure never reloads a valid page");
        check(h.state() == BrowserHealthSupervisor.State.DEGRADED_STABLE,
                "bridge-only failure settles degraded stable");
        check(h.nextDelayMs(healthy(rebindDeadline)) == Long.MAX_VALUE,
                "DEGRADED_STABLE also has no watchdog");
        h.onBridgeHealthy(rebindDeadline + 1);
        check(h.state() == BrowserHealthSupervisor.State.STABLE,
                "late bridge recovery returns to stable without reload");

        h.resetIncident();
        h.onPaintInvalid(100L);
        check(h.evaluate(s(100L,false,false,false,false,false,true,true,true,false))
                        == BrowserHealthSupervisor.Decision.NONE,
                "paint reset alone does not recover");
        check(h.nextDelayMs(s(100L,false,false,false,false,false,true,true,true,false))
                        == Long.MAX_VALUE,
                "single weak page signal does not arm timer");
        h.onSlowScript(101L);
        check(h.evaluate(s(101L,false,false,false,false,false,true,true,true,false))
                        == BrowserHealthSupervisor.Decision.NONE,
                "slow script never independently reloads");

        h.onBridgeDisconnected(102L);
        check(h.evaluate(s(102L,false,false,false,false,false,true,true,true,false))
                        == BrowserHealthSupervisor.Decision.PROBE,
                "page+bridge suspicion still starts non-destructively");
        long probeTimeout = 102L + BrowserHealthSupervisor.PROBE_GRACE_MS;
        check(h.evaluate(s(probeTimeout,false,false,false,false,false,true,true,true,false))
                        == BrowserHealthSupervisor.Decision.REBIND_BRIDGE,
                "weak quorum still gives bridge rebind a chance");
        long quorumDeadline = probeTimeout + BrowserHealthSupervisor.REBIND_GRACE_MS;
        check(h.evaluate(s(quorumDeadline,false,false,false,false,false,true,false,false,false))
                        == BrowserHealthSupervisor.Decision.SOFT_RELOAD,
                "independent bridge+paint evidence may reach one soft recovery");
        check(h.state() == BrowserHealthSupervisor.State.SOFT_RECOVERY, "soft state active");
        long softDeadline = quorumDeadline + BrowserHealthSupervisor.SOFT_GRACE_MS;
        check(h.evaluate(s(softDeadline,false,false,false,false,false,true,false,false,false))
                        == BrowserHealthSupervisor.Decision.HARD_REPLACE,
                "failed soft recovery escalates once to hard replacement");
        long hardDeadline = softDeadline + BrowserHealthSupervisor.HARD_GRACE_MS;
        check(h.evaluate(s(hardDeadline,false,false,false,false,false,true,false,false,false))
                        == BrowserHealthSupervisor.Decision.SHOW_UI,
                "failed hard recovery stops at user recovery UI");

        h.resetIncident();
        h.onTopLevelLoadFailed(1_000L);
        check(h.evaluate(healthy(1_000L)) == BrowserHealthSupervisor.Decision.SOFT_RELOAD,
                "strong top-level load failure may directly request soft recovery");

        System.out.println("BrowserHealthSupervisorTest OK");
    }
}
