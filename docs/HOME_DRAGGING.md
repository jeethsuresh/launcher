# Home dragging and paging — 0.3.0

October 20, 2026. API 36.1 phone emulator; Android 8.0+ app, compile/target SDK 35.

## Changes

Apps and widget rows can be held and dragged from All apps/Widgets onto home. Widget binding and configuration preserve the dropped page, cell, span, and item identity in durable state. Cancellation releases the allocated widget ID.

Holding at a horizontal edge turns to the adjacent page. At an outer edge, insertion preserves every existing item's identity and position relative to its page. Each edge entry turns once, preventing an unmoving pointer from creating endless empty pages.

The workspace tracks horizontal touch movement, displays real neighboring pages with widgets, and animates into place on release. The date and dock stay fixed. Hold the date or empty home space for customization and settings. The persistent settings icon and top STILL branding are removed.

## Verification

- App/debug-test APK builds, five layout unit tests, and app lint passed.
- APK signature and 16 KB ZIP alignment checks passed. Signing certificate matches earlier releases.
- Native touch tests passed drawer app dragging, right page creation, existing-page navigation, left page insertion, preservation of existing items, and the stationary-edge guard.
- Swipe tests passed finger tracking before release, page changes in both directions, canceled gestures, the date header, and long-press settings access.
- The mandatory-configuration test widget passed placement across permission/setup, launcher activity recreation, completion, and cancellation/ID cleanup after its test-only runtime dependency was corrected.
- Hidden-app search/persistence/restoration and wallpaper/photo/external-change tests passed with the new settings gesture.
- A final combined run passed six of seven tests. Its widget test was interrupted by another app's Daybreak installer on the shared emulator; the captured foreground window was an unrelated Install/Cancel dialog. The widget flow had passed in the preceding run. No further shared-emulator reruns were performed. A clean isolated combined run and physical-device testing remain outstanding.

APK SHA-256: `8ff468c17b26f049576b5c55e04ab08c3c3d7866bab8a5197d511f40dc2dd669`.
Version name 0.3.0; version code 5. Debug-signed preview.
