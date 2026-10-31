# Notification badges

In **Launcher settings → Notification badges**, choose **Open Android settings** and allow **Still Launcher notification badges**. Android controls this optional access; revoke it on the same system screen to turn badges off. The launcher remains usable without it.

A red numbered badge appears at the upper right of app icons on home screens, in the dock, in All apps, and inside open folders. It displays 1–99, then `99+` for larger totals. Closed folders add the counts for contained apps, including apps outside the four-icon preview. Multiple shortcuts for the same package/profile count only once per folder. TalkBack app/folder names announce the full count, including counts above 99. Badges stay within the icon bounds, including small icons and seven-slot docks.

For each eligible active notification, Still uses the app's positive `Notification.number` (set by `Notification.Builder.setNumber()`), or one when that value is missing, zero or negative. Counts are summed per package/profile. These values depend on what the app reports; they are not a universal unread-message count. Replacing a notification replaces its contribution, dismissing one removes its contribution, and dismissing the last eligible notification removes the badge. Opening an app alone does not remove a notification. Android's global and channel badge settings, and ranking restrictions such as Do Not Disturb's badge suppression, are respected. Ongoing notifications and group summaries do not contribute; eligible individual notifications in a group do. Large totals saturate safely rather than overflowing.

Badges match both the package and Android user/profile. Android decides which profile notifications the listener can access; enterprise policy, locked profiles, low-RAM devices, and OEM restrictions can limit delivery. Badges never infer notifications for a different profile. Physical-device enterprise/OEM policy coverage is still required.

Android notification access can expose notification content. Still reads only package/profile identity, notification flags, numeric count metadata, and badge eligibility. It keeps the current counts in memory, without notification text, history, disk storage, or network access. Counts are rebuilt from Android's active notifications on reconnect and cleared on disconnect or access revocation. Badge events redraw numbers and update accessible names without rebuilding the workspace, clearing search, or interrupting drags/widgets.

## Regression checks

Use the pinned production toolchain (JDK 17, Android SDK/build tools 35, NDK 27.0.12077973):

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.still.launcher.NotificationBadgesIntegrationTest
```

The second command requires an isolated API 35 emulator. The test APK supplies its own launchable app and broadcast receiver, so notifications are posted under a real separate app identity. The test grants notification access and posting permission using the emulator shell. It covers app-provided counts, replacement without accumulation, fallback counting, home/dock and closed/open folders, drawer search preservation, multiple notifications and last-notification dismissal, ongoing/group/channel exclusions, the global badge switch, access revocation/reconnection, activity restart, unchanged saved layout, numeral pixel changes, `99+`, small icons in a seven-slot dock, accessible full counts, and the system settings link. The CI emulator runs this regression after the existing keyboard suggestion checks and saves screenshots for visual inspection.

`BadgeTargetsTest` covers reported/fallback counts, replacement/removal snapshots, profile isolation, folder aggregation without duplicate targets, exclusions, invalid counts, integer overflow, empty snapshots, visual capping and accessible labels without Android dependencies.

The emulator saves screenshots in `/sdcard/Pictures/still-launcher-badges` so they survive test-package cleanup. CI requires all three PNG previews before accepting the run and uploads them with the test report.

Android contracts: [notification badges](https://developer.android.com/develop/ui/views/notifications/badges), [notification listener lifecycle](https://developer.android.com/reference/android/service/notification/NotificationListenerService), and [ranking badge eligibility](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking).
