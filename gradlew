#!/usr/bin/env sh
# Lightweight wrapper bootstrap. GitHub Actions installs Gradle on PATH;
# local developer machines can use this script to download the pinned distribution.
set -eu
GRADLE_VERSION="8.11.1"
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi
GRADLE_HOME_BASE="${GRADLE_USER_HOME:-${HOME}/.gradle}/aeris-gradle"
GRADLE_HOME_PATH="${GRADLE_HOME_BASE}/gradle-${GRADLE_VERSION}"
if [ ! -x "${GRADLE_HOME_PATH}/bin/gradle" ]; then
    mkdir -p "${GRADLE_HOME_BASE}"
    ARCHIVE="${GRADLE_HOME_BASE}/gradle-${GRADLE_VERSION}-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 "${URL}" -o "${ARCHIVE}"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "${ARCHIVE}" "${URL}"
    else
        echo "Install curl or wget to bootstrap Gradle." >&2
        exit 1
    fi
    if command -v unzip >/dev/null 2>&1; then
        unzip -q -o "${ARCHIVE}" -d "${GRADLE_HOME_BASE}"
        rm -f "${ARCHIVE}"
    else
        echo "Install unzip to extract Gradle." >&2
        exit 1
    fi
fi
exec "${GRADLE_HOME_PATH}/bin/gradle" "$@"
