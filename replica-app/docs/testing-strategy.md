# Testing strategy

- Pure Kotlin unit tests cover action-catalog order, exact validation copy, sentence rendering, history filtering, the one-shot self-test key, the content-free diagnostics report, category allowlisting, and channel transfer across two install keys.
- An instrumented round-trip posts the self-test notification through Android, observes the real listener callback, and proves that History and ingestion counters do not change.
- An instrumented Room test proves that the startup scrub keeps known categories and removes unknown app-authored strings from older rows.
- Compose instrumentation verifies that the installed activity creates an accessible semantics root on the authorized emulator.
- PowerShell smoke tests validate the SDK/JDK/device, build the debug APK, install by explicit serial, and launch audited debug states.
- Visual validation compares every enabled native audit capture at native dimensions and emits side-by-side, 50% overlay, raw diff, heatmap, and JSON metrics.
- Dynamic status/navigation regions are masked; app-owned content is not masked globally. Exact branding/art/font differences remain visible and are interpreted through the asset-rights and deviation registers.
- Lifecycle validation includes cold launch, warm navigation, persisted rule state, force-stop/relaunch, and reversible rotation for the one audited landscape state.

Pixel similarity is reported as one minus the normalized mean absolute RGB error. The 11x11 Gaussian-windowed structural similarity is also reported as a diagnostic. Passing is based only on the per-screen pixel threshold in `validation/screen-validation-matrix.csv`. A pass does not supersede manual review or known-deviation documentation.
