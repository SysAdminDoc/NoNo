# Recommended implementation backlog

This is a future rebuild plan only. No implementation has started.

## Phase 0: decisions and legal assets

1. Confirm product scope and acceptable behavior differences.
2. Resolve app name/icon/trademark authorization.
3. Select an authorized rounded font and original icon/illustration family.
4. Resolve open questions marked release-blocking.

## Phase 1: design foundation

5. Encode measured colors, spacing, radii, typography, system bars.
6. Build reusable primary button, bottom nav, segmented control, dialog, menu, card, setting row.
7. Build semantic natural-language sentence renderer with wavy underline and independent token actions.
8. Add portrait/landscape goldens and accessibility baseline.

## Phase 2: model and navigation

9. Define Rule/AppScope/FilterExpression/Action/History/Settings models.
10. Establish route graph matching `flows/navigation-map.mmd`.
11. Add Room/DataStore repositories and draft-state persistence.

## Phase 3: Rules read path

12. Rules empty/populated states, search control, rule card enabled/disabled styling.
13. Rule More menu, safe rename/folder/duration/priority/duplicate/delete confirmation.

## Phase 4: Rule builder

14. App selector and search.
15. Content composer: operators, Phrase, Extras, Group, deletion, Apply.
16. Contextual filters seen in suggestions.
17. Data-driven action catalog and selection.
18. Validation exactness and recent matching notifications preview.

## Phase 5: History

19. Daily count/timeline, empty/populated cards.
20. Search and both segmented filters.
21. History item menu and Rules/Changes activity page.
22. Retention and privacy policies.

## Phase 6: Explore and settings

23. Article cards with original/licensed assets and outbound links.
24. Suggestion cards and suggestion-to-draft flow.
25. Settings hierarchy/dialogs and shortcut editor.
26. Import/export/automatic backup after schema approval.

## Phase 7: capabilities/runtime

27. Three-step onboarding and capability re-checks.
28. NotificationListenerService and foreground channels.
29. Implement actions in increasing risk: Mute/Dismiss → Cooldown/Batch → Remind/Snooze → visual/audio actions → automation/reply/open → external integrations.
30. Add reboot, listener reconnect, duplicate prevention, and failure feedback.

## Phase 8: hardening

31. TalkBack, 200% font, keyboard/focus, contrast, 48 dp touch targets.
32. Locale, light/follow-system theme, tablets/foldables/multi-window.
33. Performance against physical-device budgets.
34. Migration/import/export compatibility and security review.
35. Acceptance-test parity against every `CONFIRMED` screen/flow.
