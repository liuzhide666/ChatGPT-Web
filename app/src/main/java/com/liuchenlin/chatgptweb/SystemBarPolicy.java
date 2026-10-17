package com.liuchenlin.chatgptweb;

/** Pure policy for matching Android system bars to the current system light/dark mode. */
public final class SystemBarPolicy {
    private static final int UI_MODE_NIGHT_MASK = 0x30;
    private static final int UI_MODE_NIGHT_YES = 0x20;

    private SystemBarPolicy() {}

    public static boolean isDarkMode(int uiMode) {
        return (uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES;
    }

    public static int barColorArgb(boolean darkMode) {
        return darkMode ? 0xFF000000 : 0xFFFFFFFF;
    }

    /** True means Android should draw dark status/navigation icons on a light bar. */
    public static boolean useLightSystemBarIcons(boolean darkMode) {
        return !darkMode;
    }
}
