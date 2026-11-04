# Implementation roadmap and phase gates

Each phase ends with implemented scope, changed-file list, decisions, command outcomes, unresolved issues, and one concrete next step. A successful cached starter build does not certify new dependencies or platforms. No phase proceeds past unresolved critical build failures for the code it introduces.

## Phase 1 — Architecture and planning

Completed: inspected the starter and environment; checked upstream version/compatibility sources; proposed modules, entities, schema, monetary/time/recurrence policies, navigation, visual direction, and verification gates.

Files added:

- `docs/architecture.md`
- `docs/database-schema.md`
- `docs/implementation-roadmap.md`

No application source, dependency pins, or existing README changed. This phase presents the plan before implementation.

Validation attempted on 2026-11-04:

```sh
./gradlew --offline :shared:jvmTest :androidApp:assembleDebug :desktopApp:assemble --console=plain
```

Result: wrapper failed before configuration because `/home/Maksym/.gradle/.../gradle-9.6.0-bin.zip.lck` is on a read-only filesystem.

Retried the installed Gradle 9.6.0 executable with `--gradle-user-home /tmp/pft-phase1-gradle --offline --no-daemon` and the same tasks. Result: initialization failed creating FileLockContentionHandler: `Could not determine a usable wildcard IP for this machine.` No compilation or tests executed. Neither result is evidence of a source compilation defect or a successful baseline. Do not modify financial code to work around host restrictions. No approval was requested.

Other findings: JDK 21 is installed; existing Kotlin 2.4.21/Compose 1.12.2 pins match current upstream releases; AGP 9.5.0 exceeds Kotlin's documented compatibility matrix; Material 3 currently uses an alpha. Linux cannot run Xcode/iOS simulator validation. Git metadata is absent, so no status/history or commits can be reported.

Next: Phase 2 toolchain/dependency spike, beginning with the supported AGP pin and a working writable Gradle execution environment. Resolve that gate before implementing features.

## Phase 2 — Foundation

1. Preserve the platform hosts, introduce `domain` and `data`, and keep `shared` as UI/composition root.
2. Pin and resolve the proposed versions; prove SQLDelight/new Android plugin compatibility and stable Material/navigation candidates. Configure Kotlin, Android, JVM, and iOS compiler targets coherently.
3. Wire the explicit AppGraph and platform drivers. Create the initial schema/seed category metadata, schema version baseline, migration checks, and one real persistence round-trip test. No sample financial records in production storage.
4. Add typed navigation, lifecycle scopes, theme tokens, light/dark surfaces, and accessible form/layout primitives. Build a real first-account empty state; do not ship inactive feature buttons.
5. Add CI for JVM/domain/data tests and Android build/lint, plus macOS iOS framework/simulator tests. Record exact JDK/Xcode/SDK pins, dependency verification metadata, and lock behavior for supported configurations.

Exit: Android debug assembly, Desktop assembly/run smoke, iOS framework link/simulator launch, navigation back/restore, database create/open/close, and dependency graph pass. Tests need actual platform coverage; any unavailable runner remains an explicit gate.

## Phase 3 — Core domain

Implement Money/CurrencySpec, checked arithmetic and parsing, rate conversion, typed IDs/errors, calendar periods, transaction/account/category/budget models, transfer policies, balance projections, recurrence schedule generation, and meaningful use cases against repository contracts. Add tests alongside each rule.

Exit: deterministic common tests pass on JVM and iOS; Android host test coverage verifies shared code integration. Include JPY/USD/KWD-like precisions through explicit test currency specs; negative/zero/maximum values; excessive precision; rounding ties; mixed currencies; overflow; Jan/Dec boundaries; leap years; DST/travel; same/cross-currency transfers; fee classification; archived references; and stable recurrence anchors. Use deterministic randomized invariant tests and a JVM arbitrary-precision oracle for conversion.

## Phase 4 — Data layer

Implement repository operations, SQLDelight mappings and reactive snapshots, atomic writes, optimistic conflict handling, search/filter/sort/pagination, recurrence persistence, local rates, categories/tags, and real migration fixtures. Verify account balances after edits/deletes and transactions that move between accounts/categories/periods.

Exit: actual SQLite integration tests pass on JVM, Android, and iOS. Cover rollback after the first mutation, missing foreign keys, stale edits, duplicate commands, concurrent recurrence callers, generation crash/retry, occurrence deletion/restore behavior, month queries, coherent Flow updates, and database reopen durability. No in-memory fake substitutes for SQLite transaction evidence.

## Phase 5 — User interface

Deliver usable slices: onboarding/accounts → transaction editor/details/history → transfers → category management → overview. Connect every displayed value to repositories. Use screen-specific immutable UiState, lifecycle ViewModels, saved drafts/route IDs, input validation, and loading/empty/error/success states. Include currency-aware search/sorting and accessible detail navigation. Subsequent budget/insight/recurrence screens land with their real functionality in Phase 6.

Exit: ViewModel state transition tests for load, success, failure/retry, cancellation, duplicate-save prevention, conflict, and live updates; Compose interaction tests for critical paths; manual Android/iOS/Desktop review for keyboard, touch, back behavior, focus, large text, dark/light, and resize. No fake charts or dead controls.

## Phase 6 — Advanced features

Connect budget/month logic, analytics series/comparisons, recurrence catch-up and management, manual-rate net worth, CSV export, JSON backup, and staged restore. Foreground generation is mandatory; background adapters are optional until independently verified. Expose recurrence errors and net-worth rate coverage in the UI.

Exit: real transaction fixtures yield independently expected reports; transfers never inflate income/expense; cross-month edits update budgets; long missed periods catch up without duplicates; missing FX is visible; CSV quotes/newlines/non-ASCII/formula-like text are safe; logical backup round-trips all entities and preserves balances/occurrence identities; malformed/future/corrupt backup and interrupted restore leave live data intact.

## Phase 7 — Quality assurance

Run the complete platform matrix, release assembly/lint, migration suites, static checks, critical UI flows, and accessibility review. Measure startup, a representative 100k-row history, pagination, query plans, Flow churn, and recurrence batches on documented hardware. Fix regressions before expanding tests further. Inspect logs and exports for accidental sensitive data; verify process restart, low-storage/storage-error recovery, and cancellation behavior. Remove generated greeting code and unused dependencies when replaced, not just for a cosmetic diff.

Exit: no critical build/test failures, no known balance/idempotency defects, and a documented list of supported platforms and any noncritical limitations. Release packaging is checked on the host OS needed for each package; unsigned artifacts are labeled honestly.

## Phase 8 — GitHub presentation

Replace the starter README with an honest feature/status overview, setup/run commands, architecture diagram, schema links, financial policy examples, test commands/results, and platform limitations. Add CONTRIBUTING, an MIT license with correct attribution, ADR summaries for the major choices, and roadmap. Capture actual application screenshots in light/dark and compact/wide layouts using an explicitly selected demo dataset isolated from user storage. No mockup presented as an application screenshot. Add release/build evidence without inventing CI badges or results.

Suggested real commit sequence (commit only completed, validated slices):

1. `docs: define architecture and financial invariants`
2. `build: establish multiplatform foundation and verification`
3. `feat(domain): add exact money and transfer policies`
4. `feat(data): persist atomic transactions and account projections`
5. `feat(ui): add accounts and transaction workflows`
6. `feat: add budgets and transaction-derived insights`
7. `feat: generate recurring transactions idempotently`
8. `feat: add export and validated backup restore`
9. `test: verify migrations and platform release flows`
10. `docs: publish setup, screenshots, and engineering decisions`

Do not manufacture historical commits. Initialize Git in Phase 2 if this directory is intended to become the repository; publication remains a separate action.

## Test obligations across phases

| Area | Essential evidence |
| --- | --- |
| Money | Exact parse/format, scale mismatch, Long boundaries, checked aggregates, rejected precision loss. |
| Conversion | Directed rates, differing scales, tie rounding, inverse precision, stale/missing quote, explicit range failure, oracle comparison. |
| Transactions/accounts | Positive magnitudes, legal kinds/categories, opening balance, both transfer legs, fees, updates/deletes, negative balances. |
| Budgets/reports | Selected currency/category/date range, zero previous period, empty period, same-month vs boundary edits, transfer exclusion. |
| Recurrence | Every unit/interval, leap day, month-end anchor, fixed zone, missed periods, pause/edit/cancel, duplicate execution and deletion. |
| Repository/database | Real drivers, rollback, concurrency/revisions, migrations, persisted reopen state, Flow commit visibility, pagination. |
| Presentation | Loading/empty/content/error, save/retry/cancel/conflict, preserved input, navigation restore, accessibility semantics. |
| Portability/recovery | Android/iOS/Desktop lifecycle and file UI, process restart, corrupted backup, restore atomicity, recurrence tombstones. |
