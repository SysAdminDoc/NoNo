# Application identity

| Field | Finding | Classification | Evidence |
|---|---|---|---|
| Visible label | BuzzKill | CONFIRMED | `007_notification_access_system`, Android settings label |
| Product target | BuzzKill Notification Manager | CONFIRMED | Operator-supplied Play listing and target instruction |
| Package | `com.samruston.buzzkill` | CONFIRMED | `evidence/package/dumpsys-package-20260801-140502.txt` |
| Version name | 36.0.0 | CONFIRMED | package dump and `020_settings_scrolled_4` |
| Version code | 338 | CONFIRMED | package dump |
| Minimum SDK | 24 | CONFIRMED | package dump |
| Target SDK | 35 | CONFIRMED | package dump |
| Launch activity | `.ui.MainActivity` | CONFIRMED | `resolve-activity-20260801-140502.txt` |
| APK splits | base only | CONFIRMED | `apk-paths-20260801-140502.txt`, package dump |
| Install source | Android shell, installer package null | CONFIRMED | package dump |
| Supplied APK SHA-256 | `9758ccc2179e34c44fe1b665b5212e3c33c25c31e43de50cf0d4c42414843116` | CONFIRMED | host hash captured before install |

The APK was installed only for authorized dynamic observation. No DEX decompilation, resource extraction, or other static APK analysis was performed.

LiteAPKS promotional overlays appeared on launch/rotation from the supplied distribution artifact. Per operator instruction, they are excluded from native product identity and reconstruction scope. Evidence: `001_launch_default`, `084_cold_relaunch_persisted`, `086_rules_landscape_rotation`.
