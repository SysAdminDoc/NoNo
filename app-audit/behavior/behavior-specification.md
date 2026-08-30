# Behavior specification

## Global navigation

`CONFIRMED`: After onboarding, the root shell has four persistent bottom destinations: Rules, History, Explore, Settings. Selecting a destination changes its icon background and label to yellow. Android Back exits modal/menu/search/builder layers in reverse order. Evidence: `010`, `011`, `013`, `016`.

`CONFIRMED`: Full-screen editors such as rule builder, app selector, filter composer, action selector, shortcut editor, and activity detail do not display the bottom navigation until returned to the root shell. Evidence: `025`, `029`, `030`, `032`, `049`, `073`.

## Onboarding

- Default: three yellow capability cards plus Contact support.
- Each completed step becomes a dark row with yellow check.
- Order observed: notifications → background access → notification-listener enablement.
- Returning from Android settings advances to Rules when all requirements are satisfied.
- Denial branches were not selected; behavior is `UNKNOWN`.

Evidence: `002`-`010`.

## Rules

### Empty/populated

- Empty state offers `Create rule`.
- Populated state shows explanatory hero, search, one or more rule cards, and a floating Create rule button.
- Rule switch changes Enabled to Disabled immediately. Disabled card changes to gray and strikes through sentence.
- Tapping the sentence/card reopens the builder.
- More menu: Enable for…, Set priority, Set folder, Rename, Duplicate, Delete.
- Enable for menu values: 10 mins, 30 mins, 1 hours, 6 hours, 8 hours, 12 hours, 1 days, 7 days.
- Set folder and Rename use title/input/Done/Cancel dialogs.

Evidence: `010`, `063`-`070`.

### Rule builder

- Default sentence: “When I get a notification from any app that contains anything + Filter then do nothing”.
- Yellow underlined spans are editable; XML may expose the whole sentence as one node rather than separate controls.
- App selector is a searchable two-column installed-app grid with Pick all apps. Self-app search returned no result.
- Content filter supports operator, phrases, extras, nested groups, row deletion, and Apply filter.
- Operators: contains any of; contains all of; doesn’t contain any of; doesn’t contain all of.
- Extras: Any image, Any phone number, Any emoji, Group conversation, Language, Custom layout, Fixed notification, Media notification, Category, Image of, Text length.
- Action picker requires a selection before save. A tap on Save with no selected action produces red token styling and exact warning.
- Save returns to Rules and initially enables the rule.
- Recent matching notifications appears below builder, with either explanatory no-match copy or history cards.

Evidence: `029`-`062`, `082`.

### Action catalog

Categorized sequence observed:

- Silence actions: Cooldown, Mute.
- Attention actions: Alarm, Pocket check, Remind me, Speak, Unsilence.
- Delay actions: Add snooze button, Batch, Batch every.
- Change actions: Custom alert, Flashlight, Secret, Sticky, Summarize, Add share button.
- Dismiss actions: Dismiss, Keep if, Undo dismiss.
- Automation actions: Open notification, Press button, Reply.
- Copy actions: Copy verification code.
- System actions: Remove from history, Restore after reboot, Set ringer, Trigger MacroDroid, Trigger Tasker.
- Advanced actions: Multi-tool.

Experimental badges were visible for some actions. Exact configuration subflows and execution semantics remain `UNKNOWN`. Evidence: `049`-`058`.

## History

- Header count and “Notifications today” update with records.
- Timeline uses five labels and small horizontal time bins.
- Left segmented control selects All or only rule-triggered history.
- Right segmented control selects Sent at or dismissed.
- Search field activates keyboard; no-result query leaves the list empty without a separate message.
- Tapping a history card opens menu: Restore, Open notification, View activity, Copy, Create rule, Delete.
- View activity has Rules and Changes tabs. Rules shows rule actions; if none, explicit 24-hour retention copy. Changes explains notification updates and shows the record.

Evidence: `013`-`015`, `071`-`076`.

## Explore

- Top contains four article cards; tap hands off to external browser.
- Below is Suggestions: colorful example-rule cards with Add to my rules.
- Add to my rules opens a prepopulated rule builder; it does not save immediately.
- Observed examples include copy verification code + dismiss for Messages, mute while screen on, cooldown, pocket check, secret transaction content, adaptive charging, and face-down flashlight.

Evidence: `011`, `077`-`082`. Article content itself is external/untested.

## Settings

Observed preference sequence:

- Help: Contact support, Guide and FAQs, Open community Reddit, Rules are not triggering?
- Settings: Mute mode; Allow dismissing fixed notifications.
- Mute actions: Mute importance level.
- Unsilence actions: Adjust silent ringer mode for calls.
- History: Notification history; Keep history for.
- Shortcuts: Create shortcut; Clear shortcuts.
- Backup: Import rules; Export rules; Automatic backups.
- Advanced: Privacy mode; Theme; Language; Translate; Hide popups when muting; Open notification settings; Restore batch; batch restore toggle; Android 15+ icon workaround; grouping workaround; Delete all rules; Version 36.0.0.

Default values captured: Mute mode Default; fixed-notification dismissal on; mute importance All important notifications; ringer-call adjustment off; history All notifications; retention 30 days; Privacy mode off; Theme Dark; batch restore off; icon and grouping workarounds on.

Evidence: `016`-`028`.

## Motion, haptics, and loading

`CONFIRMED`: Menus/dialogs appeared promptly after taps and scrolls were visually smooth during manual inspection. No explicit app loading spinner, snackbar, toast, success animation, or haptic event was captured.

`UNKNOWN`: Exact animation curves/durations and haptic patterns. Proposed timing tokens in `design-tokens.json` are recommendations, not observed internal constants.
