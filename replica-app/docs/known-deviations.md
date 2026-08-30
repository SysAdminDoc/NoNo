# Known deviations

These deviations are intentional and remain traceable throughout validation.

| ID | Area | Deviation | Reason | Validation handling |
|---|---|---|---|---|
| DEV-001 | Identity | Product name is **NoNo**, package is `com.sysadmindoc.nono`, and launcher art is original. | No authorization to reuse BuzzKill identity or artwork. | Mask/discount identity-only regions; verify geometry and behavior separately. |
| DEV-002 | Typography | Uses Android's legal system sans-serif family with rounded presentation where available. | Exact original font family was not confirmed or licensed. | Compare size, weight, line wrapping, and position; record residual glyph differences. |
| DEV-003 | Illustrations | Explore/onboarding art uses code-native abstract replacements. | Original illustrations and photographs are not authorized. | Compare occupied bounds, aspect ratio, color role, and hierarchy, not pixels. |
| DEV-004 | Accessibility | Interactive targets are at least 48 dp and builder tokens expose separate semantic actions. | Audit observed some ~44.2 dp boxes and merged sentence semantics. This is an intentional accessibility improvement. | Record small layout shifts and run semantics tests. |
| DEV-005 | Runtime actions | Notification-changing actions are deterministic local simulations. | A production notification engine/backend was not observable and real side effects are outside safe reconstruction validation. | Validate configuration, confirmation, local history, and feedback only. |
| DEV-006 | External content | Help/articles use safe explanatory local content and optional browser handoff. | Browser content was outside native audit scope and may be copyrighted or mutable. | Validate handoff intent and back-stack only. |
| DEV-007 | Loading/error | No extra network loading/error screen is added. | The audit confirmed no reproducible native loading spinner or network error state. | Treat those states as not applicable instead of inventing behavior. |
| DEV-008 | Android system UI | Permission and settings prompts are produced by the current Android image. | System-controlled UI cannot and should not be cloned. | Validate the public intent and return behavior, not system-screen pixels. |
| DEV-009 | Repackaging | LiteAPKS advertising/promotional overlays are omitted. | Explicitly excluded by the user and audit. They are not part of the native app. | No replica state or asset is created. |
| DEV-010 | Signing | Uses only the standard throwaway Android debug key. | Required for local installation; real signing is prohibited. | Debug build only. |
| DEV-011 | Backup and shortcuts | Versioned JSON import/export is implemented with conflict preview; scheduled backup and launcher-shortcut publication remain unavailable. | The audit did not safely execute scheduled or shortcut side effects. | Import/export round-trip and conflict preview are unit-tested; unavailable controls remain explicit. |
