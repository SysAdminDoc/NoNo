# Validation strategy

The immutable source evidence remains under `../app-audit`. Native screenshots are copied into `validation/baseline` so comparison tooling never writes into the audit. Every native catalog row has a baseline, requested debug state, configured threshold, system-bar mask, current screenshot destination, and evidence reference. The historical 0.985 preferred target is retired: identity artwork and editorial copy are unauthorized clean-room substitutions, so per-screen configured thresholds and known-deviation review are the acceptance gate.

Comparisons require equal dimensions. They generate:

- side-by-side image;
- 50% alpha overlay;
- raw absolute RGB difference;
- amplified heatmap;
- JSON metrics with dimensions, compared-pixel ratio, MAE, RMSE, pixel similarity, 11x11 Gaussian-windowed structural similarity, threshold, and verdict.

Threshold misses are retained as failures and documented; they are not silently waived. The independent onboarding identity/art and inaccessible unsafe runtime effects are expected high-risk deviation areas.
