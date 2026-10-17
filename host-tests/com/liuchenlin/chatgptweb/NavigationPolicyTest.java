package com.liuchenlin.chatgptweb;

public final class NavigationPolicyTest {
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        assertEquals(NavigationPolicy.Action.ALLOW_IN_GECKO,
                NavigationPolicy.classify("https://chatgpt.com/"), "ChatGPT https");
        assertEquals(NavigationPolicy.Action.ALLOW_IN_GECKO,
                NavigationPolicy.classify("https://accounts.google.com/o/oauth2/v2/auth"), "OAuth https");
        assertEquals(NavigationPolicy.Action.EXTERNAL_INTENT,
                NavigationPolicy.classify("http://example.com"), "http external");
        assertEquals(NavigationPolicy.Action.EXTERNAL_INTENT,
                NavigationPolicy.classify("mailto:test@example.com"), "mailto external");
        assertEquals(NavigationPolicy.Action.EXTERNAL_INTENT,
                NavigationPolicy.classify("tel:+861234567890"), "tel external");
        assertEquals(NavigationPolicy.Action.EXTERNAL_INTENT,
                NavigationPolicy.classify("intent://scan/#Intent;scheme=zxing;end"), "intent external");
        assertEquals(NavigationPolicy.Action.DENY,
                NavigationPolicy.classify("file:///sdcard/a.html"), "file denied");
        assertEquals(NavigationPolicy.Action.DENY,
                NavigationPolicy.classify("content://provider/document/1"), "content denied");
        assertEquals(NavigationPolicy.Action.DENY,
                NavigationPolicy.classify("javascript:alert(1)"), "javascript denied");
        assertEquals(NavigationPolicy.Action.DENY,
                NavigationPolicy.classify("not a uri"), "malformed denied");
        assertEquals(NavigationPolicy.Action.DENY,
                NavigationPolicy.classify(null), "null denied");
        System.out.println("NavigationPolicyTest OK");
    }
}
