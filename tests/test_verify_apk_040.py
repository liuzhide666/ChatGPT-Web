from tools.verify_apk_040 import parse_aapt_identity, parse_aapt_permissions, verify_beta1_identity, verify_permission_set


BASE = {
    "android.permission.INTERNET",
    "android.permission.RECORD_AUDIO",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.CAMERA",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.WAKE_LOCK",
    "android.permission.HIGH_SAMPLING_RATE_SENSORS",
}
APP_ID = "com.liuchenlin.chatgptweb.geckoprobe"
DYNAMIC = APP_ID + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"


def test_permission_gate_accepts_gecko_and_androidx_required_permissions():
    verify_permission_set(BASE | {DYNAMIC}, APP_ID)


def test_permission_gate_rejects_unrelated_privileged_or_broad_permissions():
    actual = BASE | {DYNAMIC, "android.permission.FOREGROUND_SERVICE", "android.permission.QUERY_ALL_PACKAGES"}
    try:
        verify_permission_set(actual, APP_ID)
    except ValueError as exc:
        message = str(exc)
        assert "FOREGROUND_SERVICE" in message
        assert "QUERY_ALL_PACKAGES" in message
        assert "WAKE_LOCK" not in message
        assert "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" not in message
    else:
        raise AssertionError("broad permissions must fail the APK gate")


def test_permission_gate_rejects_wrong_dynamic_receiver_permission_namespace():
    wrong = BASE | {"evil.example.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    try:
        verify_permission_set(wrong, APP_ID)
    except ValueError as exc:
        assert "evil.example" in str(exc)
    else:
        raise AssertionError("dynamic receiver permission must match application id")


def test_aapt_permission_parser_reads_only_permission_records():
    badging = """package: name='com.liuchenlin.chatgptweb.geckoprobe'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.RECORD_AUDIO'
uses-feature-not-required: name='android.hardware.microphone'
"""
    assert parse_aapt_permissions(badging) == {
        "android.permission.INTERNET",
        "android.permission.RECORD_AUDIO",
    }


def test_beta1_identity_parser_and_gate():
    badging = "package: name='com.liuchenlin.chatgptweb.geckoprobe' versionCode='40' versionName='0.4.0 Beta 1' compileSdkVersion='37'"
    assert parse_aapt_identity(badging) == ('com.liuchenlin.chatgptweb.geckoprobe', 40, '0.4.0 Beta 1')
    verify_beta1_identity(badging)


def test_beta1_identity_gate_rejects_old_beta_name():
    badging = "package: name='com.liuchenlin.chatgptweb.geckoprobe' versionCode='40' versionName='0.4.0 Beta'"
    try:
        verify_beta1_identity(badging)
    except ValueError as exc:
        assert 'versionName' in str(exc)
    else:
        raise AssertionError('old Beta name must fail Beta 1 APK gate')
