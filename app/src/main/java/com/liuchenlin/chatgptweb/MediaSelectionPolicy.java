package com.liuchenlin.chatgptweb;

import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource;

public final class MediaSelectionPolicy {
    private MediaSelectionPolicy() {}

    public static MediaSource firstPhysicalMicrophone(MediaSource[] audio) {
        if (audio == null) {
            return null;
        }
        for (MediaSource source : audio) {
            if (source != null
                    && source.type == MediaSource.TYPE_AUDIO
                    && source.source == MediaSource.SOURCE_MICROPHONE) {
                return source;
            }
        }
        return null;
    }

    public static MediaSource firstCamera(MediaSource[] video) {
        if (video == null) {
            return null;
        }
        for (MediaSource source : video) {
            if (source != null
                    && source.type == MediaSource.TYPE_VIDEO
                    && source.source == MediaSource.SOURCE_CAMERA) {
                return source;
            }
        }
        return null;
    }
}
