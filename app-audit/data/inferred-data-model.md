# Inferred user-facing data model

Internal databases, schemas, APIs, and serialization are not known. The following model is derived only from visible UI and state transitions.

## Rule

`CONFIRMED observable fields`: enabled, optional nickname, optional folder, priority/order, app scope, content filter, contextual filters, one action (or Multi-tool), and temporary enable duration. Rule cards render a sentence and action icon. Evidence: `029`, `063`–`069`, `082`.

`STRONG INFERENCE`: Rules have stable identifiers because they can be edited, duplicated, foldered, prioritized, enabled, and persisted independently.

## AppScope

Visible values include any app, selected app(s), and Pick all apps. Search filters installed-app candidates. Evidence: `030`, `031`.

## FilterExpression

Recursive expression with operator and members:

- Operators: any, all, not-any, not-all.
- Member types: Phrase, Extra, Group.
- Extras: image, phone number, emoji, group conversation, language, custom layout, fixed/media notification, category, image recognition target, text length.
- Suggestion evidence shows contextual device predicates such as pocket/face-down and on-table.

Evidence: `032`–`044`, `082`.

## Action

Visible fields vary by action type. Common observable properties are type, icon/color, description, optional parameters, and experimental status. Action execution details remain mostly `UNKNOWN`. Evidence: `049`–`062`.

## NotificationHistoryItem

Fields visible: app icon/name, time, title, body, sent/dismissed classification, triggered-rule summary, activity records, and change records. Menu implies restorability, openability, copyable text, rule creation, and deletion. Evidence: `071`–`074`.

## HistoryActivity

Visible relationship: belongs to one notification, divided into triggered rule activity and notification changes; rule activity retained for 24 hours. Evidence: `073`, `074`.

## ExploreArticle and RuleSuggestion

Article: image, title, description, external URL. Suggestion: colored theme, natural-language rule preview, and Add to my rules action producing an unsaved Rule. Evidence: `011`, `077`–`082`.

## SettingsProfile

Observable fields include mute policy, fixed dismissal, unsilence-call behavior, history capture/retention, privacy mode, theme, language, backup folder/schedule, popup behavior, batch restoration, and Android workarounds. Evidence: `016`–`028`.

## Confidence limits

Storage technology, table names, keys, migration format, encryption, remote synchronization, analytics identifiers, and backup file schema are `UNKNOWN`.
