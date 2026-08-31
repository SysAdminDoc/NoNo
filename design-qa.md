# NoNo v1.4.0 design QA

## Visual target

The selected direction uses an AMOLED black foundation, graphite surfaces, citron accents, compact rectangular controls, and a maximum 12 dp corner radius. Page spacing, typography, iconography, navigation, and active states were checked against the reference mockups.

## Test environment

- Android API 35 headless emulator
- 1080 x 2400 px at 420 dpi
- Portrait orientation and gesture navigation
- Font scale 1.0 for parity captures
- Font scale 1.3 for large text checks
- Dark and light theme checks

Each comparison places the reference mockup on the left and the implemented app on the right.

## Page coverage

| Page | Reference | Implementation | Comparison |
| --- | --- | --- | --- |
| Onboarding | `replica-app/design/mockups/v1.4.0/page-onboarding.png` | `replica-app/design/qa/v1.4.0/implementation/002_welcome_default.png` | `replica-app/design/qa/v1.4.0/comparisons/onboarding.png` |
| Rules | `replica-app/design/mockups/v1.4.0/page-rules.png` | `replica-app/design/qa/v1.4.0/implementation/063_rules_populated_test_record.png` | `replica-app/design/qa/v1.4.0/comparisons/rules.png` |
| History | `replica-app/design/mockups/v1.4.0/page-history.png` | `replica-app/design/qa/v1.4.0/implementation/071_history_populated_test_notification.png` | `replica-app/design/qa/v1.4.0/comparisons/history.png` |
| Explore | `replica-app/design/mockups/v1.4.0/page-explore.png` | `replica-app/design/qa/v1.4.0/implementation/011_explore_default.png` | `replica-app/design/qa/v1.4.0/comparisons/explore.png` |
| Settings | `replica-app/design/mockups/v1.4.0/page-settings.png` | `replica-app/design/qa/v1.4.0/implementation/016_settings_default.png` | `replica-app/design/qa/v1.4.0/comparisons/settings.png` |
| Rule builder | `replica-app/design/mockups/v1.4.0/page-rule-builder.png` | `replica-app/design/qa/v1.4.0/implementation/029_rule_builder_default.png` | `replica-app/design/qa/v1.4.0/comparisons/rule-builder.png` |
| App selector | `replica-app/design/mockups/v1.4.0/page-app-selector.png` | `replica-app/design/qa/v1.4.0/implementation/030_app_selector.png` | `replica-app/design/qa/v1.4.0/comparisons/app-selector.png` |
| Phrase editor | `replica-app/design/mockups/v1.4.0/page-phrase-editor.png` | `replica-app/design/qa/v1.4.0/implementation/901_phrase_urgent.png` | `replica-app/design/qa/v1.4.0/comparisons/phrase-editor.png` |
| Filter group | `replica-app/design/mockups/v1.4.0/page-filter-group.png` | `replica-app/design/qa/v1.4.0/implementation/902_filter_group_populated.png` | `replica-app/design/qa/v1.4.0/comparisons/filter-group.png` |
| Action selector | `replica-app/design/mockups/v1.4.0/page-action-selector.png` | `replica-app/design/qa/v1.4.0/implementation/049_action_selector_top.png` | `replica-app/design/qa/v1.4.0/comparisons/action-selector.png` |
| History activity | `replica-app/design/mockups/v1.4.0/page-history-activity.png` | `replica-app/design/qa/v1.4.0/implementation/073_history_item_activity.png` | `replica-app/design/qa/v1.4.0/comparisons/history-activity.png` |
| Shortcut editor | `replica-app/design/mockups/v1.4.0/page-shortcut-editor.png` | `replica-app/design/qa/v1.4.0/implementation/900_shortcut_selected.png` | `replica-app/design/qa/v1.4.0/comparisons/shortcut-editor.png` |

## Accessibility evidence

- `replica-app/design/qa/v1.4.0/accessibility/903_light_rules.png`
- `replica-app/design/qa/v1.4.0/accessibility/002_welcome_large_text.png`
- `replica-app/design/qa/v1.4.0/accessibility/029_rule_builder_large_text.png`

## Resolution history

- P1: Removed transient audit banners and stabilized deterministic page data.
- P1: Fixed the shortcut preview crash caused by loading an adaptive launcher icon as a drawable.
- P2: Added populated phrase, filter, action, history, and shortcut states for direct comparison.
- P2: Matched grouped history rows, clipped segmented controls, tightened button placement, and aligned selected states.
- Verified: shortcut publication stays disabled with its reason visible because this build does not publish launcher shortcuts.

final result: passed
