package com.liuchenlin.chatgptweb;

import android.content.Context;
import org.mozilla.geckoview.GeckoRuntime;

public final class GeckoRuntimeHolderTest {
    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message);
    }
    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        Context application = new Context();
        Context activity = new Context(application);
        GeckoRuntime first = GeckoRuntimeHolder.get(activity);
        GeckoRuntime second = GeckoRuntimeHolder.get(activity);
        assertSame(first, second, "runtime must be process singleton");
        assertEquals(1, GeckoRuntime.createCount, "GeckoRuntime.create called exactly once");
        assertSame(application, GeckoRuntime.lastContext, "runtime must bind application context");
        System.out.println("GeckoRuntimeHolderTest OK");
    }
}
