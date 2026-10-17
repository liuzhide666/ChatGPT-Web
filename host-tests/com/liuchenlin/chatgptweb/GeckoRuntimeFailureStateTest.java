package com.liuchenlin.chatgptweb;

import android.content.Context;
import org.mozilla.geckoview.GeckoRuntime;

public final class GeckoRuntimeFailureStateTest {
    public static void main(String[] args) {
        Context application = new Context();
        Context activity = new Context(application);
        GeckoRuntime.failure = new RuntimeException("first runtime failure");
        try {
            GeckoRuntimeHolder.get(activity);
            throw new AssertionError("first create must fail");
        } catch (RuntimeException expected) {
            // expected
        }
        check(GeckoRuntimeHolder.state() == GeckoRuntimeHolder.State.FAILED, "holder must latch FAILED");
        check(GeckoRuntime.createCount == 1, "create count after first failure");

        GeckoRuntime.failure = null;
        try {
            GeckoRuntimeHolder.get(activity);
            throw new AssertionError("same process must not recreate runtime");
        } catch (IllegalStateException expected) {
            // expected
        }
        check(GeckoRuntime.createCount == 1, "failed process must not call create again");
        System.out.println("GeckoRuntimeFailureStateTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
