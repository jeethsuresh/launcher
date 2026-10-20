# Hidden apps validation — 0.2.2

October 20, 2026. Android API 36.1 phone emulator; compile/target SDK 35, min SDK 26.

## Behavior

In 0.3.0, hold an app in All apps and drag it onto **Options**, then choose **Hide from All apps**. TalkBack also exposes **App options**. Hidden apps are excluded from both browsing and drawer search. Existing home and dock shortcuts remain available.

In **Launcher settings → Hidden apps**, select the apps to hide and tap **Save**. Uncheck an app to restore it, use **Show all** to clear the hidden list, or **Cancel** to discard checkbox changes. The list follows the catalog's alphabetical order. Choices use the existing activity/profile identities; temporarily unavailable apps retain their choices when saving changes to the available list.

## Checks

- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest`, and `:app:lintDebug` passed.
- Five layout unit tests passed.
- Two `HiddenAppsIntegrationTest` tests passed in 12.384 seconds. They cover hiding through the app menu, search exclusion, recreation persistence, unchanged home/dock data, Cancel, individual restoration, preservation of an unavailable profile entry, hiding all apps, and restoring everything through settings. Native dialog controls receive real touch input.
- Manual long-press hiding of Calendar survived force-stopping and reopening the launcher. Calendar stayed absent from the drawer while Clock remained visible. Restored the test choice afterward through Hidden apps → Show all.
- APK signature verification and 16 KB ZIP alignment passed. Certificate SHA-256 matches earlier releases: `508184332d54eadbf5ad558c0b00c8350769cb386130c68eeec4aa8bf47c8b09`.

## Artifact

- Version name: 0.2.2; version code: 4.
- APK SHA-256: `6a72ec51face269c0120bca2d01462006a84f4a26eebbe9e29873a4aa0ee9041`.
- English portrait phone preview. Physical-device and managed-profile policy testing remains outstanding.
