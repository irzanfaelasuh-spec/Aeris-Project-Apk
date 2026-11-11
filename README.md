# AERIS V3 — Liquid Dynamic Island for Android

AERIS V3 is a native Kotlin / Jetpack Compose Android app built from the supplied V2 source. It keeps the original permission flow, optional Shizuku integration, overlay foreground service, Android media-session bridge, lyrics parser, and notification listener.

## V3 interface

- Four-second animated AERIS V3 boot screen.
- Compact idle island: a clean black capsule with no eye/mascot graphic.
- Compact music state: circular cover art, track and artist, and a small waveform. When the player is paused the bars stay still; without an active media session the pill returns to its idle capsule.
- Expanded music state: square cover art, cover-derived accent gradient, timeline, previous / play-pause / next, volume controls, and an optional lyrics panel.
- Lyrics are hidden until the user opens the music panel and taps the music-note control. When the media player does not expose lyrics, AERIS shows a music-note fallback instead of `null`.
- Expanded notification card with app icon, per-app accent color, title and text, history navigation, swipe navigation, optional RemoteInput reply, and notification dismissal.
- New notifications can auto-preview for two seconds by default. The preview duration can be adjusted from Settings.
- Manual expansion stays open. An outside touch is watched by the overlay and closes the panel; this behavior can vary by OEM/window policy.
- Liquid Glass theme for the app and island, with a translucent surface, soft optical highlights and bubble-like reflections. On Android 12+ AERIS asks WindowManager for blur-behind when system cross-window blur is available; the layered glass surface remains as fallback.
- Single tap / double tap / press-and-hold shortcuts can each be configured in Settings.
- Portrait and landscape responsive sizing, smooth bounds animation, and reduced-motion / low-power options.

## Retained systems

- First-run permission verification.
- Optional Shizuku connection / permission status.
- `TYPE_APPLICATION_OVERLAY` island service and foreground notification.
- `MediaSessionManager` controls, including advertised transport actions only.
- Notification Listener, in-memory notification history (up to eight), valid free-form `RemoteInput` reply, and notification dismissal.
- LRC / enhanced LRC lyric parsing, including per-word timestamps when supplied.
- Preferences, boot receiver, stopwatch, countdown, tutorial and About screen.

## Important platform limits

- **Lyrics:** Android media sessions do not guarantee lyrics metadata. Spotify or another player may expose no lyrics. AERIS does not scrape streaming services or bundle copyrighted lyrics. Karaoke word highlighting requires enhanced LRC timestamps; ordinary LRC is line-synchronized.
- **Notification reply:** Reply works only when the source notification includes a compatible free-form `RemoteInput` action. Dismiss removes the Android notification; it does not delete or unsend the original chat message.
- **Notification history:** The queue is kept in RAM only and contains at most eight recent notifications, even after a notification is dismissed from Android's shade. It is cleared when the app process is killed. A saved reply action may become invalid if the source app cancels its original `PendingIntent`; AERIS reports failure rather than claiming the reply was sent.
- **Media controls:** Previous, next, seek, and other custom actions depend on the active player publishing those capabilities. AERIS does not fake unavailable actions.
- **Blur:** Cross-window blur is an Android / GPU / OEM capability and can be disabled by the system. Liquid Glass therefore includes a layered translucent fallback; it cannot promise iPhone-identical blur on every device.
- **Recording detection:** AERIS can react to notifications and media sessions published by apps. It cannot universally detect every third-party screen/audio recording session on Android; those states are not exposed consistently to ordinary apps.
- **Overlay:** Android can place critical system windows above application overlays, change overlay visibility, or restrict background behavior. Some secure/full-screen apps may block overlays.
- **Background service:** V3 retains the special-use foreground-service declaration for the user-enabled floating overlay. Android 14+ requires the type and its specific permission; Play distribution also needs a valid use-case declaration and policy review.

## Build

- Android Gradle Plugin: 8.10.0
- Gradle: 8.11.1
- Kotlin: 2.2.21
- JDK: 17
- Compile SDK: 36
- Target SDK: 35
- Minimum SDK: 26
- App version: 3.0.0 / versionCode 3

### GitHub Actions (phone-friendly)

1. Extract this ZIP.
2. Upload the **contents** of the `AERIS` folder to the root of the GitHub repository. Keep `.github/workflows/android.yml` at exactly that path.
3. Commit to `main` or `master`.
4. Open GitHub Actions → `AERIS Android Build` → latest run.
5. Download `AERIS-V3-debug-apk` and install `app-debug.apk` after extracting the artifact.

The workflow runs source checks, the LyricsEngine unit tests, and the Android Gradle debug build. This ZIP has passed the local source/XML checks only; it has **not** been compiled by Android Gradle or tested on a physical phone in this environment. A successful GitHub Actions run is still required before calling the APK build verified.

## Privacy

- No analytics, ads, cloud sync, account, or remote lyric service.
- No internet permission is requested.
- Notification content and reply targets stay in process memory; they are not saved to disk.
- Shizuku remains optional and is not used to execute arbitrary privileged shell commands.

See `FEATURE_STATUS.md` and `AERIS_V3_HANDOFF.md` for verification details.
