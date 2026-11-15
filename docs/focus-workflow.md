# Focus workflow contract

This records the completed focus milestone baseline. The subsequent [M3 organization milestone](organization-m3.md) implements the goal/resource/workspace editors previously listed as pending here, and advances storage to schema 4 / JSON format 3.

Implemented 15 November 2026. This user-requested milestone supersedes the original M3 cycle plan with single focus sessions, history and real dashboard totals. No Windows enforcement, blocker start, break timer or rewards engine is introduced.

## Lifecycle and services

FocusSessionService reuses StudySessionLedger, UserProfileStore, ClassroomCatalog and StudyPlanStore. Injected Clock supplies timestamps and LongSupplier supplies monotonic nanoseconds; production uses Clock.systemUTC() and System::nanoTime. Synchronized commands serialize transitions. Domain/application code has no JavaFX/JDBC dependencies.

| From | Command/event | Result |
| --- | --- | --- |
| IDLE or saved final | Start with valid inputs | RUNNING after durable initial save |
| RUNNING | Pause | PAUSED, accumulated time saved |
| PAUSED | Resume after successful save | RUNNING with new time anchor |
| RUNNING or PAUSED | Finish | COMPLETED with actual time |
| RUNNING or PAUSED | Stop / confirmed close | INTERRUPTED with actual time |
| RUNNING | Target reached | COMPLETED capped at target |
| RUNNING | Negative or >15-second update gap | PAUSED without gap credit |
| Save | Storage failure | Pending retry; advancement paused |

Existing persisted SessionStatus.ACTIVE maps to FocusState.RUNNING. Other transitions throw FocusModeException. Pending saves block Start/Resume; retry preserves identity/facts and identical finalized saves are idempotent. The live service, unfinished check, directory lock and SQLite unique unfinished-per-profile constraint prevent duplicates.

Running nanoseconds accumulate across pauses, with whole seconds stored only at persistence boundaries. Resume-save latency and paused time are excluded. Normal targets are 1–240 whole minutes, demo 1–14400 whole seconds, explicitly labelled. Presets remain actual 25/50-minute targets. Profile, classroom existence and goal ownership are validated before creation.

## Data and recovery

V003 extends the existing session table with optional classroom/goal FKs, captured labels, planned seconds, demo boolean and checkpoint timestamp. Legacy facts retain unknown target 0, empty labels and original end/start checkpoint. Old Java constructors remain compatible. Context/finalized facts are immutable, elapsed/checkpoint facts cannot decrease. Session references restrict classroom/goal deletion; captured names survive renames.

The UI pulses every 250 ms with at most one queued background tick. Checkpoints save every five running seconds; commands save transitions. Startup interrupts unfinished records at their last committed checkpoint without offline estimates. Unsaved seconds since the checkpoint are deliberately lost. Close confirms interruption and asynchronously saves first; failure leaves the window open with feedback. Force exit during save failure can lose uncommitted time, but saved checkpoints remain recoverable.

JSON format 2 carries new context/checkpoint metadata. Strict validation of the original shape precedes legacy format 1 upgrade with trusted defaults. Demo accepts only JSON true/false. Coercions, required nulls, malformed arrays/objects and invalid references are rejected before replacement. Imported unfinished records are interrupted at saved checkpoints. Replacement deletes sessions before referenced entities. Old application versions cannot read schema 3; no downgrade/reset is attempted.

## UI and summaries

FocusSessionController owns genuine JavaFX controls, error/retry feedback and a bounded scheduler. Shared background I/O keeps JDBC off the FX thread. DashboardController and StudyHistoryController use StudyStatisticsService over committed sessions.

Completed totals/count exclude interrupted records; demo contributes actual seconds only. Today uses completion date in the current profile zone; most recent includes either final outcome. History shows date, captured classroom, actual duration, outcome, mode and goal, newest first. Legacy records are labelled Legacy. History currently loads in memory; filters/paging/charts, goal creation and break cycles remain planned.

See [verification](verification/focus-workflow.md), [launch/demo](../README.md) and [actual UML](uml-implemented.puml).
