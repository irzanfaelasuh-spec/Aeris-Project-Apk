# AERIS V3 — feature status and verification

Status labels below distinguish source implementation from device/OS behavior that still needs testing.

| Feature | V3 source state | Caveat / required test |
|---|---|---|
| Four-second V3 splash | Implemented | Check first launch and upgrade launch on device |
| Compact idle island without eyes | Implemented | Verify top offset around the device camera/cutout |
| Music compact / expanded layout | Implemented | Verify Spotify and another media app |
| Album-art-derived lower gradient | Implemented | Artwork center pixel supplies an accent approximation, not full album palette extraction |
| Static bars when paused / no active session | Implemented | Verify the source app publishes a sensible playback state |
| Timeline and media controls | Implemented | Seek / previous / next only when advertised by the player |
| Lyrics panel toggle and fallback note symbol | Implemented | Source must publish lyrics metadata; no remote lyric lookup |
| LRC / enhanced LRC parsing | Retained | Enhanced timestamps required for word karaoke |
| Notification app icon and app-specific accent | Implemented | App icons / package names vary by OEM and installed app |
| Notification card + up to eight recent items | Implemented in memory | Recent snapshots stay in history after shade dismissal; cleared when process dies |
| Swipe / arrow notification history navigation | Implemented in source | Validate gesture feel; an older item's reply may stop working if Android cancels its original PendingIntent |
| RemoteInput quick reply | Implemented | Only compatible free-form text actions work |
| Notification dismiss | Implemented | Dismisses system notification only, never original chat message |
| Auto notification preview | Implemented, 2-second default | Adjustable in Settings; test notification rate and timing |
| Liquid Glass app palette | Implemented | Translucent surface, optical highlights and bubble-like reflections; not a pixel-identical iOS clone |
| Blur-behind request | Implemented on Android 12+ | Requires device support and runtime cross-window blur; fallback otherwise |
| Tap / double tap / long-press shortcuts | Implemented | Test gesture recognition with all controls and screen readers |
| Outside-touch collapse | Implemented using `FLAG_WATCH_OUTSIDE_TOUCH` | OEM / window policy may affect `ACTION_OUTSIDE` delivery |
| Portrait / landscape sizing | Implemented | Needs physical device testing, especially split-screen / keyboard |
| Optional Shizuku | Retained | Not required by core features; permission path needs test |
| Recording detection | Not implemented universally | Android does not provide a reliable global recorder-session feed to ordinary apps |
| Permanent notification history | Not implemented | Intentionally RAM-only for privacy |

## Verification completed in this workspace

- [x] ZIP extracted and source tree inspected.
- [x] V2 workflow filename mismatch identified and corrected (`android.xml` → `android.yml`).
- [x] XML parsing and project structural checks run after V3 updates (see the final CI note in the handoff).
- [ ] Android Gradle compilation.
- [ ] JUnit execution under Gradle.
- [ ] Installation / runtime test on Android 15 device.

## Device test checklist

1. Enable overlay permission and start AERIS. Confirm the compact island is centered and does not cover the camera/cutout or status icons.
2. Leave media stopped: idle capsule must have no eyes and no animated waveform. Start Spotify: artwork/title/artist appear; playing bars animate. Pause: bars freeze.
3. Tap the island. Confirm expansion is smooth and remains open until an outside touch. Test the 1-second idle expansion separately.
4. Open Lyrics. Check unavailable metadata shows the music-note fallback, ordinary LRC follows lines, and enhanced LRC highlights words.
5. Send multiple WhatsApp / Instagram / YouTube notifications. Check icon, title, body, app accent, two-second preview, swipe history, reply, and dismiss.
6. Reply from an older notification in history. Verify the exact original app thread receives it.
7. Enable Liquid Glass. Test Android 12+ blur; toggle airplane mode, battery saver, and any OEM blur setting to verify fallback.
8. Test every gesture shortcut: single tap, double tap, press-and-hold. Make sure controls remain reachable and gestures do not trigger accidentally.
9. Rotate to landscape, open keyboard for reply, and test narrow screen width.
10. Reboot. Confirm auto-start only if the user enabled it and the OEM permits background start.

## Known design choices

- V3 removes the V2 idle-eye mascot completely.
- The compact album image is circular; expanded artwork is rounded-square.
- Notification dismiss does not attempt to delete the source message because Android's notification listener API cannot safely provide that capability.
- The visualizer is a smooth playback-state waveform, not a microphone/audio-capture spectrum analyzer. Live audio amplitude would need a separate audio-capture permission/architecture and is not silently enabled.
