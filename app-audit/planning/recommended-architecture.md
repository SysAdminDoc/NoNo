# Recommended Android architecture

Everything in this document is a reconstruction recommendation, not a claim about the original app's internals.

## Platform

- Kotlin, current stable Android Gradle Plugin at rebuild time, minimum SDK 24, target current policy-compliant SDK.
- Jetpack Compose for new UI, with a small interoperability layer for Android settings/permission intents and any APIs that require Views.
- Single-activity navigation with typed routes and saved-state handling.
- Material primitives may provide semantics/input plumbing, but visible components should use the measured custom tokens rather than default Material appearance.

## Modules

- `app`: navigation shell, capability orchestration, dependency wiring.
- `core-design`: tokens, typography, icons, sentence renderer, reusable components.
- `core-model`: Rule, FilterExpression, ActionDefinition, HistoryItem, SettingsProfile.
- `core-data`: Room persistence, DataStore preferences, import/export adapters.
- `feature-onboarding`, `feature-rules`, `feature-history`, `feature-explore`, `feature-settings`.
- `runtime-notifications`: NotificationListenerService, channel setup, safe action executor.
- `integrations`: Android settings, DocumentsUI, shortcuts/tiles, optional Tasker/MacroDroid/Wear adapters.

## State model

- Immutable UI state + event/reducer ViewModels per route.
- Builder state is a draft Rule separate from persisted rules.
- Recursive sealed FilterExpression (`Operator`, `Phrase`, `Extra`, `Group`).
- Action catalog is data-driven; each action declares configuration schema, required capabilities, execution risk, icon/color, and experimental status.
- Permission/capability state is queried on resume; onboarding never trusts a prior click.

## Persistence

- Room for rules/history/activity with explicit migrations.
- DataStore for theme/language/settings and onboarding hints.
- Store raw notification content only according to the selected history/privacy policy.
- Apply retention pruning transactionally and test 30-day/3-month/6-month boundaries.
- Version import/export schema; preview conflicts before mutation.

## Runtime safety

- Notification actions execute through a capability-checked command layer with idempotency keys and duplicate-action protection.
- Never run automatic reply/open/button actions without explicit rule configuration and visible warnings.
- Isolate Tasker/MacroDroid/Wear adapters from core rule evaluation.
- Use WorkManager only for deferrable work; exact alarms/full-screen intents require separate platform-policy handling.

## Testing

- Golden screenshot tests at 411 × 914 dp and landscape 914 × 411 dp.
- Unit tests for sentence rendering, filter evaluation, action validation, retention, persistence migrations.
- Contract tests with fake notifications/actions before device tests.
- Instrumented permission-return, listener reconnect, process-death, and rotation tests.
- Accessibility tests for token semantics, 48 dp targets, 200% font scale, contrast, TalkBack announcements.

## Security/privacy

- Minimize retained notification text; expose clear privacy mode/history controls.
- No secrets in logs; redact notification text in diagnostics by default.
- Avoid broad permissions unless a reconstructed feature requires them.
- Treat provider/file exports as explicit least-privilege surfaces.
