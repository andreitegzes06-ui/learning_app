# Requirements baseline and inspection

## Repository inspection

- Inspected `C:\Users\andre\OneDrive\Documents\CODING\studyapp`, including hidden entries, on 15 November 2026.
- The directory was empty. No supplied requirements document, source, tests, configuration, build files, or existing Git history was found. No applicable ancestor AGENTS.md was found in the inspected parent chain.
- `git status` and `git log` reported that this was not a Git repository. Initialize a local repository for the planning commit; do not invent a remote.
- Git is available. `java`, `javac`, and `mvn` were not discoverable on PATH. This does not establish whether a JDK exists elsewhere. Before M1, locate or install/configure JDK 21 and bootstrap a pinned Maven wrapper.
- No application compilation or JUnit run is possible or appropriate in this documentation-only stage. No implementation exists to verify.

## Binding scope

Build a local, functional Windows Java 21/JavaFX application with Maven, SQLite, JUnit 5, and CSS. Deliver incrementally, test and commit each completed milestone, and wait for an explicit request before starting the next. This stage only inspects and plans.

The full product must include a persistent local profile; focus/break cycles; selected application blocking; classrooms and external educational URLs; workspace templates; goals, tasks, resources; history/statistics; XP, levels, achievements, streaks; export/import; and multiple startup configurations. No Google authentication is allowed.

The rubric requires at least four meaningful application classes, two implemented interfaces, two genuinely used collections, accurate implemented-class UML, file reads/writes, configuration or startup arguments, three handled standard exception types, two useful custom exceptions, complete input validation, and a working presentation-ready app.

## Conflicts and resolutions

| Tension | Resolution |
| --- | --- |
| Complete product versus no features in this stage | M0 produces planning documents only. M1-M8 implement explicitly requested slices. |
| UML must reflect implementation, but no implementation is authorized | Supply clearly marked planned UML now; maintain a separate actual-code UML starting in M1. Neither future classes nor planned relationships count as implemented rubric evidence. |
| Compile/test after each milestone versus no source or build | M0 gets document consistency checks. Compilation/JUnit are explicitly not applicable; every implementation milestone must build and test. |
| Git commit required, repository absent | Initialize local Git and commit this completed planning stage. Do not configure identity globally or create a remote. |
| Blocking versus safety and no admin | Restrict to verified, explicitly selected, noncritical current-user applications. Refuse uncertain cases and disclose partial enforcement. |
| SQLite versus reading/writing files rubric | SQLite is a file, but also demonstrate explicit properties-file and JSON export/import reads/writes so grading does not depend on that interpretation. |
| Simulated blocking versus no fake features | Simulation is an explicitly labelled, selectable adapter with observable events, used for tests/demo; production blocking remains a separate real implementation. |

## Missing details: proposed defaults, not additional user requirements

| ID | Unspecified detail | Working design assumption |
| --- | --- | --- |
| A1 | Supported Windows versions/CPU | Windows 11 x64 primary acceptance platform; Windows 10/ARM64 support requires separate verification. |
| A2 | Profile count/authentication | One local profile per data directory, no password, account, cloud sync, or Google login. |
| A3 | Timer defaults | 25 minute focus, 5 minute short break, 15 minute long break, long break every four completed focus periods; configurable. Next interval requires explicit Start. |
| A4 | Pause, early stop, crash | Pause is allowed, immediately disables blocking, and earns no time. Early stop records actual active time with INTERRUPTED status. A crash never auto-resumes blocking or awards uncheckpointed time. |
| A5 | Goals | Focus-minute targets with daily or weekly periods; tasks can optionally belong to a goal/classroom. Completed focus time counts, including actual time from explicitly interrupted sessions. |
| A6 | Rewards | 1 XP per completed active focus minute, 100 XP per level, no break XP; streak requires 10 active focus minutes per local calendar day. Deterministic policy and idempotent awards. |
| A7 | Import semantics | Versioned JSON backup; replace local application records after explicit preview/confirmation and a recoverable backup. No merge in initial product. |
| A8 | Workspace behaviour | One classroom per saved workspace, resources selected from that classroom, one workspace active at a time; session captures a snapshot so later edits cannot rewrite history. |
| A9 | Startup configurations | Named properties presets plus command-line overrides for data directory, blocker, initial page, and optional workspace. Never auto-start focus/blocking. |
| A10 | Packaging | Self-contained Windows app image with bundled Java runtime first; installer only if Windows packaging prerequisites are available and tested. |
| A11 | Educational-resource behaviour | Validate and launch HTTP/HTTPS links in the default browser on user click. No embedded browser, remote content scraping, or credentials stored. |
| A12 | History and retention | Keep history until explicit import replacement or future data-management work; paginate long histories. No hidden deletion or telemetry. |

These defaults allow planning to proceed. Confirm or revise affected assumptions before implementing their milestone; routine choices need no additional approval ceremony. A separately supplied marking document may change scope and should be reconciled when available.

## Principal risks

| Risk | Mitigation and acceptance gate |
| --- | --- |
| Toolchain unavailable | M1 must establish JDK 21, pinned Maven wrapper, dependencies, compilation and tests; report installation/permission failures honestly. |
| Data directory inside OneDrive | Default to LocalAppData, not the source directory or synced folders. Warn on an explicitly selected synced/network location; document unsupported concurrent/sync access. |
| Terminating user applications can lose unsaved work | Explicit blocklist selection and warning, conservative eligibility, best-effort results, Stop always available; Windows fixture tests before release. |
| Process identity races/unknown metadata | Same native handle for identity, critical checks and termination, fail closed for eligibility; unknown means skipped. |
| SQLite/native packaging | Test JDBC extraction and Windows native library loading from packaged app, with spaces/non-ASCII paths and standard user permissions. |
| Timer drift, suspend and clock changes | Monotonic active-time accounting, injected clock/ticker, conservative suspend policy and periodic persistence; test each transition. |
| Corruption and import loss | Transactional migrations/import, pre-change backup, validate before writes, do not reset on failures. |
| UI becomes disconnected screens | One shell, reusable CSS tokens, feature views in one content area, keyboard access, resizing and error-state checks each UI milestone. |
| Rubric evidence becomes stale | Map tests and classes as they land; actual-code UML and final presentation checklist are release gates. |
