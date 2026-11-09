# AERIS — Dynamic Island for Android

AERIS is a native Android app prototype built with Kotlin and Jetpack Compose. Its central feature is a black, floating pill that can show current media and notification activity above other apps.

The project is designed for GitHub Actions builds. It targets Android 15 (API 35), compiles against Android 16 / API 36, and supports Android 8.0 (API 26) and later.

## What works in this build

- Animated splash screen, first-run verification, and a terminal-style startup sequence.
- Floating overlay using Android's `TYPE_APPLICATION_OVERLAY` window type.
- Tap-to-expand, timed auto-collapse, landscape re-layout, width/height/position/radius/opacity controls.
- Foreground service with a persistent Android notification while the overlay is running.
- Media-session detection through `MediaSessionManager`, including title, artist, playback state, available album art, playback progress, and previous/play-pause/next controls.
- Small animated playback bars. These are a playback-state visualizer, not a captured audio waveform.
- Notification preview through `NotificationListenerService`.
- Quick reply only when the source notification contains a free-form `RemoteInput` action. AERIS forwards the reply through the original `PendingIntent`; it does not simulate sending.
- Privacy control to hide notification text, optional auto-start attempt after reboot, and low-power refresh mode.
- Dashboard metrics: battery level, charging state, battery temperature, RAM use, available storage, and connection type.
- Stopwatch and countdown timer.
- Tutorial, About screen, optional Shizuku status/request integration, and a local settings panel with switches and sliders.

Settings are stored locally in Android SharedPreferences. The app does not include an account system, analytics SDK, ad SDK, or cloud relay.

## Important limitations — please read

Android does not provide every system-level capability to ordinary apps. AERIS deliberately avoids fake data and fake actions.

1. **Overlay compatibility:** some games, secure windows, DRM content, and manufacturer-specific full-screen modes can hide or block overlays. AERIS cannot override those system protections.
2. **Background execution:** Android and device manufacturers can stop background services. Auto-start is an opt-in best-effort attempt, not a guarantee. The user must start the service from the app at least once and allow the relevant system settings.
3. **Android 15 foreground services:** AERIS starts its foreground service from the visible app after the user presses Start. The app does not try to silently start the overlay from the background.
4. **Music metadata:** the player must publish a media session. Spotify, YouTube Music, and other media apps may be detected when Android exposes a session and notification access has been granted.
5. **Lyrics:** lyrics appear only when the player itself exposes lyric text in its notification or media metadata. There is no universal Android lyrics API, so AERIS does not scrape music services.
6. **Quick reply:** a Reply control appears only when the source app supplies a valid free-form `RemoteInput`. Notifications without that action cannot be replied to from AERIS.
7. **Visualizer:** the bars animate while playback is active; they do not record or capture the audio stream.
8. **Gaming FPS / CPU temperature:** Android does not guarantee reliable cross-app FPS or CPU-temperature access to normal third-party apps. This release does not invent those readings. The game-session timer is available.
9. **Shizuku:** the project can detect/request Shizuku permission, but the core island does not require Shizuku and this release does not run privileged system commands.
10. **Weather and online services:** live weather, currency data, cloud calendars, and similar integrations are not included because they require reliable data sources and, in some cases, API credentials.

See [FEATURE_STATUS.md](FEATURE_STATUS.md) for a compact status map of the larger feature ideas.

## Build with GitHub Actions (phone-friendly workflow)

1. Create a new repository named `AERIS` on GitHub.
2. Extract this ZIP on your phone.
3. Upload the **contents of the extracted `AERIS` folder** to the repository root. The `.github/workflows/android.yml` file must stay at `.github/workflows/android.yml`.
4. Commit the files to `main` (or `master`). GitHub Actions will run the structural check, run the Gradle test task, and build the debug APK.
5. Open the repository's **Actions** tab, open the latest **AERIS Android Build** run, and download the `AERIS-debug-apk` artifact.
6. Extract the artifact ZIP and install `app-debug.apk` on your Android device. Android may ask you to allow installation from the browser or file manager you used.

You can also start the workflow manually from **Actions → AERIS Android Build → Run workflow** after the files are committed.

### Build configuration

- Android Gradle Plugin: 8.10.0
- Gradle: 8.11.1
- Kotlin: 2.2.21
- JDK: 17
- Compile SDK: 36
- Target SDK: 35
- Minimum SDK: 26

The included `gradlew` bootstrap uses a Gradle installation already placed on `PATH` by GitHub Actions. On a local machine without Gradle, it downloads the pinned Gradle distribution; it is intentionally not the binary Gradle wrapper JAR.

## First launch

1. Open AERIS.
2. Grant **Display over other apps** to enable the floating pill.
3. Grant **Notification access** for notification previews and media-session discovery.
4. Allow AERIS notifications when Android asks. The persistent foreground-service notice is expected while the island is active.
5. Battery/background optimization is recommended for longer sessions. Manufacturer-specific auto-launch settings may still be required.
6. Shizuku is optional. Do not install or enable it unless you actually need the optional advanced bridge.
7. Return to AERIS, press **Next**, then use **Start AERIS** on the Overview screen.

## Privacy

- Notification contents are handled on-device and kept in memory for the latest preview only.
- No notification history is uploaded.
- The app requests notification access because Android requires it for notification listening and active media-session discovery.
- Disable Notification access in Android Settings to revoke that capability.

## Project structure

```text
AERIS/
├── .github/workflows/android.yml
├── app/
│   ├── src/main/AndroidManifest.xml
│   └── src/main/java/dev/aeris/island/
│       ├── MainActivity.kt
│       ├── AerisApp.kt
│       ├── AerisPrefs.kt
│       ├── media/MediaBridge.kt
│       ├── notifications/AerisNotificationListener.kt
│       ├── overlay/IslandService.kt
│       ├── overlay/PillOverlayView.kt
│       └── shizuku/ShizukuBridge.kt
├── gradle/wrapper/gradle-wrapper.properties
└── tests/static_check.py
```

## Credits

AERIS uses AndroidX / Jetpack Compose and the Shizuku API. Third-party components remain under their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
