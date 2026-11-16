# Week 8 presentation notes

Prepared 16 November 2026 for the implemented M5 application. **The in-class presentation has not been completed or awarded.** This is a rehearsal guide, not proof of attendance. Do not claim M6 rewards are implemented.

## Opening explanation

"Study Mode is a local Windows study application built with Java 21, JavaFX, Maven and SQLite. It organizes classrooms and workspaces, measures actual focus time, offers short and long breaks, and saves session history. It uses ports and adapters so business logic does not depend on JavaFX or JDBC."

Actual features: local profile; classrooms with notes, color, educational links and task checklist; saved workspace timings/resource selections/application selections; daily/weekly goals with linked progress; focus Start/Pause/Resume/Finish/Interrupt; configurable/manual/automatic breaks and skip; completed-time dashboard; persistent history; properties/CLI startup settings; validated JSON export/import with recovery backup; optional in-app completion notification and sound; explicit simulated, Windows dry-run and best-effort graceful-close blocker modes.

## Explain the UML in two minutes

Open [the PNG](WEEK8_UML.png) or [the PDF](../output/pdf/WEEK8_UML.pdf). The grading view contains ten actual classes/records and two implemented application interfaces. The [full implementation diagram](uml-implemented.puml) remains available for deeper questions. Neither diagram represents future reward policy.

| Type | What to explain |
| --- | --- |
| `FocusSessionService` | Existing session engine: validates Start, uses injected time, pauses/resumes, saves actual seconds, recovers checkpoints and coordinates break/blocker lifecycle. |
| `StudySession` | Immutable focus fact: identity, profile, timestamps, status and `activeFocusSeconds`; it never represents break time. |
| `UserProfile` | Stable local identity, display name and study zone. Editing preserves its UUID and creation instant. |
| `SqliteDatabase` | Short-lived JDBC connections and transaction commit/rollback; failures preserve data and produce an actionable custom exception. |
| `SqliteUserProfileStore` / `SqliteStudySessionLedger` | Concrete adapters that implement the two diagram interfaces using prepared SQL. |
| `Classroom` / `StudyResource` | A subject owns an ordered resource list; each resource validates its HTTP/HTTPS URL. |
| `FocusWorkspace` / `BlockedApplication` | A saved classroom environment with timing preferences and unique executable selections. |

The two interfaces to point at are `UserProfileStore` and `StudySessionLedger`. `FocusSessionService` receives those contracts in its constructor. Dashed triangular arrows show the SQLite adapters implementing them. Solid arrows show held references. The dotted `profileId` and `classroomId` arrows are UUID references, not Java object fields. Open diamonds show collection membership; immutable snapshots make sharing safe.

Record fields in the diagram correspond to real record components with generated public accessors. Ordinary class private fields are marked `-`; public methods are marked `+`. This is a selected view: other actual dependencies and methods are omitted for readability.

## Show two genuine collections

1. `Classroom.resources` is a `List<StudyResource>` ([source](../src/main/java/com/studymode/domain/classroom/Classroom.java), line 7). Resource order matters; the database stores `position`. The UI lists/edits the links. `List.copyOf` prevents outside mutation.
2. `FocusWorkspace.blocklist` is a `Set<BlockedApplication>` ([source](../src/main/java/com/studymode/domain/workspace/FocusWorkspace.java), line 9). The same executable should appear once. `BlockedApplication.equals/hashCode` use normalized, case-insensitive path identity ([source](../src/main/java/com/studymode/domain/workspace/BlockedApplication.java), line 32). The selected set is used by the real/simulated blocker boundary.

Do not describe these as unused demonstration collections. They determine actual screen contents, persistence and blocking membership. A third example, if asked, is `StudySessionLedger.findByPeriod` returning `List<StudySession>` for history and statistics.

## Explain saving and reading files

- `studymode.db` is the SQLite file. Profile, organization settings and focus facts are read through the SQLite adapters on every launch. Connections close after transactions. No database reset is used to hide errors.
- `settings.properties` saves window dimensions; `StartupConfigurationLoader` reads UTF-8 properties and writes through a temporary file with atomic-move fallback.
- JSON export explicitly writes portable application data. Import reads and validates fields/types/references, displays a replacement confirmation, creates and verifies a recovery backup, then replaces records transactionally. Existing exports are never overwritten.

The normal data directory is `%LOCALAPPDATA%\StudyMode`. For presentation, use a separate ignored directory as below. Backups are private, unencrypted files: do not submit your personal database or export to Git.

## Explain startup behavior

Precedence: bundled defaults < saved data-directory settings < explicit `--config` properties file < CLI arguments. Useful flags are `--start-page`, `--demo`, `--blocker`, `--data-dir`, `--window-width` and `--window-height`. A missing profile deliberately routes to Settings even if another start page was requested. Unknown/duplicate flags, invalid booleans, out-of-range sizes and invalid paths produce a startup error screen.

```powershell
# Normal focus screen, with no process-closing actions.
.\scripts\dev.ps1 -Task run -StartPage focus -Blocker simulated

# Isolated, labelled demonstration. Focus duration is real seconds.
.\scripts\dev.ps1 -Task run -Demo -Blocker simulated -DataDirectory .run\week8-presentation -StartPage focus

# Reopen the exact same saved data directly in History.
.\scripts\dev.ps1 -Task run -Demo -Blocker simulated -DataDirectory .run\week8-presentation -StartPage history
```

For a properties example, save this as an ignored local `.run\week8.properties` and pass its absolute path with `--config=<absolute-path>` to `mvnw.cmd javafx:run` using `-Djavafx.args="..."`. Keep the quoting examples in the README handy; the run script covers the common flags without manual quoting.

```properties
start-page=history
demo=true
blocker=simulated
window-width=1100
window-height=720
```

Do not change security settings or request administrator rights to run the demo. This host uses the existing project-local BellSoft JDK 21 Full because unsigned downloaded JavaFX natives were previously blocked. A fresh clone needs a compatible Java 21/JavaFX runtime and Maven downloads; verify the classroom computer in advance.

## Explain standard and custom exceptions

| Standard exception | Real handling and response |
| --- | --- |
| `NumberFormatException` | `FocusSessionController` line 89 rejects malformed/fractional/overflow numeric entries with an instruction to enter whole seconds or minutes. Config, goal and workspace forms also handle numeric failures. |
| `IOException` | `DataTransferController` lines 49/68/75 reports failed export/import without pretending success; failed backup cancels replacement. Startup and settings also report unreadable files. |
| `SQLException` | `SqliteDatabase.transaction` lines 45-49 rolls back and wraps the cause with the failed operation and corrective guidance. Invalid relationships and damaged storage do not silently erase data. |
| `URISyntaxException` (extra example) | `StudyResource.fromUrl` line 23 converts malformed URL syntax to a meaningful validation error; the classroom form displays it. |

Useful custom exceptions: `StudyDataException` describes persistence failures and preserves the cause; `FocusModeException` describes invalid focus actions, missing prerequisites and unsafe/unavailable application operations. `DataImportException` is a third custom exception for invalid/incompatible snapshots and unsafe replacement. Point to real throws and UI catches, not merely class definitions.

## Explain input validation

Show both layers: forms report errors, while domain constructors/services enforce the same rules before saving or starting. Name lengths/control characters, description limits, valid zone selection, positive whole durations, long-break cadence, classroom/resource membership, goal ownership, URL schemes/hosts/credentials, absolute `.exe` selections, duplicate records, CLI options and strict JSON types are validated. Prepared statements and SQLite checks/foreign keys add storage protection. The malformed import suite checks that rejection leaves the durable database unchanged.

For the live validation example, enter a blank profile name, focus `0` or `abc`, and a resource URL such as `javascript:alert(1)`. Explain the rejection and then correct the entry. Do not deliberately corrupt your presentation database.

## Live demonstration order (about 6-8 minutes)

1. Launch the isolated demo command. Show the Demo/Test label and create a synthetic profile, such as "Week 8 Student". Try a blank name first to demonstrate validation.
2. Create a classroom "Java revision" with a note and a valid educational link. Add a task and mark it complete. Save changes before navigating away.
3. Create a workspace for that classroom. Save focus 25, short break 5, long break 15 and cadence 4. Explain that these are real minutes and survive relaunch. Select resources if useful. Leave application selections empty for the main demo.
4. On Focus Session, enter **10 seconds** in demo mode. Leave automatic breaks off. Start and allow ten real seconds to elapse. A manual break becomes READY; History must show **10 seconds**, not 25 minutes. Completion notification is optional and in-app.
5. Start the break, Pause, Resume, then Skip break. Actual focused time must remain unchanged throughout. Break durations remain real minutes even in demo mode; skip rather than asking the class to wait five minutes.
6. Enter 60 seconds, Start, Pause, wait briefly, Resume, then Finish early. Explain that Finish counts a completed period but saves only elapsed focused seconds. Skip its pending break. Start another 60-second focus and use Stop/Interrupt; it must appear as INTERRUPTED and add no completed-study total.
7. Close normally with no active focus/break. Reopen the same directory with `-StartPage history`. Show the same profile, completed/interrupted rows and actual durations. Reopen Workspaces and select the saved workspace to show its four timing values.
8. Show the UML and source handlers/collections. If time permits, export JSON to a new file outside the app data directory and explain how the automated import/rollback tests protect existing data. Do not replace the demonstration data during the presentation unless you have rehearsed a separate disposable directory.

Optional blocker explanation: start with `-Blocker windows-dry-run`. This is explicitly a no-close detection mode, not proof that an app was closed. Existing native verification enumerates/query-checks identity and refuses Study Mode's own runtime. Do not select or close classmates' or your ordinary running apps during grading. Native graceful closing remains best-effort and is not an acceptance claim of this audit.

## Honest limitations and closing statement

No XP, levels, streak calculation or achievement-unlock engine; no M6 work. Detailed charts and history paging/filtering are unfinished. Notifications are JavaFX popups rather than Windows Action Center toasts. Live break state/counts and notification/automatic-transition choices are not persisted across application restart; four workspace timing values are saved. The blocker requests graceful close only, and an application can refuse or prompt for unsaved work. Packaging into a self-contained installer, full DPI/accessibility acceptance and native disposable-fixture close testing remain outstanding.

"My completed scope is M5. The grading evidence links each rubric criterion to actual source and tests; the in-class presentation itself is still for the examiner to assess."
