#!/usr/bin/env python3
"""Verify the executable Gradle Wrapper against official published Gradle 9.3.1 SHA-256.

This validates the checked-in launcher bytes and distribution policy, not the entire
Gradle dependency graph or an Android release artifact.
"""
from hashlib import sha256
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
JAR_SHA256 = 'b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13'
ZIP_SHA256 = 'b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06'
EXPECTED_URL = r'https\://services.gradle.org/distributions/gradle-9.3.1-bin.zip'


def verify(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    wrapper = root / 'gradle/wrapper/gradle-wrapper.jar'
    props = root / 'gradle/wrapper/gradle-wrapper.properties'
    sh = root / 'gradlew'
    bat = root / 'gradlew.bat'
    for path in (wrapper, props, sh, bat):
        if not path.is_file():
            errors.append(f'missing {path.relative_to(root)}')
    if not wrapper.is_file():
        return errors
    actual = sha256(wrapper.read_bytes()).hexdigest()
    if actual != JAR_SHA256:
        errors.append(f'wrapper JAR SHA256 mismatch: {actual}')
    if props.is_file():
        lines = props.read_text(encoding='utf-8').splitlines()
        settings = dict(line.split('=', 1) for line in lines if line and not line.lstrip().startswith('#') and '=' in line)
        if settings.get('distributionUrl') != EXPECTED_URL:
            errors.append('distributionUrl differs from fixed Gradle 9.3.1 HTTPS endpoint')
        if settings.get('distributionSha256Sum') != ZIP_SHA256:
            errors.append('distributionSha256Sum differs from official release checksum')
        if settings.get('validateDistributionUrl') != 'true':
            errors.append('distribution URL validation must be enabled')
    if sh.is_file() and (not sh.read_text(encoding='utf-8').startswith('#!/bin/sh') or not sh.stat().st_mode & 0o111):
        errors.append('gradlew is not an executable POSIX shell launcher')
    if bat.is_file() and '@rem' not in bat.read_text(encoding='utf-8', errors='replace').lower():
        errors.append('gradlew.bat does not look like an official Windows launcher')
    return errors


if __name__ == '__main__':
    problems = verify()
    if problems:
        for problem in problems:
            print('GRADLE_WRAPPER_FAIL:', problem, file=sys.stderr)
        sys.exit(1)
    print('GRADLE_WRAPPER_SHA256_OK Gradle=9.3.1')
