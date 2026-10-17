package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaCallback;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource;

public final class GeckoMediaPermissionDelegateTest {
    private static final class Host implements AndroidPermissionCoordinator.Host {
        final Set<String> granted = new HashSet<>();
        final List<String[]> requests = new ArrayList<>();
        @Override public boolean isGranted(String permission) { return granted.contains(permission); }
        @Override public void requestPermissions(String[] permissions, int requestCode) { requests.add(permissions.clone()); }
    }
    private static final class PermissionCallback implements GeckoSession.PermissionDelegate.Callback {
        int grants, rejects;
        @Override public void grant() { grants++; }
        @Override public void reject() { rejects++; }
    }
    private static final class MediaResult implements MediaCallback {
        int grants, rejects;
        MediaSource video, audio;
        @Override public void grant(MediaSource video, MediaSource audio) {
            grants++; this.video = video; this.audio = audio;
        }
        @Override public void reject() { rejects++; }
    }
    private static final class Sink implements MediaDiagnostics.Sink {
        final List<String> lines = new ArrayList<>();
        @Override public void write(String tag, String message) { lines.add(tag + ":" + message); }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Host host = new Host();
        ExternalFlowTracker flows = new ExternalFlowTracker();
        AndroidPermissionCoordinator android = new AndroidPermissionCoordinator(host, flows);
        Sink sink = new Sink();
        GeckoMediaPermissionDelegate delegate = new GeckoMediaPermissionDelegate(android, new MediaDiagnostics(sink));
        GeckoSession session = new GeckoSession();

        MediaSource playback = new MediaSource("playback", "Playback", MediaSource.SOURCE_AUDIOCAPTURE, MediaSource.TYPE_AUDIO);
        MediaSource mic = new MediaSource("mic", "Mic", MediaSource.SOURCE_MICROPHONE, MediaSource.TYPE_AUDIO);
        MediaSource camera = new MediaSource("camera", "Camera", MediaSource.SOURCE_CAMERA, MediaSource.TYPE_VIDEO);
        MediaSource screen = new MediaSource("screen", "Screen", MediaSource.SOURCE_SCREEN, MediaSource.TYPE_VIDEO);

        String micPermission = "android.permission.RECORD_AUDIO";
        String cameraPermission = "android.permission.CAMERA";

        MediaResult beforeOsGrant = new MediaResult();
        delegate.onMediaPermissionRequest(session, "https://chatgpt.com/voice", null,
                new MediaSource[]{mic}, beforeOsGrant);
        check(beforeOsGrant.rejects == 1 && beforeOsGrant.grants == 0,
                "physical microphone alone cannot bypass Android RECORD_AUDIO");

        PermissionCallback permission = new PermissionCallback();
        delegate.onAndroidPermissionsRequest(session, new String[]{micPermission}, permission);
        check(host.requests.size() == 1, "Android permission request delegated");
        check(flows.isActive(ExternalFlowTracker.Flow.ANDROID_PERMISSION), "permission external flow active");
        host.granted.add(micPermission);
        android.onPermissionsResult(AndroidPermissionCoordinator.REQUEST_CODE, new boolean[]{true});
        check(permission.grants == 1 && permission.rejects == 0, "Gecko callback granted after Android result");

        MediaResult trusted = new MediaResult();
        delegate.onMediaPermissionRequest(session, "https://chatgpt.com/voice", null,
                new MediaSource[]{playback, mic}, trusted);
        check(trusted.grants == 1 && trusted.rejects == 0, "trusted mic request grants after OS permission");
        check(trusted.audio == mic, "grant returns exact original microphone object");
        check(trusted.video == null, "no video requested");

        MediaResult untrusted = new MediaResult();
        delegate.onMediaPermissionRequest(session, "https://evilchatgpt.com", null,
                new MediaSource[]{mic}, untrusted);
        check(untrusted.rejects == 1 && untrusted.grants == 0, "untrusted media origin rejected");

        MediaResult noMic = new MediaResult();
        delegate.onMediaPermissionRequest(session, "https://chatgpt.com", null,
                new MediaSource[]{playback}, noMic);
        check(noMic.rejects == 1, "audio request without physical microphone rejected");

        MediaResult cameraBeforeOsGrant = new MediaResult();
        delegate.onMediaPermissionRequest(session, "https://openai.com", new MediaSource[]{camera},
                null, cameraBeforeOsGrant);
        check(cameraBeforeOsGrant.rejects == 1 && cameraBeforeOsGrant.grants == 0,
                "physical camera alone cannot bypass Android CAMERA permission");

        host.granted.add(cameraPermission);
        MediaResult cameraOnly = new MediaResult();
        delegate.onMediaPermissionRequest(session, "https://openai.com", new MediaSource[]{screen, camera},
                null, cameraOnly);
        check(cameraOnly.grants == 1 && cameraOnly.video == camera && cameraOnly.audio == null,
                "camera-only request grants original camera after OS permission");

        MediaResult noCamera = new MediaResult();
        delegate.onMediaPermissionRequest(session, "https://openai.com", new MediaSource[]{screen},
                null, noCamera);
        check(noCamera.rejects == 1, "video request without camera rejected");

        PermissionCallback unknown = new PermissionCallback();
        delegate.onAndroidPermissionsRequest(session, new String[]{"android.permission.READ_CONTACTS"}, unknown);
        check(unknown.rejects == 1, "unknown Android permission rejected");

        MediaCallback throwingGrant = new MediaCallback() {
            @Override public void grant(MediaSource video, MediaSource audio) {
                throw new IllegalStateException("dead Gecko callback");
            }
            @Override public void reject() {}
        };
        delegate.onMediaPermissionRequest(session, "https://chatgpt.com/voice", null,
                new MediaSource[]{mic}, throwingGrant);

        String logs = String.join("\n", sink.lines);
        check(logs.contains("MEDIA_REQUEST origin=https://chatgpt.com"), "trusted media request logged");
        check(logs.contains("MEDIA_REJECT reason=ANDROID_RECORD_AUDIO_NOT_GRANTED"),
                "OS microphone permission rejection is diagnosable");
        check(logs.contains("AUDIO_SELECTED source=MICROPHONE"), "selected microphone logged");
        check(logs.contains("ANDROID_RESULT permission=RECORD_AUDIO granted=true"), "Android result logged");
        check(logs.contains("MEDIA_CALLBACK phase=GRANT exception=IllegalStateException"),
                "throwing Gecko media callback is isolated and diagnosable");
        System.out.println("GeckoMediaPermissionDelegateTest OK");
    }
}
