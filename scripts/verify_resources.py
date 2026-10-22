"""Verify bundled Rime data against the original SDK Git objects."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / "resources/rime-manifest.json").read_text())
errors = []
for entry in manifest["files"]:
    path = root / entry["path"]
    if not path.is_file():
        errors.append(f"Missing runtime asset: {entry['path']}")
        continue
    data = path.read_bytes()
    digest = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
    if digest != entry["git_blob_sha1"]:
        errors.append(f"Runtime asset differs from pinned SDK: {entry['path']}")
for path in (root / "app/src").rglob("AndroidManifest.xml"):
    for permission in ET.parse(path).getroot().findall("uses-permission"):
        if permission.get("{http://schemas.android.com/apk/res/android}name") == "android.permission.RECORD_AUDIO":
            errors.append(f"Recording permission remains: {path}")
# Optionally check that Gradle actually packaged all verified source assets.
for apk_path in sys.argv[1:]:
    with zipfile.ZipFile(apk_path) as apk:
        names = set(apk.namelist())
        for entry in manifest["files"]:
            name = entry["path"].removeprefix("app/src/main/")
            if name not in names:
                errors.append(f"Asset missing from APK: {name}")
            elif apk.read(name) != (root / entry["path"]).read_bytes():
                errors.append(f"APK asset differs: {name}")
        if any("sherpa" in name.lower() for name in names):
            errors.append("Speech recognition resources remain in APK")
if errors:
    raise SystemExit("\n".join(errors))
print(f"Verified {len(manifest['files'])} Rime assets; no recording permission in source manifests.")
