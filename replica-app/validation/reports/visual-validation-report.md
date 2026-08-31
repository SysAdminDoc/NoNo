# Visual validation report

- Compared: 76
- Passed configured threshold: 65
- Below threshold: 11
- Operational failures: 0
- Missing or invalid result: 0
- Matrix: validation/screen-validation-matrix.csv
- Detailed metrics and comparison images: validation/diffs

| Screen | Group | Similarity | Threshold | Result |
|---|---|---:|---:|---|
| 002_welcome_default | onboarding_welcome | 0.66353446 | 0.85 | THRESHOLD_MISS |
| 004_welcome_notifications_granted | onboarding_welcome | 0.79664851 | 0.85 | THRESHOLD_MISS |
| 006_welcome_background_allowed | onboarding_welcome | 0.86254265 | 0.85 | PASS |
| 010_home_empty | rules_home | 0.93864466 | 0.85 | PASS |
| 011_explore_default | explore | 0.88239344 | 0.85 | PASS |
| 013_history_empty | history | 0.93686402 | 0.85 | PASS |
| 014_history_search_empty | history | 0.98499503 | 0.85 | PASS |
| 015_history_search_no_results | history | 0.98298038 | 0.85 | PASS |
| 016_settings_default | settings | 0.90647735 | 0.85 | PASS |
| 017_settings_scrolled_1 | settings | 0.90097648 | 0.85 | PASS |
| 018_settings_scrolled_2 | settings | 0.90753967 | 0.85 | PASS |
| 019_settings_scrolled_3 | settings | 0.90247229 | 0.85 | PASS |
| 020_settings_scrolled_4 | settings | 0.90349702 | 0.85 | PASS |
| 021_mute_mode_dialog | mute_mode_dialog | 0.93518899 | 0.85 | PASS |
| 022_mute_importance_dialog | mute_importance_dialog | 0.93264651 | 0.85 | PASS |
| 023_notification_history_dialog | history_storage_dialog | 0.92225164 | 0.85 | PASS |
| 024_history_retention_dialog | history_retention_dialog | 0.92319373 | 0.85 | PASS |
| 025_create_shortcut_empty | shortcut_editor | 0.95898503 | 0.85 | PASS |
| 027_theme_dialog | theme_dialog | 0.93417287 | 0.85 | PASS |
| 028_language_system | language_dialog | 0.9302768 | 0.85 | PASS |
| 029_rule_builder_default | rule_builder | 0.88404493 | 0.85 | PASS |
| 030_app_selector | app_selector | 0.91817764 | 0.85 | PASS |
| 031_app_selector_search | app_selector | 0.93563517 | 0.85 | PASS |
| 032_condition_match_type_dialog | condition_builder | 0.85909472 | 0.85 | PASS |
| 033_phrase_filter_editor | condition_builder | 0.91842369 | 0.85 | PASS |
| 034_phrase_filter_input | phrase_editor | 0.91486708 | 0.85 | PASS |
| 035_filter_with_phrase | condition_builder | 0.87341457 | 0.85 | PASS |
| 036_extras_filter_selector | condition_extras_menu | 0.84545345 | 0.85 | FAIL |
| 037_extras_filter_selector_scrolled | condition_extras_menu | 0.84557574 | 0.85 | FAIL |
| 038_extras_filter_selector_bottom | condition_extras_menu | 0.84539138 | 0.85 | FAIL |
| 039_filter_group_default | nested_filter_group | 0.85413702 | 0.85 | PASS |
| 040_filter_operator_dialog | filter_operator_menu | 0.86355316 | 0.85 | PASS |
| 041_phrase_input_filled | phrase_editor | 0.91500283 | 0.85 | PASS |
| 042_filter_phrase_added | condition_builder | 0.87663839 | 0.85 | PASS |
| 043_rule_builder_filtered | rule_builder | 0.87915762 | 0.85 | PASS |
| 044_add_filter_menu | condition_builder | 0.85833245 | 0.85 | PASS |
| 045_action_selector_top | phrase_editor | 0.91066373 | 0.85 | PASS |
| 046_action_selector_top | phrase_editor | 0.91481848 | 0.85 | PASS |
| 047_action_selector_top | phrase_editor | 0.91481901 | 0.85 | PASS |
| 048_action_selector_top | phrase_editor | 0.91479727 | 0.85 | PASS |
| 049_action_selector_top | action_selector | 0.89537811 | 0.85 | PASS |
| 050_action_selector_scroll_1 | action_selector | 0.88953596 | 0.85 | PASS |
| 051_action_selector_scroll_2 | action_selector | 0.88859183 | 0.85 | PASS |
| 052_action_selector_scroll_3 | action_selector | 0.89042566 | 0.85 | PASS |
| 053_action_selector_scroll_4 | action_selector | 0.88831913 | 0.85 | PASS |
| 054_action_selector_scroll_5 | action_selector | 0.89195922 | 0.85 | PASS |
| 055_action_selector_scroll_6 | action_selector | 0.89107571 | 0.85 | PASS |
| 056_action_selector_scroll_7 | action_selector | 0.88793077 | 0.85 | PASS |
| 057_action_selector_bottom | action_selector | 0.88356412 | 0.85 | PASS |
| 058_action_selector_bottom_2 | action_selector | 0.88242277 | 0.85 | PASS |
| 059_rule_builder_validation_missing | rule_builder | 0.86937944 | 0.85 | PASS |
| 060_rule_builder_filter_restored | rule_builder | 0.87912737 | 0.85 | PASS |
| 061_action_selector_mute_selected | action_selector | 0.88862801 | 0.85 | PASS |
| 062_rule_builder_complete | rule_builder | 0.8803837 | 0.85 | PASS |
| 063_rules_populated_test_record | rules_home | 0.88195822 | 0.85 | PASS |
| 064_rules_test_record_disabled | rules_home | 0.91797663 | 0.85 | PASS |
| 065_rule_overflow_menu | rule_overflow_menu | 0.89505002 | 0.85 | PASS |
| 066_rule_enable_for_dialog | enable_for_menu | 0.88615671 | 0.85 | PASS |
| 067_rule_priority_dialog | rules_home | 0.8973065 | 0.85 | PASS |
| 068_rule_folder_dialog | folder_dialog | 0.93549368 | 0.85 | PASS |
| 069_rule_rename_dialog | rename_dialog | 0.93146118 | 0.85 | PASS |
| 070_rule_edit_existing | rule_builder | 0.87432686 | 0.85 | PASS |
| 071_history_populated_test_notification | history | 0.94749959 | 0.85 | PASS |
| 072_history_notification_detail | history_item_menu | 0.90520051 | 0.85 | PASS |
| 073_history_item_activity | history_activity | 0.90677637 | 0.85 | PASS |
| 074_history_item_changes | history_activity | 0.90361605 | 0.85 | PASS |
| 075_history_rule_triggered_filter_empty | history | 0.93824601 | 0.85 | PASS |
| 076_history_dismissed_filter_empty | history | 0.93905989 | 0.85 | PASS |
| 077_explore_scrolled_1 | explore | 0.78545068 | 0.85 | FAIL |
| 078_explore_scrolled_2 | explore | 0.76715792 | 0.85 | FAIL |
| 079_explore_scrolled_3 | explore | 0.73110806 | 0.85 | FAIL |
| 080_explore_bottom | explore | 0.72754331 | 0.85 | FAIL |
| 081_explore_bottom_2 | explore | 0.75362387 | 0.85 | FAIL |
| 082_explore_suggestion_rule_preview | rule_builder | 0.82950221 | 0.85 | FAIL |
| 085_cold_relaunch_native_state | rules_home | 0.88384461 | 0.85 | PASS |
| 087_rules_landscape_native | rules_home | 0.86986528 | 0.85 | PASS |
