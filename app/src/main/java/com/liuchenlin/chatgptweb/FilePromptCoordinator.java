package com.liuchenlin.chatgptweb;

import android.content.Context;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse;

/** Bridges Gecko file prompts to a narrow ACTION_OPEN_DOCUMENT host. */
public final class FilePromptCoordinator implements GeckoSession.PromptDelegate {
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";

    public interface PickerHost {
        /** Launches the system picker and returns false when no safe handler is available. */
        boolean launch(PickerRequest request);
    }

    public static final class PickerRequest {
        public final String action;
        public final boolean allowMultiple;
        public final String[] mimeTypes;

        PickerRequest(boolean allowMultiple, String[] mimeTypes) {
            this.action = ACTION_OPEN_DOCUMENT;
            this.allowMultiple = allowMultiple;
            this.mimeTypes = mimeTypes.clone();
        }
    }

    private static final class Pending {
        final FilePrompt prompt;
        final GeckoResult<PromptResponse> result;

        Pending(FilePrompt prompt, GeckoResult<PromptResponse> result) {
            this.prompt = prompt;
            this.result = result;
        }
    }

    private final Context applicationContext;
    private final PickerHost host;
    private final ExternalFlowTracker flows;
    private Pending pending;

    public FilePromptCoordinator(Context context, PickerHost host, ExternalFlowTracker flows) {
        Context app = Objects.requireNonNull(context, "context").getApplicationContext();
        this.applicationContext = app == null ? context : app;
        this.host = Objects.requireNonNull(host, "host");
        this.flows = Objects.requireNonNull(flows, "flows");
    }

    @Override
    public synchronized GeckoResult<PromptResponse> onFilePrompt(
            GeckoSession session,
            FilePrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        dismissPendingLocked();

        if (prompt.type != FilePrompt.Type.SINGLE && prompt.type != FilePrompt.Type.MULTIPLE) {
            return GeckoResult.fromValue(prompt.dismiss());
        }

        GeckoResult<PromptResponse> result = new GeckoResult<>();
        pending = new Pending(prompt, result);
        flows.begin(ExternalFlowTracker.Flow.FILE_PICKER);

        String[] mimeTypes = normalizedMimeTypes(prompt.mimeTypes);
        boolean launched = host.launch(new PickerRequest(prompt.type == FilePrompt.Type.MULTIPLE, mimeTypes));
        if (!launched) {
            Pending failed = pending;
            pending = null;
            flows.end(ExternalFlowTracker.Flow.FILE_PICKER);
            failed.result.complete(failed.prompt.dismiss());
        }
        return result;
    }

    /** Completes the active Gecko file prompt from an Android picker result. */
    public synchronized boolean onPickerResult(Uri[] uris) {
        if (pending == null) {
            return false;
        }
        Pending current = pending;
        pending = null;
        flows.end(ExternalFlowTracker.Flow.FILE_PICKER);

        if (!allContentUris(uris)) {
            current.result.complete(current.prompt.dismiss());
            return true;
        }

        if (current.prompt.type == FilePrompt.Type.MULTIPLE) {
            current.result.complete(current.prompt.confirm(applicationContext, uris));
        } else {
            current.result.complete(current.prompt.confirm(applicationContext, uris[0]));
        }
        return true;
    }

    public synchronized void cancelPending() {
        dismissPendingLocked();
    }

    /** Drops a picker result owned by a dead GeckoSession without completing its stale GeckoResult. */
    public synchronized void abandonPending() {
        if (pending == null) {
            return;
        }
        pending = null;
        flows.end(ExternalFlowTracker.Flow.FILE_PICKER);
    }

    private void dismissPendingLocked() {
        if (pending == null) {
            return;
        }
        Pending current = pending;
        pending = null;
        flows.end(ExternalFlowTracker.Flow.FILE_PICKER);
        current.result.complete(current.prompt.dismiss());
    }

    private static String[] normalizedMimeTypes(String[] requested) {
        if (requested == null || requested.length == 0) {
            return new String[]{"*/*"};
        }
        List<String> normalized = new ArrayList<>();
        for (String value : requested) {
            if (value != null && !value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? new String[]{"*/*"} : normalized.toArray(new String[0]);
    }

    private static boolean allContentUris(Uri[] uris) {
        if (uris == null || uris.length == 0) {
            return false;
        }
        for (Uri uri : uris) {
            if (uri == null || uri.getScheme() == null
                    || !"content".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}
