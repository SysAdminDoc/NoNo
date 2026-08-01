# Deterministic replica states

Debug builds accept the intent extra `replica_state` with any native audit screen ID. The supplied launcher and capture scripts cold-start the activity so persistence cannot overwrite the requested fixture.

Example:

```powershell
.\scripts\launch-replica.ps1 -Serial emulator-5554 -State 029_rule_builder_default
```

The mapping deliberately excludes the LiteAPKS/repackaging overlay. Android permission/settings and browser screens are launched through public intents and are not imitated inside the app.

The state selector is guarded by `BuildConfig.DEBUG`; a non-debug build ignores the extra.
