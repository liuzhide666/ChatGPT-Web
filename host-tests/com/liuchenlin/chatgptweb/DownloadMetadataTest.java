package com.liuchenlin.chatgptweb;

import java.util.Map;
import java.util.TreeMap;
import org.mozilla.geckoview.WebResponse;

public final class DownloadMetadataTest {
    private static WebResponse response(String uri, String disposition, String contentType) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (disposition != null) headers.put("Content-Disposition", disposition);
        if (contentType != null) headers.put("Content-Type", contentType);
        return new WebResponse(uri, headers, null);
    }

    private static void equals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        DownloadMetadata rfc5987 = DownloadMetadata.from(response(
                "https://example.test/fallback.bin",
                "attachment; filename*=UTF-8''%E6%B5%8B%E8%AF%95%20file.pdf; filename=old.pdf",
                "application/pdf; charset=binary"));
        equals("\u6d4b\u8bd5 file.pdf", rfc5987.fileName, "filename* must win");
        equals("application/pdf", rfc5987.mimeType, "MIME parameters must be stripped");

        DownloadMetadata quoted = DownloadMetadata.from(response(
                "https://example.test/a.bin", "attachment; filename=\"report final.docx\"", null));
        equals("report final.docx", quoted.fileName, "quoted filename");
        equals("application/octet-stream", quoted.mimeType, "missing MIME fallback");

        DownloadMetadata uri = DownloadMetadata.from(response(
                "https://example.test/path/photo%20one.png?token=x", null, "image/png"));
        equals("photo one.png", uri.fileName, "URI path fallback must decode percent escapes");

        DownloadMetadata unsafe = DownloadMetadata.from(response(
                "https://example.test/ignored", "attachment; filename=\"../bad\\name\u0000.txt\"", "text/plain"));
        if (unsafe.fileName.contains("/") || unsafe.fileName.contains("\\") || unsafe.fileName.indexOf((char) 0) >= 0) {
            throw new AssertionError("unsafe filename characters must be removed: " + unsafe.fileName);
        }
        if (unsafe.fileName.equals(".") || unsafe.fileName.equals("..") || unsafe.fileName.isBlank()) {
            throw new AssertionError("unsafe filename must resolve to a usable name");
        }

        DownloadMetadata fallback = DownloadMetadata.from(response("https://example.test/", null, null));
        equals("download", fallback.fileName, "empty URI path fallback");
        System.out.println("DownloadMetadataTest OK");
    }
}
