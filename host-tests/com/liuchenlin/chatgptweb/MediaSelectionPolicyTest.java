package com.liuchenlin.chatgptweb;

import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource;

public final class MediaSelectionPolicyTest {
    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message);
    }
    private static void assertNull(Object actual, String message) {
        if (actual != null) throw new AssertionError(message + ": actual=" + actual);
    }

    public static void main(String[] args) {
        MediaSource playback = new MediaSource("playback", "Playback", MediaSource.SOURCE_AUDIOCAPTURE, MediaSource.TYPE_AUDIO);
        MediaSource otherAudio = new MediaSource("other", "Other", MediaSource.SOURCE_OTHER, MediaSource.TYPE_AUDIO);
        MediaSource mic1 = new MediaSource("mic-1", "Mic 1", MediaSource.SOURCE_MICROPHONE, MediaSource.TYPE_AUDIO);
        MediaSource mic2 = new MediaSource("mic-2", "Mic 2", MediaSource.SOURCE_MICROPHONE, MediaSource.TYPE_AUDIO);
        MediaSource wrongType = new MediaSource("wrong", "Wrong", MediaSource.SOURCE_MICROPHONE, MediaSource.TYPE_VIDEO);

        assertSame(mic1, MediaSelectionPolicy.firstPhysicalMicrophone(new MediaSource[]{playback, otherAudio, mic1, mic2}),
                "select first original physical microphone object");
        assertNull(MediaSelectionPolicy.firstPhysicalMicrophone(new MediaSource[]{playback, otherAudio, wrongType}),
                "never substitute playback/other/wrong-type source");
        assertNull(MediaSelectionPolicy.firstPhysicalMicrophone(null), "null audio list");
        assertNull(MediaSelectionPolicy.firstPhysicalMicrophone(new MediaSource[]{}), "empty audio list");

        MediaSource screen = new MediaSource("screen", "Screen", MediaSource.SOURCE_SCREEN, MediaSource.TYPE_VIDEO);
        MediaSource camera = new MediaSource("camera", "Camera", MediaSource.SOURCE_CAMERA, MediaSource.TYPE_VIDEO);
        assertSame(camera, MediaSelectionPolicy.firstCamera(new MediaSource[]{screen, camera}), "select first physical camera");
        assertNull(MediaSelectionPolicy.firstCamera(new MediaSource[]{screen}), "screen is not camera");
        System.out.println("MediaSelectionPolicyTest OK");
    }
}
