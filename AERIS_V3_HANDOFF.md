# AERIS V3 implementation notes

## UI model

- Idle state is deliberately simple: dark narrow pill and still visualizer, no eyes.
- Media state: album cover, title, artist, progress and available actions. Cover corners morph from circular to rounded-square between compact and expanded modes.
- Lyrics state: independent tall region, with centered current line and previous/next context; missing lyrics render a note glyph.
- Notification state: app icon, package-derived accent tint, readable title/body and an in-memory swipeable history. New notifications request a two-second peek.
- Gesture model: one tap expands/collapses; double tap defaults to play/pause. Both are configurable in app settings.

## Motion

Entrance uses alpha + vertical lift. Window size is interpolated using a decelerating ValueAnimator. Lyrics change uses a low-amplitude alpha transition. Visualizer bars animate only while a media session reports playing.

## Safety / platform boundaries

No universal tap interception. No bypass of secure-window restrictions. No synthetic quick reply. No pretending every player exposes lyrics or artwork. No claims of device-wide screen recording detection.

## Follow-up QA on target phone

- Test Android version / OEM overlay limits, safe area and camera cutout position.
- Test notification grouping, duplicate notification updates and `RemoteInput` actions by app.
- Check TalkBack descriptions, font scaling, landscape and multi-window.
- Verify blur fallback with cross-window blur toggled off in system developer settings.
- Verify foreground-service start restrictions and notification permission flow on Android 13+.
- Run Gradle unit tests/build and physical-device smoke tests before producing a release APK.
