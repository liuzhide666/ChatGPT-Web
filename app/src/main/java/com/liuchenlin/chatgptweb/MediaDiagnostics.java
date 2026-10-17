package com.liuchenlin.chatgptweb;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.MediaDelegate;
import org.mozilla.geckoview.GeckoSession.MediaDelegate.RecordingDevice;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource;

public final class MediaDiagnostics implements MediaDelegate {
    public static final String TAG_PERMISSION = "ChatGPTWeb/Permission";
    public static final String TAG_MEDIA = "ChatGPTWeb/Media";
    public static final String TAG_SESSION = "ChatGPTWeb/Session";
    public static final String TAG_NAVIGATION = "ChatGPTWeb/Navigation";
    public static final String TAG_RECOVERY = "ChatGPTWeb/Recovery";

    public interface Sink {
        void write(String tag, String message);
    }

    public interface RecordingStateListener {
        void onRecordingStateChanged(boolean active);
    }

    private final Sink sink;
    private final MediaCrashTraceStore traceStore;
    private volatile boolean recordingActive;
    private volatile RecordingStateListener recordingStateListener = active -> {};

    public MediaDiagnostics(Sink sink) {
        this(sink, null);
    }

    public MediaDiagnostics(Sink sink, MediaCrashTraceStore traceStore) {
        this.sink = sink;
        this.traceStore = traceStore;
    }

    public String recentTraceSummary() {
        return traceStore == null ? "" : traceStore.summary();
    }

    public boolean isRecordingActive() {
        return recordingActive;
    }

    public void setRecordingStateListener(RecordingStateListener listener) {
        recordingStateListener = listener == null ? active -> {} : listener;
    }

    private void emit(String tag, String message, boolean persist) {
        sink.write(tag, message);
        if (persist && traceStore != null) {
            traceStore.append(message);
        }
    }

    public void androidRequest(String permission) {
        emit(TAG_PERMISSION, "ANDROID_REQUEST permission=" + permissionLabel(permission), true);
    }

    public void androidResult(String permission, boolean granted) {
        emit(TAG_PERMISSION, "ANDROID_RESULT permission=" + permissionLabel(permission) + " granted=" + granted, true);
    }

    public void mediaRequest(String uri) {
        if (traceStore != null) {
            traceStore.beginAttempt();
        }
        emit(TAG_MEDIA, "MEDIA_REQUEST origin=" + originOnly(uri), true);
    }

    public void audioSources(MediaSource[] audio) {
        int count = audio == null ? 0 : audio.length;
        emit(TAG_MEDIA, "AUDIO_SOURCE count=" + count, true);
        if (audio == null) {
            return;
        }
        for (int i = 0; i < audio.length; i++) {
            MediaSource source = audio[i];
            if (source == null) {
                emit(TAG_MEDIA, "AUDIO_SOURCE index=" + i + " null=true", true);
                continue;
            }
            emit(TAG_MEDIA,
                    "AUDIO_SOURCE index=" + i
                            + " type=" + mediaTypeLabel(source.type)
                            + " source=" + mediaSourceLabel(source.source)
                            + " idHash=" + shortHash(source.id), true);
        }
    }

    public void sessionEvent(String event) {
        emit(TAG_SESSION, event, false);
    }

    public void recoveryEvent(String event, String action) {
        emit(TAG_RECOVERY, "CONTENT_PROCESS event=" + event + " action=" + action, true);
        if (traceStore != null && ("CRASH".equals(event) || "KILL".equals(event))) {
            traceStore.completeAttempt();
        }
    }

    public void navigationDecision(String uri, NavigationPolicy.Action action, boolean externalOpened) {
        emit(TAG_NAVIGATION,
                "NAVIGATION action=" + action
                        + " target=" + safeNavigationTarget(uri)
                        + (action == NavigationPolicy.Action.EXTERNAL_INTENT
                                ? " externalOpened=" + externalOpened
                                : ""), false);
    }

    public void mediaRejected(String reason) {
        String safe = reason == null ? "UNKNOWN" : reason.replaceAll("[^A-Z0-9_]", "_");
        emit(TAG_MEDIA, "MEDIA_REJECT reason=" + safe, true);
        if (traceStore != null) {
            traceStore.completeAttempt();
        }
    }

    public void mediaCallbackException(String phase, Throwable error) {
        emit(TAG_MEDIA, "MEDIA_CALLBACK phase=" + safePhase(phase)
                + " exception=" + exceptionType(error), true);
        if (traceStore != null) {
            traceStore.completeAttempt();
        }
    }

    public void permissionCallbackException(String phase, Throwable error) {
        emit(TAG_PERMISSION, "ANDROID_CALLBACK phase=" + safePhase(phase)
                + " exception=" + exceptionType(error), true);
    }

    public void audioSelected(MediaSource source) {
        if (source == null) {
            emit(TAG_MEDIA, "AUDIO_SELECTED source=NONE", true);
            return;
        }
        emit(TAG_MEDIA,
                "AUDIO_SELECTED source=" + mediaSourceLabel(source.source)
                        + " idHash=" + shortHash(source.id), true);
    }

    public void mediaGrantBegin(MediaSource video, MediaSource audio) {
        emit(TAG_MEDIA, "MEDIA_GRANT begin video=" + (video == null ? "NONE" : mediaSourceLabel(video.source))
                + " audio=" + (audio == null ? "NONE" : mediaSourceLabel(audio.source)), true);
    }

    public void mediaGrantReturned() {
        emit(TAG_MEDIA, "MEDIA_GRANT returned=true", true);
    }

    @Override
    public void onRecordingStatusChanged(GeckoSession session, RecordingDevice[] devices) {
        if (devices == null) {
            return;
        }
        boolean sawDevice = false;
        boolean anyRecording = false;
        for (RecordingDevice device : devices) {
            if (device == null) {
                continue;
            }
            sawDevice = true;
            if (device.status == RecordingDevice.Status.RECORDING) {
                anyRecording = true;
            }
            emit(TAG_MEDIA,
                    "RECORDING type=" + recordingTypeLabel(device.type)
                            + " status=" + recordingStatusLabel(device.status), true);
        }
        if (sawDevice) {
            boolean changed = recordingActive != anyRecording;
            recordingActive = anyRecording;
            if (changed) recordingStateListener.onRecordingStateChanged(anyRecording);
        }
        if (sawDevice && !anyRecording && traceStore != null) {
            traceStore.completeAttempt();
        }
    }

    private static String safePhase(String phase) {
        if (phase == null) {
            return "UNKNOWN";
        }
        String safe = phase.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return safe.isEmpty() ? "UNKNOWN" : safe;
    }

    private static String exceptionType(Throwable error) {
        return error == null ? "UNKNOWN" : error.getClass().getSimpleName();
    }

    private static String permissionLabel(String permission) {
        if (permission == null) {
            return "UNKNOWN";
        }
        String prefix = "android.permission.";
        return permission.startsWith(prefix) ? permission.substring(prefix.length()) : "UNKNOWN";
    }

    private static String safeNavigationTarget(String value) {
        if (value == null) {
            return "<invalid>";
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return "<invalid>";
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if ("http".equals(scheme) || "https".equals(scheme)) {
                String host = uri.getHost();
                if (host == null) {
                    return scheme + ":";
                }
                StringBuilder out = new StringBuilder()
                        .append(scheme)
                        .append("://")
                        .append(host.toLowerCase(Locale.ROOT));
                if (uri.getPort() >= 0) {
                    out.append(':').append(uri.getPort());
                }
                return out.toString();
            }
            return scheme + ":";
        } catch (URISyntaxException | IllegalArgumentException e) {
            return "<invalid>";
        }
    }

    private static String originOnly(String value) {
        if (value == null) {
            return "<invalid>";
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return "<invalid>";
            }
            StringBuilder out = new StringBuilder()
                    .append(scheme.toLowerCase(Locale.ROOT))
                    .append("://")
                    .append(host.toLowerCase(Locale.ROOT));
            if (uri.getPort() >= 0) {
                out.append(':').append(uri.getPort());
            }
            return out.toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return "<invalid>";
        }
    }

    private static String mediaTypeLabel(int type) {
        if (type == MediaSource.TYPE_AUDIO) return "AUDIO";
        if (type == MediaSource.TYPE_VIDEO) return "VIDEO";
        return "OTHER";
    }

    private static String mediaSourceLabel(int source) {
        if (source == MediaSource.SOURCE_MICROPHONE) return "MICROPHONE";
        if (source == MediaSource.SOURCE_AUDIOCAPTURE) return "AUDIOCAPTURE";
        if (source == MediaSource.SOURCE_CAMERA) return "CAMERA";
        if (source == MediaSource.SOURCE_SCREEN) return "SCREEN";
        return "OTHER";
    }

    private static String recordingTypeLabel(long type) {
        if (type == RecordingDevice.Type.MICROPHONE) return "MICROPHONE";
        if (type == RecordingDevice.Type.CAMERA) return "CAMERA";
        return "OTHER";
    }

    private static String recordingStatusLabel(long status) {
        if (status == RecordingDevice.Status.RECORDING) return "RECORDING";
        if (status == RecordingDevice.Status.INACTIVE) return "INACTIVE";
        return "OTHER";
    }

    private static String shortHash(String value) {
        if (value == null) {
            return "null";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                out.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
