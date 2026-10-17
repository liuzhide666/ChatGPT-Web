package com.liuchenlin.chatgptweb;

import java.util.Objects;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource;

/** Bridges Gecko permission callbacks to the Android runtime-permission state machine. */
public final class GeckoMediaPermissionDelegate implements PermissionDelegate {
    private final AndroidPermissionCoordinator androidPermissions;
    private final MediaDiagnostics diagnostics;

    public GeckoMediaPermissionDelegate(
            AndroidPermissionCoordinator androidPermissions,
            MediaDiagnostics diagnostics) {
        this.androidPermissions = Objects.requireNonNull(androidPermissions, "androidPermissions");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public void onAndroidPermissionsRequest(
            GeckoSession session,
            String[] permissions,
            PermissionDelegate.Callback callback) {
        Objects.requireNonNull(callback, "callback");
        if (permissions != null) {
            for (String permission : permissions) {
                diagnostics.androidRequest(permission);
            }
        }
        androidPermissions.request(permissions, new AndroidPermissionCoordinator.Callback() {
            @Override
            public void grant() {
                logAndroidResults(permissions, true);
                safePermissionGrant(callback);
            }

            @Override
            public void reject() {
                logAndroidResults(permissions, false);
                safePermissionReject(callback);
            }
        });
    }

    @Override
    public void onMediaPermissionRequest(
            GeckoSession session,
            String uri,
            MediaSource[] video,
            MediaSource[] audio,
            PermissionDelegate.MediaCallback callback) {
        Objects.requireNonNull(callback, "callback");
        diagnostics.mediaRequest(uri);

        boolean wantsVideo = video != null && video.length > 0;
        boolean wantsAudio = audio != null && audio.length > 0;
        if (!wantsVideo && !wantsAudio) {
            diagnostics.mediaRejected("EMPTY_MEDIA_REQUEST");
            safeMediaReject(callback);
            return;
        }
        if (!TrustedOriginPolicy.isTrustedMediaUri(uri)) {
            diagnostics.mediaRejected("UNTRUSTED_ORIGIN");
            safeMediaReject(callback);
            return;
        }
        if (wantsAudio && !androidPermissions.isGranted(AndroidPermissionCoordinator.RECORD_AUDIO)) {
            diagnostics.mediaRejected("ANDROID_RECORD_AUDIO_NOT_GRANTED");
            safeMediaReject(callback);
            return;
        }
        if (wantsVideo && !androidPermissions.isGranted(AndroidPermissionCoordinator.CAMERA)) {
            diagnostics.mediaRejected("ANDROID_CAMERA_NOT_GRANTED");
            safeMediaReject(callback);
            return;
        }

        MediaSource selectedAudio = null;
        if (wantsAudio) {
            diagnostics.audioSources(audio);
            selectedAudio = MediaSelectionPolicy.firstPhysicalMicrophone(audio);
            if (selectedAudio == null) {
                diagnostics.audioSelected(null);
                diagnostics.mediaRejected("NO_PHYSICAL_MICROPHONE");
                safeMediaReject(callback);
                return;
            }
        }

        MediaSource selectedVideo = null;
        if (wantsVideo) {
            selectedVideo = MediaSelectionPolicy.firstCamera(video);
            if (selectedVideo == null) {
                diagnostics.mediaRejected("NO_PHYSICAL_CAMERA");
                safeMediaReject(callback);
                return;
            }
        }

        if (wantsAudio) {
            diagnostics.audioSelected(selectedAudio);
        }
        diagnostics.mediaGrantBegin(selectedVideo, selectedAudio);
        safeMediaGrant(callback, selectedVideo, selectedAudio);
    }

    private void safePermissionGrant(PermissionDelegate.Callback callback) {
        try {
            callback.grant();
        } catch (RuntimeException | LinkageError error) {
            diagnostics.permissionCallbackException("GRANT", error);
        }
    }

    private void safePermissionReject(PermissionDelegate.Callback callback) {
        try {
            callback.reject();
        } catch (RuntimeException | LinkageError error) {
            diagnostics.permissionCallbackException("REJECT", error);
        }
    }

    private void safeMediaGrant(
            PermissionDelegate.MediaCallback callback, MediaSource video, MediaSource audio) {
        try {
            callback.grant(video, audio);
            diagnostics.mediaGrantReturned();
        } catch (RuntimeException | LinkageError error) {
            diagnostics.mediaCallbackException("GRANT", error);
        }
    }

    private void safeMediaReject(PermissionDelegate.MediaCallback callback) {
        try {
            callback.reject();
        } catch (RuntimeException | LinkageError error) {
            diagnostics.mediaCallbackException("REJECT", error);
        }
    }

    private void logAndroidResults(String[] permissions, boolean granted) {
        if (permissions == null) {
            return;
        }
        for (String permission : permissions) {
            diagnostics.androidResult(permission, granted);
        }
    }
}
