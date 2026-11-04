# Contributing

Start with [the architecture](docs/architecture.md), [current schema](docs/database-schema.md), and [verification status](docs/verification.md). This is a manually managed cash-account tracker. Financial rules belong in `domain`, persistence in `data`, and UI state/interaction in `shared`.

Use constructor injection and lifecycle-owned coroutine scopes. Keep monetary values as checked integer minor units, and retain explicit currency and business-date semantics. Never replace a financial error with zero or silently drop an unrepresentable amount. Use cases and database commits must remain atomic.

For a change, add a test that could fail for a plausible bug, run the relevant Gradle tests, and include commands/results in the PR. Database changes require a numbered migration, a historical schema fixture, and data-preservation tests. Backup format changes require explicit version handling. Native adapters need validation on their platform; JVM success does not establish iOS correctness.

Use small commits that explain a completed behavior, for example `fix: preserve recurrence anchors after February` or `feat: validate backup references before restore`. Do not invent historical commits or claim unrun tests. Screenshot fixtures must use isolated sample data, never a contributor's real finances.

PR descriptions should state the user-visible behavior, important financial/concurrency decisions, and verification evidence. Keep dependency additions justified and centrally pinned in `gradle/libs.versions.toml`.
