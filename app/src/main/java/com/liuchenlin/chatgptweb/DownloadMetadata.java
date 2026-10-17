package com.liuchenlin.chatgptweb;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.mozilla.geckoview.WebResponse;

/** Deterministic metadata extracted from one Gecko download response. */
public final class DownloadMetadata {
    private static final int MAX_FILE_NAME = 180;

    public final String fileName;
    public final String mimeType;

    private DownloadMetadata(String fileName, String mimeType) {
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public static DownloadMetadata from(WebResponse response) {
        if (response == null) {
            return new DownloadMetadata("download", "application/octet-stream");
        }
        Map<String, String> headers = response.headers;
        String disposition = header(headers, "Content-Disposition");
        String name = filenameStar(disposition);
        if (name == null) name = filenamePlain(disposition);
        if (name == null) name = uriFileName(response.uri);
        name = sanitize(name);

        String mime = header(headers, "Content-Type");
        if (mime != null) {
            int semicolon = mime.indexOf(';');
            if (semicolon >= 0) mime = mime.substring(0, semicolon);
            mime = mime.trim();
        }
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
        return new DownloadMetadata(name, mime);
    }

    private static String header(Map<String, String> headers, String wanted) {
        if (headers == null) return null;
        String direct = headers.get(wanted);
        if (direct != null) return direct;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (wanted.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private static String filenameStar(String disposition) {
        String raw = parameter(disposition, "filename*");
        if (raw == null) return null;
        int first = raw.indexOf('\'');
        int second = first < 0 ? -1 : raw.indexOf('\'', first + 1);
        if (first <= 0 || second < 0) return percentDecode(raw, StandardCharsets.UTF_8);
        String charsetName = raw.substring(0, first).trim();
        String encoded = raw.substring(second + 1);
        Charset charset;
        try {
            charset = Charset.forName(charsetName);
        } catch (RuntimeException ignored) {
            charset = StandardCharsets.UTF_8;
        }
        return percentDecode(encoded, charset);
    }

    private static String filenamePlain(String disposition) {
        return parameter(disposition, "filename");
    }

    private static String parameter(String disposition, String key) {
        if (disposition == null) return null;
        int length = disposition.length();
        int cursor = 0;
        while (cursor < length) {
            int end = nextSemicolon(disposition, cursor);
            String part = disposition.substring(cursor, end).trim();
            int equals = part.indexOf('=');
            if (equals > 0 && key.equalsIgnoreCase(part.substring(0, equals).trim())) {
                String value = part.substring(equals + 1).trim();
                if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                    value = unescapeQuoted(value.substring(1, value.length() - 1));
                }
                return value.isEmpty() ? null : value;
            }
            cursor = end + 1;
        }
        return null;
    }

    private static int nextSemicolon(String value, int start) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\' && quoted) {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ';' && !quoted) {
                return i;
            }
        }
        return value.length();
    }

    private static String unescapeQuoted(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                out.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private static String uriFileName(String uri) {
        if (uri == null || uri.isBlank()) return null;
        try {
            String path = URI.create(uri).getRawPath();
            if (path == null || path.isEmpty() || path.endsWith("/")) return null;
            int slash = path.lastIndexOf('/');
            String last = slash >= 0 ? path.substring(slash + 1) : path;
            return percentDecode(last, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String percentDecode(String value, Charset charset) {
        StringBuilder out = new StringBuilder(value.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < value.length();) {
            if (value.charAt(i) != '%') {
                out.append(value.charAt(i++));
                continue;
            }
            bytes.reset();
            int cursor = i;
            while (cursor + 2 < value.length() && value.charAt(cursor) == '%') {
                int hi = Character.digit(value.charAt(cursor + 1), 16);
                int lo = Character.digit(value.charAt(cursor + 2), 16);
                if (hi < 0 || lo < 0) break;
                bytes.write((hi << 4) | lo);
                cursor += 3;
            }
            if (bytes.size() == 0) {
                out.append('%');
                i++;
            } else {
                out.append(new String(bytes.toByteArray(), charset));
                i = cursor;
            }
        }
        return out.toString();
    }

    private static String sanitize(String candidate) {
        if (candidate == null) return "download";
        StringBuilder out = new StringBuilder(candidate.length());
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c == '/' || c == '\\' || c == 0 || Character.isISOControl(c)) {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        String value = out.toString().trim();
        if (value.equals(".") || value.equals("..") || value.isEmpty()) value = "download";
        if (value.length() > MAX_FILE_NAME) value = value.substring(0, MAX_FILE_NAME).trim();
        if (value.isEmpty() || value.equals(".") || value.equals("..")) value = "download";
        return value;
    }
}
