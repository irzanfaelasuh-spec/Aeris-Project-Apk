# AERIS V2 feature status

This file separates code that has been added from behavior that still depends on Android, the active media app, or on-device verification. `IMPLEMENTED IN SOURCE` is not the same as `BUILD VERIFIED` or `DEVICE TESTED`.

## Added or updated in V2 source

| Feature | Source status | Verification still needed |
|---|---|---|
| Native AERIS V2 splash | Implemented in source | Check motion and spacing on the target phone |
| Verification / permission checklist | Retained from V1 | Re-test every Android settings deep link |
| Shizuku bridge | Retained as optional integration | Test connected, authorized, denied, and not-installed states |
| Dynamic Island overlay | Retained and updated | Test overlay lifecycle and OEM restrictions |
| Idle eye mascot | Implemented in source; replaces idle AERIS text | Test eye movement, blink, touch and reduced-motion behavior |
| Rounded album artwork | Implemented in source | Test Spotify and other players' artwork |
| Active media-session selection | Improved in source | Test with Spotify and multiple simultaneous media sessions |
| Previous / Next | Capability-aware in source | Works only when the active session advertises the action |
| Play / Pause | Implemented in source | Test with each target media player |
| Seek timeline and elapsed / total time | Implemented in source | Requires `ACTION_SEEK_TO` and accurate playback state from player |
| Skip back / forward 10 seconds | Implemented in source | Requires seek support from player |
| Volume up / down | Implemented in source | Uses Android music stream volume |
| Player custom actions | Implemented in source | Shuffle / repeat / like appear only if the player publishes such actions |
| Lyrics panel in the pill | Implemented in source | Requires lyrics text exposed by media metadata |
| Enhanced LRC word highlighting | Implemented in source | Requires per-word timestamps in enhanced LRC supplied by the player |
| Standard LRC line highlighting | Implemented in source | Requires line-timestamped LRC supplied by the player |
| Plain lyric preview | Implemented in source | No synchronized movement without timestamps |
| Notification queue (up to 8 in memory) | Implemented in source | Test notification churn and listener reconnection |
| Notification quick reply | Retained and expanded | Requires a valid free-form Android `RemoteInput` action |
| Notification dismiss | Implemented in source | Dismisses the Android notification only; does not delete a WhatsApp message |
| Notification privacy | Retained | Test hidden previews throughout all screens |
| Stopwatch and countdown | Retained from V1 | Test timer behavior while app is backgrounded |
| Settings / layout controls | Retained from V1; lyrics toggle added | Test persisted preferences after upgrade |
| Foreground service and boot receiver | Retained | Background start remains subject to Android/OEM policy |
| GitHub Actions | Retained and should build V2 debug APK | Actual CI build is still required |

## Important limitations

- AERIS does not scrape lyrics from streaming services or bundle copyrighted song lyrics. It displays text the active player explicitly exposes through its Android media session. Spotify may not expose lyrics to third-party apps, so the lyrics panel can report that the source player does not provide them.
- Word-by-word karaoke highlighting is exact only when the exposed data contains enhanced LRC word timestamps. Ordinary LRC can only highlight by line. Plain text is shown without pretending to be synchronized.
- Previous, Next, seek, and app-specific controls depend on the active media session's advertised capabilities. Unsupported actions are disabled rather than faked.
- Shuffle, repeat, favorite, and queue are not universal MediaSession actions. V2 displays custom media actions only when the source player publishes them.
- Notification reply only works when the original notification contains a free-form `RemoteInput` action. Dismiss removes the Android notification only; it cannot delete or unsend a message in WhatsApp or another chat app.
- Notification history is an in-memory queue of up to eight recent notifications. It is not persisted to disk and is cleared when the process ends.
- Shizuku remains optional. The current bridge detects connection / requests permission; it does not run arbitrary privileged commands.
- Android can block overlays above secure or immersive apps, stop background services, or restrict auto-start. AERIS cannot bypass those platform rules.
- This work has passed source-level structural checks and standalone LyricsEngine compilation only. Full Android Gradle compilation, GitHub Actions, and physical device testing have not yet been run in this environment.

## Next verification sequence

1. Run `.github/workflows/android.yml` on GitHub Actions and download the `AERIS-V2-debug-apk` artifact.
2. Install on the target Android 15 device; verify first-run Verification, Shizuku optional status, overlay permission, and notification access.
3. Test Spotify: session selection, Previous / Next, play/pause, seek scrubber, volume, album corners, lyrics data availability, and idle mascot after media stops.
4. Test WhatsApp: notification preview privacy, RemoteInput reply, dismiss notification, and simultaneous notifications while music continues.
5. Check portrait / landscape, touch targets, app switching, background service stability, and battery use.


## Ideas retained as a future V2.x backlog (not implemented in this source snapshot)

These ideas were discussed during planning and are recorded so they are not lost. They are deliberately not shown as working controls until their Android APIs, permissions, and device behavior are implemented and tested.

- Smart queue modes / separate activity cards for calls, notifications, and music. Current V2 keeps media controls and the newest notification in one expanded island, plus an eight-item notification queue.
- Clipboard preview and clipboard history. Requires an explicit privacy design and Android clipboard access rules.
- Weather pill and navigation pill. Requires a reliable provider / location or navigation handoff; no location permission is requested in V2.
- Focus mode, translation preview, reminder pill, and app shortcuts. These need their own UI and behavior design.
- Deeper gaming mode, FPS, CPU temperature, thermal state, and advanced memory telemetry. Some metrics are OEM / API restricted and must not be simulated.
- Additional media features such as A/B repeat, sleep timer, playback-speed control, audio-output picker, and queue browsing. These require support from the active player or dedicated Android audio APIs.
