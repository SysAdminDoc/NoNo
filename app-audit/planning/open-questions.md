# Open questions

## Release-blocking behavior questions

1. `UNKNOWN`: What is the intended selected action for a new rule before the user chooses one—does “do nothing” represent a real action or only a missing-field placeholder?
2. `UNKNOWN`: Exact configuration fields/defaults for every action.
3. `UNKNOWN`: Rule evaluation precedence when multiple enabled rules match.
4. `UNKNOWN`: Set priority values and whether priority changes evaluation order or only display order.
5. `UNKNOWN`: Folder semantics, folder UI, and whether folders affect execution.
6. `UNKNOWN`: Duplicate-rule behavior and naming collisions.
7. `UNKNOWN`: Delete confirmations and undo behavior.
8. `UNKNOWN`: Import/export file schema, versioning, merge/replace behavior, encryption.
9. `UNKNOWN`: Automatic backup schedule and restore flow.
10. `UNKNOWN`: Exact history deletion/restore semantics.
11. `UNKNOWN`: History timeline binning and timezone/day-boundary behavior.
12. `UNKNOWN`: Rule/activity record retention interaction beyond the visible 24-hour activity note.
13. `UNKNOWN`: Network dependencies and offline behavior.
14. `UNKNOWN`: Permission-denied copy, retry controls, and degraded modes.
15. `UNKNOWN`: Notification-listener disconnect/reconnect feedback.

## Visual/accessibility questions

16. `UNKNOWN`: Exact font family, weights, line heights, and license.
17. `UNKNOWN`: Exact corner radii/elevations/motion timing.
18. `UNKNOWN`: Light-theme tokens and follow-system transitions.
19. `UNKNOWN`: TalkBack names/order/announcements for rule tokens and errors.
20. `UNKNOWN`: Layout at large font scale and non-English locales.
21. `UNKNOWN`: Tablet/foldable/multi-window behavior.

## Integration questions

22. `UNKNOWN`: Tasker/MacroDroid variable mapping and error reporting.
23. `UNKNOWN`: Wear/Quick Settings behavior and cross-device state.
24. `UNKNOWN`: Exact Android-version workarounds controlled by the two Advanced toggles.

No open question is answered by guessing; resolve through a separately authorized targeted test pass or product decision.
