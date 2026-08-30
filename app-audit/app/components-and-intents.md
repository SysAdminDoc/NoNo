# Components, intents, and external integrations

## Observable navigation integrations

| Integration | Observable behavior | Classification | Evidence |
|---|---|---|---|
| Android notification permission | Runtime permission dialog | CONFIRMED | `003_notification_permission_dialog` |
| Background access | Android device-idle/background confirmation | CONFIRMED | `005_background_access_system` |
| Notification listener | Android Settings detail + warning | CONFIRMED | `007`-`009` |
| Folder picker | Android DocumentsUI tree picker | CONFIRMED | `026_automatic_backups_dialog` |
| Article/help links | External browser intent; Chrome first-run intercepted | CONFIRMED | `012_article_focused`, `083_rules_not_triggering_help` |
| Community | Reddit item visible; not opened | CONFIRMED control / UNKNOWN destination | `016_settings_default` |
| Support | Contact support control visible; not invoked | CONFIRMED control / UNKNOWN destination | `016_settings_default` |
| App notification settings | Settings item visible; not opened | CONFIRMED control / UNKNOWN result | `019_settings_scrolled_3` |
| Quick Settings shortcuts | Create/clear shortcut settings and two tile services | CONFIRMED | `017_settings_scrolled_1`, package dump |
| Tasker/MacroDroid | Actions visible; resolver components reported | CONFIRMED surface; execution UNKNOWN | `057`-`058`, package dump |
| Wear OS | Wear message listener reported | CONFIRMED package component; UI behavior UNKNOWN | package dump |

## Deep links

No http/https application deep link was reported by Android's target resolver tables. The only observed web navigation was outbound. Classification: `CONFIRMED` for the resolver snapshot, `UNKNOWN` for any dynamically registered or non-resolver-visible routes.

## Safe reconstruction boundary

Model integrations behind interfaces and provide test doubles. Do not reproduce Tasker/MacroDroid protocol details, provider contracts, or proprietary backend assumptions without separate authorized evidence.
