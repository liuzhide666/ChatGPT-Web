package com.liuchenlin.chatgptweb;

import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.mozilla.geckoview.WebResponse;

public final class DownloadCoordinatorTest {
    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        boolean closed;
        CloseTrackingInputStream(String value) { super(value.getBytes(StandardCharsets.UTF_8)); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    private static final class Host implements DownloadCoordinator.Host {
        int sdk = 29;
        boolean directAvailable = true;
        int directStoreRequests;
        int createCalls, publishCalls, deleteCalls, legacyLaunchCalls, successCalls, failureCalls;
        Uri created = Uri.parse("content://downloads/1");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DownloadMetadata lastLegacyMetadata;
        boolean legacyLaunchResult = true;
        boolean failOpen;

        @Override public MediaStoreDownloadsCompat mediaStoreDownloads() {
            directStoreRequests++;
            return sdk >= 29 && directAvailable ? MediaStoreDownloadsCompat.forTesting(
                    this::createMediaStoreItem, this::openOutputStream,
                    this::publishMediaStoreItem, this::deleteMediaStoreItem) : null;
        }
        public Uri createMediaStoreItem(DownloadMetadata metadata) { createCalls++; return created; }
        @Override public OutputStream openOutputStream(Uri uri) throws IOException {
            if (failOpen) throw new IOException("open failed");
            return output;
        }
        public void publishMediaStoreItem(Uri uri) { publishCalls++; }
        public void deleteMediaStoreItem(Uri uri) { deleteCalls++; }
        @Override public boolean launchLegacySaveAs(DownloadMetadata metadata) {
            legacyLaunchCalls++;
            lastLegacyMetadata = metadata;
            return legacyLaunchResult;
        }
        @Override public void notifyStarted(String fileName) {}
        @Override public void notifySuccess(String fileName) { successCalls++; }
        @Override public void notifyFailure(String fileName) { failureCalls++; }
    }

    private static WebResponse response(CloseTrackingInputStream body) {
        return new WebResponse("https://example.test/report.txt",
                Map.of("Content-Type", "text/plain", "Content-Disposition", "attachment; filename=report.txt"), body);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        ExternalFlowTracker flows = new ExternalFlowTracker();
        Host host = new Host();
        DownloadCoordinator coordinator = new DownloadCoordinator(host, flows, Runnable::run);

        CloseTrackingInputStream body = new CloseTrackingInputStream("hello");
        coordinator.handle(response(body));
        check(host.createCalls == 1 && host.publishCalls == 1 && host.deleteCalls == 0,
                "API29 success publishes one MediaStore item");
        check("hello".equals(host.output.toString(StandardCharsets.UTF_8)), "body streamed exactly once");
        check(body.closed, "API29 body closed");
        check(host.successCalls == 1 && host.failureCalls == 0, "success notified");

        Host failing = new Host();
        failing.failOpen = true;
        CloseTrackingInputStream failedBody = new CloseTrackingInputStream("x");
        new DownloadCoordinator(failing, new ExternalFlowTracker(), Runnable::run).handle(response(failedBody));
        check(failing.deleteCalls == 1 && failing.publishCalls == 0, "failed pending item deleted");
        check(failing.failureCalls == 1 && failedBody.closed, "failure closes body and notifies");


        Host incompleteApi29 = new Host();
        incompleteApi29.sdk = 29;
        incompleteApi29.directAvailable = false;
        ExternalFlowTracker fallbackFlows = new ExternalFlowTracker();
        DownloadCoordinator fallbackCoordinator = new DownloadCoordinator(incompleteApi29, fallbackFlows, Runnable::run);
        CloseTrackingInputStream fallbackBody = new CloseTrackingInputStream("fallback");
        fallbackCoordinator.handle(response(fallbackBody));
        check(incompleteApi29.createCalls == 0 && incompleteApi29.legacyLaunchCalls == 1,
                "API29 without Downloads capability falls back to SAF");
        check(fallbackFlows.isActive(ExternalFlowTracker.Flow.DOWNLOAD_SAVE),
                "capability fallback tracks save-as flow");
        fallbackCoordinator.onLegacySaveResult(null);
        check(fallbackBody.closed, "cancelled capability fallback closes body");

        ExternalFlowTracker legacyFlows = new ExternalFlowTracker();
        Host legacy = new Host();
        legacy.sdk = 28;
        DownloadCoordinator legacyCoordinator = new DownloadCoordinator(legacy, legacyFlows, Runnable::run);
        CloseTrackingInputStream legacyBody = new CloseTrackingInputStream("legacy");
        legacyCoordinator.handle(response(legacyBody));
        check(legacy.legacyLaunchCalls == 1 && legacyFlows.isActive(ExternalFlowTracker.Flow.DOWNLOAD_SAVE),
                "legacy download launches save-as and tracks external flow");
        check(!legacyBody.closed, "legacy body remains open only while destination is pending");
        legacyCoordinator.onLegacySaveResult(Uri.parse("content://document/7"));
        check(!legacyFlows.isAnyActive(), "legacy flow ends on result");
        check("legacy".equals(legacy.output.toString(StandardCharsets.UTF_8)), "legacy body written to selected URI");
        check(legacyBody.closed && legacy.successCalls == 1, "legacy body closes after save");

        ExternalFlowTracker abandonedFlows = new ExternalFlowTracker();
        Host abandoned = new Host();
        abandoned.sdk = 28;
        DownloadCoordinator abandonedCoordinator = new DownloadCoordinator(abandoned, abandonedFlows, Runnable::run);
        CloseTrackingInputStream abandonedBody = new CloseTrackingInputStream("pending");
        abandonedCoordinator.handle(response(abandonedBody));
        abandonedCoordinator.abandonPending();
        check(abandonedBody.closed && !abandonedFlows.isAnyActive(), "abandon closes pending body and clears flow");

        System.out.println("DownloadCoordinatorTest OK");
    }
}
