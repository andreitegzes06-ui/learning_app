# Incremental implementation plan

Each milestone requires its own explicit user request. A successful earlier milestone never authorizes the next. M0 is the present stage; M1-M8 are future work. Deliver working slices with no inert controls and keep all earlier tests passing.

For each implementation milestone: run `.\mvnw.cmd clean verify` with JDK 21, perform the listed UI/platform checks, update traceability and actual-code UML, record results in `docs/verification/mN.md`, commit the completed slice, report limitations and commit ID, and stop. Do not mark a milestone complete if required checks cannot run. Build scripts and tests must use temporary data, not the student's live DB.

## M0 — Planning (current)

Deliver repository inspection, baseline/assumptions, architecture, storage and Windows strategies, milestone plan, planned UML, AGENTS.md and traceability. Initialize Git and commit documentation. Verify document links, plan/UML consistency, requirement coverage and absence of application code. Compilation/JUnit are not applicable because no source or build is authorized.

## M1 — Runnable shell and persistent local profile

Scope amended by the user's explicit M1 request on 15 November 2026. This implemented scope supersedes the original two-page plan:

1. Establish JDK 21; create a single-module Maven build, pinned dependency/plugins, Maven wrapper, appropriate `.gitignore`, and setup/run README. Resolve and test JavaFX 21, SQLite JDBC, JUnit 5; do not merely add an untested POM.
2. Create launcher/composition root and one resizable JavaFX window with shared dark CSS. Dashboard, Focus Session, Classrooms, Workspaces, Blocked Applications, Goals, Statistics, Achievements and Settings all navigate. Later workflows have labelled status screens, without inert actions or fake data.
3. Implement `UserProfile`, `UserProfileStore`, `SqliteUserProfileStore`, `ProfileService`, `SqliteDatabase`, `SchemaMigrator` and `StudyDataException`. Create the first real profile migration with singleton constraint; persist ID/name/created time/zone.
4. First launch shows a validated name form; saving creates the profile transactionally and opens the dashboard. Dashboard greets the saved user and honestly explains that no study sessions exist. Settings allows a validated name/zone update that survives restart.
5. Implement `StartupConfiguration`, `StartupConfigurationLoader`, default data location, explicit config/data-dir and all nine initial page flags, and actual settings.properties read/write. Enforce single-instance data-root lock and explicit data-path/config errors; never auto-start focus.
6. Provide loading/error/retry states for profile I/O, no silent DB reset, and correct executor/database shutdown. Make errors useful enough for presentation and troubleshooting.
7. Implement the agreed domain records/classes and validation now, without future workflow services. Implement `StudySessionLedger` through a real `SqliteStudySessionLedger` (save/query durable session facts), and `DistractionBlocker` through an observable, explicitly simulated adapter. Integrate read-only ledger/blocker status into the foundation; no timer or OS blocking is started.

Acceptance: build/test domain validation, profile persistence/reopen, properties precedence/read-write, migrations, duplicate profile/session prevention, immutable ledger finalization, exact period queries, blocker simulation and error handling. Real JavaFX smoke test navigates every destination, validates/creates/edits the profile, resizes and saves preferences. Record actual Maven launch and host restrictions honestly. Classroom/workspace editing, timers, process termination, goals/reward workflows and export/import remain later milestones.

## M2 — Classrooms, resources and saved workspaces

Implement classroom/resource CRUD with archive rules, validated HTTP/HTTPS links opening in the default browser on click, workspace preferences, selected resources, and stored executable blocklist metadata. Add real Classrooms/Workspaces navigation, empty/error states and migrations. Blocklist editing explicitly says enforcement is unavailable until M5; there is no Start Blocking action.

Checks: domain validation boundaries, resource/classroom membership, unique executable paths, CRUD/restart persistence, referenced archive behaviour and transaction rollback. Smoke external links, editing dialogs, selectors and resizing. Verify `List<StudyResource>` and `Set<BlockedApplication>` are genuinely used.

## M3 — Focus cycles and study history with simulation

Implement `FocusCycle`, `StudySession`, `StudySessionLedger`/`SqliteStudySessionLedger`, `DistractionBlocker`/`SimulatedDistractionBlocker`, `FocusSessionService`, timing scheduler, snapshots and per-day checkpoints. Add Focus and History pages. Start/Pause/Resume/Stop, focus/short/long-break rules, saved session history, labelled simulated events and crash recovery work end to end. Persist interrupted and completed sessions without break/pause time. Simulated targets from M2 are observable without claiming real Windows blocking.

Checks: fake-clock transitions, zero/invalid durations, long breaks, no duplicate starts/finalization, suspend/clock-change/midnight handling, pause/stop cancellation, restart interrupted recovery, date filtering and paging. Smoke a short test-controlled cycle without changing production minimum duration, pause/resume/stop, force-close/reopen and confirm conservative recovery. Both main suggested interfaces now have working implementations; Windows implementation is still pending.

## M4 — Tasks, goals and useful statistics

Implement planning models/stores/services, CRUD and task completion, daily/weekly goal progress, dashboard cards and History statistics/charts using committed sessions. Show totals by day/classroom, completed/interrupted counts and genuine empty states.

Checks: goals across day/week/zone boundaries, task due dates and completion persistence, period filters, independent aggregate SQL comparisons and zero-data behaviour. Smoke adding/completing tasks and recording study that advances a goal. All previous timer/blocker simulation tests remain passing.

## M5 — Safe best-effort Windows enforcement

Implement `WindowsDistractionBlocker`, native gateway, conservative process policy and actual adapter selection. Validate full paths and identities, current user/session, critical status, self/runtime/ancestor protection, cancellation, bounded polling and detailed outcomes. Add explicit loss-of-unsaved-work warning and active enforcement status; Pause/break/Stop/exit disable enforcement. Pin native dependencies only after verifying their APIs.

Checks: comprehensive policy tests via injected gateway; Windows standard-user disposable-fixture termination; access denied, disappeared targets, path aliases, unknown critical state, same-name different path, Stop race, repeated starts and handle cleanup. Confirm no security setting changes, privilege prompt or persistent service. No real personal application is an automated target. Release gate: Windows integration checks must pass; simulation alone cannot complete M5.

## M6 — XP, levels, achievements and streaks

Implement reward policy/awards, unique per-session XP, versioned achievements (first completed focus, ten completed focus sessions, three-day streak), level and streak cards, Rewards page, startup reconciliation and idempotent persistence. Default policy is in requirements A6; interrupted active minutes also count and are labelled honestly.

Checks: minute rounding, no break/pause XP, duplicate finish/reconciliation, midnight/DST, yesterday/today streak rules, imported historical time consistency and persistence failure retry. Smoke earning a real reward and restarting without double awards. Do not change history facts to manufacture a demo.

## M7 — Versioned export/import and startup presets

Implement JSON snapshot export/import with schema version, bounded validation, preview, explicit replace confirmation, verified pre-import backup and transactional replacement/rollback. Implement named startup presets and remaining CLI options, usable Settings/Data controls, disabled import during active sessions, safe foreign-machine blocklist revalidation.

Checks: full round trip of every entity, malformed/oversized/unsupported files, dangling/duplicate IDs, failed writes/rollback, read-only paths, preset precedence and path traversal, no import-triggered focus or blocking. Smoke export, modify local records, preview/import/restore and launch two distinct safe configurations. Explicit file read/write rubric now includes portable JSON in addition to properties.

## M8 — Presentation readiness and Windows distribution

Finish usability/accessibility/resizing/scaling checks, package a self-contained Windows app image with JDK 21 tools, test native SQLite/Windows adapters from the packaged app and validate clean-machine startup without installed Java. Add an installer only with tested tooling. Complete actual-code UML, rubric evidence, user guide, test report and classroom presentation script using a user-created dataset. No fake controls or silently unfinished requirements may be declared complete.

Checks: full `clean verify`, complete end-to-end profile -> classroom/workspace -> focus -> history/goal/reward -> export/import, real/simulated startup modes, Stop/exit failures, paths with spaces/non-ASCII, 100/125/150% scaling, keyboard access and recovery. Review all traceability rows and documented safety limits. Commit only after required checks pass, then stop.
