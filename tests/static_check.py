from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
required = [
    "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/dev/aeris/island/MainActivity.kt",
    "app/src/main/java/dev/aeris/island/AerisPrefs.kt",
    "app/src/main/java/dev/aeris/island/media/MediaBridge.kt",
    "app/src/main/java/dev/aeris/island/media/LyricsEngine.kt",
    "app/src/main/java/dev/aeris/island/media/LyricsProvider.kt",
    "app/src/main/java/dev/aeris/island/notifications/AerisNotificationListener.kt",
    "app/src/main/java/dev/aeris/island/overlay/IslandService.kt",
    "app/src/main/java/dev/aeris/island/overlay/PillOverlayView.kt",
]
missing = [p for p in required if not (ROOT / p).is_file()]
assert not missing, f"Missing files: {missing}"
for p in (ROOT / "app/src/main").rglob("*.xml"):
    ET.parse(p)
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
for token in ["INTERNET", "SYSTEM_ALERT_WINDOW", "BIND_NOTIFICATION_LISTENER_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE", "DETECT_SCREEN_RECORDING"]:
    assert token in manifest, f"Missing manifest permission/binding: {token}"
pill = (ROOT / "app/src/main/java/dev/aeris/island/overlay/PillOverlayView.kt").read_text()
service = (ROOT / "app/src/main/java/dev/aeris/island/overlay/IslandService.kt").read_text()
notifs = (ROOT / "app/src/main/java/dev/aeris/island/notifications/AerisNotificationListener.kt").read_text()
main = (ROOT / "app/src/main/java/dev/aeris/island/MainActivity.kt").read_text()
assert "IdleEyesView" not in pill
for token in ["LyricsEngine.frame", "browseNotifications", "lyricsExpanded", "sampleArtworkTint", "appTint"]:
    assert token in pill, f"Missing overlay feature: {token}"
for token in ["notification_peek_seconds", "addScreenRecordingCallback", "animateTo", "setBlurBehindRadius"]:
    assert token in service, f"Missing service feature: {token}"
for token in ["markSourceRemoved", "RemoteInput.addResultsToIntent", "recent()"]:
    assert token in notifs, f"Missing notification feature: {token}"
for token in ["liquid_glass", "single_tap_action", "double_tap_action", "notification_peek_seconds", "online_lyrics"]:
    assert token in main, f"Missing settings feature: {token}"
gradle = (ROOT / "app/build.gradle.kts").read_text()
assert 'versionCode = 3' in gradle and 'versionName = "3.0.0"' in gradle
print(f"AERIS V3 static checks passed: {len(required)} required files and XML resources parsed.")
