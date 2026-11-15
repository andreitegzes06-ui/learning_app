# Study Mode

Java 21 / JavaFX Windows desktop application with SQLite persistence. The current milestone adds a working **focus timer, persistent History and real Dashboard totals**, reusing the existing profile, classroom, ledger and JSON infrastructure.

## Build and launch

From this directory in PowerShell:

```powershell
.\scripts\dev.ps1 -Task build -IncludeUiTests -RealDemoTest
.\scripts\dev.ps1 -Task run -StartPage focus
```

The script uses JAVA_HOME or the ignored project-local JDK and caches Maven in .tools/. A fresh clone needs Java **21**. The committed Maven wrapper downloads Maven 3.9.16; initial downloads need Internet. Standard commands are .\mvnw.cmd clean verify and .\mvnw.cmd javafx:run.

Windows Application Control here blocks unsigned Maven JavaFX DLLs. Runtime checks use policy-approved BellSoft Liberica **JDK 21 Full**, with signed bundled JavaFX natives: Java 21.0.13.0.3+5 and JavaFX 21.0.13.0.3. Compile dependencies remain pinned to OpenJFX 21.0.13. Do not disable security settings. Toolchains are not committed.

This OneDrive checkout can prevent Maven clean deleting generated directories. The script removes only the verified, nonlinked local target output before clean verify. Prefer a nonsynced checkout for direct Maven builds.

## Presentation demo

```powershell
.\scripts\dev.ps1 -Task run -Demo -DataDirectory .run\presentation-demo -StartPage focus
```

1. On first use, create a profile in Settings with a valid study zone. Optionally create a subject in Classrooms.
2. Return to Focus Session, reload choices if needed and optionally select the classroom. The title/sidebar identify **Demo / Test** mode. Duration is in **seconds**, initially 10.
3. Start and let the real 10 seconds elapse. History shows a completed Demo session of 10 seconds; on a fresh directory Dashboard shows 10 seconds and one completed session.
4. Start again, Pause, wait, Resume and Finish. Only running time is saved. Stop instead records INTERRUPTED, excluded from completed totals.
5. Restart with the same command/data directory to show the saved profile, History and totals.

Demo mode credits actual time only. Normal mode takes whole minutes from 1–240, with 25/50-minute presets. Demo permits 1–14400 whole seconds; presets still represent actual 25/50 minutes. Startup never starts focus automatically.

## Configuration and data

Default writable root: %LOCALAPPDATA%\StudyMode. SQLite and settings.properties live there, protected by a single-instance directory lock. No Google authentication, admin access or Program Files writes. Prefer a local nonsynced data directory.

| CLI flag (--key=value) | Values |
| --- | --- |
| --data-dir | Absolute local directory; script resolves relative -DataDirectory |
| --config | Absolute existing UTF-8 properties file |
| --start-page | dashboard, focus, history, classrooms, workspaces, blocked, goals, statistics, achievements, settings |
| --demo | true / false, default false; script switch -Demo |
| --blocker | simulated only; does not start blocking |
| --window-width / --window-height | Whole pixels 900–3840 / 640–2160 |

Properties support start-page, demo, blocker and dimensions. Precedence: bundled defaults < data-directory settings < explicit config < CLI. Invalid/unknown/duplicate flags are rejected. Saving dimensions preserves other settings.

Schema **3** migrates existing data additively. JSON exports use format **2**; strictly validated format **1** imports remain supported. Settings import replaces data only after validation, preview, confirmation and verified recovery backup. Failures roll back; active local sessions prevent import. Imported unfinished sessions become interrupted at their saved checkpoint. Exports require a new filename outside the data root. Backups contain private data without encryption.

## Timing and recovery

System.nanoTime() supplies elapsed time; wall-clock changes do not alter credited duration. Paused time is excluded; fractional seconds accumulate across pauses, then persistence floors the final sum to seconds. Automatic completion caps time at the target.

Checkpoints occur every five running seconds. Close confirms interruption and saves before exit. Save errors pause advancement and offer Retry saving; Start/Resume are blocked until saved. Failed close saves leave the window open. After unexpected shutdown, startup interrupts unfinished sessions at the last committed checkpoint only. Uncommitted seconds are lost; offline time is never estimated. A timer-update gap over 15 seconds conservatively pauses and excludes the gap.

Dashboard totals/count use completed sessions, including only actual demo time. Today groups by completion date in the current profile zone. Most recent includes either final outcome. History preserves captured classroom/goal labels after renames. Session references restrict classroom/goal deletion.

## Verification and structure

Full build: **679 tests, zero failures, errors or skips**, including deterministic lifecycle/recovery/statistics, strict JSON regressions, migration/round-trip compatibility, a real JavaFX window workflow and a separate real 10-second monotonic test that persisted/reloaded exactly 10 seconds. Ordinary builds skip the two opt-in UI/real-time tests.

| Package | Responsibility |
| --- | --- |
| domain | Validated immutable entities, session context and lifecycle |
| application | Ports, profile/focus/statistics services, DTOs and errors |
| persistence | SQLite, migrations, strict JSON, configuration and locking |
| platform | OS-free simulation; no process enforcement |
| ui | Shell, controllers, dark controls and views |
| bootstrap | Entry point, dependency wiring, recovery and shutdown |

Database work uses a shared background executor; controls update on the FX thread. Existing StudySessionLedger/SqliteStudySessionLedger are extended and reused.

See [focus contract](docs/focus-workflow.md), [verification](docs/verification/focus-workflow.md), [actual UML](docs/uml-implemented.puml), [traceability](docs/traceability.md), [planning index](docs/README.md) and [development rules](AGENTS.md).

## Remaining scope

Break cycles, charts/filtering/paged history, goal/task editors and progress, full resource/workspace editors and focus integration, Windows enforcement, rewards and named startup presets remain planned. Stored/imported goals can be selected; goal creation UI is pending. Self-contained Windows packaging and full DPI/accessibility acceptance remain future work. Launch through Maven.
