# Errors, loading, empty, and disabled states

## Captured states

| State type | Observable behavior | Evidence |
|---|---|---|
| Rules empty | Hero explains rules; Create rule CTA | `010_home_empty` |
| History empty | 0 count, timeline, filters, explanatory paragraph | `013_history_empty` |
| History search empty/no result | Search + keyboard; list remains empty | `014`, `015` |
| Rule recent matches empty | Exact no-match explanation below divider | `029`, `043`, `062` |
| Rule builder validation | Red token/underline + warning row | `059` |
| Shortcut disabled | Create shortcut disabled because no rule can be selected | `025` |
| Rule disabled | Gray card/switch and sentence strike-through | `064`, `085` |
| History filtered empty | Existing record excluded by rule-triggered or dismissed segment | `075`, `076` |
| Activity empty | “No activity recorded” and 24-hour retention explanation | `073` |
| Settings selection | Radio dialogs with selected rows | `021`-`024`, `027`, `028` |

## Not observed

No native loading spinner/skeleton, offline banner, retry panel, network error, database error, permission-denied inline state, success snackbar/toast, destructive confirmation dialog, or duplicate-action guard was captured.

Do not fabricate these states in a faithful reconstruction. Add them only as recommended defensive behavior and label them as reconstruction improvements.
