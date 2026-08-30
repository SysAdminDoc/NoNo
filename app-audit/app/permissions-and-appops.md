# Permissions and app operations

## Onboarding permissions observed

1. `POST_NOTIFICATIONS`: Android runtime dialog, granted. Evidence: `003_notification_permission_dialog`, updated package dump line 204.
2. Background execution/device-idle exemption: Android confirmation, granted; target present in user device-idle whitelist. Evidence: `005_background_access_system`, `evidence/package/device-idle-target-summary-20260801-1413.txt`.
3. Notification listener access: Android settings, high-risk confirmation, granted; listener live afterward. Evidence: `007_notification_access_system`, `008_notification_access_warning`, `009_notification_access_confirmation`, `notification-target-summary-20260801-1405.txt`.

## Requested permissions reported by Android

`POST_NOTIFICATIONS`, fine/coarse/background location, notification policy access, foreground service and foreground-service special use, boot completed, high-sampling-rate sensors, Bluetooth/Connect, ignore battery optimizations, write secure settings, full-screen intent, network/Wi-Fi state, promoted notifications, fingerprint/biometric, exact alarm, vibrate, Tasker run-tasks, query all packages, wake lock, and the app's signature-only dynamic-receiver permission. Evidence: `dumpsys-package-20260801-140502.txt` lines 148-173.

`CONFIRMED`: At final capture, `POST_NOTIFICATIONS` was granted. Fine, coarse, and background location and `BLUETOOTH_CONNECT` remained denied. Evidence: package dump lines 203-208.

## AppOps snapshot

The target-only AppOps snapshot reported background execution and vibration allowed, foreground service use running, restricted settings at default, media writes denied, and notification-listener rapid clear allowed. Most privacy-sensitive operations were `ignore`. Evidence: `evidence/package/appops-20260801-140502.txt`.

AppOps are device/time specific and do not establish that every associated feature was executed.

## Reconstruction requirements

- Request permissions only at the feature boundary and preserve the three-step onboarding progress model.
- Implement explicit denial and return-from-settings checks; the observed completed cards use a yellow check and muted surface.
- Treat notification listener, full-screen intent, exact alarm, DND policy, location, Bluetooth, accessibility, and battery exemption as independent capabilities.
- Never infer a granted capability from request permission presence alone.
- Accessibility-service use is optional/workaround-specific in the observed package and was not enabled in the audited baseline.
