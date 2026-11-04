# Implementation decisions

This document records the implemented choices relative to the original [Phase 1 proposal](architecture.md). Build verification limits are documented separately in [verification](verification.md).

## Module graph and ownership

`domain` contains immutable financial models, validation, projections, a repository contract, and meaningful write operations. `data` implements SQLDelight storage, explicit JSON mapping, CSV export, and atomic repository state. `shared` contains the Compose application, screen state, editors, and native document adapters. Platform hosts remain thin. The lifecycle ViewModel owns repository initialization and observes the committed StateFlow; configuration changes retain that owner.

Constructor injection is sufficient for this graph. The app never installs a global container. Clock injection in FinanceService makes due dates deterministic in tests. Coroutine ownership is explicit; cancellation is rethrown. The repository serializes transforms with a mutex and SQL commits, then emits a complete validated snapshot. A global optimistic revision rejects stale forms and cross-instance writes.

## Technology pins

Kotlin 2.4.21, Compose 1.12.2, AGP 9.3.1, Gradle 9.6.0, JDK 21, Coroutines 1.11.0, and SQLDelight 2.2.1 are configured. The Compose compiler matches Kotlin. The Android KMP library plugin is used instead of the deprecated Android target plugin combination.

The implementation uses datetime 0.7.1 and serialization JSON 1.8.1, both available for local compiler checks, rather than upgrading to the proposal's newer candidates. JSON trees are mapped explicitly, so domain entities do not acquire serialization annotations or compiler-plugin coupling. Material 3 remains the starter's 1.12.0-alpha03, listed in Compose 1.12.2's component matrix; the proposed stable alternative could not be resolved here. That pre-release dependency remains a release risk, not a claim of a stable-only dependency graph.

Navigation uses a typed Screen enum, saveable per-screen state, native NavigationEventHandler back integration, and Material dialogs with discard confirmation. This small graph does not need Navigation 3's full stack machinery. Desktop Escape returns to Overview and Ctrl+N opens an entry. Native back/gesture behavior must still be checked on devices.

## Storage and schema

Core financial records are normalized. Tags are a bounded JSON string set on a transaction, and recurring templates are versioned through the backup mapping. They are value objects, not independently editable entities, so separate tag identity tables would not currently provide value. Account/category relationships and occurrence uniqueness have SQLite constraints; currency compatibility and other cross-row rules are validated inside the repository commit boundary. See [schema](database-schema.md).

Regular writes diff immutable snapshots and insert/update only changed rows. Replace-restore explicitly clears and rebuilds the graph inside one transaction. Database state is never optimistically published before persistence succeeds. No cached balance column exists. SQLDelight queries remain private to data; other layers cannot mutate rows behind the repository.

A snapshot loads all records for coherent local reports. History renders 50 rows at a time, but filtering and sorting currently happen in memory. This is **not database keyset pagination**. Large-ledger query paging and incremental aggregates are a documented scalability follow-up; no unmeasured 100k-row performance claim is made.

## Money and time

Money uses signed Long minor units and a fixed supported-currency enum (including 0-, 2-, and 3-decimal currencies). Parsing accepts invariant decimal text and rejects excess precision. Display uses comma grouping with a period decimal separator; locale-specific number entry is not yet implemented. Signed opening balances and negative account balances are supported; transaction magnitudes must be positive.

Conversion uses exact rational rates with reduced factors, checked integer operations, and half-even rounding. Intermediate ranges that cannot be represented produce an error, even if an arbitrary-precision implementation could compute the eventual result. The JVM oracle test exercises 10,000 deterministic cases. Chart coordinates are the only floating-point transformation of financial amounts.

Transfers record actual source and destination amounts, with source/timestamp for cross-currency execution. A bank fee is recorded as its own expense. There is no automatic linked-fee workflow. Rates used for net worth are directed, timestamped, manually recorded quotes; missing quotes suppress the aggregate valuation. Rates older than seven days are flagged. Income/expense/budget reports remain currency-specific, and full-month comparisons are explicit. Investment cost basis and realized FX gains are not modeled.

Business dates are stored independently from audit instants. Recurrence uses an anchor date, calendar unit/interval, zone, and ordinal cursor. Computing each date from the anchor avoids February drift. Occurrence identities survive transaction deletion and backup restore. Generation runs on startup, resume, and every minute while the ViewModel is active, in bounded batches. Native background execution is not claimed. Pausing preserves the cursor; changing an existing calendar requires a new schedule, while amounts/descriptions can be edited.

## Recovery and privacy

Backups are versioned JSON snapshots with monetary integers encoded as strings. Import is bounded to 20 MB, validates references and financial rules, and presents replacement counts before confirmation. CSV escapes fields and protects user text from spreadsheet formula execution. Native file dialogs return cancellation distinctly from success. Android automatic app backup is disabled; JSON exports are user-controlled plaintext. There is no application-level encryption or synchronization claim.

## Delivery status

The feature implementation and local JVM/SQLite checks are present. SQLDelight generation, full Gradle resolution, Android assembly, iOS compilation, and real-device interaction are still release gates. Source code and recorded local checks are not proof that these gates passed. See the exact commands and results in [verification](verification.md).
