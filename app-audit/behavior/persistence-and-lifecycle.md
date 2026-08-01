# Persistence and lifecycle

## Confirmed persistence

- A saved rule containing `AUDIT_SENTINEL_8675309` persisted after background/resume and force-stop/relaunch.
- The rule's Disabled state persisted.
- Relaunch returned to Rules rather than the prior Settings/History detail layer.
- History retained the Shell test notification across navigation and the observed lifecycle sequence.
- Permission/onboarding completion persisted.

Evidence: `064_rules_test_record_disabled`, `071_history_populated_test_notification`, `085_cold_relaunch_native_state`, package dump final permission state.

## Startup measurements

`am start -W` reported:

- Background/resume: `LaunchState: WARM`, TotalTime 1422 ms, WaitTime 1426 ms.
- After `am force-stop`: still `LaunchState: WARM`, TotalTime 6447 ms, WaitTime 6450 ms.

Evidence: `runtime-warm-start-20260801-140100.txt` and `runtime-cold-start-20260801-140100.txt`.

The second result is not a valid true-cold benchmark. Android kept/restarted the live notification-listener process, and the excluded repackaging overlay appeared on relaunch. Classification: `CONFIRMED measurement`, `UNKNOWN native cold-launch time`.

## Memory and rendering snapshot

After relaunch, Android reported total PSS 81,206 KB, total RSS 234,748 KB, Java heap 16,172 KB, and native heap 32,908 KB. Evidence: `runtime-meminfo-after-cold-start-20260801-140100.txt`.

Gfxinfo reported 18 frames, 15 janky, 50th percentile 150 ms. This is not representative product performance: the sample is tiny, uses a headless emulator with SwiftShader, includes process initialization and the excluded overlay, and logged an HWUI format fallback. Treat as `UNKNOWN` for production performance, useful only as a test-environment warning. Evidence: `runtime-gfxinfo-after-cold-start-20260801-140100.txt`, `target-logcat-20260801-140752.txt`.

## Rotation

The disabled rule persisted through portrait → landscape → portrait. Landscape supports reflow but vertically clips card content at the observed viewport. Orientation and free-rotation mode were restored. Evidence: `087_rules_landscape_native`.

## Unknown lifecycle behaviors

Scroll-position persistence, active search-query persistence, backup schedule persistence, process-death restoration during an incomplete builder, reboot restoration, and multi-day history expiry were not tested.
