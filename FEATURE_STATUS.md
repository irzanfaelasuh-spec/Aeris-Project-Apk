# AERIS feature status

This document distinguishes shipped behavior from ideas that depend on Android, the source app, extra permissions, or future implementation. A label in a feature list is not treated as evidence that the feature works.

## Implemented in this source build

| Feature | Status | Notes |
|---|---|---|
| Dynamic Island overlay | Implemented | `TYPE_APPLICATION_OVERLAY`; requires user-granted overlay access |
| Expand / collapse | Implemented | Tap-to-expand and configurable auto-collapse |
| Landscape adaptation | Implemented | Recalculates overlay width after orientation changes |
| Music metadata | Implemented | Depends on active Android media sessions |
| Spotify / YouTube Music support | Source-dependent | Uses the same Android media-session bridge as other players |
| Media play / pause / next / previous | Implemented | Sends commands to the selected media session |
| Album art / app icon | Implemented | Album art preferred; package icon fallback |
| Playback progress | Implemented | Based on the playback position and duration supplied by the media session |
| Animated visualizer bars | Implemented | Playback-state animation, not raw audio capture |
| Notification preview | Implemented | Latest notification only; requires Notification access |
| Notification quick reply | Conditional | Only if source app publishes free-form RemoteInput |
| Sensitive preview hiding | Implemented | Hides message text in AERIS surfaces |
| Battery level / charging / battery temperature | Implemented | Read from Android battery status broadcast |
| RAM use / free storage / connection type | Implemented | System-reported summary values |
| Stopwatch / countdown | Implemented | Operates while the Tools screen is open |
| Auto-expand on music / notifications | Implemented | Optional switches |
| Auto-start after reboot | Best effort | Opt-in receiver; Android/OEM policies may block startup |
| Low-power refresh | Implemented | Slower overlay refresh cadence |
| Width / height / top offset / opacity / radius | Implemented | Local settings |
| Animation speed / expanded width / expand duration | Implemented | Local settings |
| Shizuku connection status / permission request | Optional integration | Core features do not depend on Shizuku |
| Tutorial / About / verification screens | Implemented | Includes simple visual walkthroughs |

## Depends on the Android OS or source application

| Feature idea | Limitation |
|---|---|
| Lyrics | Only available when the player exposes lyric text to AERIS |
| Notification reply | Requires the source notification to expose a valid RemoteInput action |
| WhatsApp / Telegram / Discord reply | Supported only when that particular notification includes an eligible reply action |
| Incoming / missed call display | Depends on call and system notification behavior; not separately implemented as a call-state service |
| Exact battery health | Not exposed consistently across Android devices |
| CPU temperature | Not exposed consistently to ordinary apps |
| Cross-app FPS | Not reliably available to ordinary apps without supported system/game integration |
| Overlay in every game | Secure / immersive windows and OEM policies can block it |
| Background keep-alive | Android and OEM power policies remain authoritative |
| Auto-start on all brands | No universal Android auto-start permission exists |

## Not implemented in v1

These remain future work rather than working features in this build: live weather, cloud calendar sync, world-clock comparisons, prayer-time data, currency conversion, QR scanning, clipboard history/paste, translation, app-usage analytics, persistent notification history, download/upload manager integration for other apps, network-speed history, per-game FPS, CPU load telemetry, screen-recording detection, camera/microphone activity indicators, flashlight control, global volume/brightness control, Wi-Fi/Bluetooth toggles, DND control, call management, and remote data services.

A later release can add these only with a suitable public Android API, a clearly described permission, or a documented source-service integration. AERIS should not display simulated system values or pretend a button performed an action that Android did not accept.
