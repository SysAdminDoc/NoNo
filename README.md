# Android Notification Manager clean-room project

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](CHANGELOG.md)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2024%2B-brightgreen.svg)](replica-app/app/build.gradle)

This repository contains two strictly separated deliverables:

- `app-audit/` — the preserved black-box audit, reconstruction specification, and captured evidence.
- `replica-app/` — the independent Android implementation named **Signal Rules**, its tests, validation tooling, and comparison artifacts.

The reconstructed app uses the independent package identity `com.anm.signalrules.reconstruction` (`.debug` for the supplied APK). It does not contain the audited app's source, package identity, branding, proprietary illustrations, signing material, or extracted APK assets.

**Signal Rules reproduces the audited interface; it is not a working notification manager.** There is no rule engine, no filter evaluation, and no action execution — the notification listener records only a package name, a timestamp, and a counter. Controls that cannot function in this build are shown disabled with the reason stated inline. See [`replica-app/README.md`](replica-app/README.md#what-this-build-does-not-do) for the specifics.

Start with [`replica-app/README.md`](replica-app/README.md). Build the APK with `replica-app/scripts/build-debug.ps1`; the binary is not tracked in git. The expected SHA-256 digest of the frozen deliverable is recorded in [`replica-app/dist/SHA256SUMS.txt`](replica-app/dist/SHA256SUMS.txt).
