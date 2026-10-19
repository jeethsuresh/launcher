#!/usr/bin/env python3
"""Check the debug launcher's persisted state after UI testing (read-only)."""
import argparse
import json
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='Explicit adb device serial')
parser.add_argument('--adb', default=shutil.which('adb') or os.path.expanduser('~/Library/Android/sdk/platform-tools/adb'))
args = parser.parse_args()
xml = subprocess.check_output([args.adb, '-s', args.serial, 'shell', 'run-as', 'app.still.launcher', 'cat', 'shared_prefs/still_launcher_v1.xml'], text=True, timeout=10)
values = {node.attrib['name']: node.text for node in ET.fromstring(xml) if node.tag == 'string'}
settings = json.loads(values['settings'])
items = json.loads(values['items'])
dock = json.loads(values['dock'])
assert 3 <= settings['columns'] <= 7
assert 4 <= settings['rows'] <= 9
assert len({item['id'] for item in items}) == len(items), 'Duplicate workspace IDs'
assert len(dock) == len(set(dock)) <= settings['dockSlots'], 'Invalid dock'
widgets = [item['widget'] for item in items if item['kind'] == 'WIDGET']
assert len(widgets) == len(set(widgets)) and all(w >= 0 for w in widgets)
occupied = set()
for item in items:
    assert item['page'] >= 0
    assert item['x'] >= 0 and item['y'] >= 0 and item['w'] > 0 and item['h'] > 0
    assert item['x'] + item['w'] <= settings['columns'], item
    assert item['y'] + item['h'] <= settings['rows'], item
    for x in range(item['x'], item['x'] + item['w']):
        for y in range(item['y'], item['y'] + item['h']):
            cell = (item['page'], x, y)
            assert cell not in occupied, f'Overlap at {cell}'
            occupied.add(cell)
print(f"PASS: {len(items)} items, {len(widgets)} widgets, {len(dock)} dock apps; {settings['columns']} x {settings['rows']} grid; no overlaps; valid widget identities.")
for item in items:
    if item['kind'] == 'WIDGET':
        print(f"Widget {item['widget']}: page {item['page'] + 1}, cell {item['x'] + 1},{item['y'] + 1}, size {item['w']} x {item['h']}")
