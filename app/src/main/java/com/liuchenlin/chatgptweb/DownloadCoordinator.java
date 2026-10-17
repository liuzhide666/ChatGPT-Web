package com.liuchenlin.chatgptweb;

import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.mozilla.geckoview.WebResponse;

/** Owns one-response download streaming and the API 26-28 save-as handoff. */
public final class DownloadCoordinator implements GeckoSessionController.DownloadHandler {
    public interface Host {
        MediaStoreDownloadsCompat mediaStoreDownloads();
        OutputStream openOutputStream(Uri uri) throws IOException;
        boolean launchLegacySaveAs(DownloadMetadata metadata);
        void notifyStarted(String fileName);
        void notifySuccess(String fileName);
        void notifyFailure(String fileName);
    }

    private static final int BUFFER_SIZE = 64 * 1024;

    private final Host host;
    private final ExternalFlowTracker externalFlows;
    private final Executor executor;
    private PendingLegacy pendingLegacy;

    private static final class PendingLegacy {
        final DownloadMetadata metadata;
        final InputStream body;
        PendingLegacy(DownloadMetadata metadata, InputStream body) {
            this.metadata = metadata;
            this.body = body;
        }
    }

    public DownloadCoordinator(Host host, ExternalFlowTracker externalFlows, Executor executor) {
        this.host = Objects.requireNonNull(host, "host");
        this.externalFlows = Objects.requireNonNull(externalFlows, "externalFlows");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void handle(WebResponse response) {
        if (response == null || response.body == null) {
            closeBody(response == null ? null : response.body);
            return;
        }
        DownloadMetadata metadata = DownloadMetadata.from(response);
        host.notifyStarted(metadata.fileName);
        MediaStoreDownloadsCompat mediaStore = host.mediaStoreDownloads();
        if (mediaStore != null) {
            executor.execute(() -> saveToMediaStore(mediaStore, metadata, response.body));
            return;
        }
        beginLegacySave(metadata, response.body);
    }

    private void beginLegacySave(DownloadMetadata metadata, InputStream body) {
        synchronized (this) {
            if (pendingLegacy != null) {
                closeBody(body);
                host.notifyFailure(metadata.fileName);
                return;
            }
            pendingLegacy = new PendingLegacy(metadata, body);
            externalFlows.begin(ExternalFlowTracker.Flow.DOWNLOAD_SAVE);
        }
        if (!host.launchLegacySaveAs(metadata)) {
            PendingLegacy pending = takePendingLegacy();
            closeBody(pending == null ? null : pending.body);
            host.notifyFailure(metadata.fileName);
        }
    }

    public void onLegacySaveResult(Uri destination) {
        PendingLegacy pending = takePendingLegacy();
        if (pending == null) return;
        if (destination == null) {
            closeBody(pending.body);
            return;
        }
        executor.execute(() -> saveToSelectedUri(pending, destination));
    }

    @Override
    public void abandonPending() {
        PendingLegacy pending = takePendingLegacy();
        closeBody(pending == null ? null : pending.body);
    }

    private synchronized PendingLegacy takePendingLegacy() {
        PendingLegacy pending = pendingLegacy;
        pendingLegacy = null;
        externalFlows.end(ExternalFlowTracker.Flow.DOWNLOAD_SAVE);
        return pending;
    }

    private void saveToMediaStore(
            MediaStoreDownloadsCompat mediaStore, DownloadMetadata metadata, InputStream body) {
        Uri item = null;
        try (InputStream input = body) {
            item = mediaStore.create(metadata);
            if (item == null) throw new IOException("MediaStore insert returned null");
            try (OutputStream output = requireOutput(mediaStore.open(item))) {
                copy(input, output);
            }
            mediaStore.publish(item);
            host.notifySuccess(metadata.fileName);
        } catch (IOException | RuntimeException error) {
            if (item != null) mediaStore.delete(item);
            closeBody(body);
            host.notifyFailure(metadata.fileName);
        }
    }

    private void saveToSelectedUri(PendingLegacy pending, Uri destination) {
        try (InputStream input = pending.body;
             OutputStream output = requireOutput(host.openOutputStream(destination))) {
            copy(input, output);
            host.notifySuccess(pending.metadata.fileName);
        } catch (IOException | RuntimeException error) {
            closeBody(pending.body);
            host.notifyFailure(pending.metadata.fileName);
        }
    }

    private static OutputStream requireOutput(OutputStream output) throws IOException {
        if (output == null) throw new IOException("ContentResolver returned null OutputStream");
        return output;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    static void closeBody(InputStream body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException ignored) {
            // Closing is best-effort on a terminal path.
        }
    }
}
