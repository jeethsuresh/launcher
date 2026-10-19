# Still Launcher

A native Kotlin Android home launcher inspired by the simplicity of Fossify. Android 8.0+; optimized for portrait phones. Original implementation, with no Fossify source or assets copied.

## Download

Get the installable APK from [GitHub Releases](https://github.com/jeethsuresh/launcher/releases). The first release is a debug-signed preview for testing on your phone.

<img src="docs/images/home.png" alt="Still Launcher home screen with a clock widget and app dock" width="320" />

## Build and run

Open this directory in Android Studio, or use a JDK 17+ and Android SDK 35:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.still.launcher/.LauncherActivity
```

On this Mac, Android Studio's bundled Java works:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Open **Still Launcher → settings → Set as default** to choose it as your Home app. You can switch back through Android Settings → Apps → Default apps → Home app. Installing the APK does not automatically change your default.

## What's included

- Alphabetical app drawer, local app-name search, profile-badged icons, package change updates.
- Multiple home pages with page indicators, horizontal swipes, and automatic overflow pages.
- Drag icons and widgets onto vacant cells; drag apps into the dock. Edge-hold during a drag to move between existing pages.
- A configurable 3–7 slot dock. Shrinking the dock moves overflow apps onto the home grid.
- Native third-party widgets, including Android's bind consent and provider configuration screens.
- Widget size controls with a grid preview, collision checks, provider resize directions and minimum/maximum dimensions.
- Home grid: 3–7 columns, 4–9 rows. Drawer: 3–6 columns. Icon size, home labels, layout lock, dark/light/system appearance.
- Three original vector-drawn wallpapers, photo selection, device/live wallpaper support, and a darkness control.
- Local layout/settings persistence. No ads, accounts, analytics, or network permission.

## Controls

- Swipe up, or tap **All apps**, to open the drawer.
- Hold a drawer app to add it to home, pin it to the dock, or open App info.
- Hold a home icon to drag it. Drop onto **Remove** to remove its shortcut, or **App info** for Android's app settings.
- Hold empty home space for widgets, wallpapers, page controls, and arrangement mode.
- Hold a widget → **Resize** for width/height controls. Unavailable resize directions are omitted. Tap **Apply** to commit; occupied cells are rejected.
- In **Arrange home screen** mode, hold a widget to drag it. Tap a home icon for cell/page movement and other options. TalkBack exposes **Item options** actions too.
- An empty home page can be removed by holding its background. At least one page remains.
- Back closes panels/edit mode; Home returns to the launcher and then the first page.

## Structure and future additions

- `LayoutEngine.kt`: pure Kotlin placement, collision checks, and grid reflow. No Android dependencies.
- `LauncherStore.kt`: a versioned, local state boundary for settings, pages, dock order, and typed workspace items.
- `AppCatalog.kt`: profile-aware app discovery, package callbacks, bounded icon cache, background loading.
- `Widgets.kt`: stable AppWidgetHost identity, bind/configure result handling, and cancellation cleanup.
- `WorkspaceView.kt`: cell geometry, placement rendering, and home gestures.
- `Design.kt`: shared styling, original wallpaper drawing, and line icons.
- `LauncherActivity.kt`: home/panel composition and user actions.

A future feed can be a separate panel alongside the drawer and settings, with its own repository. Weather can arrive first as a regular Android widget, then as a dedicated content module. Device-wide search should add search providers beside the catalog, leaving app discovery and workspace geometry intact. There are no fake feed or weather controls in this first version.

## Current scope

This is a functional first version, delivered as a debug-signed APK. It is not a Play Store release. Portrait phone layout and English copy are the initial focus. Folders, notification dots, icon packs, pinned app shortcuts, Android Private Space integration, backup/restore, a news feed, weather services, and device-wide search are not included. Standard accessible profiles use LauncherApps; enterprise and OEM-specific policies still need physical-device testing. Layout backup is disabled because widget IDs cannot safely be restored as ordinary preference values.

See `docs/VALIDATION.md` for the checks run on this build.

## Platform references

- [Android widget hosting contract](https://developer.android.com/develop/ui/views/appwidgets/host)
- [AppWidgetHostView size reporting](https://developer.android.com/reference/android/appwidget/AppWidgetHostView#updateAppWidgetSize(android.os.Bundle,%20java.util.List%3Candroid.util.SizeF%3E))
- [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)
- [Default Home role](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_HOME)
