package com.liuchenlin.chatgptweb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/** Thin Android shell. Browser, permission, media and lifecycle policy live in dedicated classes. */
public final class MainActivity extends Activity implements DownloadCoordinator.Host {
    private static final String PREFS_NAME = "gecko-session-state";
    private static final int FILE_PICKER_REQUEST_CODE = 41041;
    private static final int DOWNLOAD_SAVE_REQUEST_CODE = 41042;

    private ChatGPTGeckoView geckoView;
    private View recoveryView;
    private View startupRecoveryView;
    private TextView recoveryMediaTraceText;
    private MediaCrashTraceStore mediaCrashTraceStore;
    private MediaDiagnostics mediaDiagnostics;
    private TextView startupRecoveryTitle;
    private TextView startupRecoveryMessage;
    private TextView startupStageText;
    private TextView startupFailureTypeText;
    private TextView startupFailureMessageText;
    private TextView startupFailureCauseText;
    private Button startupRetryButton;
    private FrameLayout geckoContainer;
    private StartupStateStore startupState;
    private GeckoSessionController sessionController;
    private ChatGPTContentBridge contentBridge;
    private ExternalFlowTracker externalFlows;
    private final BrowserHealthSupervisor browserHealthSupervisor = new BrowserHealthSupervisor();
    private Handler healthHandler;
    private NetworkStateMonitor networkStateMonitor;
    private boolean pageForeground;
    private boolean preserveHealthIncidentForBind;
    private boolean paintValid;
    private long lastPongMs;
    private long lastFcpMs;
    private long currentNavigationGeneration;
    private String bridgeExtensionVersion = "";
    private int bridgeProtocolVersion;
    private boolean composerFocused;
    private boolean composerDirty;
    private boolean composerComposing;
    private boolean composerAttachmentActive;
    private AndroidPermissionCoordinator androidPermissions;
    private FilePromptCoordinator filePromptCoordinator;
    private DownloadCoordinator downloadCoordinator;
    private LazyDownloadHandler downloadHandler;
    private MediaStoreDownloadsCompat mediaStoreDownloads;
    private boolean mediaStoreDownloadsProbed;
    private ExecutorService downloadExecutor;
    private boolean startupFailedThisProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        healthHandler = new Handler(Looper.getMainLooper());
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        networkStateMonitor = new NetworkStateMonitor(
                connectivityManager, healthHandler, this::onNetworkAvailabilityChanged);
        networkStateMonitor.start();
        applySystemBars();
        setContentView(R.layout.activity_main);

        geckoContainer = findViewById(R.id.gecko_container);
        recoveryView = getLayoutInflater().inflate(R.layout.view_recovery, geckoContainer, false);
        installRecoveryMediaTraceView();
        geckoContainer.addView(recoveryView);
        startupRecoveryView = buildStartupRecoveryView();
        geckoContainer.addView(startupRecoveryView);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferencesBackend backend = new SharedPreferencesBackend(preferences);
        startupState = new StartupStateStore(backend);
        mediaCrashTraceStore = new MediaCrashTraceStore(new MediaTracePreferencesBackend(preferences));
        startupRetryButton.setOnClickListener(v -> retryStartup(backend));

        if (mediaCrashTraceStore.hasInterruptedAttempt()) {
            showInterruptedMediaRecovery(backend);
            return;
        }
        if (startupState.hasInterruptedStartup()) {
            showStartupRecovery(startupState.lastStage(), startupState.lastFailure(), false);
            return;
        }
        initializeGecko();
    }

    private void initializeGecko() {
        mediaDiagnostics = new MediaDiagnostics(Log::d, mediaCrashTraceStore);
        mediaDiagnostics.setRecordingStateListener(active -> onRecoveryBlockerChanged());
        MediaDiagnostics diagnostics = mediaDiagnostics;
        try {
            startupState.begin(StartupStateStore.Stage.UI_READY);

            externalFlows = new ExternalFlowTracker();
            externalFlows.setChangeListener(this::onRecoveryBlockerChanged);
            downloadHandler = new LazyDownloadHandler(() -> createDownloadCoordinator(externalFlows));
            androidPermissions = new AndroidPermissionCoordinator(
                    new AndroidPermissionCoordinator.Host() {
                        @Override
                        public boolean isGranted(String permission) {
                            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
                        }

                        @Override
                        public void requestPermissions(String[] permissions, int requestCode) {
                            MainActivity.this.requestPermissions(permissions, requestCode);
                        }
                    },
                    externalFlows);

            GeckoMediaPermissionDelegate permissionDelegate =
                    new GeckoMediaPermissionDelegate(androidPermissions, diagnostics);
            GeckoNavigationDelegate navigationDelegate =
                    new GeckoNavigationDelegate(this::openExternalIfResolvable, diagnostics);
            filePromptCoordinator = new FilePromptCoordinator(this, this::launchFilePicker, externalFlows);

            startupState.advance(StartupStateStore.Stage.GECKO_RUNTIME);
            GeckoRuntime runtime = GeckoRuntimeHolder.get(this);

            contentBridge = new ChatGPTContentBridge(
                    () -> sessionController == null ? null : sessionController.currentSession(),
                    this::onBridgeEvent,
                    diagnostics::sessionEvent);
            contentBridge.setConnectionListener(new ChatGPTContentBridge.ConnectionListener() {
                @Override public void onPortConnected() {
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
                @Override public void onPortDisconnected() {
                    browserHealthSupervisor.onBridgeDisconnected(SystemClock.elapsedRealtime());
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
            });
            contentBridge.install(runtime);

            startupState.advance(StartupStateStore.Stage.GECKO_VIEW);
            geckoView = new ChatGPTGeckoView(this);
            geckoContainer.addView(geckoView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            startupState.advance(StartupStateStore.Stage.GECKO_SESSION);
            sessionController = new GeckoSessionController(
                    runtime,
                    GeckoSession::new,
                    this::bindSession,
                    permissionDelegate,
                    diagnostics,
                    navigationDelegate,
                    filePromptCoordinator,
                    downloadHandler,
                    new CrashRecoveryPolicy(),
                    externalFlows,
                    diagnostics,
                    new GeckoSessionController.RecoveryUi() {
                        @Override public void showRecovery() {
                            showRecoveryMediaTrace(diagnostics.recentTraceSummary());
                            recoveryView.setVisibility(View.VISIBLE);
                        }
                        @Override public void hideRecovery() { recoveryView.setVisibility(View.GONE); }
                    },
                    () -> {
                        if (androidPermissions != null) {
                            androidPermissions.abandonPending();
                        }
                        if (filePromptCoordinator != null) {
                            filePromptCoordinator.abandonPending();
                        }
                        if (downloadHandler != null) {
                            downloadHandler.abandonPending();
                        }
                        externalFlows.clearAll();
                    },
                    SystemClock::elapsedRealtime);

            sessionController.setHealthObserver(new GeckoSessionController.HealthObserver() {
                @Override public void onPageStart(String url, long generation, long nowMs) {
                    currentNavigationGeneration = generation;
                    paintValid = false;
                    browserHealthSupervisor.onNavigationStarted(nowMs);
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
                @Override public void onProgress(int progress, long generation, long nowMs) {
                    if (generation != currentNavigationGeneration) return;
                    browserHealthSupervisor.onContentProgress(nowMs);
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
                @Override public void onPageStop(boolean success, long generation, long nowMs) {
                    if (generation != currentNavigationGeneration) return;
                    if (success) {
                        browserHealthSupervisor.onContentProgress(nowMs);
                    } else {
                        browserHealthSupervisor.onTopLevelLoadFailed(nowMs);
                    }
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
                @Override public void onFirstContentfulPaint(long generation, long nowMs) {
                    if (generation != currentNavigationGeneration) return;
                    paintValid = true;
                    lastFcpMs = nowMs;
                    browserHealthSupervisor.onContentHealthy(nowMs);
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
                @Override public void onPaintStatusReset(long generation, long nowMs) {
                    if (generation != currentNavigationGeneration) return;
                    paintValid = false;
                    browserHealthSupervisor.onPaintInvalid(nowMs);
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
                @Override public void onSlowScript(long generation, long nowMs) {
                    if (generation != currentNavigationGeneration) return;
                    browserHealthSupervisor.onSlowScript(nowMs);
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
                @Override public void onSessionStateChange(long generation, long nowMs) {
                    if (generation != currentNavigationGeneration) return;
                    browserHealthSupervisor.onContentProgress(nowMs);
                    evaluateBrowserHealth();
                    scheduleHealthDeadline();
                }
            });

            recoveryView.findViewById(R.id.recovery_retry)
                    .setOnClickListener(v -> sessionController.retryAfterRecoveryUi());
            sessionController.startFreshHome();
            startupState.complete();
        } catch (RuntimeException | LinkageError error) {
            StartupFailureInfo failure = StartupFailureInfo.from(error, startupState.lastStage());
            startupState.recordFailure(failure);
            startupFailedThisProcess = true;
            diagnostics.recoveryEvent(
                    "STARTUP_" + startupState.lastStage().name(),
                    "JAVA_OR_LINK_ERROR_" + error.getClass().getSimpleName());
            showStartupRecovery(startupState.lastStage(), failure, true);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applySystemBars();
        }
    }

    private void applySystemBars() {
        boolean darkMode = SystemBarPolicy.isDarkMode(getResources().getConfiguration().uiMode);
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        int barColor = SystemBarPolicy.barColorArgb(darkMode);
        window.setStatusBarColor(barColor);
        window.setNavigationBarColor(barColor);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (SystemBarPolicy.useLightSystemBarIcons(darkMode)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private void retryStartup(SharedPreferencesBackend backend) {
        if (startupFailedThisProcess
                || GeckoRuntimeHolder.state() == GeckoRuntimeHolder.State.FAILED) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().commit();
            finishAndRemoveTask();
            Process.killProcess(Process.myPid());
            return;
        }
        startupState.complete();
        startupRecoveryView.setVisibility(View.GONE);
        initializeGecko();
    }

    private void showInterruptedMediaRecovery(SharedPreferencesBackend backend) {
        startupRecoveryTitle.setText(zh("\u68c0\u6d4b\u5230\u5a92\u4f53\u64cd\u4f5c\u5f02\u5e38\u4e2d\u65ad"));
        startupRecoveryMessage.setText(zh("\u4e0a\u6b21\u8fdb\u7a0b\u5728\u9ea6\u514b\u98ce\u6216\u5a92\u4f53\u6743\u9650\u94fe\u4e2d\u672a\u6b63\u5e38\u5b8c\u6210\u3002\u4e3a\u907f\u514d\u6062\u590d\u5230\u53ef\u80fd\u635f\u574f\u7684\u9875\u9762\u72b6\u6001\uff0c\u672c\u6b21\u5c06\u4ece ChatGPT \u5b98\u7f51\u91cd\u65b0\u5f00\u59cb\u3002"));
        startupStageText.setText(zh("\u6700\u8fd1\u5a92\u4f53\u8bca\u65ad\uff1a\n") + mediaCrashTraceStore.summary());
        startupFailureTypeText.setVisibility(View.GONE);
        startupFailureMessageText.setVisibility(View.GONE);
        startupFailureCauseText.setVisibility(View.GONE);
        startupRetryButton.setText(zh("\u6e05\u7406\u5f02\u5e38\u72b6\u6001\u5e76\u8fd4\u56de\u5b98\u7f51"));
        startupRetryButton.setOnClickListener(v -> {
            mediaCrashTraceStore.acknowledgeInterruptedAttempt();
            startupState.complete();
            startupRecoveryView.setVisibility(View.GONE);
            startupRetryButton.setOnClickListener(v2 -> retryStartup(backend));
            initializeGecko();
        });
        startupRecoveryView.setVisibility(View.VISIBLE);
    }

    private void showStartupRecovery(
            StartupStateStore.Stage stage, StartupFailureInfo failure, boolean failedThisProcess) {
        startupRecoveryTitle.setText(zh("\u7f51\u9875\u5185\u6838\u542f\u52a8\u5931\u8d25"));
        if (geckoView != null && geckoView.getParent() == geckoContainer) {
            geckoContainer.removeView(geckoView);
            geckoView = null;
        }

        startupRecoveryMessage.setText(failedThisProcess
                ? zh("\u8fd9\u662f\u7b2c\u4e00\u6b21\u53ef\u6355\u83b7\u7684\u7f51\u9875\u5185\u6838\u542f\u52a8\u5931\u8d25\u3002\u8bf7\u4fdd\u7559\u4e0b\u65b9\u8bca\u65ad\u4fe1\u606f\u3002")
                : zh("\u68c0\u6d4b\u5230\u4e0a\u6b21\u542f\u52a8\u5f02\u5e38\u4e2d\u65ad\u3002\u8bf7\u67e5\u770b\u4e0a\u6b21\u4e2d\u65ad\u9636\u6bb5\u3002"));
        startupStageText.setText(zh("\u5931\u8d25\u9636\u6bb5\uff1a") + stageLabel(stage));

        if (failure == null) {
            startupFailureTypeText.setVisibility(View.GONE);
            startupFailureCauseText.setVisibility(View.GONE);
            startupFailureMessageText.setVisibility(View.VISIBLE);
            startupFailureMessageText.setText(zh("\u672a\u83b7\u53d6\u5230 Java \u5f02\u5e38\u7ec6\u8282\uff1b\u4e0a\u6b21\u53ef\u80fd\u5728 native \u9636\u6bb5\u4e2d\u65ad\u3002"));
        } else {
            startupFailureTypeText.setVisibility(View.VISIBLE);
            startupFailureMessageText.setVisibility(View.VISIBLE);
            startupFailureTypeText.setText(zh("\u9519\u8bef\u7c7b\u578b\uff1a") + failure.exceptionType);
            String detail = failure.exceptionMessage.isEmpty()
                    ? zh("\uff08\u5f02\u5e38\u672a\u63d0\u4f9b\u6587\u5b57\u4fe1\u606f\uff09")
                    : failure.exceptionMessage;
            startupFailureMessageText.setText(zh("\u9519\u8bef\u4fe1\u606f\uff1a") + detail);
            if (failure.causeType.isEmpty() && failure.causeMessage.isEmpty()) {
                startupFailureCauseText.setVisibility(View.GONE);
            } else {
                startupFailureCauseText.setVisibility(View.VISIBLE);
                String cause = failure.causeType;
                if (!failure.causeMessage.isEmpty()) {
                    cause = cause.isEmpty() ? failure.causeMessage : cause + ": " + failure.causeMessage;
                }
                startupFailureCauseText.setText(zh("\u6839\u672c\u539f\u56e0\uff1a") + cause);
            }
        }

        startupRetryButton.setText(failedThisProcess
                ? zh("\u5173\u95ed\u5e94\u7528")
                : zh("\u5f00\u59cb\u7f51\u9875\u5185\u6838"));
        startupRecoveryView.setVisibility(View.VISIBLE);
    }

    private String stageLabel(StartupStateStore.Stage stage) {
        if (stage == null) {
            return zh("\u672a\u77e5\u9636\u6bb5");
        }
        switch (stage) {
            case UI_READY: return zh("\u51c6\u5907\u5e94\u7528\u754c\u9762");
            case GECKO_RUNTIME: return zh("\u521d\u59cb\u5316\u7f51\u9875\u5185\u6838\uff08GeckoRuntime\uff09");
            case GECKO_VIEW: return zh("\u521b\u5efa\u7f51\u9875\u89c6\u56fe");
            case GECKO_SESSION: return zh("\u5efa\u7acb\u7f51\u9875\u4f1a\u8bdd");
            default: return zh("\u672a\u77e5\u9636\u6bb5");
        }
    }

    private View buildStartupRecoveryView() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        int padding = dp(24);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackgroundColor(0xFFF7F7F7);
        panel.setVisibility(View.GONE);

        startupRecoveryTitle = new TextView(this);
        startupRecoveryTitle.setText(zh("\u7f51\u9875\u5185\u6838\u542f\u52a8\u5931\u8d25"));
        startupRecoveryTitle.setTextSize(24f);
        startupRecoveryTitle.setGravity(Gravity.CENTER);
        panel.addView(startupRecoveryTitle, wrapMatch());

        startupRecoveryMessage = diagnosticText();
        startupStageText = diagnosticText();
        startupFailureTypeText = diagnosticText();
        startupFailureMessageText = diagnosticText();
        startupFailureCauseText = diagnosticText();
        startupRetryButton = new Button(this);
        startupRetryButton.setText(zh("\u5f00\u59cb\u7f51\u9875\u5185\u6838"));

        panel.addView(startupRecoveryMessage, wrapMatch());
        panel.addView(startupStageText, wrapMatch());
        panel.addView(startupFailureTypeText, wrapMatch());
        panel.addView(startupFailureMessageText, wrapMatch());
        panel.addView(startupFailureCauseText, wrapMatch());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(16);
        panel.addView(startupRetryButton, buttonParams);
        return panel;
    }

    private TextView diagnosticText() {
        TextView view = new TextView(this);
        view.setTextSize(16f);
        view.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = wrapMatch();
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout.LayoutParams wrapMatch() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String zh(String value) {
        return value;
    }



    private final Runnable healthDeadlineCheck = new Runnable() {
        @Override public void run() {
            evaluateBrowserHealth();
            scheduleHealthDeadline();
        }
    };

    private BrowserHealthSupervisor.Snapshot healthSnapshot(long nowMs) {
        boolean mediaActive = mediaDiagnostics != null && mediaDiagnostics.isRecordingActive();
        boolean externalActive = externalFlows != null && externalFlows.isAnyActive();
        return new BrowserHealthSupervisor.Snapshot(
                nowMs, pageForeground, sessionController != null && sessionController.isLoading(),
                sessionController != null && sessionController.isCurrentPageTrusted(),
                composerFocused, composerDirty, composerComposing, composerAttachmentActive,
                mediaActive, externalActive,
                networkStateMonitor == null || networkStateMonitor.mayHaveNetwork(),
                contentBridge != null && contentBridge.hasConnectedPort(),
                contentBridge != null && contentBridge.isProtocolCompatible(),
                paintValid);
    }

    private void scheduleHealthDeadline() {
        if (healthHandler == null) return;
        healthHandler.removeCallbacks(healthDeadlineCheck);
        if (!pageForeground || sessionController == null) return;
        long now = SystemClock.elapsedRealtime();
        long delay = browserHealthSupervisor.nextDelayMs(healthSnapshot(now));
        if (delay != Long.MAX_VALUE) healthHandler.postDelayed(healthDeadlineCheck, delay);
    }

    private void onNetworkAvailabilityChanged(boolean mayHaveNetwork) {
        // Connectivity changes update the snapshot only. A healthy page is never reloaded/rebound just
        // because the device moved offline or online. Existing incidents are re-evaluated conservatively.
        evaluateBrowserHealth();
        scheduleHealthDeadline();
    }

    private void onRecoveryBlockerChanged() {
        Handler handler = healthHandler;
        if (handler == null) return;
        handler.post(() -> {
            evaluateBrowserHealth();
            scheduleHealthDeadline();
        });
    }

    private void onBridgeEvent(ChatGPTContentBridge.Event event) {
        if (event == null) return;
        long nowMs = SystemClock.elapsedRealtime();
        if (event.type == ChatGPTContentBridge.EventType.READY) {
            bridgeExtensionVersion = event.extensionVersion;
            bridgeProtocolVersion = event.protocolVersion;
            if (!event.protocolCompatible) {
                browserHealthSupervisor.onBridgeVersionMismatch(nowMs);
                if (mediaDiagnostics != null) {
                    mediaDiagnostics.sessionEvent("CONTENT_BRIDGE incompatible extension="
                            + event.extensionVersion + " protocol=" + event.protocolVersion);
                }
                evaluateBrowserHealth();
                scheduleHealthDeadline();
                return;
            }
            browserHealthSupervisor.onBridgeHealthy(nowMs);
            if (contentBridge != null) contentBridge.requestComposerState();
        } else if (contentBridge == null || !contentBridge.isProtocolCompatible()) {
            return;
        } else {
            browserHealthSupervisor.onBridgeHealthy(nowMs);
        }
        if (event.type == ChatGPTContentBridge.EventType.PONG) {
            lastPongMs = nowMs;
        } else if (event.type == ChatGPTContentBridge.EventType.COMPOSER_STATE) {
            composerFocused = event.composerFocused;
            composerDirty = event.composerDirty;
            composerComposing = event.composing;
            composerAttachmentActive = event.attachmentActive;
        }
        if (sessionController != null) sessionController.recordTrustedUri(event.pageUrl);
        evaluateBrowserHealth();
        scheduleHealthDeadline();
    }

    private void evaluateBrowserHealth() {
        if (sessionController == null) return;
        BrowserHealthSupervisor.Decision decision = browserHealthSupervisor.evaluate(
                healthSnapshot(SystemClock.elapsedRealtime()));
        switch (decision) {
            case PROBE:
                if (contentBridge != null) contentBridge.requestPing();
                break;
            case REBIND_BRIDGE:
                if (contentBridge != null) {
                    contentBridge.rebindCurrentSession();
                    contentBridge.requestPing();
                }
                break;
            case SOFT_RELOAD:
                sessionController.softRecoverCurrentPage();
                break;
            case HARD_REPLACE:
                preserveHealthIncidentForBind = true;
                try { sessionController.hardRecoverTrustedPage(); }
                finally { preserveHealthIncidentForBind = false; }
                break;
            case SHOW_UI:
                sessionController.showPageHealthRecoveryUi();
                break;
            case NONE:
            default:
                break;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (sessionController != null && isLauncherIntent(intent)) {
            sessionController.reopenFreshHome();
        }
    }

    private static boolean isLauncherIntent(Intent intent) {
        return intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    }

    @Override
    protected void onStart() {
        super.onStart();
        pageForeground = true;
        if (sessionController != null) {
            sessionController.onStart();
        }
        scheduleHealthDeadline();
    }

    @Override
    protected void onStop() {
        pageForeground = false;
        if (healthHandler != null) {
            healthHandler.removeCallbacks(healthDeadlineCheck);
        }
        if (networkStateMonitor != null) {
            networkStateMonitor.stop();
        }
        if (sessionController != null) {
            sessionController.onStop();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        pageForeground = false;
        if (healthHandler != null) {
            healthHandler.removeCallbacks(healthDeadlineCheck);
        }
        if (networkStateMonitor != null) {
            networkStateMonitor.stop();
        }
        if (filePromptCoordinator != null) {
            filePromptCoordinator.cancelPending();
        }
        if (androidPermissions != null) {
            androidPermissions.cancelPending();
        }
        if (downloadHandler != null) {
            downloadHandler.abandonPending();
        }
        if (sessionController != null) {
            sessionController.destroy();
        }
        if (downloadExecutor != null) {
            downloadExecutor.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (androidPermissions == null || grantResults == null) {
            return;
        }
        boolean[] granted = new boolean[grantResults.length];
        for (int i = 0; i < grantResults.length; i++) {
            granted[i] = grantResults[i] == PackageManager.PERMISSION_GRANTED;
        }
        androidPermissions.onPermissionsResult(requestCode, granted);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_PICKER_REQUEST_CODE && filePromptCoordinator != null) {
            filePromptCoordinator.onPickerResult(
                    resultCode == RESULT_OK ? selectedUris(data) : null);
            return;
        }
        if (requestCode == DOWNLOAD_SAVE_REQUEST_CODE && downloadCoordinator != null) {
            downloadCoordinator.onLegacySaveResult(
                    resultCode == RESULT_OK && data != null ? data.getData() : null);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void bindSession(GeckoSession session) {
        // A replacement Session must prove its own bridge health before normal monitoring is armed.
        // A page-health hard replacement preserves its incident so a missing new bridge can reach manual UI.
        if (!preserveHealthIncidentForBind) browserHealthSupervisor.resetIncident();
        paintValid = false;
        lastPongMs = 0L;
        lastFcpMs = 0L;
        composerFocused = false;
        composerDirty = false;
        composerComposing = false;
        composerAttachmentActive = false;
        currentNavigationGeneration = sessionController == null ? 0L : sessionController.navigationGeneration();
        if (geckoView.getSession() != null) {
            geckoView.releaseSession();
        }
        geckoView.setSession(session);
        if (contentBridge != null) {
            contentBridge.attachSession(session);
        }
    }

    private boolean launchFilePicker(FilePromptCoordinator.PickerRequest request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(request.mimeTypes.length == 1 ? request.mimeTypes[0] : "*/*");
        if (request.mimeTypes.length > 1) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, request.mimeTypes);
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.allowMultiple);
        if (intent.resolveActivity(getPackageManager()) == null) {
            return false;
        }
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
        return true;
    }

    private DownloadCoordinator createDownloadCoordinator(ExternalFlowTracker externalFlows) {
        if (downloadExecutor == null || downloadExecutor.isShutdown()) {
            downloadExecutor = Executors.newSingleThreadExecutor();
        }
        downloadCoordinator = new DownloadCoordinator(this, externalFlows, downloadExecutor);
        return downloadCoordinator;
    }

    @Override
    public synchronized MediaStoreDownloadsCompat mediaStoreDownloads() {
        if (!mediaStoreDownloadsProbed) {
            mediaStoreDownloads = MediaStoreDownloadsCompat.tryCreate(
                    getContentResolver(), Build.VERSION.SDK_INT);
            mediaStoreDownloadsProbed = true;
        }
        return mediaStoreDownloads;
    }

    @Override
    public OutputStream openOutputStream(Uri uri) throws IOException {
        OutputStream output = getContentResolver().openOutputStream(uri, "w");
        if (output == null) {
            throw new IOException("ContentResolver returned null OutputStream");
        }
        return output;
    }

    @Override
    public boolean launchLegacySaveAs(DownloadMetadata metadata) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(metadata.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, metadata.fileName);
        if (intent.resolveActivity(getPackageManager()) == null) {
            return false;
        }
        startActivityForResult(intent, DOWNLOAD_SAVE_REQUEST_CODE);
        return true;
    }

    @Override
    public void notifyStarted(String fileName) {
        showDownloadToast(zh("\u6b63\u5728\u4e0b\u8f7d\uff1a") + fileName);
    }

    @Override
    public void notifySuccess(String fileName) {
        showDownloadToast(zh("\u5df2\u4fdd\u5b58\uff1a") + fileName);
    }

    @Override
    public void notifyFailure(String fileName) {
        showDownloadToast(zh("\u4e0b\u8f7d\u5931\u8d25\uff1a") + fileName);
    }

    private void showDownloadToast(String message) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    private boolean openExternalIfResolvable(String uri) {
        if (uri == null) {
            return false;
        }
        try {
            Intent intent = uri.startsWith("intent:")
                    ? Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            if (intent.resolveActivity(getPackageManager()) == null) {
                return false;
            }
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void installRecoveryMediaTraceView() {
        if (!(recoveryView instanceof LinearLayout)) {
            return;
        }
        recoveryMediaTraceText = new TextView(this);
        recoveryMediaTraceText.setTextSize(14f);
        recoveryMediaTraceText.setGravity(Gravity.CENTER);
        recoveryMediaTraceText.setPadding(0, dp(12), 0, 0);
        ((LinearLayout) recoveryView).addView(recoveryMediaTraceText, 1, wrapMatch());
        Button forceRefetchButton = new Button(this);
        forceRefetchButton.setText("强制重新获取页面资源");
        forceRefetchButton.setOnClickListener(v -> {
            if (sessionController != null) sessionController.forceRefetchCurrentPage();
        });
        ((LinearLayout) recoveryView).addView(forceRefetchButton, wrapMatch());
        Button browserDiagnosticsButton = new Button(this);
        browserDiagnosticsButton.setText("浏览器诊断");
        browserDiagnosticsButton.setOnClickListener(v -> showBrowserDiagnostics());
        ((LinearLayout) recoveryView).addView(browserDiagnosticsButton, wrapMatch());
    }

    private void showBrowserDiagnostics() {
        BrowserDiagnosticsDialog.Snapshot snapshot = browserDiagnosticsSnapshot();
        String text = new BrowserDiagnosticsDialog().render(snapshot);
        new AlertDialog.Builder(this)
                .setTitle("浏览器诊断")
                .setMessage(text)
                .setPositiveButton("关闭", null)
                .show();
    }

    private BrowserDiagnosticsDialog.Snapshot browserDiagnosticsSnapshot() {
        boolean mediaActive = mediaDiagnostics != null && mediaDiagnostics.isRecordingActive();
        boolean externalActive = externalFlows != null && externalFlows.isAnyActive();
        boolean networkAvailable = networkStateMonitor == null || networkStateMonitor.mayHaveNetwork();
        boolean trusted = sessionController != null && sessionController.isCurrentPageTrusted();
        return new BrowserDiagnosticsDialog.Snapshot(
                "1.1.0 / vc113", bridgeExtensionVersion, bridgeProtocolVersion,
                trusted ? "CHATGPT_HTTPS" : "UNTRUSTED_OR_NONE", currentNavigationGeneration,
                sessionController != null && sessionController.currentSession() != null,
                sessionController != null && sessionController.isLoading(), paintValid, lastFcpMs,
                contentBridge != null && contentBridge.hasConnectedPort(), lastPongMs, networkAvailable,
                browserHealthSupervisor.state().name(), browserHealthSupervisor.lastReason().name(),
                "DEFAULT (automatic); BYPASS_ONCE=user-only", composerFocused, composerDirty,
                composerComposing, composerAttachmentActive, mediaActive, externalActive);
    }

    private void showRecoveryMediaTrace(String trace) {
        if (recoveryMediaTraceText == null) {
            return;
        }
        String body = trace == null || trace.isEmpty() ? zh("\u6682\u65e0\u5a92\u4f53\u8bca\u65ad\u8bb0\u5f55") : trace;
        recoveryMediaTraceText.setText(zh("\u6700\u8fd1\u5a92\u4f53\u8bca\u65ad\uff1a\n") + body);
    }

    private static Uri[] selectedUris(Intent data) {
        if (data == null) {
            return null;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris.isEmpty() ? null : uris.toArray(new Uri[0]);
    }

    private static final class MediaTracePreferencesBackend implements MediaCrashTraceStore.Backend {
        private final SharedPreferences preferences;

        MediaTracePreferencesBackend(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override public String getString(String key) {
            return preferences.getString(key, null);
        }

        @Override public void putString(String key, String value) {
            preferences.edit().putString(key, value).commit();
        }

        @Override public void remove(String key) {
            preferences.edit().remove(key).commit();
        }
    }

    private static final class SharedPreferencesBackend implements StartupStateStore.Backend {
        private final SharedPreferences preferences;

        SharedPreferencesBackend(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override public String getString(String key) {
            return preferences.getString(key, null);
        }

        @Override public void putString(String key, String value) {
            preferences.edit().putString(key, value).apply();
        }

        @Override public void remove(String key) {
            preferences.edit().remove(key).apply();
        }


        @Override public boolean getBoolean(String key, boolean fallback) {
            return preferences.getBoolean(key, fallback);
        }

        @Override public void putBoolean(String key, boolean value) {
            preferences.edit().putBoolean(key, value).apply();
        }
    }
}
