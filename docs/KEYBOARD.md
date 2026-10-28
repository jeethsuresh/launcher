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
actions there. Password-manager suggestions appear in a separate row and use the
manager's normal tap and authentication controls.

Keyboard settings include layout/height, themes, long-press alternates,
correction/prediction, gesture trail, languages, dictionary imports, clipboard,
and other HeliBoard preferences. Clipboard history is opt-in.

## Passwords and verification codes

Open **Launcher settings → Still Keyboard → Choose Proton Pass for autofill** and
choose Proton Pass in Android. Enable inline autofill in Proton Pass. On Android
11 or later Still renders the system-provided suggestions; vault unlocking and
which credentials match the app/site remain controlled by Proton Pass and Android.
Older Android versions keep the system popup autofill UI.

Proton Pass supplies Android TOTP codes through its copy-2FA-to-clipboard setting
after login. Still offers a literal 4–8 character numeric/alphanumeric clipboard
code for at most 30 seconds after copying, even with clipboard history disabled.
Clipboard suggestions must be enabled in keyboard settings. Hold a code to insert
it without autocorrection or automatic spaces. Codes and sensitive clips are not
added to clipboard history. Still does not read vault secrets or generate TOTP.

**Enable text-message code suggestions** in the same setup screen to opt into
Android's SMS receive permission. Only new incoming SMS broadcasts are inspected;
Still never queries your inbox. Candidate codes must be standalone or appear in
verification/login/code context. Multipart texts are assembled before extracting
codes. Only up to three codes and their receive times are held in memory, for
30 seconds. No message bodies or senders are saved. Disable the feature to clear
the cache. Some installers/OEMs restrict SMS permission; the setup screen reports
a denied grant and leaves password-manager/clipboard suggestions available. This
SMS path does not receive RCS or other messaging apps' encrypted messages.

Code chips refresh when a message or clipboard update arrives while the keyboard
is open, expire without another keystroke, and recheck freshness before insertion.
They are hidden on the lock screen and during emoji search. Password-manager
suggestions are cleared when the field/keyboard session ends. The dedicated row
is independent of word predictions and the toolbar visibility setting.

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
