# AERIS V2 — Continuation Handoff

Use this file when continuing the AERIS project in a new conversation. This ZIP is the source-of-truth snapshot for the V2 work in this turn.

## Project identity

- Project: AERIS, native Android app / Dynamic Island overlay.
- Package / namespace: `dev.aeris.island`.
- Repository previously used by the user: `irzanfaelasuh-spec/AERIS_project` / `Aeris-Project-Apk` in earlier discussions; do not assume a repository URL or branch until the user confirms.
- Device target: Infinix Hot 60i X6728, Android 15, phone-only workflow.
- Build: Android Gradle Plugin 8.10.0, Gradle 8.11.1, Kotlin 2.2.21, JDK 17, compileSdk 36, targetSdk 35, minSdk 26.

## V1 systems that must remain

- Verification / permission checklist in `MainActivity.kt`.
- Optional Shizuku integration in `shizuku/ShizukuBridge.kt` and manifest provider.
- Overlay foreground service in `overlay/IslandService.kt`.
- Media session bridge in `media/MediaBridge.kt`.
- Notification Listener and RemoteInput reply in `notifications/AerisNotificationListener.kt`.
- Preferences, BootReceiver, timer tools, tutorial, and About screen.

Do not rebuild from scratch and do not remove these systems without a specific reason and user approval.

## V2 implemented in source snapshot

- App version 2.0.0, versionCode 2.
- Splash: logo plus animated V2 label drops from above into a reserved slot underneath the wordmark.
- Idle Dynamic Island: animated pair of eyes gaze left/right and blink; no idle `AERIS / READY` text.
- Rounded album artwork.
- Media bridge ranks active sessions, computes current position using PlaybackState update time, checks advertised Previous / Next / Seek capabilities, and exposes player custom actions.
- Expanded pill controls: ±10s, Previous, Play/Pause, Next, ±10s, volume down/up, and at most one source-provided custom action. Control row scrolls horizontally.
- Timeline with elapsed / total time and drag seek, only if `ACTION_SEEK_TO` is supported.
- Lyrics display in expanded pill and Music screen. `LyricsEngine.kt` parses standard LRC and enhanced LRC; enhanced per-word timestamps are highlighted in karaoke style. It also previews plain text without inventing timestamps.
- Lyrics data is read only from the current media app's Android media metadata key `android.media.metadata.LYRICS` (and compatible `lyrics` / state extras keys). No remote lyrics scraping, no bundled lyrics, no internet permission.
- Notification queue keeps up to eight recent notifications in RAM. Quick reply uses a valid RemoteInput action only. Dismiss cancels the Android notification only, never the original chat message.
- Overlay entry animation; reduced-motion visualizer preference retained.

## Important constraints / honesty

- Spotify may not expose lyrics to AERIS; a no-lyrics message is shown. Word-by-word sync only works if the source provides enhanced LRC word timestamps. Regular LRC is line-synchronized. Plain lyrics are not synchronized.
- Previous / Next / Seek / source custom actions depend on the MediaSession's advertised actions. If not advertised, controls should be disabled or hidden, not faked.
- Shizuku remains optional; bridge detects state and requests permission, but does not run arbitrary privileged commands.
- Android/OEM may block overlays, background keepalive, or boot auto-start.
- Build not yet Gradle-verified in the original creation environment because Android SDK / Gradle were not installed and outbound network unavailable. Static project checks and standalone `LyricsEngine.kt` compilation were run. GitHub Actions build and physical device testing are still mandatory.

## Files with substantive V2 changes

- `app/build.gradle.kts` — version 2.0.0 / code 2.
- `app/src/main/java/dev/aeris/island/MainActivity.kt` — V2 splash, Music screen, seek UI, lyrics screen, settings toggle, notification recent queue.
- `app/src/main/java/dev/aeris/island/media/MediaBridge.kt` — media session scoring, transport capability checks, seek, custom actions, playback position, lyrics metadata.
- `app/src/main/java/dev/aeris/island/media/LyricsEngine.kt` — added LRC / Enhanced LRC parser.
- `app/src/main/java/dev/aeris/island/overlay/IslandService.kt` — expanded sizing, new controls data, dismiss callback, enter animation.
- `app/src/main/java/dev/aeris/island/overlay/PillOverlayView.kt` — new pill UI, moving eye mascot, rounded artwork, seek controls, word/line lyrics, notification actions.
- `app/src/main/java/dev/aeris/island/notifications/AerisNotificationListener.kt` — in-memory queue and per-notification reply targets/dismiss support.
- `app/src/main/AndroidManifest.xml` — existing permissions/services retained; no network permission required.
- `tests/static_check.py`, `tests/LyricsEngineTest.kt` — source checks and parser tests.
- `.github/workflows/android.yml` — debug APK artifact name updated.

## Next required actions

1. Run `python3 tests/static_check.py` from project root. The LyricsEngine JUnit test is in `app/src/test/java/dev/aeris/island/media/`.
2. Run GitHub Actions `AERIS Android Build`; inspect Gradle compile and unit test output. Do not claim a successful build until the Actions run succeeds.
3. If Kotlin / Android API errors occur, fix the exact file(s) and provide complete replacement files or another project ZIP.
4. Install the debug APK on Android 15 and test Verification, Shizuku optional path, overlay, idle eyes, Spotify controls/lyrics, WhatsApp reply/dismiss, notifications while music is active, landscape and battery usage.
5. Keep this file updated with verified statuses before handing the project to another conversation.
