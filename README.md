# Focus Shield

An Android study-blocking app: you pick the apps that break your concentration, set how long
they should stay shut, and the app ejects you back to the home screen if you open one during a
focus session. Written in Kotlin with a Jetpack Compose UI.

Display name is **Focus Shield**; the package and folder keep the original `focussheild`
spelling (`com.example.focussheild`) so the installed app's identity does not change.

## What you can do in it

- **Apps tab** — every launchable app on the device is listed with a switch. Tap the timer on
  one to block it for 15, 30, 60 or 120 minutes.
- **Master switch** (top bar) — starts and stops the blocking service.
- **Profile tab** — focus score, screen time, distraction count, a weekly report, a daily
  focus-goal in hours, and profile fields (name, aim, exam, phone, institute, target period).
- **YouTube content filter** — a separate accessibility-service feature that intercepts
  distracting videos while you watch. It is labelled "AI Filter" in the UI; see
  [Known limitations](#known-limitations) for what it actually is.

## How the blocking works

`FocusService` is a foreground service (`specialUse` type) running a `Handler` loop that polls
every **300 ms** ([FocusService.kt:26](app/src/main/java/com/example/focussheild/FocusService.kt#L26)).
Each tick asks `UsageStatsManager` which package is in the foreground, via `queryEvents` over the
last two seconds with a `queryUsageStats` fallback ([FocusService.kt:58](app/src/main/java/com/example/focussheild/FocusService.kt#L58)).
If that package is in the blocked set and its timer has not expired, the service inflates a
full-screen `TYPE_APPLICATION_OVERLAY` window, vibrates, increments the distraction counter and
calls `forceGoHome()` ([FocusService.kt:76-83](app/src/main/java/com/example/focussheild/FocusService.kt#L76)).

The YouTube filter is a different path. `FocusAccessibilityService` is configured to watch
`com.google.android.youtube` only, for window-state, window-content and view-scroll events, and
recursively reads the on-screen text looking for keyword matches. A distracting match opens
`InterventionActivity`, which imposes a 5-second cooldown before "Continue to App" works.

## Privacy

**The APK declares no `INTERNET` permission**
([AndroidManifest.xml:5-12](app/src/main/AndroidManifest.xml#L5)), so it cannot open a socket,
and there is no HTTP client, analytics or crash-reporting SDK in the dependency list. Every
preference lives in the device-private `SharedPreferences` store `focus_shield_prefs`
([MainViewModel.kt:19](app/src/main/java/com/example/focussheild/MainViewModel.kt#L19)).
Nothing is uploaded and there is no account or backend.

The profile fields — including the phone number — are stored unencrypted in that file. That is
safe for a device-only app, but it is readable by anything with root or a full backup on the
device, and it is why `allowBackup` should stay off if you harden this further.

## Permissions and why each one is needed

| Permission | Used for |
| --- | --- |
| `PACKAGE_USAGE_STATS` | Reading which app is in the foreground — the core mechanism. A special access grant, not a runtime prompt. |
| `SYSTEM_ALERT_WINDOW` | Drawing the full-screen block overlay over the blocked app. |
| `QUERY_ALL_PACKAGES` | Listing every installed app so you can choose which to block. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keeping the polling loop alive while you study. |
| `POST_NOTIFICATIONS` | The persistent "Focus Shield is running" notification. |
| `VIBRATE` | Haptic feedback when a block fires. |
| `USE_FULL_SCREEN_INTENT` | Showing the intervention screen over a locked device. |
| Accessibility service | Reading YouTube's on-screen text for the keyword filter. Optional; the app works without it. |

## Build and run

Requires Android Studio with AGP 9.2.1, Kotlin 2.2.10, Gradle wrapper 9.4.1.
`compileSdk 36.1`, `minSdk 24`, `targetSdk 36`.

```
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

After installing, grant in this order or the blocking silently does nothing:
**Usage access** (Settings → Security → Usage access), **Display over other apps**, then open the
app and flip the master switch. Enable the accessibility service separately only if you want the
YouTube filter.

## Known limitations

These are real, read off the source, and worth knowing before a live demo:

- Blocking does not resume after a reboot. There is no `BOOT_COMPLETED` receiver, so the master
  switch correctly reads **off** and you have to turn it back on. It no longer lies: the switch
  state is derived from a `service_heartbeat` stamp that `FocusService` writes while its polling
  loop is alive, so a system kill or a failed start now shows as off.
- The hourly encouragement overlay fires on **allowed** apps too — that is the design — but it now
  only dismisses itself. It used to call `forceGoHome()` from its own button, which ejected you
  from whatever app you were in, including a study app.
- `POST_NOTIFICATIONS` is declared but **never requested at runtime**, so on Android 13+ the
  foreground-service notification is suppressed.
- The **block is soft**: the overlay has a dismiss button, and the YouTube intervention has a
  5-second cooldown after which "Continue to App" works. It is a friction device, not a lock.
- "AI Filter" is **substring keyword matching** against two hardcoded lists
  ([FocusAccessibilityService.kt:11-19](app/src/main/java/com/example/focussheild/FocusAccessibilityService.kt#L11)).
  No model, no ML Kit, no learning.
- The focus score is a fixed `100 − 5 × distractions` heuristic
  ([MainViewModel.kt:117](app/src/main/java/com/example/focussheild/MainViewModel.kt#L117)), and the
  weekly report keys on a weekday abbreviation over a rolling 7-day window, so days can overwrite
  each other.
- `recordDistraction()` and `AppInfo.blockKeywords` are dead code, and the UI footer says "v1.2"
  while `versionName` is `1.0`.

## Demo

<table><tr>
<td valign="top" width="260"><a href="docs/focus-shield-demo.mp4"><img src="docs/focus-shield-demo.svg" width="240" alt="Play the Focus Shield demo"></a><br><em>~10 MB MP4 — opens in GitHub's player</em></td>
<td valign="top"><b>What the clip shows</b><br>Add one or two lines here describing the order you demonstrated it in — block an app, flip the master switch on, watch the overlay interrupt it, then let the block expire.</td>
</tr></table>
