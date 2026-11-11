# Android platform references

- MediaSessionManager active sessions: https://developer.android.com/reference/android/media/session/MediaSessionManager
- RemoteInput notification reply: https://developer.android.com/reference/android/app/RemoteInput
- WindowManager / cross-window blur: https://developer.android.com/reference/android/view/WindowManager and https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- Android 15 screen recording callback: https://developer.android.com/about/versions/15/features and https://developer.android.com/reference/android/view/WindowManager
- Android 15 behavior changes / foreground service starts: https://developer.android.com/about/versions/15/behavior-changes-15

Blur availability is runtime/system-controlled. The Android 15 screen-recording callback reports whether the registering app's own activities are visible in a recording; it is not a universal device-wide recording indicator. Notification quick reply must use the action and PendingIntent provided by the source app.

Lyrics lookup uses LRCLIB only when the user enables the in-app option. The request contains track title, artist and duration. Disable the option to keep lyrics retrieval limited to metadata exposed by the local media session.
