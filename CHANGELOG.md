# Changelog

## Unreleased

### Fixed
- Sound/vibration settings had no effect on Android 8+ (they are channel properties); dose channels now exist per sound/vibration combination.
- Status-bar alarm icon fired the dose broadcast early; it now opens the app.
- Snoozed and repeat reminders were lost after reboot/time change; they are re-armed by the maintenance pass.
- Snoozed doses could be marked MISSED while the snooze was still pending.
- Taking/skipping/snoozing or deleting from inside the app left the notification and reminder alarms alive.
- `NotificationActionReceiver` could crash on unexpected errors; `MaintenanceWorker` no longer swallows cancellation.
- Integral doses rendered as `5.0`; now `5`.
- Detekt plugin was requested twice with a version (Gradle configuration error).
- Release builds without signing secrets were signed with the debug key; they are now left unsigned.
- Removed `USE_EXACT_ALARM` (Play-restricted); the app uses `SCHEDULE_EXACT_ALARM` with a runtime fallback.

### Changed
- `MainActivity` no longer shows over the lock screen by default; it does so only when opened from a full-screen alarm notification.

### Added
- All UI text moved to string resources (English); CI caching/artifacts, tag-driven release workflow, Dependabot, issue/PR templates, `.editorconfig`, kotlinx.serialization R8 rules, formatting unit test.

## 1.0.0 — Professionalized baseline

- Restored missing Android application/bootstrap components.
- Added Hilt dependency graph and Room/DataStore providers.
- Added Compose UI for Today, Medications, Statistics, and Settings.
- Added medication creation, pause/resume, deletion, and dose actions.
- Added notification action receiver.
- Added boot/timezone/clock-change maintenance receiver.
- Added WorkManager maintenance worker.
- Added Room migration registration.
- Fixed medication edit scheduling so unrelated medication doses are not deleted.
- Fixed inclusive medication end-date scheduling.
- Fixed Snooze notification action cancellation order.
- Preserved `repeatCount` through domain/backup mappings.
- Made backup restore transactional.
- Added stricter backup validation.
- Replaced the broken Jalali conversion implementation with a tested break-year algorithm.
- Added unit tests for scheduling and Jalali conversion.
- Added release signing through environment/local properties.
- Added Gradle wrapper bootstrap scripts.
- Added professional documentation.
