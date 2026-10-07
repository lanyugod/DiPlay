#!/usr/bin/env python3
"""Select the two runtime assets from an explicitly chosen, checksum-verified release APK.

This preserves the source/standalone build split. Outputs stay in an ignored private
directory; the key and certificate contents are never printed or added to Git.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--apk", required=True, type=Path)
parser.add_argument("--sha256", required=True, help="Expected SHA256 from the selected official release")
parser.add_argument("--output", required=True, type=Path)
args = parser.parse_args()
if not re.fullmatch(r"[0-9a-f]{64}", args.sha256):
    parser.error("Expected a lowercase SHA256 digest")
output = args.output.resolve()
private_root = (root / ".private").resolve()
if not output.is_relative_to(private_root):
    parser.error("Runtime output must be inside the repository's ignored .private directory")
actual = hashlib.sha256(args.apk.read_bytes()).hexdigest()
if actual != args.sha256:
    raise SystemExit("Official APK checksum mismatch; no runtime files written")
selected = {}
with zipfile.ZipFile(args.apk) as archive:
    for name in ("identity.pk8", "certificate.p7b"):
        entry = "assets/offline-mfi/" + name
        if archive.namelist().count(entry) != 1:
            raise SystemExit("Official APK runtime input missing or duplicated; no runtime files written")
        info = archive.getinfo(entry)
        if not 0 < info.file_size <= 16 * 1024:
            raise SystemExit("Official APK runtime input size invalid; no runtime files written")
        selected[name] = archive.read(entry)
directory = output / "offline-mfi"
if not directory.resolve().is_relative_to(private_root):
    parser.error("Runtime directory must not resolve outside .private")
directory.mkdir(parents=True, exist_ok=True)
output.chmod(0o700)
directory.chmod(0o700)
for name, data in selected.items():
    path = directory / name
    if path.is_symlink():
        raise SystemExit("Runtime inputs must not be symlinks; no files written")
    if path.exists() and path.read_bytes() != data:
        raise SystemExit("Selected runtime input differs from an existing private file; choose a fresh output directory")
for name, data in selected.items():
    path = directory / name
    path.write_bytes(data)
    path.chmod(0o600)
receipt = dict(source="Official DiPlay release APK selected locally", apk_sha256=actual,
               runtime_files=list(selected), source_tree_contains_identity=False,
               iphone_trust_verified=False)
receipt_path = output.parent / (output.name + ".receipt.json")
if receipt_path.is_symlink():
    raise SystemExit("Receipt must not be a symlink")
receipt_path.write_text(json.dumps(receipt, indent=2) + "\n")
receipt_path.chmod(0o600)
print("Verified official APK and selected two runtime inputs in .private; no credential contents printed.")
