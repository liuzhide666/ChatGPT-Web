package com.liuchenlin.chatgptweb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class TrustedOriginPolicy {
    private static final String CHATGPT_HOST = "chatgpt.com";
    private static final String OPENAI_HOST = "openai.com";

    private TrustedOriginPolicy() {}

    public static boolean isTrustedMediaUri(String value) {
        if (value == null) {
            return false;
        }
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return isHostOrSubdomain(host, CHATGPT_HOST) || isHostOrSubdomain(host, OPENAI_HOST);
    }

    private static boolean isHostOrSubdomain(String host, String base) {
        return host.equals(base) || host.endsWith("." + base);
    }
}
