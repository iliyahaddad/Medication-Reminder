# Contributing

Thank you for contributing to Med Reminder.

## Before opening a pull request

Run:

```bash
./gradlew test lint detekt
```

Keep changes focused and include tests for scheduling, persistence, or backup changes.

## Reliability rules

Medication reminders are safety-sensitive software. Do not:

- introduce destructive database migrations,
- assume an alarm callback represents current state,
- remove validation from backup import,
- make notification actions non-idempotent,
- add network dependencies without a clear security/privacy justification.

## Pull requests

A good pull request should include:

1. A concise problem statement.
2. The implementation approach.
3. Tests added or updated.
4. Any migration or permission implications.
5. Manual verification steps for Android-specific behavior.

## Security

Do not commit:

- keystores,
- passwords,
- API tokens,
- local.properties,
- user medication data,
- generated private backups.

For security issues, use the repository's private security-reporting process when available.
