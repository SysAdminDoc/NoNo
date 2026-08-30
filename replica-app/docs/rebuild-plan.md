# NoNo reconstruction plan

Status: implementation authorized by `BEGIN REBUILD` on 2026-08-01.

This project is an independent, clean-room reconstruction of the observable native behavior documented in `../app-audit`. The product name is **NoNo** and the application ID is `com.sysadmindoc.nono`. The original BuzzKill package name, logo, illustrations, fonts, and distribution overlays are not used.

## Evidence baseline

- Primary device: 1080 × 2400 px, 420 dpi (411.4 × 914.3 dp), Android 16/API 36, gesture navigation, `en-US`, font scale 1.0.
- Native scope: 76 captures, 67 distinct native visual states, 26 native surface families.
- Primary evidence: `../app-audit/evidence`, linked through `../app-audit/evidence/evidence-manifest.csv` and the 88 screen specifications.
- Observed design: dark application surfaces with a near-black background, yellow interactive sentence tokens, rounded cards and dialogs, and four-item bottom navigation.

## Implementation sequence

1. Establish this documentation, legal asset boundaries, deterministic fixtures, and a minimal Android/Compose build.
2. Implement the application shell, bottom navigation, replacement branding, design tokens, accessibility semantics, and debug-only audited-state launcher.
3. Implement onboarding and Android capability handoffs.
4. Implement rules home, rule sentence builder, selectors, menus, validation, rule management, and local persistence.
5. Implement history, search, filters, detail activity views, and deterministic test notifications.
6. Implement Explore articles/suggestions with safe local content and explicit external handoffs.
7. Implement Settings, selection dialogs, shortcut draft, local preference persistence, and safe file-picker handoffs.
8. Add unit tests, Compose UI tests, lifecycle checks, and PowerShell 5.1 automation.
9. Install on the explicit audit emulator, capture representative and state-matrix screenshots, compare them to audit evidence, iterate, and publish reports.

## Architecture

A single application module is proportional to this reconstruction. It uses Kotlin, Jetpack Compose, a single activity, immutable screen state held by a ViewModel, DataStore-backed preferences, and a small local JSON-style fixture repository. Navigation is modeled explicitly so the same states can be reached through normal interaction and through debug-only `replica_state` intent extras.

Potential notification-changing actions are simulated locally. Android permission/settings screens are opened only through documented public intents, and no backend is required. No original internal technology is assumed.

## Acceptance gates

- The debug APK builds, installs, launches, and survives relaunch on the authorized emulator.
- Every confirmed native surface family has an implementation mapping and test status.
- Rule and settings mutations persist locally; reset is available only through the supplied development script.
- Back, keyboard dismissal, scrolling, rotation, and safe lifecycle paths have device evidence.
- Unit and UI test reports are retained under `validation/reports`.
- Every comparison records source baseline, replica capture, dimensions, masks, similarity metrics, threshold, result, and documented deviation.

## Completion policy

Exact matching is not claimed where independently created branding, artwork, or font metrics intentionally differ. Unsafe notification side effects, destructive controls, external article contents, and unobserved network/error states remain simulated or documented. A material validation gap produces a partial-completion verdict rather than an unsupported claim.
