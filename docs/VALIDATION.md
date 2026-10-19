# Validation — Still Launcher 0.1.0

Validated on October 19, 2026.

## Build checks

Command: `./gradlew --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

- Debug APK assembled successfully.
- All five JVM layout tests passed, with zero failures or errors.
- Android lint completed with zero errors. Non-blocking warnings remain for the initial portrait orientation, SDK target, English copy, and custom view drawing/tooling conventions.
- No runtime third-party SDKs. The manifest does not request Internet permission.

## Emulator checks

Device: Android 36.1 ARM64 emulator, 1080 × 2400, 420 dpi.

- Registered as a Home app in Android's role chooser, selected as default on the emulator, and returned to using the Home button.
- Loaded installed apps, rendered their real icons, searched for Clock, and opened the actual Clock application.
- Confirmed keyboard search input filters to a single matching app. Android's first-run stylus tutorial initially intercepted test input; it was dismissed before completing this check.
- Dragged the Clock home shortcut from column 1 / row 6 to column 4 / row 5. Saved geometry reflected the move.
- Added the real Google Clock Analog widget through Android's consent screen and the clock app's clock-face configuration screen.
- Resized that widget from 2 × 2 to 3 × 3. A 3 × 4 attempt overlapping shortcuts was rejected and the size dialog stayed open.
- Restarted the launcher and verified the native widget remained bound, with the same widget ID and saved dimensions.
- Reduced the grid from 4 × 6 to 3 × 4. All six workspace item identities remained present across overflow pages.
- Reduced the dock from five slots to three. The two displaced apps moved into the workspace: eight workspace items and three dock apps, with no overlaps.
- Swiped between the resulting home pages.
- Applied the Dune wallpaper through the launcher; the wallpaper panel closed and the home view used the new wallpaper.
- Checked AndroidRuntime logs during these interactions; no launcher crashes were reported.

The saved-state checker in `tools/check_layout.py` verifies real persisted geometry, unique identities, dock capacity, and widget IDs independently of the Kotlin placement algorithm:

```sh
python3 tools/check_layout.py --serial emulator-5554
```

It is read-only and requires a debuggable install. Use an explicit device serial.

## Not yet verified on physical hardware

OEM launchers/settings, enterprise profiles and policies, third-party widgets beyond the tested clock provider, older Android versions, and foldables/tablets still need hardware coverage. Photo decoding includes orientation handling and bounded decoding, but photo providers and live wallpaper providers were not exercised end to end in this emulator run.

The APK is debug-signed for installation and iteration. A public distribution needs a release signing key, broader device testing, and the usual release preparation.
