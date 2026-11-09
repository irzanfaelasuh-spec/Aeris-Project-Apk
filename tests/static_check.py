#!/usr/bin/env python3
"""Fast structural checks for AERIS. Android compilation remains the CI job's next gate."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/dev/aeris/island/MainActivity.kt",
    "app/src/main/java/dev/aeris/island/AerisApp.kt",
    "app/src/main/java/dev/aeris/island/AerisPrefs.kt",
    "app/src/main/java/dev/aeris/island/overlay/IslandService.kt",
    "app/src/main/java/dev/aeris/island/overlay/PillOverlayView.kt",
    "app/src/main/java/dev/aeris/island/media/MediaBridge.kt",
    "app/src/main/java/dev/aeris/island/notifications/AerisNotificationListener.kt",
    "app/src/main/java/dev/aeris/island/shizuku/ShizukuBridge.kt",
    ".github/workflows/android.yml",
]
missing = [item for item in REQUIRED if not (ROOT / item).is_file()]
if missing:
    print("Missing required files:")
    for item in missing:
        print(f" - {item}")
    sys.exit(1)

xml_files = list((ROOT / "app/src/main").rglob("*.xml"))
for xml_file in xml_files:
    try:
        ET.parse(xml_file)
    except ET.ParseError as exc:
        print(f"Invalid XML: {xml_file.relative_to(ROOT)}: {exc}")
        sys.exit(1)

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
checks = {
    "overlay permission": "android.permission.SYSTEM_ALERT_WINDOW",
    "notification listener binding": "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "special-use foreground service": "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "Shizuku provider": "rikka.shizuku.ShizukuProvider",
    "foreground overlay service": "android:foregroundServiceType=\"specialUse\"",
}
for label, needle in checks.items():
    if needle not in manifest:
        print(f"Manifest check failed: {label}")
        sys.exit(1)

main = (ROOT / "app/src/main/java/dev/aeris/island/MainActivity.kt").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/dev/aeris/island/overlay/IslandService.kt").read_text(encoding="utf-8")
notification = (ROOT / "app/src/main/java/dev/aeris/island/notifications/AerisNotificationListener.kt").read_text(encoding="utf-8")
for label, content, needles in [
    ("main UI", main, ["SplashScreen", "VerificationScreen", "HomeScreen", "SettingsScreen", "TutorialScreen", "AboutScreen"]),
    ("overlay", service, ["TYPE_APPLICATION_OVERLAY", "startForeground", "landscape_enabled", "expand_seconds"]),
    ("notification bridge", notification, ["onNotificationPosted", "RemoteInput.addResultsToIntent", "PendingIntent.CanceledException"]),
]:
    for needle in needles:
        if needle not in content:
            print(f"{label} check failed: missing {needle}")
            sys.exit(1)

for path in ROOT.rglob("*.kt"):
    content = path.read_text(encoding="utf-8")
    if "TODO(" in content or "IMPLEMENT ME" in content:
        print(f"Placeholder marker found in {path.relative_to(ROOT)}")
        sys.exit(1)

print(f"AERIS structural checks passed: {len(REQUIRED)} required files, {len(xml_files)} XML resources.")
print("Note: the GitHub Actions job performs the actual Android/Gradle compile.")
