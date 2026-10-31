# Folders, app actions, and appearance — 0.6.0

- Hold and drag an app onto another home or dock app to create a folder. Drop an app onto a folder to add it. Open a folder to launch apps, rename it, or hold and drag an app back out. Entire folders move between home pages and the dock. Empty folders disappear; unavailable profile apps are retained.
- Folders open over the current home screen in a popover above the dock, using the home grid's column count. They start at two icon rows and grow only as far as the source folder's home-screen position, with the two-row minimum taking precedence for folders near the dock. Dock folders use two rows. Overflow apps scroll within the popover.
- Tap outside a folder, press Back or Home, or swipe down with the folder list at its top to dismiss it. Outside taps only dismiss the popover; they do not activate an app or widget behind it. A downward swipe while scrolled through the list scrolls toward the top. A short or cancelled pull leaves the folder open. The current home page remains in place, with the dock visible underneath. TalkBack stays within the open folder until it closes.
- Hold an app in the drawer, then drag below the dock onto **Hide** or **Uninstall**. Hide affects the drawer and search; existing shortcuts stay available. Restore apps in Settings → Hidden apps. Android confirms uninstalling and enforces profile/device restrictions.
- The drawer and home handle omit the old Drawer/All apps headings. Drawer search sits at the bottom and moves above the keyboard.
- Hold the date or empty home space → Launcher settings → **Drawer colour & opacity** / **Dock colour & opacity**. Each control has preset colours, a custom `#RRGGBB` field, an opacity slider, Apply, Cancel and Reset. Drawer text follows the chosen colour's brightness.
- Wallpapers → **Choose a photo** opens Android Files, including image/document providers. The image is decoded at bounded resolution, EXIF orientation is honoured, and a centre-crop preview precedes Apply. A screen-shaped image is installed as the system home wallpaper and retained privately for consistent launcher rendering. External system wallpaper changes take precedence. The image and crop survive launcher restarts without depending on the original file's permission.

Folders, shortcuts and metadata are saved in one committed preference transaction. Existing 0.3.0 app layouts and dock keys load without migration loss. Folder moves do not create nested folders or duplicate app entries in one folder, and cancelled drags keep their source.

## Folder popover validation

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.still.launcher.FolderPopoverIntegrationTest
```

Use the pinned JDK 17, SDK/build tools 35, NDK 27.0.12077973, and an isolated API 35 emulator. The regression checks current-page preservation, visible home/dock, the home column count when the drawer uses a different count, minimum/position-limited/dock heights, outside-tap suppression, Back/Home dismissal, real list scrolling, pull-to-dismiss at the top, short-pull recovery, renaming, cancelled extraction, and dragging an app back onto home. CI retains a screenshot of the open popover for visual review. The notification regression also exercises badges inside an open folder.
