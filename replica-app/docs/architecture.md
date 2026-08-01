# Reconstruction architecture

## Runtime shape

- One Android application module and one edge-to-edge `MainActivity`.
- Kotlin and Jetpack Compose with custom observed color/spacing/typography tokens.
- An `AndroidViewModel` exposes immutable `UiState` through `StateFlow`.
- Routes and overlays are explicit enums, which keeps normal back navigation and debug evidence routing deterministic.
- DataStore retains onboarding, one primary reconstructed rule, enabled state, identity fields, every observed selection-dialog value, and every observed Settings switch.
- `SignalNotificationListener` is a least-privilege public Android listener surface. It stores only package name, timestamp, and a counter; it does not log/transmit payload text or execute destructive actions.

## Clean-room boundaries

This architecture is a recommendation, not an inference about the original application's internals. The project does not depend on the original APK, package name, resources, signing identity, private storage, network endpoints, or implementation technology.

The original product is much broader than the safe runtime action simulator in this reconstruction. All action-catalog configuration is present, while real notification mutation, replies, external automation, alarm/ringer changes, and destructive history actions are deliberately inert and documented under DEV-005.

## State and persistence

The sentence builder stores a draft separately from persisted rules. Save validates the action and uses the exact observed missing-field message. Saved enabled/disabled state persists across activity recreation and process relaunch. Settings choices and switches write values to DataStore and were verified after a force-stop/relaunch.

## Debug state contract

`replica_state` maps confirmed native screen IDs to deterministic routes, scroll offsets, local fixtures, and overlays only when `BuildConfig.DEBUG` is true. Validation cold-starts every state to prevent prior navigation or preferences from contaminating evidence.
