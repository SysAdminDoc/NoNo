# Testing strategy

- Pure Kotlin unit tests cover action-catalog order, exact validation copy, sentence rendering, and history filtering.
- Compose instrumentation verifies that the installed activity creates an accessible semantics root on the authorized emulator.
- PowerShell smoke tests validate the SDK/JDK/device, build the debug APK, install by explicit serial, and launch audited debug states.
- Visual validation compares every enabled native audit capture at native dimensions and emits side-by-side, 50% overlay, raw diff, heatmap, and JSON metrics.
- Dynamic status/navigation regions are masked; app-owned content is not masked globally. Exact branding/art/font differences remain visible and are interpreted through the asset-rights and deviation registers.
- Lifecycle validation includes cold launch, warm navigation, persisted rule state, force-stop/relaunch, and reversible rotation for the one audited landscape state.

Pixel similarity is reported as `1 - normalized mean absolute RGB error`; global structural similarity is also reported as a diagnostic. Passing is based on the per-screen pixel threshold in `validation/screen-validation-matrix.csv`. A pass does not supersede manual review or known-deviation documentation.
