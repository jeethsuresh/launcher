# Source and license notices

Still Launcher and its integrated keyboard are distributed under GPL-3.0-only.
See `LICENSE`. Existing third-party notices and their applicable licenses are
retained in the vendored source; the GPL does not replace those notices.

## HeliBoard / AOSP keyboard

`keyboard/` derives from [HeliBoard 3.5](https://github.com/HeliBorg/HeliBoard),
tag `v3.5`, commit `d8a5842b7379083f0681484f11b6484919a77eaa`.
Copyright belongs to the HeliBoard, OpenBoard, Android Open Source Project,
FlorisBoard, and other contributors identified in individual files.

Preserved license files:

- `keyboard/LICENSE`: GNU GPL v3.
- `keyboard/LICENSE-Apache-2.0`: Apache License 2.0 for AOSP and other identified code.
- `keyboard/LICENSE-CC-BY-SA-4.0`: upstream HeliBoard icon artwork, retained in keyboard settings.

Modifications made for Still, October 2026:

- Converted the standalone application to a library hosted in the launcher APK;
  preserved the Java/JNI namespace and native typing, correction, and widget-independent IME lifecycle.
- Added launcher keyboard setup, changed keyboard branding/default styling, and
  put an apostrophe beside the space bar on the alphabet layout.
- Added a source-available, offline gesture decoder using the bundled word-frequency
  lexicon and actual keyboard geometry. Proprietary gesture library loading is disabled.
- Made toolbar actions and suggested-word selection require a long press;
  holding the left arrow opens toolbar setup. Emoji category/search controls also
  require a hold. Inline remote autofill chips are disabled; regular Android autofill remains available.
- Added local English emoji search with Unicode CLDR names and keywords.
- Bundled only the upstream US/UK English main dictionaries. Other dictionaries can
  be imported using HeliBoard's settings; imported files retain their own licenses.
- Prevented the upstream boot/update receiver from killing the shared launcher process.
- Updated build dependencies for embedding, enabled flexible native page sizes,
  corrected a translation format string and scoped an internal debug broadcast.
- Retained upstream test sources under `keyboard/upstream-tests` as reference;
  Still's runnable decoder/search tests live under `keyboard/src/test`.

The complete keyboard source, native sources, resources, layout assets, build
configuration, and modifications are included here. Still is an independent fork;
it is not an official HeliBoard or Apple product. It contains no Apple fonts or artwork.

## Unicode emoji search data

Copyright © Unicode, Inc. Used under the Unicode License V3, included in
`keyboard/LICENSE-Unicode`.

`keyboard/src/main/assets/still/emoji-en.json` derives only from Unicode CLDR JSON
English annotations and derived annotations, revision
`91c267402229a59e3ef2774544f001bf959e8809` of
[unicode-org/cldr-json](https://github.com/unicode-org/cldr-json).
The generated index uses the exact emoji forms in HeliBoard's bundled palette.
It contains no Signal emoji annotation data.

English swipe frequency assets are exported from those same pinned US/UK main dictionaries.
The maintainer-only `SwipeLexiconExportTest` documents regeneration; the `.bin`
files contain gzip-compressed TSV (a `.gz` asset name would be rewritten by Android packaging).

Regenerate the emoji index with `python3 keyboard/tools/update_emoji_index.py`.
This is a maintainer tool requiring Internet access; the APK uses the bundled
index and has no Internet permission. Emoji glyphs come from Android's installed font.

The distributed APK also contains these license texts and an attribution notice
under Keyboard setup → Source & licenses.
