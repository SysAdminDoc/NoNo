# Display and insets

## Baseline

`CONFIRMED`: The audit device is an Android 16 Pixel-style emulator at 1080 × 2400 px, 420 dpi, 60 Hz. This converts to approximately 411.4 × 914.3 dp using `dp = px × 160 ÷ 420`. Font scale is 1.0 and navigation mode value 2 corresponds to gesture navigation. Evidence: `evidence/measurements/device-wm-size-20260801-140011.txt`, `device-wm-density-20260801-140011.txt`, `device-font-scale-20260801-140011.txt`, and `device-navigation-mode-20260801-140011.txt`.

## Cutout and rounded corners

`CONFIRMED`: The emulated display reports a centered top cutout from x=480 to 625 and y=0 to 136 px, a 136 px/51.8 dp top safe inset, no waterfall insets, 47 px top corner radii, and 48 px bottom corner radii. BuzzKill's portrait layouts keep important content below the status/cutout area. Evidence: `evidence/measurements/device-display-20260801-140011.txt` and `device-window-displays-20260801-140011.txt`.

## System bars and edge-to-edge

`CONFIRMED`: Native dark screens paint `#0A0B0D` behind a transparent status bar; white system icons are visible above app content. The app's custom bottom navigation ends above the system gesture area; the gesture bar area is system controlled. Main UI hierarchy roots span the full 1080 × 2400 display while meaningful app content observes safe regions. Evidence: `010_home_empty`, `013_history_empty`, `016_settings_default`, `063_rules_populated_test_record`.

`STRONG INFERENCE`: Rebuild using edge-to-edge window configuration, transparent system bars, explicit status/cutout padding for page content, and explicit navigation-bar/gesture insets for the bottom navigation shell.

## Theme

`CONFIRMED`: The Android system reported night mode off, while BuzzKill's theme dialog selected Dark. Therefore app theme selection is independent of the system default. Evidence: `evidence/measurements/device-ui-night-mode-20260801-140011.txt` and `027_theme_dialog`.

## Rotation

`CONFIRMED`: BuzzKill rotates to landscape and preserves the disabled rule. At 2400 × 1080, the page remains centered and the four-item navigation becomes a wide bottom bar, but the Rules card is vertically clipped by the limited viewport. The rotation override was restored to free/portrait immediately after capture. Evidence: `087_rules_landscape_native`, with portrait reference `085_cold_relaunch_native_state`.

`UNKNOWN`: Behavior on foldables, multi-window, freeform windows, tablets, and displays below the observed dp size was not tested.
