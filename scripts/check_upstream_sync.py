#!/usr/bin/env python3
"""Check the selective upstream checkpoint and the API23 source-build contract."""
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
record = json.loads((root / "docs/upstream-sync.json").read_text())
errors = []

def require(condition, message):
    if not condition:
        errors.append(message)

def source(name):
    return (root / name).read_text()

require(record["schema_version"] == 1, "Unknown sync record schema")
require(record["mode"] == "selective", "This fork tracks selective ports")
require(record["minimum_android_api"] == 23, "H6 API23 baseline changed")
require(record["upstream_tag"] == "v" + record["local_version"].removesuffix("-h6"),
        "Local version and upstream checkpoint differ")
for name in ("upstream_commit", "previous_upstream_commit"):
    require(re.fullmatch(r"[0-9a-f]{40}", record[name]), f"Invalid {name}")
allowed = {"ported", "partial", "equivalent", "skipped", "reviewed_merge"}
commits = record["reviewed_commits"]
require(bool(commits), "No commit review ledger")
require(len({c["commit"] for c in commits}) == len(commits), "Duplicate reviewed commits")
require(record["upstream_commit"] in {c["commit"] for c in commits}, "Checkpoint is not reviewed")
for commit in commits:
    require(re.fullmatch(r"[0-9a-f]{40}", commit["commit"]), "Invalid reviewed commit SHA")
    require(commit["disposition"] in allowed and bool(commit["reason"].strip()),
            f"Missing disposition or reason: {commit['commit']}")

info = source("common/src/main/java/com/shilapi/xcertplay/UpstreamSyncInfo.kt")
for key, value in {"VERSION": record["upstream_tag"][1:], "COMMIT": record["upstream_commit"],
                   "LOCAL_VERSION": record["local_version"]}.items():
    require(f'const val {key} = "{value}"' in info, f"Diagnostic {key} differs from checkpoint")
mobile = source("mobile/build.gradle.kts")
require(f'versionName = "{record["local_version"]}"' in mobile, "APK version differs from checkpoint")
require(f'versionCode = {record["local_version_code"]}' in mobile, "APK versionCode differs from checkpoint")
for name in ("mobile/build.gradle.kts", "common/build.gradle.kts", "shared/build.gradle"):
    require(re.search(r"minSdk\s*=\s*23\b", source(name)), f"{name}: minSdk23 must remain")
require("APP_PLATFORM=android-23" in source("shared/build.gradle"), "NDK API23 platform changed")
require("APP_PLATFORM := android-23" in source("shared/src/main/jni/Application.mk"), "Native API23 platform changed")
require("armeabi-v7a" in source("shared/build.gradle"), "ARM32 ABI missing")
require("checkDependencies = true" in source("scripts/check-android6-newapi.gradle"), "Cross-module NewApi gate missing")
workflow = source(".github/workflows/android.yml")
require("check_upstream_sync.py" in workflow and "check-android6-newapi.gradle" in workflow,
        "CI must run checkpoint and cross-module API checks")
if errors:
    raise SystemExit("Upstream/API23 contract check failed:\n" + "\n".join(errors))
print(f"Selective {record['upstream_tag']} checkpoint verified; {len(commits)} commits reviewed; API23/ARM32 retained.")
