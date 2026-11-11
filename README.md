# AERIS V3 — Android Dynamic Island

Native Android starter for a polished, compact media and notification island. This is a separate V3 source package, not a commit to the original GitHub repository.

## Included

- Compact black pill, with stationary bars while idle and animated audio bars only while media is playing.
- Circular album image in compact mode; rounded-square image in expanded mode.
- Cover-art sampled background tint, with a source-app tint fallback for notification previews.
- Music metadata, playback controls, seek bar, time remaining and a separate lyrics panel.
- LRC / Enhanced LRC line and word timestamp parsing. When lyrics are unavailable, show `♫` instead of `null` or an error label. Optional LRCLIB lookup can retrieve synced lyrics; it is disabled by default.
- Notification app icons, an in-memory recent-notification queue, horizontal swipe history, two-second preview, dismiss and genuine RemoteInput quick reply.
- Single/double tap shortcuts, configurable Liquid Glass palette, optional system blur when Android permits it, landscape adaptation and four-second boot animation.
- Android 15 screen-recording callback scoped to AERIS activity visibility.

## Build

Open the folder in Android Studio with JDK 17, Gradle 8.11.1 and Android SDK 36. Sync Gradle, then run `:app:assembleDebug`. The package was created in an environment without an Android SDK / connected device. **It has not been Gradle-compiled, installed or device-tested here.** Treat Gradle sync and build as the next verification gate.

## Setup

1. Open AERIS and grant Display over other apps.
2. Enable AERIS notification access in Android settings.
3. Start the island.
4. Test with a media player that exposes an Android media session.

## Privacy and platform constraints

Notification data is kept in process memory and is not written to disk. Reply is only available if the source notification contains a free-form `RemoteInput` action. Artwork, seek support and lyrics depend on metadata/actions supplied by the player. Cross-window blur is optional and has a non-blur fallback. Secure apps and OEM restrictions may block overlays. The recording callback is not a universal detector for all device recording or activity in other apps.

This package intentionally does not use AccessibilityService to monitor arbitrary taps in other apps. A normal overlay can only react to touches on its own surface.
