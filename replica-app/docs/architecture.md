# Reconstruction architecture

## Runtime shape

- One Android application module and one edge-to-edge `MainActivity`.
- Kotlin and Jetpack Compose with custom observed color/spacing/typography tokens. Dark, light, and
  system palettes are all implemented in `ui/SignalTheme.kt` and the choice is persisted.
- An `AndroidViewModel` exposes immutable `UiState` through `StateFlow`.
- Routes and overlays are explicit enums, which keeps normal back navigation and debug evidence
  routing deterministic.
- `SignalNotificationListener` is a least-privilege listener surface. It sanitizes each callback,
  hands it to a bounded worker, and never logs or transmits payload text or executes an action.
- `SignalCapturePauseTileService` is a Quick Settings tile that pauses and resumes capture through
  `CaptureGate`. `SignalWidgetProvider` is a RemoteViews widget showing counts and provenance only.

## Capture pipeline

`onNotificationPosted` checks `CaptureGate`, then `NotificationRedaction.sanitizeNotification`
classifies content provenance as available, hidden by the system, or not available, keeping the
title and body out of everything it returns. The result goes to `NotificationIngestor`, a bounded
`Channel(64)` drained by a single worker: a full queue drops the newest event and increments a
counter rather than blocking the platform callback. The worker writes through
`insertAndPrune`, which inserts and applies the retention cutoff in one transaction, then asks the
widget to refresh.

Ingestion counters (persisted, dropped, failed, last failure time) are mirrored into Room so they
survive process death, and republished through `ListenerHealth` on the next start.

## State and persistence

- **Room** (`SignalDatabase`, version 4, `exportSchema = true`) holds notification metadata and a
  single-row diagnostics table. Migrations 1 to 4 add grouping, diagnostics, and channel columns;
  the exported schemas under `app/schemas` are packaged into the test APK so
  `SignalDatabaseMigrationTest` can replay them. The database file lives under `noBackupFilesDir`.
- `SignalDatabase.get` returns one instance per process. Room's invalidation tracker only notifies
  observers registered on the instance that performed the write, so the listener, the view model,
  and the widget must share one or the history flow never updates.
- **DataStore** holds onboarding state, the rule list (`RuleStore` version 3, encoded by
  `RuleCodec`), and every observed settings value. It also lives under `noBackupFilesDir`, and a
  corrupt store is replaced with defaults rather than crashing the process.
- Retention is 30 days, 3 months, or 6 months, applied on every insert and again when the setting
  changes.

## Evaluation

`RuleEvaluation` is pure. It takes saved rules and a sanitized payload and returns per-condition
traces, deterministic specificity and priority conflict resolution, and an action result that is
always `NOT_EXECUTED`. Content the platform redacted is never matchable as text. Nothing in the
evaluator touches the notification manager, a `PendingIntent`, or the ringer.

## Transfer

`RuleTransfer` writes rules and settings, never history, through the Storage Access Framework.
Encryption is AES-GCM with a 256-bit key derived by PBKDF2-HMAC-SHA256, a random salt, and a random
IV; the passphrase is held as a char array and zeroed. Import previews additions and same-id
conflicts before anything is committed, and cancellation leaves the caller's state alone.

## Clean-room boundaries

This architecture is a recommendation, not an inference about the original application's internals.
The project does not depend on the original APK, package name, resources, signing identity, private
storage, network endpoints, or implementation technology.

The original product is much broader than this reconstruction. All action-catalog configuration is
present, while real notification mutation, replies, external automation, alarm/ringer changes, and
destructive history actions are deliberately inert and documented under DEV-005.

## Debug state contract

`replica_state` maps confirmed native screen IDs to deterministic routes, scroll offsets, local
fixtures, and overlays only when `BuildConfig.DEBUG` is true. Validation cold-starts every state to
prevent prior navigation or preferences from contaminating evidence.
