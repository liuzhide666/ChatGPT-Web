package org.mozilla.geckoview;

import java.util.ArrayList;
import java.util.List;

public class GeckoSession {
    public static final int LOAD_FLAGS_NONE = 0;
    public static final int LOAD_FLAGS_BYPASS_CACHE = 1;

    public static class Loader {
        public String uri;
        public int flags = LOAD_FLAGS_NONE;
        public Loader uri(String uri) { this.uri = uri; return this; }
        public Loader flags(int flags) { this.flags = flags; return this; }
    }

    private final WebExtension.SessionController webExtensionController = new WebExtension.SessionController();
    public final List<String> events = new ArrayList<>();
    public PermissionDelegate permissionDelegate;
    public MediaDelegate mediaDelegate;
    public NavigationDelegate navigationDelegate;
    public ProgressDelegate progressDelegate;
    public PromptDelegate promptDelegate;
    public ContentDelegate contentDelegate;
    public int reloadCalls;
    public int reloadFlags = LOAD_FLAGS_NONE;
    public int flushCalls;
    public int restoreCalls;
    public int loadCalls;
    public int activeTrueCalls;
    public int activeFalseCalls;
    public int closeCalls;
    public SessionState restoredState;
    public String loadedUri;
    public int loadedFlags = LOAD_FLAGS_NONE;
    public Runnable onOpenHook;

    public static class SessionState {
        private final String value;
        public SessionState(String value) { this.value = value; }
        public static SessionState fromString(String value) {
            return value != null && value.startsWith("valid:") ? new SessionState(value) : null;
        }
        @Override public String toString() { return value; }
    }

    public interface PermissionDelegate {
        class MediaSource {
            public static final int SOURCE_CAMERA = 0;
            public static final int SOURCE_SCREEN = 1;
            public static final int SOURCE_MICROPHONE = 2;
            public static final int SOURCE_AUDIOCAPTURE = 3;
            public static final int SOURCE_OTHER = 4;
            public static final int TYPE_VIDEO = 0;
            public static final int TYPE_AUDIO = 1;

            public final String id;
            public final String name;
            public final int source;
            public final int type;

            public MediaSource(String id, String name, int source, int type) {
                this.id = id; this.name = name; this.source = source; this.type = type;
            }
        }
        interface Callback { void grant(); void reject(); }
        interface MediaCallback { void grant(MediaSource video, MediaSource audio); void reject(); }
        default void onAndroidPermissionsRequest(GeckoSession session, String[] permissions, Callback callback) {}
        default void onMediaPermissionRequest(GeckoSession session, String uri,
                MediaSource[] video, MediaSource[] audio, MediaCallback callback) {}
    }

    public interface PromptDelegate {
        class PromptResponse {
            public final String kind;
            public final String[] uris;
            public PromptResponse(String kind, String[] uris) { this.kind = kind; this.uris = uris; }
        }
        class FilePrompt {
            public static final class Type {
                public static final int SINGLE = 1;
                public static final int MULTIPLE = 2;
                public static final int FOLDER = 3;
            }
            public final int type;
            public final String[] mimeTypes;
            public int dismissCalls;
            public int confirmSingleCalls;
            public int confirmMultipleCalls;
            public FilePrompt(int type, String[] mimeTypes) { this.type = type; this.mimeTypes = mimeTypes; }
            public PromptResponse dismiss() { dismissCalls++; return new PromptResponse("dismiss", null); }
            public PromptResponse confirm(android.content.Context context, android.net.Uri uri) {
                confirmSingleCalls++; return new PromptResponse("single", new String[]{uri.toString()});
            }
            public PromptResponse confirm(android.content.Context context, android.net.Uri[] uris) {
                confirmMultipleCalls++;
                String[] values = new String[uris.length];
                for (int i=0;i<uris.length;i++) values[i] = uris[i].toString();
                return new PromptResponse("multiple", values);
            }
        }
        default GeckoResult<PromptResponse> onFilePrompt(GeckoSession session, FilePrompt prompt) { return null; }
    }

    public interface NavigationDelegate {
        class LoadRequest {
            public final String uri;
            public LoadRequest(String uri) { this.uri = uri; }
        }
        default GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, LoadRequest request) { return null; }
    }

    public interface MediaDelegate {
        class RecordingDevice {
            public static final class Status { public static final long RECORDING = 0L; public static final long INACTIVE = 1L; }
            public static final class Type { public static final long CAMERA = 0L; public static final long MICROPHONE = 1L; }
            public final long status;
            public final long type;
            public RecordingDevice(long type, long status) { this.type = type; this.status = status; }
        }
        default void onRecordingStatusChanged(GeckoSession session, RecordingDevice[] devices) {}
    }

    public interface ProgressDelegate {
        default void onSessionStateChange(GeckoSession session, SessionState sessionState) {}
        default void onPageStart(GeckoSession session, String url) {}
        default void onProgressChange(GeckoSession session, int progress) {}
        default void onPageStop(GeckoSession session, boolean success) {}
    }

    public interface ContentDelegate {
        default void onFirstContentfulPaint(GeckoSession session) {}
        default void onPaintStatusReset(GeckoSession session) {}
        default GeckoResult<SlowScriptResponse> onSlowScript(GeckoSession session, String scriptFileName) { return null; }
        default void onExternalResponse(GeckoSession session, WebResponse response) {}
        default void onCrash(GeckoSession session) {}
        default void onKill(GeckoSession session) {}
    }

    public void setPermissionDelegate(PermissionDelegate delegate) { permissionDelegate = delegate; events.add("setPermissionDelegate"); }
    public void setMediaDelegate(MediaDelegate delegate) { mediaDelegate = delegate; events.add("setMediaDelegate"); }
    public void setNavigationDelegate(NavigationDelegate delegate) { navigationDelegate = delegate; events.add("setNavigationDelegate"); }
    public void setProgressDelegate(ProgressDelegate delegate) { progressDelegate = delegate; events.add("setProgressDelegate"); }
    public void setPromptDelegate(PromptDelegate delegate) { promptDelegate = delegate; events.add("setPromptDelegate"); }
    public void setContentDelegate(ContentDelegate delegate) { contentDelegate = delegate; events.add("setContentDelegate"); }
    public void open(GeckoRuntime runtime) { events.add("open"); if (onOpenHook != null) onOpenHook.run(); }
    public void setActive(boolean active) { if (active) activeTrueCalls++; else activeFalseCalls++; events.add("active=" + active); }
    public void flushSessionState() { flushCalls++; events.add("flush"); }
    public void reload() { reloadCalls++; reloadFlags = LOAD_FLAGS_NONE; events.add("reload"); }
    public void reload(int flags) { reloadCalls++; reloadFlags = flags; events.add("reloadFlags=" + flags); }
    public void restoreState(SessionState state) { restoreCalls++; restoredState = state; events.add("restore"); }
    public void loadUri(String uri) { loadCalls++; loadedUri = uri; loadedFlags = LOAD_FLAGS_NONE; events.add("loadUri"); }
    public void load(Loader loader) {
        loadCalls++;
        loadedUri = loader == null ? null : loader.uri;
        loadedFlags = loader == null ? LOAD_FLAGS_NONE : loader.flags;
        events.add("loadFlags=" + loadedFlags);
    }
    public void close() { closeCalls++; events.add("close"); }
    public WebExtension.SessionController getWebExtensionController() { return webExtensionController; }
}
