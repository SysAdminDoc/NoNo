# NoNo

[![Version](https://img.shields.io/badge/version-1.5.1-blue.svg)](CHANGELOG.md)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2024%2B-brightgreen.svg)](replica-app/app/build.gradle)

This repository contains two strictly separated deliverables:

- `app-audit/` contains the preserved black-box audit, reconstruction specification, and captured evidence.
- `replica-app/` contains the independent Android implementation named **NoNo**, its tests, validation tooling, and comparison artifacts.

The reconstructed app uses the independent package identity `com.sysadmindoc.nono` (`.debug` for the supplied APK). It does not contain the audited app's source, package identity, branding, proprietary illustrations, signing material, or extracted APK assets.

**NoNo is a local notification rule manager built around redacted metadata.** The listener persists
bounded package, channel, grouping, timestamp, and content-provenance metadata; a pure dry-run
evaluator checks app, text, schedule, and typed notification metadata while content is still in
memory. History keeps the rule ids that matched, and its Activity view can compare current
metadata conditions with a selected record. Notification mutation and action execution remain
intentionally absent. See [`replica-app/README.md`](replica-app/README.md#what-this-build-does-not-do)
for the privacy and capability boundary.

![NoNo rules screen with a wallpaper-matched accent](replica-app/docs/screenshots/nono-rules-dynamic-v1.4.1.png)

![NoNo metadata condition editor](replica-app/docs/screenshots/nono-metadata-filters-v1.4.1.png)

![NoNo capture self-test result](replica-app/docs/screenshots/nono-capture-self-test-v1.4.1.png)

![NoNo rule deletion with an Undo action](replica-app/docs/screenshots/nono-rule-delete-undo-v1.4.1.png)

Version 1.5.1 is a small fix. Type a filter in the action chooser, rotate the phone, and what you
typed is still there; it used to clear itself while the rest of the screen came back. This release
also records the first check of the no-backup boundary against a real device, described in the app
README.

Version 1.5.0 added five things. Explore leads to an Insights screen that counts what History already
holds: the total captured, the apps you hear from most, an hour-of-day histogram, a fortnight's
trend, and a match count per rule, all from stored metadata with nothing new captured to build it.
Rules can back themselves up to a folder you pick, daily or weekly, without the app open; because a
job on a timer has nobody to ask for a passphrase, it encrypts with a key held by the device, so
those files restore there and nowhere else and the encrypted export stays the way to move rules
between phones. NoNo can put your device's screen lock in front of every rule and record, using the
lock you already have rather than one of its own. The widget's number answers a question you choose
and its label says which. And the rules list groups under folder headings once you have filed
anything.

Version 1.4.1 added an optional wallpaper-matched accent on Android 12 and newer. NoNo checks the
derived colour against every surface where it appears and keeps the built-in accent if the
wallpaper colour is not readable. Dark, Light, and System default keep their existing palettes.
Rules can also require a channel pseudonym, importance, category, conversation status, ongoing
status, or group-summary status. These conditions are checked during live capture and explained
against stored records without saving notification content. Unknown category strings are dropped
at capture. Channel filters in a transferred rule stay blocked until a channel is selected again on
the receiving install, because channel pseudonyms are deliberately different on every install.
Settings can also post one temporary notification to prove that Android delivers it to NoNo's
listener. The check never enters History or the ingestion counters. A shareable plain-text report
contains the app version, listener state, counters, and last capture age, with no notification
content or posting-app identifiers.
Deleting one rule or clearing the list now offers Undo. A second deletion restores the first
pending rule before replacing its snackbar, so a quick pair of taps cannot lose both changes.
On Android 17, History also labels a notification that Android cleared with its organizer bundle.
The platform does not identify who dismissed that bundle, so NoNo does not count it as a user
dismissal.
The v1.4.0 source mockups and side-by-side implementation checks remain recorded in
[`design-qa.md`](design-qa.md).

Accessibility is measured rather than asserted. Contrast is computed against the shipped palette on
every surface in both themes, and the screens are composed at twice the system font size inside a
320dp viewport with checks for unreachable content, truncated text, and touch targets under 48dp.
What that covers, and the several things it does not, are set out in
[`replica-app/README.md`](replica-app/README.md#accessibility-what-is-tested-and-what-is-not).

Installing this APK yourself is not affected by the app-store developer-verification requirement
starting 2026-09-30, which applies to participating stores in four countries. The detail, and the
obligation to recheck the dates before any release, is in
[`replica-app/README.md`](replica-app/README.md#android-developer-verification-and-what-it-does-and-does-not-cover).

Start with [`replica-app/README.md`](replica-app/README.md). Build the APK with `replica-app/scripts/build-debug.ps1`; the binary is not tracked in git. The expected SHA-256 digest of the frozen deliverable is recorded in [`replica-app/dist/SHA256SUMS.txt`](replica-app/dist/SHA256SUMS.txt).
