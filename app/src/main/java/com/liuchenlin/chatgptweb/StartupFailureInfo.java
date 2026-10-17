package com.liuchenlin.chatgptweb;

import java.util.Locale;
import java.util.regex.Pattern;

/** Small, persisted and deliberately sanitized startup-failure summary. */
public final class StartupFailureInfo {
    public static final int MAX_MESSAGE_LENGTH = 160;

    private static final String REDACTED = "[\u5df2\u9690\u85cf]";
    private static final Pattern URL_QUERY = Pattern.compile(
            "([A-Za-z][A-Za-z0-9+.-]*://[^\\s?]+)\\?[^\\s]*");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(bearer\\s+)[^\\s]+");
    private static final Pattern OPENAI_KEY = Pattern.compile(
            "(?i)sk-[A-Za-z0-9_-]{4,}");
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)(token|key|secret|auth|authorization)=([^\\s&]+)");

    public final StartupStateStore.Stage stage;
    public final String exceptionType;
    public final String exceptionMessage;
    public final String causeType;
    public final String causeMessage;

    StartupFailureInfo(
            StartupStateStore.Stage stage,
            String exceptionType,
            String exceptionMessage,
            String causeType,
            String causeMessage) {
        this.stage = stage == null ? StartupStateStore.Stage.NONE : stage;
        this.exceptionType = safeType(exceptionType);
        this.exceptionMessage = sanitize(exceptionMessage);
        this.causeType = safeType(causeType);
        this.causeMessage = sanitize(causeMessage);
    }

    public static StartupFailureInfo from(Throwable error, StartupStateStore.Stage stage) {
        if (error == null) {
            return new StartupFailureInfo(stage, "Unknown", "", "", "");
        }
        Throwable cause = error.getCause();
        return new StartupFailureInfo(
                stage,
                typeName(error),
                error.getMessage(),
                cause == null ? "" : typeName(cause),
                cause == null ? "" : cause.getMessage());
    }

    static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String value = raw.replaceAll("[\\r\\n\\t]+", " ").trim();
        value = URL_QUERY.matcher(value).replaceAll("$1?" + REDACTED);
        value = BEARER.matcher(value).replaceAll("$1" + REDACTED);
        value = OPENAI_KEY.matcher(value).replaceAll(REDACTED);
        value = SECRET_PAIR.matcher(value).replaceAll("$1=" + REDACTED);
        value = value.replaceAll("\\s{2,}", " ");
        if (value.length() > MAX_MESSAGE_LENGTH) {
            value = value.substring(0, MAX_MESSAGE_LENGTH - 1) + "\u2026";
        }
        return value;
    }

    private static String typeName(Throwable error) {
        String simple = error.getClass().getSimpleName();
        return simple == null || simple.isEmpty() ? error.getClass().getName() : simple;
    }

    private static String safeType(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String value = raw.replaceAll("[^A-Za-z0-9_.$]", "_");
        return value.length() <= 96 ? value : value.substring(0, 96);
    }
}
