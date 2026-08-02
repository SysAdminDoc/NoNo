# Android Notification Manager clean-room project

This repository contains two strictly separated deliverables:

- `app-audit/` — the preserved black-box audit, reconstruction specification, and captured evidence.
- `replica-app/` — the independent Android implementation named **Signal Rules**, its tests, validation tooling, comparison artifacts, and installable debug APK.

The reconstructed app uses the independent package identity `com.anm.signalrules.reconstruction` (`.debug` for the supplied APK). It does not contain the audited app's source, package identity, branding, proprietary illustrations, signing material, or extracted APK assets.

**Signal Rules reproduces the audited interface; it is not a working notification manager.** There is no rule engine, no filter evaluation, and no action execution — the notification listener records only a package name, a timestamp, and a counter. Controls that cannot function in this build are shown disabled with the reason stated inline. See [`replica-app/README.md`](replica-app/README.md#what-this-build-does-not-do) for the specifics.

Start with [`replica-app/README.md`](replica-app/README.md). The frozen install artifact is [`replica-app/dist/SignalRules-debug.apk`](replica-app/dist/SignalRules-debug.apk), with its SHA-256 digest in [`replica-app/dist/SHA256SUMS.txt`](replica-app/dist/SHA256SUMS.txt).
