package com.liuchenlin.chatgptweb;

import android.content.Context;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse;

public final class FilePromptCoordinatorTest {
    private static final class Host implements FilePromptCoordinator.PickerHost {
        final List<FilePromptCoordinator.PickerRequest> requests = new ArrayList<>();
        boolean launchable = true;
        @Override public boolean launch(FilePromptCoordinator.PickerRequest request) {
            requests.add(request);
            return launchable;
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Host host = new Host();
        ExternalFlowTracker flows = new ExternalFlowTracker();
        Context context = new Context();
        FilePromptCoordinator coordinator = new FilePromptCoordinator(context, host, flows);
        GeckoSession session = new GeckoSession();

        FilePrompt single = new FilePrompt(FilePrompt.Type.SINGLE, new String[]{"image/*", "application/pdf"});
        GeckoResult<PromptResponse> singleResult = coordinator.onFilePrompt(session, single);
        check(!singleResult.isComplete(), "single prompt remains pending until picker result");
        check(flows.isActive(ExternalFlowTracker.Flow.FILE_PICKER), "file picker external flow active");
        check(host.requests.size() == 1, "picker launched once");
        FilePromptCoordinator.PickerRequest singleRequest = host.requests.get(0);
        check("android.intent.action.OPEN_DOCUMENT".equals(singleRequest.action), "uses ACTION_OPEN_DOCUMENT");
        check(!singleRequest.allowMultiple, "single prompt does not allow multiple");
        check(singleRequest.mimeTypes.length == 2, "MIME filters preserved");
        check(coordinator.onPickerResult(new Uri[]{Uri.parse("content://picker/image/1")}), "picker result consumed");
        check(singleResult.isComplete() && "single".equals(singleResult.poll().kind), "single content URI confirms prompt");
        check(single.confirmSingleCalls == 1, "single confirm overload used");
        check(!flows.isAnyActive(), "flow cleared after success");

        FilePrompt multiple = new FilePrompt(FilePrompt.Type.MULTIPLE, null);
        GeckoResult<PromptResponse> multipleResult = coordinator.onFilePrompt(session, multiple);
        check(host.requests.get(1).allowMultiple, "multiple prompt enables multiple selection");
        check(host.requests.get(1).mimeTypes.length == 1 && "*/*".equals(host.requests.get(1).mimeTypes[0]),
                "missing accept list falls back to */*");
        check(coordinator.onPickerResult(new Uri[]{Uri.parse("content://picker/a"), Uri.parse("content://picker/b")}),
                "multiple picker result consumed");
        check(multipleResult.isComplete() && multiple.confirmMultipleCalls == 1,
                "multiple content URIs use array confirm overload");

        FilePrompt cancel = new FilePrompt(FilePrompt.Type.SINGLE, null);
        GeckoResult<PromptResponse> cancelResult = coordinator.onFilePrompt(session, cancel);
        check(coordinator.onPickerResult(null), "cancellation consumed");
        check(cancelResult.isComplete() && "dismiss".equals(cancelResult.poll().kind), "cancel dismisses prompt");
        check(!flows.isAnyActive(), "flow cleared after cancellation");

        FilePrompt invalid = new FilePrompt(FilePrompt.Type.SINGLE, null);
        GeckoResult<PromptResponse> invalidResult = coordinator.onFilePrompt(session, invalid);
        check(coordinator.onPickerResult(new Uri[]{Uri.parse("file:///sdcard/not-saf.txt")}), "invalid result consumed");
        check(invalidResult.isComplete() && "dismiss".equals(invalidResult.poll().kind),
                "non-content result is rejected/dismissed");

        FilePrompt first = new FilePrompt(FilePrompt.Type.SINGLE, null);
        GeckoResult<PromptResponse> firstResult = coordinator.onFilePrompt(session, first);
        FilePrompt second = new FilePrompt(FilePrompt.Type.SINGLE, null);
        GeckoResult<PromptResponse> secondResult = coordinator.onFilePrompt(session, second);
        check(firstResult.isComplete() && "dismiss".equals(firstResult.poll().kind),
                "superseded prompt is explicitly dismissed");
        check(!secondResult.isComplete() && flows.isActive(ExternalFlowTracker.Flow.FILE_PICKER),
                "replacement prompt owns one active flow");
        coordinator.cancelPending();
        check(secondResult.isComplete() && !flows.isAnyActive(), "explicit cancellation completes and clears flow");

        FilePrompt deadSessionPrompt = new FilePrompt(FilePrompt.Type.SINGLE, null);
        GeckoResult<PromptResponse> deadSessionResult = coordinator.onFilePrompt(session, deadSessionPrompt);
        coordinator.abandonPending();
        check(!deadSessionResult.isComplete(), "dead-session abandonment never completes stale GeckoResult");
        check(!flows.isAnyActive(), "dead-session abandonment clears picker flow");
        check(!coordinator.onPickerResult(new Uri[]{Uri.parse("content://late/result")}),
                "late picker result cannot reach abandoned dead session");

        FilePrompt folder = new FilePrompt(FilePrompt.Type.FOLDER, null);
        GeckoResult<PromptResponse> folderResult = coordinator.onFilePrompt(session, folder);
        check(folderResult.isComplete() && "dismiss".equals(folderResult.poll().kind),
                "folder upload not broadened into filesystem access");
        check(!flows.isAnyActive(), "folder rejection does not start external flow");

        host.launchable = false;
        FilePrompt noResolver = new FilePrompt(FilePrompt.Type.SINGLE, null);
        GeckoResult<PromptResponse> noResolverResult = coordinator.onFilePrompt(session, noResolver);
        check(noResolverResult.isComplete() && "dismiss".equals(noResolverResult.poll().kind),
                "picker launch failure dismisses synchronously");
        check(!flows.isAnyActive(), "failed launch cleans flow");

        System.out.println("FilePromptCoordinatorTest OK");
    }
}
