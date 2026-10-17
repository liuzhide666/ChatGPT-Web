package com.liuchenlin.chatgptweb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class NavigationPolicy {
    public enum Action {
        ALLOW_IN_GECKO,
        EXTERNAL_INTENT,
        DENY
    }

    private NavigationPolicy() {}

    public static Action classify(String value) {
        if (value == null) {
            return Action.DENY;
        }
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Action.DENY;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return Action.DENY;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return uri.getHost() == null ? Action.DENY : Action.ALLOW_IN_GECKO;
        }
        if ("http".equals(scheme)) {
            return uri.getHost() == null ? Action.DENY : Action.EXTERNAL_INTENT;
        }
        if ("mailto".equals(scheme) || "tel".equals(scheme) || "intent".equals(scheme)) {
            return Action.EXTERNAL_INTENT;
        }
        return Action.DENY;
    }
}
