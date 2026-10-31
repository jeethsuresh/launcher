# Notification badges

In **Launcher settings → Notification badges**, choose **Open Android settings** and allow **Still Launcher notification badges**. Android controls this optional access; revoke it on the same system screen to turn dots off. The launcher remains usable without it.

A red dot appears at the upper right of app icons on home screens, in the dock, in All apps, and inside open folders. Closed folders show a dot if any contained app has an eligible notification, including apps outside the four-icon preview. TalkBack app/folder names include “notifications” while a dot is present. Dots stay within the icon bounds, including small icons and seven-slot docks.

Dots represent active notifications rather than unread message counts. Dismissing the last eligible notification removes the dot. Opening an app alone does not remove a notification. Android's global and channel badge settings, and ranking restrictions such as Do Not Disturb's badge suppression, are respected. Ongoing notifications and group summaries do not create dots; eligible individual notifications in a group do.

Badges match both the package and Android user/profile. Android decides which profile notifications the listener can access; enterprise policy, locked profiles, low-RAM devices, and OEM restrictions can limit delivery. Badges never infer notifications for a different profile. Physical-device enterprise/OEM policy coverage is still required.

Android notification access can expose notification content. Still reads only package/profile identity, notification flags, and badge eligibility. It keeps the current set of badged apps in memory, without notification text, history, disk storage, or network access. The set is rebuilt from Android's active notifications on reconnect and cleared on disconnect or access revocation. Badge events redraw icon dots and update accessible names without rebuilding the workspace, clearing search, or interrupting drags/widgets.

## Regression checks

Use the pinned production toolchain (JDK 17, Android SDK/build tools 35, NDK 27.0.12077973):

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.still.launcher.NotificationBadgesIntegrationTest
```

The second command requires an isolated API 35 emulator. The test APK supplies its own launchable app and broadcast receiver, so notifications are posted under a real separate app identity. The test grants notification access and posting permission using the emulator shell. It covers home/dock dots, closed/open folders, drawer search preservation, multiple notifications and last-notification dismissal, ongoing/group/channel exclusions, the global badge switch, access revocation/reconnection, activity restart, unchanged saved layout, rendered dot pixels, and the system settings link. The CI emulator runs this regression after the existing keyboard suggestion checks.

`BadgeTargetsTest` covers profile isolation, aggregation, exclusions, and clearing an empty snapshot without Android dependencies.

Android contracts: [notification badges](https://developer.android.com/develop/ui/views/notifications/badges), [notification listener lifecycle](https://developer.android.com/reference/android/service/notification/NotificationListenerService), and [ranking badge eligibility](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking).
