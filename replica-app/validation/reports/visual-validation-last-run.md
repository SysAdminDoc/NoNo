# Visual validation report

- Device: emulator-5566
- Compared: 11
- Passed configured threshold: 9
- Below threshold or operational failure: 2
- Matrix: validation/screen-validation-matrix.csv
- Detailed metrics/artifacts: validation/diffs

| Screen | Group | Similarity | Threshold | Result |
|---|---|---:|---:|---|
| 002_welcome_default | onboarding_welcome | 0.66353446 | 0.85 | THRESHOLD_MISS |
| 004_welcome_notifications_granted | onboarding_welcome | 0.79664851 | 0.85 | THRESHOLD_MISS |
| 006_welcome_background_allowed | onboarding_welcome | 0.86254265 | 0.85 | PASS |
| 020_settings_scrolled_4 | settings | 0.91805727 | 0.85 | PASS |
| 030_app_selector | app_selector | 0.91817764 | 0.85 | PASS |
| 031_app_selector_search | app_selector | 0.93563517 | 0.85 | PASS |
| 035_filter_with_phrase | condition_builder | 0.87341457 | 0.85 | PASS |
| 042_filter_phrase_added | condition_builder | 0.87663839 | 0.85 | PASS |
| 043_rule_builder_filtered | rule_builder | 0.87915762 | 0.85 | PASS |
| 060_rule_builder_filter_restored | rule_builder | 0.87912737 | 0.85 | PASS |
| 062_rule_builder_complete | rule_builder | 0.8803837 | 0.85 | PASS |
