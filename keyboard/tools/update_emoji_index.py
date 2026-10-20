#!/usr/bin/env python3
"""Regenerate the bundled English search index. Maintainer tool; never runs in the APK."""
import json
from pathlib import Path
from urllib.request import urlopen

REVISION = "91c267402229a59e3ef2774544f001bf959e8809"
ROOT = Path(__file__).resolve().parents[1]
BASE = f"https://raw.githubusercontent.com/unicode-org/cldr-json/{REVISION}/"


def main():
    annotations = {}
    for path, field in [
        ("cldr-json/cldr-annotations-full/annotations/en/annotations.json", "annotations"),
        ("cldr-json/cldr-annotations-derived-full/annotationsDerived/en/annotations.json", "annotationsDerived"),
    ]:
        data = json.loads(urlopen(BASE + path, timeout=30).read())
        annotations.update(data[field]["annotations"])
    # Use the exact emoji presentation forms supported by our bundled palette.
    # CLDR also names punctuation and standalone modifiers; those are not emoji results.
    canonical = {key.replace("\ufe0f", ""): value for key, value in annotations.items()}
    entries = []
    seen = set()
    for file in sorted((ROOT / "src/main/assets/emoji").glob("*.txt")):
        if file.name in ("minApi.txt", "EMOTICONS.txt"):
            continue
        for emoji in file.read_text().split():
            if emoji in seen:
                continue
            seen.add(emoji)
            item = canonical.get(emoji.replace("\ufe0f", ""))
            if not item:
                continue
            entries.append({"emoji": emoji, "name": item["tts"][0], "keywords": item.get("default", [])})
    target = ROOT / "src/main/assets/still/emoji-en.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(entries, ensure_ascii=False, separators=(",", ":")) + "\n")
    (ROOT / "LICENSE-Unicode").write_bytes(urlopen(BASE + "LICENSE", timeout=30).read())
    print(f"Wrote {len(entries)} emoji from Unicode CLDR {REVISION}")


if __name__ == "__main__":
    main()
