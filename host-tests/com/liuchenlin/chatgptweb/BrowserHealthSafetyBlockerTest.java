package com.liuchenlin.chatgptweb;

public final class BrowserHealthSafetyBlockerTest {
    private static void check(boolean v,String m){ if(!v) throw new AssertionError(m); }
    private static BrowserHealthSupervisor.Snapshot s(long now, boolean focused, boolean dirty,
            boolean composing, boolean media, boolean external, boolean network) {
        return new BrowserHealthSupervisor.Snapshot(now,true,false,true,
                focused,dirty,composing,false,media,external,network,true,true,true);
    }
    private static void assertBlocks(boolean focused, boolean dirty, boolean composing,
            boolean media, boolean external, boolean network, String message) {
        BrowserHealthSupervisor h = new BrowserHealthSupervisor();
        h.onTopLevelLoadFailed(10L);
        check(h.evaluate(s(10L,focused,dirty,composing,media,external,network))
                        == BrowserHealthSupervisor.Decision.NONE, message);
        check(h.nextDelayMs(s(10L,focused,dirty,composing,media,external,network)) == Long.MAX_VALUE,
                message + " leaves no destructive timer armed");
    }
    public static void main(String[] args) {
        assertBlocks(true,false,false,false,false,true,"focused composer blocks destructive recovery");
        assertBlocks(false,true,false,false,false,true,"dirty composer blocks destructive recovery");
        assertBlocks(false,false,true,false,false,true,"composition blocks destructive recovery");
        assertBlocks(false,false,false,true,false,true,"recording blocks destructive recovery");
        assertBlocks(false,false,false,false,true,true,"external flow blocks destructive recovery");
        assertBlocks(false,false,false,false,false,false,"offline blocks destructive recovery");

        BrowserHealthSupervisor attachment = new BrowserHealthSupervisor();
        attachment.onTopLevelLoadFailed(20L);
        BrowserHealthSupervisor.Snapshot attachmentOnly = new BrowserHealthSupervisor.Snapshot(
                20L,true,false,true,false,false,false,true,false,false,true,true,true,true);
        check(attachment.evaluate(attachmentOnly) == BrowserHealthSupervisor.Decision.NONE,
                "attachment-only composer blocks destructive recovery without sendReady");

        BrowserHealthSupervisor h = new BrowserHealthSupervisor();
        h.onBridgeDisconnected(100L);
        check(h.evaluate(s(100L,true,true,false,false,false,false))
                        == BrowserHealthSupervisor.Decision.PROBE,
                "safety blockers do not prevent non-destructive probe");
        check(h.evaluate(s(100L + BrowserHealthSupervisor.PROBE_GRACE_MS,
                        true,true,false,false,false,false))
                        == BrowserHealthSupervisor.Decision.REBIND_BRIDGE,
                "safety blockers do not prevent bridge-only rebind");
        System.out.println("BrowserHealthSafetyBlockerTest OK");
    }
}
