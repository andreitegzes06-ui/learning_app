# Requirements-to-implementation checklist

Status on 15 November 2026: focus workflow implemented following M1/M2 and the JSON correction. `[x]` means verified for the stated scope; `[ ]` or partial means remaining work is pending. Historical sections retain their original counts/scope. Current evidence: [focus verification](verification/focus-workflow.md), [focus contract](focus-workflow.md), [actual-code UML](uml-implemented.puml).

## Current focus milestone acceptance

- [x] R1: working Start/Pause/Resume/Finish/Stop, custom duration, 25/50-minute presets, readable countdown and progress.
- [x] R2: IDLE/RUNNING/PAUSED/COMPLETED/INTERRUPTED transitions; existing ACTIVE persistence reused.
- [x] R3: monotonic injected timing, fractional accumulation, pause exclusion, duplicate prevention, close interruption and checkpoint-only crash recovery.
- [x] R4: existing SQLite ledger persists profile, optional classroom/goal, timestamps, actual seconds, outcome and explicit demo flag; additive schema 3 preserves prior data.
- [x] R5: working persistent History with date/classroom/duration/outcome and mode/goal.
- [x] R6: committed-session Dashboard totals, current-zone completion-day totals, completed count and most recent outcome.
- [x] R7: dark JavaFX screen, responsive cards, background I/O, bounded scheduled ticks, save errors/retry and honest empty states.
- [x] R8: duration, references, transitions and duplicate validation; FocusModeException reused; SQL uniqueness/FKs retained.
- [x] R9: deterministic clock, lifecycle, recovery, rollback/save failures, reload and statistics tests; actual-window workflow test.
- [x] R10: explicit demo/testing mode permits real short sessions; real 10-second test saves exactly 10 seconds, never the standard preset duration.
- [x] Full build: 679 tests, zero failures/errors/skips including opt-in GUI and real-time test. Strict JSON type regression suite expanded for format 2.
- [x] Actual UML and launch/demo documentation updated. No Windows termination or rewards implemented.
- [ ] Future: break cycles, history paging/filtering, detailed charts, goal editor/progress, workspace integration, Windows blocking and rewards.

## Post-audit JSON validation correction

- [x] Numeric/boolean-to-text coercion defect fixed, including `displayName: 123` and `displayName: true`.
- [x] Entire JSON structure checked before domain binding: exact object properties, array/element shapes, string types, integer ranges, missing values and required null rejection.
- [x] Unknown boolean flags/string flags rejected; format 1 declares no boolean properties. Nullable fields and legitimate empty/numeric-looking text retain compatibility.
- [x] Empty strings cannot silently become null optional IDs/dates; fractional/overflow integer tokens, enum ordinals and scalar-to-array coercion are rejected.
- [x] Field-specific validation errors flow through existing import feedback; replacement does not start for invalid input.
- [x] All 597 tests pass, including 549 new regression cases, the previous 48 tests and actual JavaFX regression. Rejected-import cases verify unchanged snapshot and database bytes, with no backup/replacement side effects.
- [x] Current-code UML updated for the added transfer-boundary validator. No later feature milestone implemented.

See [validation correction verification](verification/json-validation.md). Validation of future feature inputs and Windows executable identity/safety remains part of their implementation milestones; this fix concerns the current JSON format.

## M2 requirements and acceptance

- [x] R1/R4/R11: first-start database and writable directory creation; V001/V002 initialization and versioned transactional migrations.
- [x] R2/R5: CRUD persistence for profile, session, goal, blocked selection, classroom/resource, workspace, task and achievement definition/award facts.
- [x] R3/R13: PK/unique/range/enum/FK constraints; composite resource membership; restrictive/cascading/detaching delete rules with rollback.
- [x] R6/R7: profile controls use real storage and bootstrap loads existing data; create/edit/reopen and actual startup checked.
- [x] R8/R9/R10: bounded lock wait, prepared entity statements, short-lived connections, explicit rollback/resource cleanup and readable SQLException/IOException feedback.
- [x] R12: complete versioned JSON export/import, strict bounded validation, preview/confirmation, verified recovery backup and atomic replacement; no overwrite or merge.
- [x] R14: temporary-database integration tests for every entity, M1 upgrades, rejected records, edit/delete relationships and failure rollback.
- [x] Acceptance: profile create/edit survives reopen; classroom create/retrieve works; invalid data rejected; SQL and export failures surface in real JavaFX feedback.
- [x] Clean build and all 48 tests pass, including real-window UI regression. Windows blocker and reward engine remain absent.
- [x] Updated docs, actual-code UML and launch instructions; commit hash is reported after Git confirms success.

## This stage

- [x] Inspect repository and any supplied requirements: [requirements.md](requirements.md); empty workspace, no separate document found.
- [x] Identify conflicts, missing details and risks: [requirements.md](requirements.md).
- [x] Design packages, models and services: [architecture.md](architecture.md).
- [x] Define persistence and Windows strategies: [persistence-and-windows.md](persistence-and-windows.md).
- [x] Write gated milestones: [milestones.md](milestones.md).
- [x] Draft explicitly planned UML: [uml-planned.puml](uml-planned.puml).
- [x] Create project development rules: [AGENTS.md](../AGENTS.md).
- [x] Create traceability checklist: this document.
- [x] M1 clean build and 30 JUnit tests, including the real JavaFX window test, passed. Compilation/tests were not applicable to M0.
- [x] M0 document consistency validation: results and limitations recorded in [verification/m0.md](verification/m0.md).
- [ ] M0 Git commit: sandbox denied Git metadata writes and the escalation request was declined; no commit created.

The historical M0 commit was never created. The first M1 source commit also includes that pending planning baseline; verify delivery using the final Git ID rather than inventing a separate M0 commit.

## M1 task acceptance

- [x] Maven/Java 21/JavaFX/SQLite/JUnit 5 configured, wrapper and dependencies pinned.
- [x] Main launcher/composition root; domain models separated from JavaFX.
- [x] Core interfaces implemented: `StudySessionLedger` -> `SqliteStudySessionLedger`; `DistractionBlocker` -> `SimulatedDistractionBlocker`.
- [x] Separate domain (model), application service/port, persistence, platform and UI packages.
- [x] All nine requested screens navigate using real JavaFX controls.
- [x] Coherent dark CSS, sidebar, cards, scrollable/resizable content and explicit later-feature statuses.
- [x] Actual profile creation/editing with validation, background save and SQLite restart persistence.
- [x] Domain validation and adapter/configuration tests; real-window profile/navigation smoke test.
- [x] Documentation, actual-code UML and build/launch instructions; host limitations recorded.
- [x] Git delivery prepared: verified source/build/docs are ready for the milestone commit; final commit ID is reported only after Git confirms success. No later milestone is authorized.

## Product and technology

| ID | Requirement | Planned ownership | Milestone | Required acceptance evidence | Status |
| --- | --- | --- | --- | --- | --- |
| T1 | Java 21, Maven, stable compatible pinned dependencies | POM, wrapper, bootstrap | M1 | JDK version, resolved versions, clean verify and real launch | [x] M1 |
| T2 | JavaFX UI and CSS; Windows primary platform | AppShell, shared CSS | M1, M8 | Windows launch, packaged launch and visual smoke | Partial: M1 actual Windows launch; M8 packaging pending |
| T3 | SQLite local persistence; JUnit 5 | JDBC adapters, JUnit suite | M1 onward | Temp-DB/restart tests; test report | [x] foundation; extend with features |
| F1 | Persistent local user profile | UserProfile, ProfileService, SqliteUserProfileStore | M1 | SqliteFoundationTest and JavaFxSmokeTest; actual app restart | [x] |
| F2 | Study timer, focus/break cycles, immediate Stop | FocusCycle, FocusSessionService | M3 | Deterministic transition/pause/stop/suspend tests and UI check | [ ] |
| F3 | User-selected Windows application blocking | WindowsDistractionBlocker | M5 | Safe fixture terminated; excluded/failed targets accurately reported | [ ] |
| F4 | Custom subjects/courses as classrooms | ClassroomCatalog/SqliteClassroomCatalog, ClassroomController | M2 | Subject CRUD/reopen tested; confirmed deletion follows relationship rules | [x] basic subject CRUD; resource editor remains future |
| F5 | Educational resource links including Google Classroom, no Google auth | StudyResource, ClassroomCatalog | M2 storage; later workflow | Resources persist and validate; no auth integration | Partial: interactive link editing/launch pending |
| F6 | Configurable workspaces: classroom/durations/resources/blocklist | FocusWorkspaceStore/SqliteFocusWorkspaceStore | M2, M3, M5 | Aggregate CRUD, relationship rollback and JSON round trip pass | Partial: storage complete; editor/session integration pending |
| F7 | Goals and tasks | StudyPlanStore/SqliteStudyPlanStore | M2 storage; M4 workflow | CRUD/completion facts persist; optional references clear on deletion | Partial: progress/workflow UI pending |
| F8 | History and statistics | StudySessionLedger, StudyStatisticsService | M3, M4 | Correct filters/totals/paging; no-break accounting | [ ] |
| F9 | XP, achievements, levels, streaks | RewardPolicy, RewardService, RewardStore | M6 | Idempotence and calendar-boundary tests; real UI awards | [ ] |
| F10 | Local export/import | StudyDataTransfer/JsonStudyDataTransfer, StudyDataArchive/SqliteStudyDataArchive, DataTransferController | M2 (brought forward) | Complete round trip, preview/backup, strict validation and rollback tests | [x] format 1 replacement; no merge |
| F11 | Multiple startup configurations | StartupConfigurationLoader, properties presets/CLI | M1, M7 | StartupConfigurationTest; two config sources and precedence/errors | Partial: files/CLI work; named presets M7 |

## Grading evidence

| ID | Requirement | Planned concrete evidence | Milestone | Status |
| --- | --- | --- | --- | --- |
| G1 | At least 4 meaningful application-specific classes | UserProfile, ProfileService, SqliteUserProfileStore, AppShell and other real application/domain types; 35 source types | M1 | [x] |
| G2 | At least 2 implemented interfaces | Three actual ports/adapters: UserProfileStore, StudySessionLedger, DistractionBlocker; concrete persistence/simulation tests | M1; Windows adapter M5 | [x] foundation |
| G3 | At least 2 genuine collections | Set<BlockedApplication> for workspace/simulation selections; List<StudySession> for durable ordered ledger reads at startup; immutability/deduplication tests | M1 | [x] foundation; history UI M3 |
| G4 | Accurate UML class relationships | Actual-code PlantUML and rendered SVG: 55 top-level and four nested types, concrete implementations/dependencies | M1-M8 | [x] through M2; keep current |
| G5 | Read and write data to files | Actual UTF-8 properties load/save and SQLite DB; portable JSON remains M7 | M1, M7 | [x] file I/O foundation |
| G6 | Configuration and/or startup arguments | Validated properties and CLI flags exercised by StartupConfigurationTest; presets remain later | M1, M7 | [x] foundation |
| G7 | At least 3 standard exceptions handled | NumberFormatException in config, IOException in file/config/startup, SQLException in JDBC; tested error paths. Also URI/path/security exceptions | M1 | [x] |
| G8 | At least 2 useful custom exceptions | StudyDataException for persistence/migration/conflicts; FocusModeException for duplicate simulation start; both thrown and tested. DataImportException remains M7 | M1, M7 | [x] two foundation exceptions |
| G9 | Complete input validation | Architecture validation matrix applied to forms, services, config and import; boundary/error tests | Each feature, audit M8 | [ ] |
| G10 | Functioning presentation-ready application | Packaged standard-user Windows run, complete demonstration script and user guide | M8 | [ ] |

## UI, safety and delivery

| ID | Requirement | Evidence/check | Milestone | Status |
| --- | --- | --- | --- | --- |
| U1 | Modern dark theme, sidebar, typography, cards | Actual AppShell/dark.css, profile and status cards; real-window inspection | M1 onward | [x] foundation |
| U2 | Responsive resizing and clear buttons/dialogs | Minimum/default sizes, scrolling, focus indication, scaling checks | M1-M8 | [ ] |
| U3 | Meaningful empty/error states; no fake controls | Empty profile/history/classroom views, validation/retry, navigation audit | Each feature | [ ] |
| W1 | Best-effort blocking with accurate status | Skipped/access-denied/disappeared targets visible | M5 | [ ] |
| W2 | No Windows security changes or automatic admin request | Source/native review and standard-user packaged test | M5, M8 | [ ] |
| W3 | Never terminate critical/system or Study Mode processes | Fail-closed safety policy; critical/self/runtime/ancestor/system/unknown tests | M5 | [ ] |
| W4 | Stop focus at any time | Stop cancels blocking before DB persistence; tests under save/native failure | M3, M5 | [ ] |
| W5 | Simulated blocking for development/tests | SimulatedDistractionBlockerTest validates events/lifecycle; runtime explicitly says simulation/inactive | M1, lifecycle UI M3 | [x] adapter; focus integration later |
| D1 | Incremental delivery and explicit milestone gates | AGENTS.md, one requested milestone per implementation turn | Every milestone | [ ] ongoing |
| D2 | Compile/tests after milestones; preserve existing features | clean verify, targeted smoke, regression suite and verification record | M1 onward | [ ] |
| D3 | Commit completed milestones to Git | Verified local commit per completed milestone; no remote assumed | M0 onward | [ ] |
| D4 | No skipped incomplete requirements | Pending rows retained, final audit against this checklist | Every milestone | [ ] ongoing |
| D5 | Clean object-oriented domain and services | Real interface implementations, boundary review, accurate UML | Every milestone | [ ] |
