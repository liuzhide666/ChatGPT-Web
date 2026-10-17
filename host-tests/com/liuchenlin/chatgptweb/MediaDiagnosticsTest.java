package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.MediaDelegate.RecordingDevice;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource;

public final class MediaDiagnosticsTest {
    private static final class TraceBackend implements MediaCrashTraceStore.Backend {
        final Map<String,String> values = new HashMap<>();
        @Override public String getString(String key) { return values.get(key); }
        @Override public void putString(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }
    private static final class CaptureSink implements MediaDiagnostics.Sink {
        final List<String> lines = new ArrayList<>();
        @Override public void write(String tag, String message) { lines.add(tag + ":" + message); }
        String joined() { return String.join("\n", lines); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        CaptureSink sink = new CaptureSink();
        TraceBackend traceBackend = new TraceBackend();
        MediaCrashTraceStore traceStore = new MediaCrashTraceStore(traceBackend, 24);
        MediaDiagnostics d = new MediaDiagnostics(sink, traceStore);
        final int[] recordingChanges = {0};
        d.setRecordingStateListener(active -> recordingChanges[0]++);
        d.androidRequest("android.permission.RECORD_AUDIO");
        d.androidResult("android.permission.RECORD_AUDIO", true);
        d.mediaRequest("https://chatgpt.com/backend-api/voice?access_token=do-not-log");
        check(traceStore.hasInterruptedAttempt(), "media request marks active attempt for process-death diagnosis");

        MediaSource playback = new MediaSource("playback-device-identifier", "System playback",
                MediaSource.SOURCE_AUDIOCAPTURE, MediaSource.TYPE_AUDIO);
        MediaSource mic = new MediaSource("physical-mic-identifier", "Internal microphone",
                MediaSource.SOURCE_MICROPHONE, MediaSource.TYPE_AUDIO);
        d.audioSources(new MediaSource[]{playback, mic});
        d.audioSelected(mic);
        d.mediaGrantBegin(null, mic);
        d.mediaGrantReturned();
        d.onRecordingStatusChanged(new GeckoSession(), new RecordingDevice[]{
                new RecordingDevice(RecordingDevice.Type.MICROPHONE, RecordingDevice.Status.RECORDING),
                new RecordingDevice(RecordingDevice.Type.CAMERA, RecordingDevice.Status.INACTIVE)
        });
        check(traceStore.hasInterruptedAttempt(), "active microphone keeps attempt sentinel set");
        d.onRecordingStatusChanged(new GeckoSession(), new RecordingDevice[]{
                new RecordingDevice(RecordingDevice.Type.MICROPHONE, RecordingDevice.Status.INACTIVE)
        });
        check(!traceStore.hasInterruptedAttempt(), "all recording devices inactive completes media attempt");
        check(recordingChanges[0] == 2, "recording start and stop both notify health scheduler");
        d.mediaRequest("https://chatgpt.com/voice");
        d.recoveryEvent("CRASH", "AUTO_RECOVER");
        check(!traceStore.hasInterruptedAttempt(), "handled content crash clears process-death sentinel");

        String all = sink.joined();
        check(all.contains("ChatGPTWeb/Permission:ANDROID_REQUEST permission=RECORD_AUDIO"), "permission request event");
        check(all.contains("ChatGPTWeb/Permission:ANDROID_RESULT permission=RECORD_AUDIO granted=true"), "permission result event");
        check(all.contains("ChatGPTWeb/Media:MEDIA_REQUEST origin=https://chatgpt.com"), "origin-only media event");
        check(!all.contains("backend-api"), "origin logging excludes path");
        check(!all.contains("do-not-log"), "origin logging excludes query");
        check(!all.contains("physical-mic-identifier"), "raw source id not logged");
        check(!all.contains("Internal microphone"), "device name not logged");
        check(all.contains("AUDIO_SOURCE count=2"), "source count");
        check(all.contains("source=MICROPHONE"), "physical microphone source classification");
        check(all.contains("AUDIO_SELECTED source=MICROPHONE"), "selected source event");
        check(all.contains("RECORDING type=MICROPHONE status=RECORDING"), "recording proof event");
        String persisted = traceStore.summary();
        check(persisted.contains("ANDROID_REQUEST permission=RECORD_AUDIO"), "permission stage persisted");
        check(persisted.contains("MEDIA_REQUEST origin=https://chatgpt.com"), "media request stage persisted");
        check(persisted.contains("AUDIO_SELECTED source=MICROPHONE"), "mic selection stage persisted");
        check(persisted.contains("MEDIA_GRANT begin video=NONE audio=MICROPHONE"), "grant entry stage persisted");
        check(persisted.contains("MEDIA_GRANT returned=true"), "grant return stage persisted");
        check(persisted.contains("RECORDING type=MICROPHONE status=RECORDING"), "recording stage persisted");
        check(!persisted.contains("do-not-log"), "persisted trace excludes URL query secrets");
        System.out.println("MediaDiagnosticsTest OK");
    }
}
