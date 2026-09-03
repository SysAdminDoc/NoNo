# Changelog

All notable changes to this project are documented here.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.6.0] (2026-09-03)

### Added

- Declining notification access no longer leaves you stuck on the first screen. "Not now" takes you into the app, where the banner on the Rules tab explains what is missing and how to turn it on when you want to. Nothing is captured until you do, and the screen says so.

### Fixed

- A failed restore no longer claims the record came back. Undoing a deletion could fail in two ways: the notification had been posted again, so the record genuinely was there, or the write itself failed and the record was gone. Both said the record was back on the device, which was the opposite of the truth in the second case.
- A backup that succeeds is reported as one even if the app then cannot write down that it happened. Recording the result could fail on a full disk, which is exactly when a backup matters, and the whole run was then reported as a failure over a file that had been written.

- The History metadata filter dialog scrolls. It grows a row for every app, channel and group in your history, so on a phone that hears from a few chatty apps it used to run off the bottom of the screen with the extra rows and the Cancel button out of reach.
- The "Add a filter" menu offers one entry per destination. "Extra property" and "Filter group" opened the identical screen, so whichever you picked you got the same thing, and the one entry is now named for the screen it opens.
- Picking a backup folder while the schedule is still Off now tells you to choose Daily or Weekly, which is the same nudge you already got for doing it the other way round.
- Every Cancel in a dialog is announced as a button and is at least as tall as the minimum this app holds itself to everywhere else. The one in the smaller dialogs measured about 44 points, and none of the three told a screen reader what they were.
- Searching History behaves like the list it searches. It used to say "No matching notifications" while the search was still running and again when the store could not be read at all, with nothing to retry. It now says which of those is happening, offers a retry when reading fails, shows how many records matched rather than only the page it drew, and lets you load the rest.
- The "Notification capture" row in Settings is a switch. It looked like a row that opens a screen, sat in a list of rows that do, and pausing every capture in the app was one mistaken tap away with nothing said afterwards. It now shows a switch, says whether capture is on or off underneath, announces itself to a screen reader as a switch with its state, and tells you what happened when you change it.
- Settings is honest about the two mute rows. They accepted and remembered a choice with nothing behind it, sitting directly beside a row that said plainly there is no action engine in this build. All three say the same thing now, and the values you had picked are kept. The mute importance list also offers the setting's own starting value, which it did not, so that dialog used to open with nothing selected next to a row showing a value you could not pick again.
- Counts read as English when there is one of something. The widget said "1 notifications", the listener warning said "1 days ago", and the rule import screen offered "3 rule(s), 1 conflict(s)". They all agree with their number now.
- Importing rules says what the import did. Replacing five conflicting rules and adding none used to report "Imported 0 new rule(s)", which describes nothing happening over a change to every rule in the file.
- The widget shows the date beside the last capture time whenever it was not today, so a phone whose listener stopped days ago no longer reads as though it captured something this afternoon. Settings shows the last backup in your phone's own time format instead of always using a 24-hour clock.
- History search treats what you type as text. A search for `100%` used to return every record, because `%` means "anything" to the database underneath, and `_` matched any single character. Searching also used to look inside a stored field describing whether content was available, so typing "not" quietly matched a large part of your history for no visible reason. That field has its own filter and is no longer part of the free-text search; the search box says what it does search.
- Exporting rules or history over a file that already exists now replaces it properly. Saving a smaller export on top of a bigger one could leave the tail of the old file behind, and a rules file in that state fails to import later with nothing to explain why. If the write fails once it has started, the half-written file is deleted; if it fails before that, your existing file is left exactly where it was and untouched. The message says which of the three happened.
- Choosing to keep your existing rules during an import now says how many rules in the file were skipped, the same way replacing them says how many were replaced.
- The status and navigation bars follow the theme you picked. The bar behind the three navigation buttons was painted the dark theme's near-black whatever theme the app was in, so switching to Light left dark buttons on a black strip under a white app. Both bars now take their colour from the same palette as the rest of the screen, in whichever theme, and regardless of what the phone itself is set to.
- A rule written as a regular expression can no longer freeze or crash the phone. Rules are checked the moment a notification arrives, and some patterns take an unreasonable amount of time on long text, which any app is free to send. Each field is now searched only as far as its first few thousand characters, and each rule gets a share of a quarter of a second to finish its patterns. A pattern still working when its share runs out is given up on, and so is one that runs out of stack space, which used to take the whole app down. A slow rule only ever spends its own share, so it cannot quietly stop the rules after it from working. A rule never fires on a pattern that was given up on, and a rule that says "none of these" no longer counts an unfinished pattern as proof the words were absent. The character limit counts what a person can actually see, so an app cannot push a message out of range by padding it with invisible characters.
- That same dialog now offers everything in your history rather than everything in the part of it currently on screen. Filtering to one app and then trying to switch straight to another used to be impossible, because the only app the dialog knew about was the one you had already picked. Anything past the first page of records was never offered either. The apps, channels and groups it finds are listed after the fixed options rather than in among them, so nothing shifts under your finger while the list loads. The content options read as sentences now, in the dialog and on the filter summary, instead of showing the names the database uses.

## [1.5.1] (2026-08-31)

### Fixed

- Typing a filter into the action chooser and then rotating the phone no longer clears what you typed. Everything else on that screen came back, so the search box emptying itself read as the screen resetting on its own.

### Changed

- The promise that nothing derived from your notifications leaves the device through an Android backup has now been checked against a real device rather than only against the code. A backup and restore cycle ran on Android 16 with capture history present. A control file placed where backups do reach came back intact, so the transfer itself worked, while the history database, the store holding your rules, the pseudonym key and the listener's scratch state did not come back at all.

## [1.5.0] (2026-08-31)

### Added

- The README says what NoNo's accessibility testing actually checks: contrast against the shipped colours on every surface in both themes, and the screens composed at twice the system font size in a 320dp viewport with checks for content you can't reach, text that gets cut off, and touch targets under 48dp. It also says what isn't checked, including a full screen-reader traversal, and gives the two commands that reproduce the evidence.
- Rules group under their folder headings once you've filed anything. If you've never used folders, the list looks exactly as it did. Rules keep the order you put them in, folders appear in the order your rules introduce them so filing one thing doesn't rearrange the screen, and anything not filed sits under its own heading at the end.
- NoNo can lock itself. Turn it on and your rules and history need your screen lock after a minute away, and after the app has been closed. It uses the lock you already have rather than one of its own, so there's no new permission and nothing new to remember. A phone with no screen lock set can't turn it on, and one that had it on and then lost its screen lock opens normally instead of locking you out of your own rules. The Quick Settings tile and the widget keep working while it's locked; neither of them shows any content.
- The home-screen widget counts what you choose: everything captured, only what a rule caught, or only what you starred. The label always says which, so the number cannot be read as answering a different question. Group summaries stay out of all three, as they do everywhere else in the app.
- Rules can back themselves up on a schedule. Pick a folder and choose daily or weekly, and a copy is written there without the app being open and across a restart. Five files are kept, older ones go, and nothing else in the folder is touched. Because a job on a timer has nobody to ask for a passphrase, it encrypts with a key held by this device, so those files restore here and nowhere else. Moving rules to another phone is still the encrypted export with a passphrase, and the app says which is which. Notification history is never in a scheduled backup. If access to the folder is withdrawn, Settings says so instead of leaving a schedule that silently does nothing.
- Explore has an Insights screen. It counts what History already holds and shows the total captured, the apps you hear from most, a bar per hour of the day, and one per day for the last two weeks, along with how often each saved rule would have fired. Nothing new is captured to build it. Group summaries stay out of the counts the same way they stay out of every other count in the app, and the screen says so rather than leaving two numbers to contradict each other. With no history yet, it explains that instead of drawing empty charts.

### Changed

- The app now carries four more install-time permissions, all of them from the library that runs the backup schedule: WAKE_LOCK, RECEIVE_BOOT_COMPLETED, ACCESS_NETWORK_STATE and FOREGROUND_SERVICE. None of them sends anything anywhere. The app still has no internet permission, and a test on a real device checks the exact list so nothing can arrive unnoticed.

## [1.4.1] (2026-08-31)

### Added

- Added an optional "Match my wallpaper" theme on Android 12 and newer. It keeps NoNo's tested dark or light surfaces, then uses a wallpaper-derived accent only when that accent clears every text and control contrast check. Older Android versions keep the choices they already had.
- Rules can require a channel pseudonym, importance level, category, conversation status, ongoing status, or group-summary status. Every condition has its own live-capture and dry-run trace, and all selected conditions must match.
- Settings can post one temporary notification and prove that the listener received it within eight seconds. The one matching callback is consumed before History, rules, and ingestion counters. A nearby share action opens Android's share sheet with app version, access and connection state, counters, and last capture age. It includes no notification content or posting-app identifiers.

### Changed

- `POST_NOTIFICATIONS` is now declared for the capture self-test. Android 13 and newer asks for it only after the user starts that check. Normal capture still needs only notification-listener access, and denying the posting permission does not disable normal capture.

- Matching does what you tell it to. A rule can look for a plain phrase or a pattern, in the title, the text, the expanded body or the conversation name separately, with or without case mattering, and across several phrases at once with any, all or none of them. Invisible characters some apps put inside words no longer stop a phrase matching: a rule written by reading the screen works. A pattern that is not valid cannot be saved, so a rule can never sit in the list looking active while testing nothing.
- The match editor has a tester. Type a sample title and body and it says whether the rule would match, which field it found the phrase in, or which phrase it could not find. Nothing typed there is stored. A rule that will not fire is the hardest thing to work out in an app like this, because the notification is gone by the time you look.
- Every rule written before this keeps doing exactly what it did: one phrase, title and text together, case ignored, and "doesn't contain" meaning the phrase is absent.
- A rule can be limited to a schedule: pick the days and the hours, and it is only checked inside that window. A window that runs past midnight belongs to the day it starts on, so weeknights from 22:00 to 07:00 covers Saturday at 01:00 and not Monday at 01:00. Times are read on your phone's clock, so they follow you across time zones and across the two days a year when the clocks change. A notification that arrives outside the window says so on its Activity screen instead of just failing to match.
- Rules has a search that works. It looks at the app, the package behind it, the phrase, the operator, the action, the folder and the priority, ignores case, and opens the exact rule you pick. An open field with nothing typed shows every rule, which reads differently from a search that found none, and closing it puts the full list back in the order it was already in.
- History records when a notification left the shade, and why, when Android says why. Android supplies a reason from version 8 onward and does not always supply one, so a record can say it went without saying why, and it says exactly that rather than guessing. The Dismissed filter works now: it selects what Android reported as you opening or clearing a notification, and nothing that merely happens to be gone. A notification the app posts again clears its own departure. Lockdown is not treated as a removal, because the notification is not gone and the history should not carry a note that your device was locked down.
- Android 17's bundle-dismissal reason is recorded as "Cleared with its bundle." Android does not say who dismissed the bundle, so this reason is not counted as a user dismissal. The same numeric code remains unknown on older Android versions.
- The README says what Android's developer-verification requirement covers and what it does not. Installing this APK yourself is not affected by the 2026-09-30 date, which applies to participating stores in four countries, and the section carries the date those facts were checked so the next person knows when to check again.
- The documentation says what the app actually does. It claimed the history schema was four versions behind where it is, that rule import and export were missing, that pinning a rule to the launcher was not built, and it carried test counts from a run with a fraction of today's tests. The README screenshot was recaptured from the current build.
- Builds no longer accept a shared or remote Gradle cache. The Kotlin version this app is built with has an advisory open against its cache handling and the fix is only in a prerelease, so the cache is switched off until there is a stable version to move to.
- The app tells you what it did again. Around thirty messages, including the confirmation that a rule saved and the warning that one did not, were being built in a way the snackbar could not see, so they never appeared.
- Importing a rule file gives every new rule an id from this device instead of the one the file names. A file could otherwise claim an id that belonged to a rule you had deleted, and an old history record would then say it was caught by the rule that just arrived.
- The Dismiss control on the capture warning is a real button. It sat inside the tappable banner, so a screen reader announced one control, activating it opened notification access instead of dismissing anything, and it was under the minimum touch size.
- Dismissing the counts covers what the banner is showing, including captures counted since the last time the numbers were written down.
- What a rule will do is spelled out in full. At a large font size the line was cut off after "Record the match", which reads as though something else happens.
- Unselected filters, the match-type switch and a focused text field are outlined in a colour you can actually see against the surface behind them.
- The rule builder, history and settings paths are covered by tests that drive the real app state rather than the functions behind it. Several defects fixed in this round were green under the old tests because the arithmetic was right and the wiring was wrong.
- The capture warning banner reports what is happening now. Ingestion counters only ever grew, so a single bad minute months ago kept the warning on screen until you learned to ignore it. There is a Dismiss control on the counts, what you dismissed is remembered across restarts, and a fresh failure brings the banner straight back. The counts are kept rather than erased, so the banner still says what it has seen before.
- Messages that report a database write wait for the write. Starring a record announced success even when the row had already gone, and deleting one said "Record deleted." whether or not anything was removed. Each of those now waits for the database to confirm a row and says plainly when it could not.
- The Notification history setting now offers Metadata only and Off, and both are enforced. The old list included content options this build never performed. Off stops new records being written and leaves everything already stored alone until you delete it.
- Settings has a separate "Keep history for" row. The retention dialog existed but nothing opened it, and the history row showed the retention value while opening the storage chooser.
- The phrase condition offers "contains" and "doesn't contain", and both now do what they say. The four older options ("contains any of" and friends) all ran the same single containment check, and a rule saved with any of them keeps working: it is read back as whichever of the two operators it meant.
- The metadata filter screen is functional. The text tester names every metadata value it cannot check, and a history record's Activity screen compares current conditions with the metadata that was captured. Rules written by store version 4 or earlier keep their free-string extras visible and unsupported, so upgrading cannot silently change whether one matches. Those legacy filters can still be cleared in one tap.
- Group summaries keep their old behavior unless a rule explicitly tests summary state. An opted-in rule can match a summary, while notification and rule-hit counts continue to leave summaries out.
- History reaches everything it kept. The list loaded 100 records and showed that number as though it were a count of notifications, so anything older was invisible and uncounted. There is a Load more button now, the big number counts what your filters actually select, and it names the total it was narrowed from. A Starred filter reaches a kept record however old it is, and each row shows when it arrived instead of a raw timestamp.
- The controls in a history record's menu do what they say. Copy puts the record's metadata on the clipboard. Delete removes it, with an Undo in the snackbar. Restore is shown disabled, because a notification belongs to the app that sent it and nothing can post it again. All three used to just close the menu.
- Creating a launcher shortcut works. Pick any saved rule and NoNo asks your launcher to pin it; tapping it opens that rule. The button used to be permanently disabled, and only ever described the first rule.
- The Explore cards each open their own page, and say so if nothing on the device can open a link. All three pointed at the same generic page before, and a failure was silent.
- The search buttons on Rules and Explore are gone rather than reporting that search does not exist. Rule search comes back when it is built.
- A notification's Activity screen shows what was recorded when it arrived, instead of re-running your current rules against it. A stored record holds no text, so re-running the rules answered a different question and could disagree with the rules that actually matched. Every rule that matched is listed, and one you have since deleted is shown by its id rather than vanishing.
- The app picker shows the apps actually on your phone. It used to be a fixed list of ten, which included NoNo itself even though NoNo ignores its own notifications. It now merges everything with a launcher icon with every app that has posted a notification, so apps with no launcher entry are reachable too, and shows real icons. Two apps calling themselves the same thing are told apart by package name. An app you have since uninstalled stays in the list, marked, so a rule written against it can still be edited. No new permission: this comes from the manifest's `queries` element, not `QUERY_ALL_PACKAGES`.
- The APK in `dist/` is a signed, non-debuggable release. What shipped before was a debug build, signed with a throwaway key, and the checksum described that. Building a release without signing credentials now fails instead of quietly producing an unsigned file.
- Release builds record what produced them. Alongside the two-build reproducibility check, `reproducible-release.ps1` writes the commit and whether the tree was dirty, the machine, the JDK, Gradle, AGP, Kotlin, KSP, build-tools and compile SDK, the dependency-verification state, the exact Gradle command, both unsigned hashes, the signed hash, and the signer's certificate. If the two builds disagree it keeps both APKs for comparison. The README says how to check a downloaded APK against the signer.
- Starring a notification survives the app reposting it. A repost used to delete and rewrite the row, which quietly unstarred it. Updated metadata is written into the existing record instead.
- An app that reposts the same notification over and over, to move a progress bar or a message count, no longer looks like a flood of new arrivals. Identical reposts within two seconds count as one capture, one activity entry, and one widget refresh. Anything that actually changed still gets through.
- Group summaries show up in History again, labelled, instead of being hidden. Each one records the app's group and the group Android imposed, and says which of the two it came from when that can be told apart. Usually it cannot, and it says so rather than guessing. The widget count still leaves summaries out, because a summary is not a notification that arrived, and now it says so.
- A rule can no longer be saved naming an action NoNo does not perform. Twenty-nine device actions were selectable and none of them ran. The only outcome the app produces is recording the match, and that is what a new rule says. A rule that arrives by import keeps whatever it named, shown as never executed, and cannot be saved again as if it were live.
- "Enable for" is gone from the rule menu. There is no scheduler, so a rule was never going to switch itself off, and the confirmation used to say it would. An imported rule carrying an expiry can have it removed.
- Importing a rule file is bounded and happens entirely off the main thread. The file is capped at 5 MB and its contents at 4 MB, 10,000 rules and 4 KB per value, checked against both the size the picker declares and the bytes actually delivered. Encryption settings are validated before any key is derived. A refusal now says which limit it hit, and never quotes the file back at you.
- Exporting history writes every retained record, not the page History happened to be showing. The old export was capped at 100 rows and honoured whatever filters were on screen, with nothing saying so. The confirmation now names the count.
- Cells that a spreadsheet would run as a formula are written as text. Every exported field is neutralized, including category values in historical rows, so one starting with `=`, `+`, `-` or `@` cannot execute in Excel or Sheets. Tabs and line breaks hiding in front of one are covered too, as are the full-width characters.
- Notification keys, channel ids and group keys are stored as per-install pseudonyms. Apps choose those strings themselves and routinely put an address, a phone number or a conversation name in them, which is not metadata. The package name is untouched, so rules still match on it, and repeated posts of one notification still collapse into a single record. Records written by an earlier build are rewritten the first time you open the app or the listener starts.
- A notification that arrives with no title and no text is now recorded as exactly that. The app used to file some of them as redacted by Android, based on an undocumented extra and a list of English phrases. Android publishes nothing that confirms it, so the app no longer claims it. The explainer covers both possibilities and still offers the ADB command.

### Fixed

- Deleting a rule now shows an Undo action and restores the complete saved rule, including its id, enabled state, schedule, filters, and place in the list. Delete all uses one batch undo. If another deletion happens while the first snackbar is open, NoNo restores the first pending change before offering the next undo.
- Notification categories now cross the storage boundary only when they match Android's documented values. Apps can write arbitrary strings into that field, so unknown values are dropped during capture and cleared from older rows when NoNo starts.
- Channel filters no longer appear to survive a rule transfer when their per-install pseudonym cannot. Import clears the unusable value, marks the filter for reselection, warns the user, and keeps the condition from matching until a local channel is chosen.
- A notification listener started by the system, with the app never opened, now reads your saved retention period before it prunes anything. It previously used the 30 day default until you opened the app, so a saved 7 day or Forever choice was ignored in the background.
- Saving a rule starter from Explore no longer overwrites an existing rule. Starters carried the id every new rule got by default, so saving one replaced whatever rule already held it.
- Editing a rule you had switched off no longer switches it back on. Save used to force every rule it wrote to enabled.

## [1.4.0] (2026-08-31)

### Added

- Added full-page visual references for onboarding, all four root tabs, and every rule editing flow.
- Added side-by-side design QA evidence for twelve pages, plus light theme and large text captures.

### Changed

- Reworked the app around an AMOLED black, graphite, and citron visual system with tighter spacing, low corner radii, clearer hierarchy, and consistent selected states.
- Rebuilt onboarding, Rules, History, Explore, Settings, the rule builder, app selection, phrase matching, extra filters, action selection, activity details, and shortcut previews.
- Added realistic deterministic design states so every page can be reproduced and checked without changing live user data.

### Fixed

- Fixed a shortcut preview crash caused by loading the adaptive launcher icon through the drawable painter.
- Removed transient listener warnings from deterministic captures and kept audit data stable while a page is open.

## [1.3.3] (2026-08-29)

### Changed

- Renamed the pre-release app from Signal Rules to **NoNo** and moved it to the new `com.sysadmindoc.nono` application ID. No migration layer is included because this identity has not shipped publicly.
- Updated the visible copy, local storage names, export names, build scripts, documentation, and frozen APK name to match NoNo.
- Removed the stale GitHub Actions workflow. Builds and validation remain local.

## [1.3.2] (2026-08-29)

### Changed

- New app icon: adaptive, themed (monochrome) and legacy variants regenerated from the 2026-08 icon set.

- Moved the build to Gradle 9.7.1, Android Gradle Plugin 9.3.2, Kotlin 2.4.10, KSP 2.3.11,
  Compose BOM 2026.08.00, kotlinx.serialization 1.11.0 and Lifecycle 2.11.0. Gradle 8.11.1 predated
  the fixes for two 2026 repository-fallback advisories, which was the reason to move. AGP 9 brings
  its own Kotlin, so the separate Kotlin plugin is gone, and Compose 1.12 requires compiling against
  API 37; the app still targets API 36. Dependency verification covers all 1,471 resolved artifacts.

- Pinned the Gradle daemon to Java 21 through `gradle/gradle-daemon-jvm.properties`, so the build
  no longer depends on what `JAVA_HOME` happens to point at. Handing Gradle the Android Studio JBR
  used to fail while creating tasks with a message that named neither Java nor the JDK. The build
  policy check now fails if that pin is removed, and the PowerShell scripts pick a supported JDK
  instead of trusting `JAVA_HOME`.

### Documentation

- Corrected the capability boundary in the README: dark, light, and system themes all ship and the
  choice is persisted. Only the Language row stays unavailable.
- Rewrote `docs/architecture.md`, which still described a prototype that stored a package name, a
  timestamp, and a counter for a single rule. It now covers the capture pipeline, Room and its
  migrations, the shared database instance, the pure evaluator, encrypted transfer, the Quick
  Settings tile, and the widget.
- `build-debug.ps1` now freezes the APK as `dist/SignalRules-v<version>.apk` and clears earlier
  artifacts first, so the directory holds one binary and `SHA256SUMS.txt` names the version it
  describes.

### Added

- History metadata can be exported as CSV through Android's storage access, under Settings, Backup.
  The file carries exactly the columns the database holds, so no notification content is written,
  and every field is quoted because notification keys and group keys routinely contain commas.

- A history record can be kept past the retention period. Starring one exempts it from pruning
  until you unstar it, so a record worth holding on to does not need retention widened for
  everything else.

- A history record can open the app it came from. This uses the app's own launcher entry, not the
  notification's action, which this build neither stores nor fires. Package visibility is declared
  narrowly, for launchable activities only, rather than by asking to see every installed package.

- History now records channel importance, conversation state, an allowlisted notification category,
  and whether the notification is ongoing. Apps author the category string, so only Android's
  documented values are kept. Together these fields distinguish a silent promotion from a priority
  conversation. History can filter on importance and conversations, and each row shows the values
  it has.

- A warning when the listener has gone quiet. Access granted, the service reporting itself
  connected, and nothing captured for twelve hours is the shape an OEM battery manager leaves
  behind, and it used to look identical to a quiet day. The last capture time is now stored on
  disk, so the warning survives restarting the app or the phone.
- Per-manufacturer steps for keeping the listener bound, under "Rules are not triggering?" in
  Settings. Samsung, Xiaomi, Huawei, OnePlus, Oppo and Vivo get their own wording, everything else
  gets a generic list, and Android 13 and newer add the restricted-settings permission step.

- History now records which saved rules matched a notification when it arrived, so the
  "Rule-triggered" filter returns real records, each row names the rules that would have matched,
  and every rule reports how many recent notifications it would have caught. Only rule ids are
  stored: the notification's own text is evaluated while it is in memory and never persisted, and
  nothing is executed. A notification the system redacted is recorded as such rather than looking
  like an ordinary miss.

- An explainer for notifications the system redacted. Android 15 and newer hide the text of
  anything that looks like a sign-in code from every app that reads notifications, and the resulting
  history rows read as a bug in whichever app you are using. History records the system hid now
  offer "Why is content hidden?", matched by a row in Settings, covering what still matches, the
  Enhanced notifications switch, and the ADB command that grants the permission where that switch
  is missing. The command can be copied to the clipboard.

### Security

- Rule export moved to format 2, which records its own key-derivation parameters and raises PBKDF2
  from 120,000 iterations to 600,000, the OWASP floor for HMAC-SHA256. The parameters are
  authenticated alongside the ciphertext, an import refuses a file asking for an unreasonable
  derivation cost, and files written by earlier builds still import.

### Fixed

- The Dismissed history filter is shown as unavailable with its reason, the way every other
  unavailable control in the app is, instead of accepting a tap and always returning nothing.
  Rule-triggered became a working filter in the same release.

- History retention honours every period the dialog offers. "7 days" and "Forever" were selectable
  and remembered, but neither was implemented, so both silently pruned at thirty days. Choosing
  "Forever" now keeps everything, and the dialog is built from the periods the code can actually
  apply, so the two cannot drift apart again.

- A rule that tests no phrase now matches a notification carrying no title or text. Custom layouts,
  foreground-service notifications and summaries routinely carry neither, and an app-only rule was
  being refused for missing content it never asked for. Content the system redacted is still
  refused, because the hidden text might have matched either way.
- The stale-listener warning clears as soon as a notification arrives, instead of insisting the
  listener is dead until the screen is left and reopened.
- Each rule's match count is taken from all stored history rather than from whatever the History
  tab happens to be filtered to, which could report an active rule as idle.
- The last-capture time is written at most once a minute instead of on every notification, so a
  burst no longer means one full preferences rewrite per notification on the callback thread.
- A settings-recovery notice is shown once rather than repeating for every screen that opens
  afterwards in the same process.
- Captures that arrive before the saved rules have been read are recorded as such, so they cannot
  be misread as "your rules were checked and none matched".

- Repaired the PowerShell helpers on Windows PowerShell 5.1, the shell the README asks for. Reading
  a JDK version through a redirected `java -version` turned its banner into a terminating error, so
  every build, test, lint and validation script failed with a message that named no cause.
- A second loss of notification access is announced again even when the listener never managed to
  bind between the two, instead of the first notice spending the flag for the life of the process.
- The rule file's legacy format no longer accepts a key-derivation cost of its own choosing, which
  a file could otherwise use to make an import take tens of seconds with no way to cancel it.

- Stopped counting Android 16's own group summaries as notifications. The platform groups an app's
  notifications itself and posts a summary beside the children, so the widget count, the widget's
  last-capture time, and the history list all included a row that carried nothing of its own.
  Summaries are still reachable through the existing metadata filter.
- Asked the platform to rebind a listener that has granted access but has never called back, rather
  than treating that state as healthy. Nothing else moved the state out of unknown, so capture could
  stay dead while the app reported itself fine.
- Kept announcing a genuine revocation when the platform had already unbound the listener first.
  Revocation is now tracked separately from the connection state instead of being inferred from it.

- Raised kotlinx.serialization from 1.7.3 to 1.8.1. Room 2.8.4 asks for 1.8.1 and the older pin
  won the conflict, so the schema parser inside Room's migration test helper hit an
  AbstractMethodError on device.
- Made the instrumented test suite runnable for the first time. Dexing rejected every Kotlin test
  name containing spaces at this minimum SDK, so the whole androidTest source set failed to build,
  and the exported Room schemas were never packaged into the test APK. Nine instrumented tests now
  execute on a device.
- Gave the listener, the UI, and the widget one shared database handle. Room only notifies
  observers registered on the instance that performed a write, so history captured by the listener
  never reached the screen watching it. The widget also opened and closed its own database on every
  broadcast, which the listener sends after each captured notification.

- Stopped a resume from reporting a working notification listener as disconnected. The healthy
  case (access granted, listener connected) fell through to the revoked branch, so every return
  to the app published a disconnected listener, emitted an access-revoked event, and raised the
  health banner over a listener that was fine. The decision is now a pure function covered by a
  table test, and losing access announces itself once instead of on every resume.

## [1.3.1] (2026-08-02)

### Added

- Applied the persisted Theme setting to dark, light, and system-default palettes, with accessible
  preference controls, selectable dialogs, and minimum touch targets across shared UI components.
- Hardened validation provenance with a build-manifest hash check, explicit capture/comparison
  failure classes, fail-fast full validation, and Gaussian-windowed structural-similarity metrics.
- Made corrupted preference recovery deterministic on Windows and OEM filesystems by removing the
  unreadable payload before DataStore rewrites recovered defaults.
- Added Android 15 sensitive-notification provenance, preventing system redaction placeholders
  from entering future rule matching and marking history as content hidden by the system.
- Added bounded listener ingestion, Room-backed metadata history, transactional retention pruning,
  drop/failure diagnostics, and 30-day/3-month/6-month boundary tests. Companion-device listener
  exemptions are not implemented in this reconstruction.
- Added a reproducible-release gate that builds two clean checkouts with a pinned JDK, honors
  `SOURCE_DATE_EPOCH`, and compares the unsigned release APK hashes outside the repository.
- Added a pure dry-run rule evaluator with redaction-aware condition traces, deterministic
  specificity and priority conflict resolution, and an explicit not-executed action result.
- The history “Create rule” action now selects the tapped record, pre-fills app and safely
  derivable phrase fields, and explains when redaction or metadata-only storage prevents copying
  notification content.
- Corrected the DataStore test fixture to start with an absent backing file, matching DataStore's
  create-on-first-write contract and removing false corruption/race failures from the local suite.
- Preserved Android notification group keys and summary provenance in metadata history, added a
  Room migration, and made group summaries ineligible for future duplicate rule evaluation.
- Made portable transfer encoding API-24-safe by using Kotlin's platform-independent Base64
  implementation instead of the API-26-only Java encoder.
- Rules now persist stable Android package IDs separately from display labels, migrate known
  legacy app selections, and pass package identity through the dry-run matcher.
- Restricted notification-listener binding to the system, declared explicit listener filter
  defaults, removed unused notification/Doze permissions, and clarified the local redaction-only
  capability during onboarding.
- History search and retention now run through bounded Room queries with metadata selectors,
  explicit loading/empty/error/retry states, and immediate pruning when the retention setting
  changes. The metadata-only build no longer claims to show only “today” or invent rule-action
  history states.
- Notification activity now shows a pure dry-run explanation for selected metadata records,
  including content provenance, unmet conditions, conflict winners, priority overrides, and an
  explicit `NOT_EXECUTED` result.
- Listener queue counters and failure timestamps now persist as redacted Room diagnostics and
  restore into the health surface after restart. The warning banner requests a safe rebind while
  directing the user to notification-access settings for recovery.
- Listener shutdown now fences new callbacks, drains the bounded worker before closing Room, and
  makes teardown idempotent. Rebind requests are limited to the platform’s disconnected window.
- History metadata now includes nullable channel IDs and supports package, channel, group,
  content-provenance, and group-summary filters through bounded Room queries and migrated schema.
- Added a deterministic build-policy task and CI gates for pinned repositories, wrapper/catalog
  versions, dependency hash coverage, strict verification, and high-severity dependency advisories.
- Reconciled the root and app READMEs with the metadata-only Room runtime, redaction-aware dry-run
  evaluator, schema versions, bounded history filters, and intentionally disabled live actions.
- Added API 24/35/36 redaction fixtures covering available, unavailable, explicit-sensitive,
  marker, package-identity, and metadata-only dry-run behavior without payload logging.
- Added checked-in Room schema fixtures for versions 1-4, an instrumented all-version migration
  test, and v1-v3 RuleCodec golden fixtures covering normalization and unsupported versions.
- Connected encrypted rule transfer to Android's Storage Access Framework with passphrase prompts,
  import preview, keep-or-replace conflict resolution, cancellation/error recovery, and explicit
  exclusion of notification history.
- Added a Quick Settings tile and in-app status for pausing metadata capture without revoking
  listener access; the persisted gate ignores callbacks before sanitization and restores on restart.
- Added an adaptive home-screen metadata widget showing only bounded count, timestamp,
  content-provenance, or paused state; listener writes request bounded widget refreshes.

## [1.1.0] (2026-08-01)

### Fixed

- `check-environment.ps1` asserts instead of narrating. It printed device properties and always
  reported success, so a mismatched emulator silently invalidated every comparison that
  followed. It now fails when API level, resolution, density, locale, or font scale differ from
  the reference device, with `-AllowMismatch` to downgrade that to a warning.
- The similarity threshold is no longer defaulted in two places to a value that disagreed with
  the authoritative one. `compare_images.py` and `compare-screen.ps1` defaulted to 0.90 while
  `validation/screen-validation-matrix.csv` specifies 0.85; both now require the caller to pass
  it.
- Screenshot comparison resolves Python through the `py` launcher before falling back to
  `python.exe`, and `scripts/requirements.txt` pins Pillow and NumPy.
- Removed `test-fixtures/` and `test-states/`, byte-identical duplicates of `test-data/`, and
  repointed the state map's `fixture_source` column at the surviving copy.
- Replaced the stale `Z:\` build path and the retired `D:	ools\jdk21` reference in
  `replica-app/README.md`.
- Cleared the low-severity correctness defects: the Explore article accent no longer indexes a
  fixed four-element list by article index (a fifth article threw
  `IndexOutOfBoundsException`), back and volume icons use their `AutoMirrored` variants for RTL,
  the deprecated `Divider` is now `HorizontalDivider`, the deprecated
  `SOFT_INPUT_ADJUST_RESIZE` call was dropped since it is ignored under the edge-to-edge
  enforcement targetSdk 36 makes mandatory, and the empty-state copy shown from the non-empty
  rules branch was corrected.
- Keyboard focus is requested once after a frame instead of three blind retries.
  `FocusRequester.requestFocus()` throws when its node is not attached, which the retry loop
  invited by firing before a Dialog's subcomposition existed. The four duplicated loops are now
  one guarded helper. The debug and release builds compile with zero deprecation warnings.
- The rule dialogs no longer discard the user's choice. Match type, extra properties, filter
  operator, "Enable for", priority, and folder all dismissed without applying anything, and the
  folder dialog wrote into the rename dialog's field before throwing it away. Each selection is
  now applied to the addressed rule or draft, persisted, and reflected in the rule builder and
  on the rule card. Evaluation semantics are still absent. The audit records rule precedence
  and folder behaviour as UNKNOWN, so these are stored and displayed, not acted on.
- The rule-builder overflow menu addresses the rule being edited rather than whichever rule
  happened to be first.
- Bottom-anchored controls on full-screen editors no longer render underneath the navigation
  bar. Only the root route has a bottom bar, and only that bar applied the navigation-bar
  inset, so "Pick all apps" and "Apply filter" sat under the system navigation.
- The history list honours its search field and segmented filter. Both were wired to state
  that the list never read, so typing a query or switching to Rule-triggered changed nothing.
- The rule card renders each rule's own action instead of a hardcoded mute glyph, and uses the
  unit-tested sentence renderer rather than a second, divergent copy of the same string.
- The notification listener now recovers from being unbound. It had no
  `onListenerConnected`/`onListenerDisconnected` overrides and never called `requestRebind`,
  so a routine platform unbind, such as an app update or OEM background kill,
  silently ended all functionality until the user toggled notification access by hand.
  Rebind is requested on disconnect and on every app resume.
- Notification access is re-checked on every resume, not only during onboarding. Access
  revoked after setup was previously invisible, leaving the app presenting a working rule list
  while the listener was dead.
- The listener no longer performs disk I/O on the main thread. `onNotificationPosted` ran two
  `getSharedPreferences` calls and a read-modify-write for every notification from every app on
  the device, into a file nothing read.
- All notification-listener settings intents are guarded and route through one helper, which
  prefers the per-app `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` screen on API 30+ and
  falls back to the global list. Two call sites previously launched an unguarded intent that
  throws `ActivityNotFoundException` on images lacking the activity or inside a work profile.
- The Settings screen no longer advertises behaviour the build does not have. Import, Export,
  Automatic backups, Clear shortcuts, Restore batch, Translate, Contact support, Open
  community, Theme, Language, and every switch that would depend on the absent action engine
  are shown disabled with the reason stated inline. Import/Export previously opened a folder
  picker whose result was discarded, and several rows only raised a toast.
- `Delete all rules` is implemented instead of showing a disabled toast.
- Notification-derived data is excluded from automatic backup and device transfer. The app
  declared `allowBackup="true"` with neither `dataExtractionRules` nor `fullBackupContent`, so
  its preference store was eligible for upload to the user's Google Drive. Both rule sets are
  now declared, and the store itself moved under `noBackupFilesDir` so the exclusion does not
  depend on backup rules that older platforms and OEM agents honour inconsistently. An
  existing store is migrated across on first run.
- Notification history search and the phrase/extras/group condition selector work in release
  builds. Both were gated on audit-capture id strings that only the debug-only QA override
  could set, so the search icon and the condition chooser were inert in a shipping build
  while the UI still offered them. They are now driven by real UI state
  (`historySearchActive`, `phraseInputVisible`), and rule-builder validation is carried by a
  `validationError` field instead of being inferred from a capture id and a snackbar message.
- Rules are addressed by id and persisted as a list. Saving replaced the entire collection
  with the single edited rule, and toggle/rename/delete all operated on `rules.first()`
  regardless of which card was touched, so duplicating a rule and then toggling the copy
  silently destroyed the original. Every mutation now targets one `SignalRule.id`, and the
  store holds the whole list under a versioned payload rather than six flat scalar keys.
  Rules written by the previous build are migrated on first read.
- Preference storage now survives a corrupt or unreadable backing file. `MainViewModel`
  built its DataStore with no `corruptionHandler` and read it with a bare
  `viewModelScope.launch`, so a truncated `signal_rules.preferences_pb` threw
  `CorruptionException` on every launch and, because the bad file persisted, bricked the
  app permanently. Reads now fall back to defaults, the store is rebuilt via
  `ReplaceFileCorruptionHandler`, the user is told their settings were reset, and every
  write is guarded so an IO failure costs one unsaved change instead of the process.
- Traceability status is now derived from recorded machine-readable evidence instead of
  being asserted. `scripts/finalize-documentation.ps1` previously wrote `test_status = PASS`
  unconditionally for every native row, so `docs/audit-traceability-matrix.csv` certified
  itself. Status is now computed from the per-screen visual results plus durable suite
  summaries emitted by the test runners; missing or failing evidence yields `NOT_RUN` or
  `FAIL`. Regenerating with no test artifacts present now produces zero `PASS` values.
  As a direct consequence the 76 native rows currently read `NOT_RUN`: the instrumented
  suite has not been executed against the reference device in this checkout, and the
  matrix no longer claims otherwise.
- `scripts/finalize-documentation.ps1` no longer crashes on screen ids whose numeric
  prefix is not exactly three digits, and validates its required inputs before writing.

### Changed

- `targetSdk` moved from 35 to 36. System bar colour attributes were removed from the theme
  (no-ops from API 35; `enableEdgeToEdge` owns them), predictive back is declared explicitly,
  and the fixed-orientation lock was dropped since API 36 ignores it on displays 600dp and
  wider and the opt-out property stops working at API 37. `androidx.activity:activity-compose`
  moved 1.9.3 to 1.13.0, which was roughly two years of skew against the Compose BOM in use.
- The 19.7 MB debug APK is no longer tracked in git. `dist/SHA256SUMS.txt` stays tracked so a
  downloaded artifact can still be verified.

- The audit-state capture tooling moved into variant source sets. `app/src/debug` holds the
  state table and the intent-extra reader; `app/src/release` links a no-op twin. The release
  DEX no longer contains the `replica_state` extra or any capture id, so production behaviour
  cannot depend on QA scaffolding.

### Added

- MIT `LICENSE` at the repository root.

- A listener health banner on the Rules screen states when rules are not running, why, and how
  long since the last notification was seen, and links straight to notification access. It is
  announced as a polite live region.

- `scripts/run-unit-tests.ps1` and `scripts/run-ui-tests.ps1` parse their JUnit XML output
  into `validation/reports/unit-test-results.json` and
  `validation/reports/instrumentation-test-results.json`, and fail when the recorded
  result is not a pass. These summaries are the evidence the traceability matrix reads.
- `Get-JUnitSummary`, `Save-TestSummary`, and `Get-TestSummaryStatus` helpers in
  `scripts/Common.ps1`. The runners force task execution and refuse to record evidence when
  Gradle reports success without producing results, so an UP-TO-DATE task cannot be mistaken
  for a suite that ran.
- `ListenerHealthTest` covers the published connection state, including that it starts
  `UNKNOWN` rather than claiming to be connected.
- `AuditStatesTest` pins the debug capture setup, including that 033 resolves to the
  condition chooser and 034/041 to the text input, so the source-set split cannot silently
  break state reproduction.
- `model/RuleOperations.kt` holds the rule-list algebra as pure functions, and
  `data/RuleCodec.kt` encodes the versioned store; a payload from a newer build, malformed
  JSON, or duplicate ids all degrade to a safe fallback instead of throwing.
  `RuleOperationsTest` and `RuleCodecTest` cover both, including the duplicate-then-toggle
  sequence that used to lose data.
- Rule priority and folder selections are now applied to the addressed rule instead of
  being discarded when the dialog closes.
- `data/SignalPreferences.kt` centralises preference-store construction, and
  `SignalPreferencesTest` covers healthy round-trip, recovery from a deliberately corrupted
  file, writability after recovery, and the view model's read guard.

## Roadmap archive (2026-08-10): ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# Roadmap: ANM / Signal Rules

Only incomplete work is listed here. This file was normalized on 2026-08-02: completed entries were removed, residual acceptance gaps were retained under their original IDs, and new research-driven items continue at R-042. Every item is traceable to `RESEARCH.md` and the cited repository evidence.

## Research-Driven Additions

### P0

### P1

### P2
```

</details>
