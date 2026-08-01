# Android Notification Manager clean-room project

This repository contains two strictly separated deliverables:

- `app-audit/` — the preserved black-box audit, reconstruction specification, and captured evidence.
- `replica-app/` — the independent Android implementation named **Signal Rules**, its tests, validation tooling, comparison artifacts, and installable debug APK.

The reconstructed app uses the independent package identity `com.anm.signalrules.reconstruction` (`.debug` for the supplied APK). It does not contain the audited app's source, package identity, branding, proprietary illustrations, signing material, or extracted APK assets.

Start with [`replica-app/README.md`](replica-app/README.md). The frozen install artifact is [`replica-app/dist/SignalRules-debug.apk`](replica-app/dist/SignalRules-debug.apk), with its SHA-256 digest in [`replica-app/dist/SHA256SUMS.txt`](replica-app/dist/SHA256SUMS.txt).
