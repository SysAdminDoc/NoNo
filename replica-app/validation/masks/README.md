# Mask register

Masks exclude only Android-owned, legitimately variable system-bar pixels:

- `system-bars.json` excludes the portrait status-bar clock/icons and gesture-navigation bar.
- `system-bars-landscape.json` excludes the landscape status-bar region and side gesture-navigation region.

No application content, branding, illustrations, copy, dialogs, or controls are masked. The compared-pixel ratio is recorded in every metrics JSON.
