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

The user-triggered self-test arms one random package/tag/id tuple, posts one local alerting
notification, and waits eight seconds for the real listener callback. `SignalNotificationListener`
checks that tuple before its normal self-package rejection. A match is consumed once and returns
before sanitization, rule evaluation, the bounded queue, History, observability events, and capture
counters. Every other notification from NoNo remains ignored.

`onNotificationPosted` checks `CaptureGate`, reads the payload for the lifetime of that callback,
and passes it to `NotificationRedaction.sanitizeNotification`. The sanitizer keeps title and body
out of everything it returns, pseudonymizes identifiers, and retains the platform metadata needed
for rule evaluation. A callback with no text is classified as unavailable because Android exposes
no supported redaction flag. The sanitized result goes to `NotificationIngestor`, a bounded
`Channel(64)` drained by a single worker: a full queue drops the newest event and increments a
counter rather than blocking the platform callback. The worker writes through
`insertAndPrune`, which inserts and applies the retention cutoff in one transaction, then asks the
widget to refresh.

Ingestion counters (persisted, dropped, failed, last failure time) are mirrored into Room so they
survive process death, and republished through `ListenerHealth` on the next start.

`CaptureDiagnostics` combines the live queue depth with durable totals and the last-capture clock.
Its plain-text report has only the app version, listener access and connection states, counters,
and relative capture age. Its type does not accept notification content or identifiers.

## State and persistence

- **Room** (`SignalDatabase`, version 11, `exportSchema = true`) holds notification metadata and a
  single-row diagnostics table. Migrations 1 to 11 add grouping, diagnostics, rule attribution,
  ranking metadata, pseudonym tracking, stars, and removal reasons;
  the exported schemas under `app/schemas` are packaged into the test APK so
  `SignalDatabaseMigrationTest` can replay them. The database file lives under `noBackupFilesDir`.
- `SignalDatabase.get` returns one instance per process. Room's invalidation tracker only notifies
  observers registered on the instance that performed the write, so the listener, the view model,
  and the widget must share one or the history flow never updates.
- **DataStore** holds onboarding state, the rule list (`RuleStore` version 5, encoded by
  `RuleCodec`), and every observed settings value. It also lives under `noBackupFilesDir`, and a
  corrupt store is replaced with defaults rather than crashing the process.
- The selected retention period is applied on every insert and again when the setting changes.

## Evaluation

`RuleEvaluation` is pure. It takes saved rules, the callback payload, and sanitized metadata, then
returns per-condition traces with deterministic specificity and priority conflict resolution. The
typed `MetadataCondition` subtypes cover channel pseudonym, importance, category, conversation,
ongoing, and group-summary state. Missing metadata fails closed and reports that it was unavailable.
Free-string extras from stores written before version 5 remain unsupported, visible, and blocking.

Ordinary rules do not see group summaries. A rule must explicitly test summary state to opt in,
and summary rows remain excluded from counts. The action result is always `NOT_EXECUTED`; nothing
in the evaluator touches the notification manager, a `PendingIntent`, or the ringer.

## Transfer

`RuleTransfer` writes the rule list, and nothing else, through the Storage Access Framework. Neither
settings nor history are included.
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
