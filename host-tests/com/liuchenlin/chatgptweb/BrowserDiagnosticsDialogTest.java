package com.liuchenlin.chatgptweb;

public final class BrowserDiagnosticsDialogTest {
    private static void check(boolean v,String m){ if(!v) throw new AssertionError(m); }
    public static void main(String[] args) {
        BrowserDiagnosticsDialog.Snapshot s = new BrowserDiagnosticsDialog.Snapshot(
                "1.1.0 / vc113", "1.1.2", 3, "CHATGPT_HTTPS", 7L,
                true, false, true, 100L, true, 120L, true,
                "STABLE", "NONE", "DEFAULT", true, true, false, true, false, false);
        String text = new BrowserDiagnosticsDialog().render(s);
        for (String expected : new String[]{"1.1.2", "protocol 3", "Navigation generation: 7",
                "Paint valid: yes", "Recovery: STABLE / NONE", "Cache mode: DEFAULT"}) {
            check(text.contains(expected), "diagnostics contains " + expected);
        }
        for (String forbidden : new String[]{"scanCode", "deviceId", "transactionId", "promptText", "clipboard"}) {
            check(!text.contains(forbidden), "diagnostics never exposes keyboard/content field " + forbidden);
        }
        System.out.println("BrowserDiagnosticsDialogTest OK");
    }
}
