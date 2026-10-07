# Security Policy

## Scope

Med Reminder is an offline-first medication reminder. Its most sensitive assets are:

- medication names and dosages,
- schedules,
- adherence history,
- stock information,
- locally exported backups.

## Security principles

- No network permission.
- No analytics or advertising SDKs.
- Explicit backup/restore.
- Strict backup validation.
- Room foreign keys and unique constraints.
- No destructive migration fallback.
- No release signing secrets in source control.
- R8 enabled for release builds.

## Reporting a vulnerability

Please do not publish sensitive vulnerability details in a public issue.

Contact the project maintainer through the private security channel associated with the repository and include:

- affected version,
- Android version,
- reproduction steps,
- impact,
- proof-of-concept where appropriate.

Do not attach real patient or medication data.
