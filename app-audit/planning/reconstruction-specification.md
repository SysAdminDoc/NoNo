# Reconstruction specification

## 1. Product overview

BuzzKill is an Android notification automation product organized around natural-language rules. Users grant notification/background/listener capabilities, create rules that match app/content/context predicates, choose an action, inspect notification history and rule activity, browse educational/suggested rules, and configure behavior in Settings.

The observable product is predominantly local/device-integrated. Backend, database, and network implementation are `UNKNOWN` and must not be treated as original-architecture facts.

Evidence anchors: onboarding `002`-`010`; root pages `010`, `013`, `011`, `016`; complete package identity `app/application-identity.md`.

## 2. Complete native screen inventory

The audit identifies 12 full-screen/page surface families, 9 dialogs, and 5 menus. Every captured state has a JSON specification under `screens/screen-specs/`; the authoritative table is `screens/screen-catalog.csv`.

Full-screen/page families:

1. Capability onboarding.
2. Rules home (empty, enabled, disabled, portrait, landscape).
3. History (empty, populated, search, filters).
4. Explore (articles and suggestions).
5. Settings (full scroll range).
6. Shortcut editor.
7. Rule builder (default, filtered, invalid, complete, suggestion preview, edit).
8. App selector.
9. Content-filter composer.
10. Nested filter group.
11. Action selector.
12. Notification activity (Rules/Changes).

Dialogs: Mute mode, Mute importance, history storage, retention, theme, language, phrase, folder, rename.

Menus: extras, filter operator, rule More, enable-for duration, history item.

External system/browser/repackaging states are separated by `scope` and are not native implementation screens.

## 3. Navigation hierarchy

The post-onboarding root has persistent bottom navigation: Rules / History / Explore / Settings. Editors and detail pages cover the root shell and return through Android Back or explicit bottom actions. Exact transitions are in `flows/navigation-map.mmd` and `flows/state-transition-map.mmd`.

Acceptance criteria:

- Each root destination is reachable in one tap and visibly selected.
- Back unwinds menu → dialog/editor → root without losing committed data.
- Unsaved Explore suggestions return without creating a rule.
- Cold/root relaunch lands on Rules in the observed configured state.

## 4. Screen-by-screen requirements

### Onboarding

- Near-black background, Welcome title/copy, three stacked yellow capability cards, Contact support.
- Pending and complete appearances exactly as `002`, `004`, `006`.
- Re-query actual capability on resume; advance only when confirmed.
- Launch Android permission/background/listener settings surfaces rather than imitating them.
- Denied-state copy is a known unknown and requires a product decision.

### Rules

- Empty state: title `Add your first rule`, explanatory placement, yellow Create rule CTA (`010`).
- Populated: hero, search, rule cards, floating Create rule (`063`).
- Enabled card uses blue frame/header and switch; disabled uses gray frame, switch off, sentence strike-through (`064`, `085`).
- More menu exact order: Enable for…, Set priority, Set folder, Rename, Duplicate, Delete (`065`).
- Landscape preserves state and follows `087`, but reconstruction should improve vertical accessibility without changing core composition.

### Rule builder

- Render the rule as wrapping natural language with separately actionable inline tokens and yellow wavy underlines (`029`, `043`).
- App token opens searchable selector (`030`, `031`).
- Content token opens recursive composer (`032`-`044`).
- Action token opens categorized catalog (`049`-`061`).
- `+ Filter` supports adding/returning to contextual conditions; suggestion evidence proves multiple clauses (`082`).
- Save validation uses exact visible red state/warning from `059`.
- Recent matching notifications occupies a separated lower section and can render empty copy or history cards.

### History

- Large daily count, heading, 24-hour timeline, two segmented controls (`013`, `071`).
- Search, rule-triggered filter, sent/dismissed filter, empty/no-result states (`014`, `015`, `075`, `076`).
- Card content and item menu exact to `071`, `072`.
- Activity page has Rules/Changes segmented tabs, explicit 24-hour no-activity explanation, and Back to history button (`073`, `074`).

### Explore

- Article cards at top; Suggestions below. Preserve observed scroll order in `011`, `077`-`081` unless product content changes.
- Article taps use external browser intents.
- Add to my rules opens a populated draft; no immediate save (`082`).
- All illustrations/icons must be authorized or original replacements.

### Settings

- Preserve group/order and captured defaults from `016`-`020` and `behavior/behavior-specification.md`.
- Native selection/text dialogs match `021`-`024`, `027`, `028`, `068`, `069`.
- Automatic backups invoke Android folder picker; do not emulate DocumentsUI (`026`).
- External support/guide/community/help uses outbound intents.

## 5. Reusable component list

Use the 17 components in `design/component-catalog.md`: capability card, bottom nav, empty hero, primary button, floating pill, rule card, natural-language token, filter composer, action card, history summary, segmented control, notification card, setting row, radio dialog, text-entry dialog, popup menu, suggestion/article cards.

All components require visual, semantic, enabled/disabled/selected, and large-text variants.

## 6. Design tokens

Canonical observed tokens are in `design/design-tokens.json`. Minimum visual parity requires:

- `#0A0B0D` background, `#1A1C21` surface, `#FFF387` accent.
- White/gray text hierarchy, `#FF7070` error.
- Blue enabled and gray disabled rule cards.
- 24 dp page margins, 16 dp list margins, 48 dp primary buttons, approximately 70 dp custom bottom nav.
- Rounded heavy authorized font substitute and custom wavy underline.

Raw values remain in `evidence/measurements/color-analysis.json`.

## 7. Data entities

Implement observable entities only: Rule, AppScope, recursive FilterExpression, Action, NotificationHistoryItem, HistoryActivity, ExploreArticle, RuleSuggestion, SettingsProfile. Fields and confidence are in `data/entity-field-matrix.csv`.

Do not claim original backend/database compatibility. Import/export needs a new, versioned specification unless separately authorized.

## 8. State-management requirements

- Root destination state.
- Draft rule separate from persisted rule.
- Recursive filter editing with Apply/Cancel semantics.
- Action selection/validation.
- Rule enabled/disabled, nickname/folder/priority/duration.
- History query and two independent filter dimensions.
- Activity tab selection.
- Permission capability state refreshed on resume.
- Dialog/menu transient state not persisted.

Recommended immutable reducer/ViewModel model is described in `recommended-architecture.md`.

## 9. Persistence requirements

- Persist rules, disabled state, settings, and onboarding completion across force-stop/relaunch.
- Persist notification history according to selected policy and retention.
- Retain activity for 24 hours as visible copy specifies.
- Rebuild scroll/query persistence only after resolving open questions; it was not confirmed.
- Import/export must be transactional, versioned, previewable, and non-destructive by default.

Evidence: `064`, `071`, `073`, `085`; `behavior/persistence-and-lifecycle.md`.

## 10. Permission requirements

Core observed flow requires POST_NOTIFICATIONS, notification-listener access, and background/device-idle exemption. Other requested permissions map to optional actions and must be requested just-in-time. Full inventory: `app/permissions-and-appops.md`.

Acceptance criteria:

- Capability cards reflect Android's actual state after returning.
- Core browsing works if optional action permissions are absent.
- Each action exposes unavailable/denied handling without silent failure.

## 11. External integrations

Android settings/permissions, DocumentsUI, notification listener, foreground channels, outbound web/support links, Quick Settings, Tasker/MacroDroid, Wear. Details: `app/components-and-intents.md`.

Tasker/MacroDroid/Wear protocols are not specified by current evidence and must remain adapter stubs until separately validated.

## 12. Error and loading behavior

Confirmed empty/validation/disabled states are in `behavior/errors-loading-and-empty-states.md`. No native spinner/retry/network-error state was observed. Preserve confirmed behavior; add defensive failures as clearly documented reconstruction improvements.

## 13. Accessibility requirements

Observed positives:

- Bottom navigation exposes labels and selected state.
- Icon buttons such as More/Delete sometimes expose content descriptions.
- Standard buttons/dialog choices are keyboard/focusable nodes.

Observed concerns:

- Builder sentence editable spans are frequently absent as independent UI Automator nodes.
- Several icon touch boxes measure about 44.2 dp, below a 48 dp target.
- Secondary gray text on near-black may need formal contrast verification at exact font size/weight.
- TalkBack focus order, error announcement, 200% scaling, and selected-state announcements were not tested.

Rebuild acceptance: independent semantics for every rule token; 48 dp targets; state/role labels; error live-region announcement; 200% reflow; no color-only meaning; logical focus order.

## 14. Recommended Android architecture

Use the modular Kotlin/Compose, Room/DataStore, data-driven action catalog, and capability-checked runtime described in `recommended-architecture.md`. This is a recommendation, not an inference about the original technology.

## 15. Suggested implementation order

Follow `implementation-backlog.md`: assets/decisions → design foundation → models/navigation → Rules read path → builder → History → Explore/Settings → runtime actions → hardening.

## 16. Testable acceptance criteria

1. Every native surface family in `screen-catalog.csv` has a matching route/dialog/menu and screenshot test.
2. Root navigation, state colors, and system bars match observed 411 × 914 dp captures within agreed visual tolerance.
3. Builder reproduces exact default/filtered/invalid/complete sentences and wrapping.
4. Filter composer supports the four operators, Phrase, all observed Extras, and nested Group.
5. Action catalog contains every observed action/category/description and selected state.
6. Save-with-missing-action reproduces exact warning and red token.
7. Saved rule appears enabled; disabling produces strike-through and persists after process death.
8. History reproduces empty/populated/search/filter/menu/activity states from `013`-`076`.
9. Explore suggestion opens populated unsaved builder.
10. Settings order/defaults/dialog options match evidence.
11. Permissions are launched through Android and rechecked on resume.
12. All semantic controls are TalkBack accessible with at least 48 dp targets.
13. Portrait and landscape preserve data; no required content becomes unreachable.
14. No LiteAPKS/repackaging promotion or third-party proprietary artwork appears.

## 17. Evidence references

- Screen table/specs: `screens/screen-catalog.csv`, `screens/screen-specs/`.
- Screenshots/XML/activity: `evidence/screenshots/`, `evidence/ui-xml/`, `evidence/activity/`.
- Package/runtime: `evidence/package/`, `evidence/measurements/`, `evidence/logs/`.
- Design sampling: `evidence/measurements/color-analysis.json`.
- Evidence links: `evidence/evidence-manifest.csv`.

## 18. Known unknowns

The 24 tracked questions are in `open-questions.md`. Highest risk: action configuration/execution, rule precedence, backup schema, light/accessibility/localization, and external integration contracts.

## 19. Features not safely tested

Deletion, real messages/replies/forms, alarm/ringer/DND/flashlight/open/copy/reboot side effects, account/purchase flows, import/export mutation, Tasker/MacroDroid/Wear completion, and Chrome onboarding. Exact reasons: `testing/untested-and-blocked-cases.md`.

## 20. Assets requiring original replacements or authorization

App name/icon/trademark, article illustrations, action/icon family, and exact font require authorization or original/open replacements. System UI must remain system-provided. LiteAPKS assets are excluded and must not be reproduced. Full inventory: `design/asset-inventory.csv`.

## Reconstruction gate

This specification is the end of the audit phase. Do not start implementation until the operator explicitly states `BEGIN REBUILD`.
