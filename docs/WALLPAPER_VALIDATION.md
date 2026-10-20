# Wallpaper patch validation — 0.2.1

Date: October 20, 2026. Android API 36.1 phone emulator; compile/target SDK 35, min SDK 26.

## Reproduction and fix

On 0.2.0, selecting **Use device wallpaper** left a flat dark green background. A regression test that set an Android wallpaper and inspected the composed home-screen pixels failed with `Active Android wallpaper is hidden (device)`.

Two layers blocked the wallpaper: the launcher window's opaque background and a locally drawn preset/photo above Android's wallpaper surface. The launcher now has a transparent home-window background and draws only a translucent darkness shade. Android owns the wallpaper image. The keyboard setup screen retains its solid theme.

## Passing checks

- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:keyboard:lintDebug`.
- Five `LayoutEngineTest` unit tests; no failures.
- Two `WallpaperIntegrationTest` tests; no failures, 13.735 seconds. They inspect actual composed screen pixels, exercise device wallpaper updates while another activity is open, verify older preset settings do not mask the system image, apply Dune through the launcher control, import a photo through the activity-result path, and confirm later external changes stay visible after photo import.
- Selected a temporary PNG through the actual Android document picker. Both colors appeared on home and remained after force-stopping and reopening Still. The existing home layout and native clock widget remained present.
- The existing 15% darkness shade remained visible over the selected photo.
- Installed the patch over 0.2.0 without clearing application data.
- APK signature verification and 16 KB ZIP alignment check passed.

## Scope and emulator limitation

The launcher's system-picker action opens Android's handler chooser. This emulator contains an AOSP `com.android.wallpaper` handler that crashes because it lacks permission to access Google's `RecentWallpapersProvider`. This is an exception in the external system app. Still's document-picker photo path, presets, and display of externally changed system wallpapers pass. OEM picker behavior and animated live wallpapers require physical-device testing.

## APK

- Version name: 0.2.1; version code: 3.
- SHA-256: `a63cb74f3d9bf5026dcaff58c2b64e8c3ef259b0475650c1c7881ca63d842c62`.
- Signing certificate SHA-256: `508184332d54eadbf5ad558c0b00c8350769cb386130c68eeec4aa8bf47c8b09`, matching 0.2.0.
