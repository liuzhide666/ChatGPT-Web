package com.liuchenlin.chatgptweb;

public final class TrustedOriginPolicyTest {
    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        assertTrue(TrustedOriginPolicy.isTrustedMediaUri("https://chatgpt.com"), "chatgpt root");
        assertTrue(TrustedOriginPolicy.isTrustedMediaUri("https://voice.chatgpt.com/path"), "chatgpt subdomain");
        assertTrue(TrustedOriginPolicy.isTrustedMediaUri("https://openai.com"), "openai root");
        assertTrue(TrustedOriginPolicy.isTrustedMediaUri("https://auth.openai.com/login"), "openai subdomain");
        assertTrue(TrustedOriginPolicy.isTrustedMediaUri("HTTPS://CHATGPT.COM/path"), "scheme and host are case-insensitive");

        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("http://chatgpt.com"), "http rejected");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("https://evilchatgpt.com"), "suffix confusion rejected");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("https://chatgpt.com.evil.example"), "superdomain confusion rejected");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("https://chatgpt.com@evil.example"), "userinfo confusion rejected");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("https://files.oaiusercontent.com"), "cdn not media trusted");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("file:///tmp/a"), "file rejected");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("content://provider/a"), "content rejected");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri("not a uri"), "malformed rejected");
        assertFalse(TrustedOriginPolicy.isTrustedMediaUri(null), "null rejected");
        System.out.println("TrustedOriginPolicyTest OK");
    }
}
