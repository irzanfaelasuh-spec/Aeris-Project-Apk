# AERIS V2 — Android Dynamic Island

AERIS V2 is an upgrade of the supplied native Kotlin / Jetpack Compose V1 project. It retains the permission Verification flow, optional Shizuku integration, overlay foreground service, media-session bridge, and notification listener.

## V2 additions

- Updated V2 splash with a smooth descending `V2` label under the AERIS wordmark, held in its own layout slot so it does not overlap the logo.
- Idle pill mascot: two animated eyes with subtle left/right gaze and blinking; no persistent `AERIS / READY` text in the idle pill.
- Rounded album artwork in the island.
- Expanded music island with elapsed / total time, draggable seek control, Previous / Play-Pause / Next, ±10-second seek, media volume up/down, and source-provided custom media actions.
- Lyrics block inside the expanded island and Music screen. Standard LRC follows lines; enhanced LRC supports per-word karaoke highlighting; plain lyrics are displayed without fabricated timing.
- Notification queue (up to eight recent items in process memory), direct reply when Android supplies a valid free-form `RemoteInput`, and notification dismissal.
- Smoothed overlay entrance and a horizontally scrollable control row to avoid clipped controls on narrow screens.

## Lyrics and media compatibility

AERIS reads lyrics only if the active music app exposes them in Android media metadata (`android.media.metadata.LYRICS`). It does not scrape streaming services or bundle song lyrics. Enhanced LRC word timestamps (`<mm:ss.xx>`) allow per-word highlighting. Standard LRC (`[mm:ss.xx]`) can only synchronize by line. Plain text is not time-synchronized.

Previous, Next, Seek, Shuffle, Repeat, and other special actions depend on the capabilities exposed by the active Android media session. Unsupported controls are disabled or omitted. Volume up/down uses the Android music stream volume.

## Notification behavior

Music and notifications share the same pill without replacing each other: the active media controls remain available, while the newest notification is shown in a separate preview row and the queue count indicates additional items. Reply uses the original notification's `RemoteInput` action. Dismiss removes the notification from Android's notification shade; it does not delete the original chat message.

## Retained V1 systems

- First-run Verification / permission checklist.
- Shizuku connection status and permission request (optional; not required for core operation).
- `TYPE_APPLICATION_OVERLAY` service and foreground-service notification.
- Notification Listener Service.
- MediaSessionManager discovery.
- Battery / system overview, stopwatch, countdown, settings, tutorial and About screens.
- Opt-in boot receiver and device-specific background limitations.

## Build settings

- Android Gradle Plugin 8.10.0
- Gradle 8.11.1
- Kotlin 2.2.21
- JDK 17
- Compile SDK 36 / Target SDK 35 / Minimum SDK 26
- Application version 2.0.0 / versionCode 2

## GitHub Actions build (phone-friendly)

1. Extract this ZIP.
2. Upload the **contents** of the `AERIS` folder into the root of your GitHub repository. Keep `.github/workflows/android.yml` at exactly that path.
3. Commit the files to `main` or `master`.
4. Open Actions → `AERIS Android Build` → latest run.
5. Download `AERIS-V2-debug-apk` and install `app-debug.apk` after extracting the artifact.

The workflow runs the structural checks, unit tests, and `assembleDebug`. A successful ZIP creation here does not mean the Android app has already passed Gradle compilation or device testing; use the GitHub Actions result as the compile gate.

## Privacy

- No account, analytics SDK, ads SDK, or cloud relay.
- Notification content is processed in memory and is not written to disk.
- No internet permission is requested by this build; lyric text comes from the active media session or is absent.
- Shizuku is optional and the current bridge does not execute privileged shell commands.

See `FEATURE_STATUS.md` for feature-by-feature state, future ideas that are not implemented yet, and the required verification sequence. See `AERIS_V2_HANDOFF.md` when continuing development in a new chat.
