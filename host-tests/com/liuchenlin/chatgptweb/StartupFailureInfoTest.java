package com.liuchenlin.chatgptweb;

public final class StartupFailureInfoTest {
    public static void main(String[] args) {
        RuntimeException error = new RuntimeException(
                "outer sk-secret https://example.test/path?token=abc\nsecond line",
                new IllegalStateException("Authorization: Bearer very-secret"));
        StartupFailureInfo info = StartupFailureInfo.from(
                error, StartupStateStore.Stage.GECKO_RUNTIME);

        check(info.stage == StartupStateStore.Stage.GECKO_RUNTIME, "stage");
        check("RuntimeException".equals(info.exceptionType), "outer type");
        check("IllegalStateException".equals(info.causeType), "cause type");
        check(!info.exceptionMessage.contains("sk-secret"), "API-like token redacted");
        check(!info.exceptionMessage.contains("token=abc"), "URL query redacted");
        check(!info.causeMessage.toLowerCase().contains("bearer very-secret"), "bearer redacted");
        check(info.exceptionMessage.length() <= StartupFailureInfo.MAX_MESSAGE_LENGTH, "bounded message");
        System.out.println("StartupFailureInfoTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
