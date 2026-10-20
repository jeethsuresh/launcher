# Still Keyboard

Install the combined launcher APK, then open **Launcher settings → Still Keyboard**.
Use **Enable Still Keyboard**, then **Choose Still Keyboard**. Android controls
both consent screens. The launcher works with any other keyboard too.

The keyboard is a system input method: it works in other apps as well as in the
launcher. Its initial focus is English US/UK, with bundled offline dictionaries.

## Layout and gestures

- Rounded light/dark keys with an apostrophe in the usual Android comma position.
  Email and URL fields keep their useful `@` / `/` keys. Commas are on the symbols page.
- Tap `?123` for symbols and numbers; hold ordinary keys for their alternate characters.
- Swipe across letters for words. This uses Still's open-source path decoder,
  HeliBoard's gesture event handling, and the bundled frequency dictionary.
  No separate proprietary library is needed. Recognition quality is an initial
  implementation and needs broader physical-device testing.
- Tap the bottom smiley key for emoji. **Hold** category icons to change groups.
  **Hold the magnifying glass** to search English emoji names and keywords.
  Type the query using the keyboard, then tap an emoji to insert it.
  Hold the close control to leave search. Queries never enter the client editor.

## Toolbar

Everything in the toolbar requires a hold: suggested words, correction choices,
clipboard/emoji controls, and other configured actions. Quick taps do nothing.
Hold a word to select it. Ordinary typing can still auto-correct when you press
space; this is configurable in keyboard settings.

**Hold the left arrow** to open toolbar setup. Select and reorder your pinned
actions there. Remote inline autofill chips are omitted to keep this interaction
consistent; Android's regular autofill UI remains available.

Keyboard settings include layout/height, themes, long-press alternates,
correction/prediction, gesture trail, languages, dictionary imports, clipboard,
and other HeliBoard preferences. Clipboard history is opt-in.

## Development checks

Run `./gradlew :app:testDebugUnitTest :keyboard:testDebugUnitTest :app:lintDebug :keyboard:lintDebug`.

For continuous touch integration tests, select Still Keyboard on an emulator first:

```sh
adb shell ime enable app.still.launcher/helium314.keyboard.latin.LatinIME
adb shell ime set app.still.launcher/helium314.keyboard.latin.LatinIME
adb shell settings put secure show_ime_with_hard_keyboard 1
./gradlew :app:connectedDebugAndroidTest
```

The English swipe lexicons are prepared in advance from the bundled HeliBoard dictionaries, so first use does not need to enumerate native dictionary entries.

The test uses continuous touchscreen events at the rendered key positions, with
a stable pointer/down time. Repeated `adb input motionevent` commands from separate
processes do not faithfully model a continuous finger stroke.

Source origin and licenses are documented in `THIRD_PARTY.md`.
