# Android Notification Manager clean-room project

[![Version](https://img.shields.io/badge/version-1.2.0-blue.svg)](CHANGELOG.md)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2024%2B-brightgreen.svg)](replica-app/app/build.gradle)

This repository contains two strictly separated deliverables:

- `app-audit/` — the preserved black-box audit, reconstruction specification, and captured evidence.
- `replica-app/` — the independent Android implementation named **Signal Rules**, its tests, validation tooling, and comparison artifacts.

The reconstructed app uses the independent package identity `com.anm.signalrules.reconstruction` (`.debug` for the supplied APK). It does not contain the audited app's source, package identity, branding, proprietary illustrations, signing material, or extracted APK assets.

**Signal Rules is a local, metadata-only notification reconstruction.** The listener persists
bounded package, channel, grouping, timestamp, and content-provenance metadata; a pure dry-run
evaluator explains how saved rules would treat a selected history record. Live notification
mutation and action execution remain intentionally absent, and unavailable controls are shown
disabled with their reason inline. See [`replica-app/README.md`](replica-app/README.md#what-this-build-does-not-do)
for the privacy and capability boundary.

Start with [`replica-app/README.md`](replica-app/README.md). Build the APK with `replica-app/scripts/build-debug.ps1`; the binary is not tracked in git. The expected SHA-256 digest of the frozen deliverable is recorded in [`replica-app/dist/SHA256SUMS.txt`](replica-app/dist/SHA256SUMS.txt).
