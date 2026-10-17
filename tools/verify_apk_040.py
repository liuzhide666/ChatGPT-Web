#!/usr/bin/env python3
import argparse
import re
import subprocess
from collections.abc import Iterable
from pathlib import Path


BASE_REQUIRED_PERMISSIONS = frozenset(
    {
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
)
DYNAMIC_RECEIVER_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

_AAPT_PERMISSION = re.compile(r"^uses-permission: name='([^']+)'$", re.MULTILINE)
_AAPT_PACKAGE = re.compile(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", re.MULTILINE)


def parse_aapt_permissions(badging: str) -> set[str]:
    return set(_AAPT_PERMISSION.findall(badging))


def parse_aapt_identity(badging: str) -> tuple[str, int, str]:
    match = _AAPT_PACKAGE.search(badging)
    if not match:
        raise ValueError("package identity missing from aapt badging")
    return match.group(1), int(match.group(2)), match.group(3)


def parse_aapt_package(badging: str) -> str:
    return parse_aapt_identity(badging)[0]


def verify_beta1_identity(badging: str) -> None:
    package_name, version_code, version_name = parse_aapt_identity(badging)
    if package_name != "com.liuchenlin.chatgptweb.geckoprobe":
        raise ValueError(f"unexpected package: {package_name}")
    if version_code != 40:
        raise ValueError(f"unexpected versionCode: {version_code}")
    if version_name != "0.4.0 Beta 1":
        raise ValueError(f"unexpected versionName: {version_name}")


def verify_permission_set(actual: Iterable[str], application_id: str) -> None:
    actual_set = set(actual)
    dynamic_permission = application_id + DYNAMIC_RECEIVER_SUFFIX
    expected = BASE_REQUIRED_PERMISSIONS | {dynamic_permission}
    missing = sorted(expected - actual_set)
    extra = sorted(actual_set - expected)
    if missing or extra:
        raise ValueError(f"permission mismatch: missing={missing}; extra={extra}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aapt", required=True, type=Path)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()

    result = subprocess.run(
        [str(args.aapt), "dump", "badging", str(args.apk)],
        check=True,
        text=True,
        capture_output=True,
    )
    verify_beta1_identity(result.stdout)
    verify_permission_set(parse_aapt_permissions(result.stdout), parse_aapt_package(result.stdout))
    print("APK_BETA1_IDENTITY_OK")
    print("APK_PERMISSION_SET_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
