# Med Reminder

A privacy-first, offline Android medication reminder built with Kotlin, Jetpack Compose, Room, DataStore, Hilt, WorkManager, and AlarmManager.

Med Reminder is designed around one principle: **the database is the source of truth, while Android alarms are a delivery mechanism**. If the process is killed, the device reboots, the clock changes, or an old alarm fires, the app re-checks current state before taking action.

> **Medical disclaimer:** Med Reminder is a reminder and tracking tool. It does not provide medical advice, diagnose conditions, or decide whether a medication should be taken. Always follow the instructions of a qualified healthcare professional.

---

## Highlights

- Offline-first; no `INTERNET` permission.
- Medication CRUD with active/paused lifecycle.
- Daily medication scheduling in the current device timezone.
- Weekly, every-N-days, every-N-hours, cyclic, and as-needed schedule models.
- Deterministic dose IDs and database uniqueness for idempotent scheduling.
- Exact AlarmManager scheduling when Android permits it, with an inexact fallback.
- Notification actions: **Taken**, **Snooze**, and **Skip**.
- Repeat reminders with a configurable repeat budget.
- Missed-dose detection through WorkManager.
- Stock tracking and low-stock notifications.
- Optional full-screen alarm presentation.
- Jalali/Persian calendar conversion for presentation.
- Versioned JSON export/import through Android's Storage Access Framework.
- Strict backup validation before data replacement.
- Room migrations without destructive fallback.
- Dark/light/system theme support.
- Unit tests for schedule calculation and Jalali conversion.
- R8/minified release configuration.
- Environment/local-properties based release signing; no signing secrets are committed.

---

## Technology Stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Scheduling | AlarmManager |
| Background reliability | WorkManager |
| Serialization | kotlinx.serialization |
| Date/time | kotlinx-datetime + `java.time` |
| Build | Android Gradle Plugin 8.7.3 |
| Gradle | 8.9 |
| Java | 17 |
| Compile SDK | 35 |
| Minimum SDK | 26 |
| Target SDK | 35 |

---

## Architecture

The project follows a layered architecture:

```text
UI / ViewModel
      |
      v
Domain Use Cases
      |
      +--------------------+
      |                    |
      v                    v
Repositories         Schedule Calculator
      |                    |
      v                    |
Room / DataStore           |
      |                    |
      +----------+---------+
                 |
                 v
        AlarmManager / Notifications
```

### Package layout

```text
app/src/main/java/com/medreminder/
├── data/
│   ├── backup/
│   ├── local/
│   │   ├── db/
│   │   ├── entity/
│   │   └── migration/
│   ├── mapper/
│   └── repository/
├── di/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── notification/
├── scheduler/
├── ui/
├── util/
└── worker/
```

The scheduling engine is deliberately Android-free and can be unit tested independently.

---

## Scheduling Model

### Floating local time

Daily and weekly schedules represent **wall-clock time**.

For example:

```text
08:00 every day
```

means 08:00 in the device's current timezone. This is intentional for medication reminders: the user generally expects the reminder to follow local clock time when travelling.

`EveryNHours` is the exception. It is anchored to an absolute instant because an hourly interval is naturally elapsed-time based.

### End dates

Medication `endDate` is user-facing and **inclusive**.

Internally, the scheduler converts it to an exclusive upper boundary. This prevents the final intended dose from disappearing one day early.

### Idempotency

Each scheduled occurrence receives a deterministic ID based on:

```text
medicationId + scheduledAtMillis
```

The Room database also enforces:

```text
UNIQUE(medication_id, scheduled_at)
```

Scheduling the same medication multiple times therefore does not create duplicate dose rows.

---

## Reliability

The app has several independent recovery paths.

### 1. Alarm receiver re-checks state

An alarm is never trusted blindly.

Before displaying a notification, the receiver checks:

1. Does the dose still exist?
2. Is its status still `SCHEDULED`?
3. Does the medication still exist?
4. Is the medication still active?
5. What are the current notification/repeat settings?

This makes stale alarms safe.

### 2. Boot and system changes

`BootReceiver` queues maintenance work after:

- device boot,
- application replacement/update,
- system clock changes,
- timezone changes.

### 3. Daily maintenance

`MaintenanceWorker`:

- marks sufficiently old unacknowledged doses as missed,
- rebuilds future medication occurrences,
- checks stock levels,
- warns when exact alarms are unavailable.

### 4. Exact alarm fallback

When exact-alarm access is unavailable, the scheduler does not silently abandon reminders. It falls back to a bounded inexact alarm window.

For medications where precise timing matters, the user should enable **Alarms & reminders** for the app.

---

## Notifications

Each dose notification provides:

- Taken
- Snooze
- Skip

Actions are handled by a dedicated broadcast receiver and are idempotent.

A successful **Taken** transition decrements tracked stock exactly once.

A **Skip** transition never decrements stock.

A **Snooze** transition preserves the scheduled dose and creates a new one-shot reminder.

---

## Backup and Restore

Backup uses the Android Storage Access Framework.

### Export

The user explicitly selects a destination and receives a JSON document containing:

- backup schema version,
- export timestamp,
- medications,
- dose history.

### Import

The complete file is validated before the database is changed.

Validation includes:

- schema version,
- record-count limits,
- positive IDs,
- duplicate IDs,
- duplicate medication/time occurrences,
- orphan dose detection,
- timestamp validation,
- repeat-count bounds,
- medication field validation.

Replacement is performed inside a single Room transaction so medication and dose tables cannot be left half-restored by a normal import operation.

No storage permission is required.

---

## Privacy

The application intentionally has no network stack.

There is no:

- analytics SDK,
- Firebase dependency,
- advertising SDK,
- cloud API,
- account system,
- network permission.

Medication data is stored locally.

The app uses explicit JSON backup/restore rather than silently uploading medication history.

Android system backup is disabled in the manifest so the application's explicit backup flow remains the predictable way to export data.

---

## Permissions

The application may request or declare permissions required for reliable Android reminders:

- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM`
- `USE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK`
- `VIBRATE`
- `USE_FULL_SCREEN_INTENT`

The exact-alarm permission is particularly important on modern Android versions.

Full-screen alarm presentation is optional and should be enabled only when the user wants alarm-style interruption.

---

## Building

### Requirements

Install:

- Android Studio with a recent Android SDK
- Android SDK Platform 35
- Android Build Tools 35.x
- JDK 17

Open the **repository root**, not the `app/` directory.

### Command line

On macOS/Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The project contains a lightweight wrapper bootstrap. If `gradle-wrapper.jar` is not present, the wrapper script downloads the Gradle 8.9 wrapper bootstrap JAR and then uses the pinned Gradle distribution.

### Unit tests

```bash
./gradlew test
```

### Lint

```bash
./gradlew lint
```

### Detekt

```bash
./gradlew detekt
```

### Release

```bash
./gradlew assembleRelease
```

Release builds use R8 and resource shrinking.

---

## Release Signing

Signing secrets must never be committed.

The Gradle build accepts these environment variables:

```text
MEDREMINDER_KEYSTORE_PATH
MEDREMINDER_KEYSTORE_PASSWORD
MEDREMINDER_KEY_ALIAS
MEDREMINDER_KEY_PASSWORD
```

For local development, the same values may be stored in an untracked `local.properties`.

If no release signing material is supplied, the project uses the debug signing configuration so a reproducible release build can still be produced for testing.

For a public release, always configure a dedicated release keystore and protect it outside the repository.

---

## Database Migrations

Room currently uses schema version `2`.

The project intentionally does **not** use:

```kotlin
fallbackToDestructiveMigration()
```

The current migration is:

```text
1 -> 2
```

which adds:

```text
dose_logs.repeat_count
```

Future schema changes should add an explicit migration and keep exported Room schemas under:

```text
app/schemas/
```

---

## Testing Strategy

The highest-risk logic is kept outside Android UI classes.

Tests cover:

- future-only schedule generation,
- inclusive medication end dates,
- deterministic dose IDs,
- Jalali/Gregorian conversion,
- Jalali leap-year handling.

For future development, the recommended next tests are:

1. DST transition scheduling.
2. Every-N-hours schedules across timezone changes.
3. Notification action races.
4. Room migration tests.
5. Backup validation and restore tests.
6. WorkManager maintenance tests.
7. Compose UI tests for medication creation and dose acknowledgement.

---

## Code Quality

The project includes:

- Detekt configuration,
- strict Room migrations,
- R8 release shrinking,
- explicit dependency versions,
- no hard-coded signing secrets,
- deterministic scheduling IDs,
- repository/domain separation,
- pure scheduling calculations,
- backup schema versioning.

Run the complete local quality pass with:

```bash
./gradlew test lint detekt
```

---

## Development Guidelines

### Do

- Keep scheduling calculations pure.
- Treat Room as the source of truth.
- Validate untrusted backup input before writing.
- Add a Room migration for every schema change.
- Keep notification actions idempotent.
- Use dependency injection instead of service locators.
- Add tests for every scheduling edge case.

### Do not

- Add network access without a documented privacy/security reason.
- Store signing keys in Git.
- Use destructive Room migrations.
- Assume an AlarmManager callback is current state.
- Decrement stock before confirming the dose transition.
- Treat a notification as proof that a dose is still scheduled.

---

## Known Android Platform Constraints

Android manufacturers can apply aggressive background restrictions. Exact alarms, notifications, battery optimization, and full-screen behavior are ultimately controlled by the operating system and, on some devices, by OEM-specific power-management policies.

The application therefore uses multiple safety mechanisms rather than relying on one background execution path.

---

## Roadmap

Potential future improvements:

- Rich medication editor for every schedule type.
- Medication images and custom icons.
- Family/caregiver mode without cloud synchronization.
- Home-screen widgets.
- Wear OS companion.
- Accessibility-focused large-text alarm screen.
- More detailed adherence reports.
- CSV export.
- Optional encrypted backup files.
- Comprehensive DST/timezone test matrix.
- Automated screenshot/UI regression tests.

---

## License

MIT License. See [`LICENSE`](LICENSE).

---

## Version

Current application version:

```text
1.1.2
```

This project is intended to be a solid offline-first foundation that can be extended without compromising medication-data privacy or scheduling reliability.

## Building

```bash
# One-time: generate the wrapper JAR if it is missing (needs Gradle 8.9+ installed)
gradle wrapper --gradle-version 8.9
./gradlew test lint detekt assembleDebug
```

Release signing reads `MEDREMINDER_KEYSTORE_PATH`, `MEDREMINDER_KEYSTORE_PASSWORD`,
`MEDREMINDER_KEY_ALIAS`, `MEDREMINDER_KEY_PASSWORD` from the environment or `local.properties`.
Without them the release APK is produced **unsigned**.

## Known limitations

- No instrumented (androidTest) suite yet; scheduling logic is covered by JVM unit tests.
- `cancelFor/cancelAll` cannot enumerate PendingIntents; stale alarms are neutralised at delivery by re-checking the database.
