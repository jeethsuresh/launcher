# Folders, app actions, and appearance — 0.4.0

- Hold and drag an app onto another home or dock app to create a folder. Drop an app onto a folder to add it. Open a folder to launch apps, rename it, or hold and drag an app back out. Entire folders move between home pages and the dock. Empty folders disappear; unavailable profile apps are retained.
- Hold an app in the drawer, then drag below the dock onto **Hide** or **Uninstall**. Hide affects the drawer and search; existing shortcuts stay available. Restore apps in Settings → Hidden apps. Android confirms uninstalling and enforces profile/device restrictions.
- The drawer and home handle omit the old Drawer/All apps headings. Drawer search sits at the bottom and moves above the keyboard.
- Hold the date or empty home space → Launcher settings → **Drawer colour & opacity** / **Dock colour & opacity**. Each control has preset colours, a custom `#RRGGBB` field, an opacity slider, Apply, Cancel and Reset. Drawer text follows the chosen colour's brightness.
- Wallpapers → **Choose a photo** opens Android Files, including image/document providers. The image is decoded at bounded resolution, EXIF orientation is honoured, and a centre-crop preview precedes Apply. A screen-shaped image is installed as the system home wallpaper and retained privately for consistent launcher rendering. External system wallpaper changes take precedence. The image and crop survive launcher restarts without depending on the original file's permission.

Folders, shortcuts and metadata are saved in one committed preference transaction. Existing 0.3.0 app layouts and dock keys load without migration loss. Folder moves do not create nested folders or duplicate app entries in one folder, and cancelled drags keep their source.
