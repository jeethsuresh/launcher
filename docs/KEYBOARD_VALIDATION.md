# Still Launcher 0.2.0 validation

Checked October 20, 2026. Debug-signed preview, package `app.still.launcher`,
version code 2, Android 8.0+ (minimum API 26), target/compile API 35.

## Build and automated checks

The combined launcher and HeliBoard-based keyboard built successfully with JDK 17+,
Gradle 8.12, AGP 8.10.1, Kotlin 2.2.10, Android SDK 35 and NDK 27.0.12077973.

Passed:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :keyboard:testDebugUnitTest \
  :app:lintDebug :keyboard:lintDebug
```

- 11 unit tests: five workspace layout/collision tests, four gesture-path tests,
  and two emoji-search tests. Zero failures.
- Android lint: zero errors in either module. Missing translations in the vendored
  HeliBoard resources are excluded; Android falls back to English. Other inherited
  lint warnings remain.
- Two emulator integration tests passed. One injects continuous touchscreen paths
  using the actual rendered key geometry and verifies `hello`, `world`, `keyboard`,
  `swipe`, `great`, `phone`, `there`, `thanks`, `tomorrow`, and `happy`.
  The other checks that emoji-search text stays out of the editor and that the
  symbols/alphabet switch still works after search.

Integration tests ran on the arm64 Android 16 / API 36 Medium Phone emulator.
Results await asynchronous input completion instead of assuming a fixed response delay.

## Manual emulator checks

- Typed `Hello` through the real key layout and inserted the bottom-row apostrophe.
- Quick taps on suggested words, the toolbar arrow, toolbar emoji action, and
  emoji-search control did nothing. Holding a suggestion changed `teh` to `the`.
  Holding the left arrow opened Toolbar setup. Holding toolbar actions activated them.
- Opened emoji search by holding its magnifying glass; searched `cat`.
  The editor remained `Hello` until an emoji was selected, then became `Hello🐈`.
- Switched to symbols after searching and inserted a comma: `Hello🐈,`.
  Verified the dark alphabet/symbol layouts and the light layout.
- Opened the bundled source and license notices without network access.
- Added a real Google Clock Digital widget through Android's bind-consent and
  configuration screens. Held and dragged it directly, outside arrangement mode,
  from column 1/row 3 to column 3/row 3. Its ID and 2×2 span stayed unchanged and
  its saved position survived APK updates.
- Dragged that widget onto Options and opened the width/height resize controls.
- Verified widget picker app headings in alphabetical order (Calendar, Chrome,
  Clock, …), with each app's widgets together.
- At the top of the app drawer, a downward pull moved/dismissed the drawer.
  With a longer three-column list, a downward scroll while below the top scrolled
  the list and kept the drawer open; after reaching the top, another pull dismissed it.
- Confirmed the launcher remains functional with Gboard selected, preserving the widget.

## APK inspection

- Includes arm64-v8a, armeabi-v7a, x86, and x86_64 native libraries.
- APK alignment check passed with `zipalign -c -P 16 4`.
- Both packaged English gesture lexicons contain 60,000 frequency-ranked entries.
  Their compressed assets were checked inside the final APK.
- No proprietary Google glide library and no Internet permission.
- Signing certificate matches v0.1.0, so this APK can update that preview in place.
  Certificate SHA-256:
  `508184332d54eadbf5ad558c0b00c8350769cb386130c68eeec4aa8bf47c8b09`.

## Scope

This is an English-focused phone preview. Swipe recognition uses a new open-source
decoder and the bundled HeliBoard frequency lexicons. Ten controlled paths are a
functional regression check; broader natural-gesture and physical-device testing
remains necessary. Additional language dictionaries can be imported for HeliBoard's
typing features. Emoji appearance depends on Android's installed emoji font.

Source provenance and licenses: `THIRD_PARTY.md`. Setup and controls: `docs/KEYBOARD.md`.
