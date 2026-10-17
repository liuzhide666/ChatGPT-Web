package com.liuchenlin.chatgptweb;

import java.lang.reflect.Method;

public final class KeyboardNeutralityReflectionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static boolean hasDeclaredMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName())) return true;
        }
        return false;
    }

    private static void classMustBeAbsent(String name) {
        try {
            Class.forName("com.liuchenlin.chatgptweb." + name);
            throw new AssertionError(name + " must be absent in vc113 keyboard-transparent build");
        } catch (ClassNotFoundException expected) {
            // expected
        }
    }

    public static void main(String[] args) {
        check(!hasDeclaredMethod(ChatGPTGeckoView.class, "dispatchKeyEvent"),
                "ChatGPTGeckoView must inherit raw key dispatch from GeckoView");
        check(!hasDeclaredMethod(ChatGPTGeckoView.class, "onCreateInputConnection"),
                "ChatGPTGeckoView must inherit raw InputConnection behavior from GeckoView");
        check(!hasDeclaredMethod(ChatGPTContentBridge.class, "requestComposerSubmit"),
                "bridge must not expose Native submit");
        check(!hasDeclaredMethod(ChatGPTContentBridge.class, "requestComposerLineBreak"),
                "bridge must not expose Native line break");

        for (String removed : new String[]{
                "NativeKeyIngress", "InputIntentTransaction", "InputEventDeduplicator",
                "ComposerInputRouter", "ChatGPTInputConnection", "InputRoutingState",
                "InputTraceBuffer", "InputDiagnosticsDialog", "ImeActionPolicy"}) {
            classMustBeAbsent(removed);
        }
        System.out.println("KeyboardNeutralityReflectionTest OK");
    }
}
