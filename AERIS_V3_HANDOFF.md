# AERIS V3 — continuation handoff

## Project identity

- Project: AERIS, native Android dynamic island overlay.
- Namespace / application ID: `dev.aeris.island`.
- Version: 3.0.0 / versionCode 3.
- Device target recorded in the V2 handoff: Infinix Hot 60i X6728, Android 15.
- Phone-first workflow: GitHub Actions can build the debug APK after the project folder contents are uploaded to the repository root.

## Source baseline and safety

V3 is built from the user-provided V2 archive. Do not discard the existing verification flow, Shizuku bridge, service, notification listener, MediaSession bridge, lyrics engine, boot receiver, and settings/tools screens. The supplied archive remains an independent backup outside this V3 project tree.

## Main V3 changes

- `MainActivity.kt`: four-second splash, V3 label, palette switch for Liquid Glass, app settings for gesture shortcuts, expanded display options.
- `overlay/PillOverlayView.kt`: complete native view redesign; no idle eyes; rounded compact artwork and rounded-square expanded artwork; static/animated waveform; dynamic color from media art; lyrics toggle; app-colored notification card; notification history arrows/swipes; app icon; per-notification RemoteInput target; glyph-first controls.
- `overlay/IslandService.kt`: animated size transitions, 2-second notification preview default, manual persistent expansion, outside-touch collapse, adaptive portrait/landscape bounds, Android 12+ blur-behind request when supported.
- `media/MediaBridge.kt`: no longer returns stopped sessions with stale metadata as active media; accepts both metadata and extras lyrics keys.
- `app/build.gradle.kts`: version 3.0.0 / code 3.
- `.github/workflows/android.yml`: canonical workflow file name and V3 artifact label.

## Build status

The workspace does not contain Android SDK platforms/build-tools or a Gradle installation/wrapper JAR. Therefore Android Gradle compilation and device runtime validation have **not** been completed here. Source-level checks and XML parsing are the only local verification gate. Do not claim the APK has compiled until the GitHub Actions job succeeds.

## Android platform behavior to preserve honestly

- MediaSessionManager can enumerate active sessions using the enabled NotificationListenerService; otherwise privileged media-session access is not generally granted to third-party apps.
- Notification reply works only through a source notification's compatible free-form `RemoteInput` action.
- Application overlays require user-granted overlay access and remain below critical system windows.
- Cross-window blur is device/GPU/runtime dependent. The app includes a gradient/glass fallback.
- AERIS does not globally detect every screen/audio recorder session. It displays media or notification events that Android exposes to it.
- Notification history remains in RAM, capped at eight entries.

## Next actions

1. Run `python3 tests/static_check.py` from project root.
2. Run GitHub Actions `AERIS Android Build`; fix any exact Kotlin / Gradle errors from its log.
3. Install the debug APK on the target Android phone.
4. Follow the device test checklist in `FEATURE_STATUS.md`.
5. Update feature statuses only after device confirmation.
