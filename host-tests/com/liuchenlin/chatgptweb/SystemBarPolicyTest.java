package com.liuchenlin.chatgptweb;

public final class SystemBarPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(!SystemBarPolicy.isDarkMode(0x10), "light mode must not be dark");
        check(SystemBarPolicy.isDarkMode(0x20), "night mode must be dark");
        check(SystemBarPolicy.barColorArgb(false) == 0xFFFFFFFF, "light bars are white");
        check(SystemBarPolicy.barColorArgb(true) == 0xFF000000, "dark bars are black");
        check(SystemBarPolicy.useLightSystemBarIcons(false), "light background uses dark icons");
        check(!SystemBarPolicy.useLightSystemBarIcons(true), "dark background uses light icons");
        System.out.println("SystemBarPolicyTest OK");
    }
}
