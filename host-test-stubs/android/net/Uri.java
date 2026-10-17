package android.net;

import java.net.URI;

public final class Uri {
    private final String value;
    private Uri(String value) { this.value = value; }
    public static Uri parse(String value) { return new Uri(value); }
    public String getScheme() {
        try { return new URI(value).getScheme(); } catch (Exception e) { return null; }
    }
    @Override public String toString() { return value; }
}
