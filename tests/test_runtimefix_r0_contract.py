from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
JAVA = MAIN / "java/com/liuchenlin/chatgptweb"


def read(path: Path) -> str:
    assert path.exists(), f"缺少文件: {path.relative_to(ROOT)}"
    return path.read_text(encoding="utf-8")


def test_runtime_holder_has_explicit_failed_state_and_never_recreates_in_failed_process():
    src = read(JAVA / "GeckoRuntimeHolder.java")
    for token in [
        "enum State", "NOT_CREATED", "CREATING", "READY", "FAILED",
        "State.FAILED", "GeckoRuntime.create", "state()",
    ]:
        assert token in src
    assert re.search(r"if\s*\(state\s*==\s*State\.FAILED\)", src)


def test_first_runtime_failure_is_persisted_with_sanitized_details():
    info = read(JAVA / "StartupFailureInfo.java")
    store = read(JAVA / "StartupStateStore.java")
    for token in [
        "exceptionType", "exceptionMessage", "causeType", "causeMessage",
        "sanitize", "from(Throwable", "MAX_MESSAGE_LENGTH",
    ]:
        assert token in info
    for token in [
        "KEY_FAILURE_TYPE", "KEY_FAILURE_MESSAGE", "KEY_CAUSE_TYPE", "KEY_CAUSE_MESSAGE",
        "recordFailure", "lastFailure",
    ]:
        assert token in store
    for forbidden in ["printStackTrace", "getStackTrace()"]:
        assert forbidden not in info


def test_startup_failure_ui_is_chinese_and_shows_type_message_and_cause():
    strings = read(MAIN / "res/values/strings.xml")
    layout = read(MAIN / "res/layout/view_startup_recovery.xml")
    activity = read(JAVA / "MainActivity.java")
    for expected in [
        "网页内核启动失败", "失败阶段", "错误类型", "错误信息", "根本原因",
        "关闭应用", "开始网页内核",
    ]:
        assert expected in strings
    for view_id in [
        "startup_stage", "startup_failure_type", "startup_failure_message", "startup_failure_cause",
    ]:
        assert f'android:id="@+id/{view_id}"' in layout
    assert "StartupFailureInfo" in activity
    assert "buildStartupRecoveryView" in activity
    assert r"\u7f51\u9875\u5185\u6838\u542f\u52a8\u5931\u8d25" in activity
    assert "R.layout.view_startup_recovery" not in activity


def test_failed_runtime_retry_never_calls_initialize_gecko_in_same_process():
    activity = read(JAVA / "MainActivity.java")
    assert "GeckoRuntimeHolder.state() == GeckoRuntimeHolder.State.FAILED" in activity
    assert "finishAndRemoveTask()" in activity
    assert "Process.killProcess(Process.myPid())" in activity
    assert "retryStartup" in activity


def test_launcher_labels_are_final_product_values():
    gradle = read(ROOT / "app/build.gradle")
    assert gradle.count("manifestPlaceholders = [appLabel: 'ChatGPT Web']") == 2
    assert "Gecko Beta 1" not in gradle


def test_visible_chinese_stage_names_live_in_resources_not_java_source():
    strings = read(MAIN / "res/values/strings.xml")
    activity = read(JAVA / "MainActivity.java")
    for expected in ["准备应用界面", "创建网页视图", "初始化网页内核", "建立网页会话", "未知阶段"]:
        assert expected in strings
        assert expected not in activity
