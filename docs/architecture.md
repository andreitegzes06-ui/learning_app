# Application architecture (planned)

## Current M5 amendment

M5 composes the existing FocusSessionService with the Java-only BreakCycle. Focus persistence, monotonic timing, checkpoint recovery and blocker lifecycle remain in the existing engine. Immutable cycle state is added to FocusSessionSnapshot; breaks never create study ledger rows. StudyNotifications is an application port; JavaFxStudyNotifications queues nonmodal popups on FX and optional audio on a dedicated worker. AppShell owns and closes the adapter. See [M5 behavior/evidence](break-cycles-m5.md). M4 native adapters and controllers are included as authorized prerequisites; the actual UML shows them. Historical sections below describe earlier scope.

## Prior M3 organization amendment

StudyOrganizationService coordinates the existing ClassroomCatalog, FocusWorkspaceStore, StudyPlanStore, UserProfileStore, StudySessionLedger and BlockedApplicationCatalog ports. ClassroomController now edits resources, notes/color and tasks and displays subject history. WorkspaceController and GoalController implement the existing domains without replacement abstractions. StudyEditorActions shares the FX/background handoff; StudyApplicationLauncher opens explicitly chosen study shortcuts without a shell or elevation. Schema 4 and JSON format 3 add metadata with legacy defaults. See [current M3 contract and evidence](organization-m3.md). Historical sections below retain the original planned scope; current implementation is shown by the actual UML.

## Implemented focus workflow amendment

The current user request replaces the original M3 cycle scope with single focus sessions, history and real dashboard totals. `FocusSessionService` orchestrates the existing ledger/profile/classroom/plan ports using injected `Clock` and monotonic `LongSupplier`. `StudyStatisticsService` derives summaries from committed facts. `FocusState` describes UI lifecycle; existing persisted `SessionStatus.ACTIVE` means RUNNING. `SessionContext` captures optional classroom/goal references and labels, planned seconds and explicit demo provenance. JavaFX controllers use the shared background executor; the timer scheduler submits bounded ticks and never performs JDBC on the FX thread.

Schema 3 and JSON format 2 preserve old data; legacy format 1 imports remain strictly validated. See [focus workflow contract](focus-workflow.md), [verification](verification/focus-workflow.md) and [actual UML](uml-implemented.puml). The historical amendments and target architecture below retain future scope; break cycles, Windows enforcement and rewards remain unimplemented.

## M2 persistence amendment

The user's M2 request authorizes all entity persistence and JSON transfer now. See [actual M2 persistence contract](persistence-m2.md) for ports/adapters, schema 2, edit/delete rules and backup/replacement semantics. ClassroomController now provides persistent subject CRUD; DataTransferController connects Settings to StudyDataTransfer. SqliteStudyDataArchive coordinates consistent multi-table snapshots/replacements, while SnapshotValidator checks the immutable portable records. JSON format 1 includes current facts only. No Windows enforcement, reward policy or timer engine is added. The original target architecture below still includes unimplemented later workflows.

## M1 implementation amendment

The user's milestone 1 request supersedes the initial two-page shell scope: all nine named screens now navigate, with later feature workflows explicitly labelled as unavailable. Models for profile, session facts, classrooms/resources, workspaces/preferences/blocklist selections, goals/tasks and achievement definitions/awards are implemented now; `FocusCycle`, reward policy and later workflow services remain planned. This document otherwise describes the target architecture, not completed work. See [actual-code UML](uml-implemented.puml) and [M1 verification](verification/m1.md) for implementation evidence.

Actual packages use `domain` for models, `application.port` and `application.service` for contracts/use cases, `persistence.sqlite` and `persistence.config` for data/config adapters, `platform.simulation` for the safe blocker, and `ui`/`ui.controller`/`ui.view` for JavaFX. These replace the earlier proposed `infrastructure.*` names without changing dependency direction.

The foundation `StudySessionLedger` has durable `save(StudySession)` and exact start-inclusive/end-exclusive `findByPeriod` operations, with monotonic checkpoint facts, one unfinished session per profile and immutable finalized facts. Session lifecycle commands, snapshots and paged history belong to M3; its migration must preserve the M1 ledger data. M1 queries filter and sort Java Instants exactly in memory, so large-history paging/index optimization remains pending. The simulated blocker exposes immutable selected targets, running state and bounded timestamped events; its OS-free lifecycle is tested, but no UI starts it in M1.

## Structure and dependency direction

Use a single Maven module and a layered ports-and-adapters design. Base package: `com.studymode`. Domain models use Java types only; application services orchestrate use cases through narrow interfaces; JDBC and Windows adapters implement those interfaces; JavaFX controllers call services. `bootstrap` constructs and wires dependencies without a dependency-injection framework. Avoid Spring, an ORM, and a generic repository hierarchy.

```text
UI -> application -> domain
infrastructure -> application ports + domain
bootstrap -> all layers (composition only)
```

Programmatic JavaFX views and CSS are the default: no FXML dependency unless justified later. Use an application launcher separate from the JavaFX Application subclass. Begin with a classpath Maven project; validate the actual launch and jpackage commands rather than promising a fat JAR or untested JPMS setup.

| Package | Responsibilities and planned types |
| --- | --- |
| `bootstrap` | `StudyModeLauncher`, `StudyModeApplication`, object construction, shutdown |
| `domain.profile` | `UserProfile`: UUID, display name, creation instant, study zone |
| `domain.study` | `StudySession`, `SessionStatus`, `FocusCycle`, `CyclePhase`: timing and valid transitions |
| `domain.classroom` | `Classroom`, `StudyResource`: subject identity, label, validated URI |
| `domain.workspace` | `FocusWorkspace`, `FocusPreferences`, `BlockedApplication`: saved focus environment |
| `domain.planning` | `StudyGoal`, `GoalPeriod`, `StudyTask`: targets, due dates, completion |
| `domain.reward` | `Achievement`, `AchievementAward`, `RewardPolicy`: definitions, earned awards, deterministic calculations |
| `application.port` | `UserProfileStore`, `StudySessionLedger`, `ClassroomCatalog`, `FocusWorkspaceStore`, `StudyPlanStore`, `RewardStore`, `DistractionBlocker`, `StudyDataTransfer` |
| `application.service` | `ProfileService`, `FocusSessionService`, `ClassroomService`, `WorkspaceService`, `StudyPlanningService`, `StudyStatisticsService`, `RewardService` |
| `application.error` | `StudyDataException`, `FocusModeException`, `DataImportException` |
| `infrastructure.sqlite` | `SqliteDatabase`, `SchemaMigrator`, concrete port implementations, transaction coordination |
| `infrastructure.config` | `StartupConfiguration`, `StartupConfigurationLoader`, validated presets/arguments, atomic file saves |
| `infrastructure.windows` | `WindowsDistractionBlocker`, `WindowsProcessGateway`, `ProcessSafetyPolicy` |
| `infrastructure.simulation` | `SimulatedDistractionBlocker`: deterministic observable simulation |
| `infrastructure.transfer` | `JsonStudyDataTransfer`: validated portable backup/import |
| `ui` / `ui.view` / `ui.controller` | `AppShell`, sidebar routing, feature views/controllers, reusable dialogs/status components |

Names describe real responsibilities. Create classes only when their milestone needs them. More than two ports are planned because profile, classroom and session persistence have different contracts; all must have real implementations before counting them as grading evidence.

Concrete SQLite adapters are `SqliteUserProfileStore`, `SqliteStudySessionLedger`, `SqliteClassroomCatalog`, `SqliteFocusWorkspaceStore`, `SqliteStudyPlanStore`, and `SqliteRewardStore`, implementing their corresponding ports through `SqliteDatabase`. UI types `ProfileController` and `FocusController` delegate to their respective application services; additional feature controllers follow the same boundary as their pages are added.

## Domain ownership and invariants

- `UserProfile` is the single local identity. UUIDs are stable; user-visible names are editable. Derived XP and streaks do not live as independent editable profile fields.
- `Classroom` owns a `List<StudyResource>`. A resource has UUID, title and HTTP/HTTPS URI. Deleting a classroom requires handling its workspaces/tasks in a transaction; default action is archive while referenced.
- `FocusWorkspace` references a classroom UUID, selected resource UUIDs, `FocusPreferences`, and a `Set<BlockedApplication>`. Resources must belong to that classroom. Blocked applications have display name and canonical executable path; equality/deduplication follows normalized Windows path identity, not display name. Resolve real paths and reject unsafe/unverifiable aliases before enforcement.
- `StudySession` records profile, optional classroom/workspace references, display/settings snapshots, start/end instants, active focus seconds, zone, and status. Recorded duration never comes from subtracting editable wall clocks. At most one unfinished session exists per profile.
- `FocusCycle` owns phase, durations, cycle count and pause state. Planned transitions: IDLE -> FOCUS -> READY_FOR_BREAK -> BREAK -> READY_FOR_FOCUS; pause/resume within active phases, Stop from any running/paused state. FOCUS completion records a completed study session; break time does not count as focus. Long-break selection follows completed focus count. No automatic next-interval start.
- `StudyGoal` has minute target and daily/weekly period; progress derives from ledger data. `StudyTask` has text, optional classroom/goal, optional due date, completion timestamp. UI never mutates internal collections directly.
- `Achievement` is an immutable definition; `AchievementAward` identifies a profile/achievement pair and award instant. Rewards must be unique and repeat-safe.
- Return immutable collection snapshots. Use `List<StudySession>` for ordered history and `Set<BlockedApplication>` to deduplicate a workspace blocklist. These are genuine application data, not unused rubric examples.

## Contracts and orchestration

| Contract/service | Main operations and rules |
| --- | --- |
| `UserProfileStore` / `ProfileService` | `findLocalProfile`, `save`; validated create/update and restart persistence |
| `StudySessionLedger` | `begin`, `checkpoint`, `finish`, `findByPeriod`, paged history; finalization is idempotent by session UUID |
| `DistractionBlocker` | `start(Set<BlockedApplication>)`, `stop`, status/result subscription; implementations are real Windows and explicit simulation |
| `FocusSessionService` | Start validated session; apply immutable workspace snapshot; manage timer and blocker lifecycle; Stop disables blocker first, then persists; surface partial failures without trapping user in focus mode |
| `ClassroomService` / `WorkspaceService` | CRUD/archive; resource selection validation; immutable session snapshots; no implicit URL launches |
| `StudyPlanningService` | Goals/tasks CRUD and completion; progress from session facts |
| `StudyStatisticsService` | Focus totals, completed/interrupted counts, per-day and per-classroom views; zero-data is a normal state |
| `RewardService` | Recompute/award XP, levels, streaks and achievements using committed sessions; retry idempotently on startup and after session completion |
| `StudyDataTransfer` | `inspectImport`, `exportSnapshot`, `replaceFromValidatedSnapshot`; typed summary plus validation errors before destructive replacement |

Use constructor injection of `Clock`, a monotonic time source and a scheduler for deterministic timer tests. Keep monotonic time for elapsed active focus, UTC `Instant` for persisted timestamps, and explicit `ZoneId` for calendar days. Treat abnormally large heartbeat gaps as suspend: pause and credit only the last safely checkpointed interval, then require explicit resume. Document the chosen gap threshold in M3 tests.

Record per-day active seconds so intervals spanning midnight can be split correctly. Use the session's captured study zone; changing profile zone affects future sessions. Streaks use date-adjacent qualifying study days; today without study does not break yesterday's streak until today's day ends. Test DST and zone changes. Rewards derive from saved active focus seconds with per-session rounding down to minutes.

Run database, file and native work on a bounded executor; run a separate single timer scheduler. Publish immutable results to the FX thread. Disable duplicate submissions, retain user form values on error, and expose retry. Stop cancellation cannot wait for a blocked database operation. On close, cancel blocker/scheduler first, persist a final safe checkpoint if possible, and close resources.

## UI design

One resizable window: persistent sidebar, page title/status area, scrollable content, consistent spacing. Dashboard cards show only real persisted/derived values. Sidebar destinations appear as their milestones become usable: Dashboard/Profile, Classrooms/Workspaces, Focus, History, Tasks/Goals, Rewards, Settings/Data.

Use CSS tokens for dark backgrounds, elevated cards, primary accent, text/muted text, success and error states. Use system-installed `Segoe UI` with fallback to JavaFX System, readable sizes, visible keyboard focus and text labels. Proposed default window 1200x800, minimum 900x640; collapse sidebar labels if needed and allow content scrolling. Test at 100%, 125% and 150% Windows scaling. No fixed-coordinate content layout.

Dialogs validate before save, show actionable field errors, and require confirmation for deletion/import replacement. Empty states explain how to add the first item using working actions. Blocking status names skipped/failed targets and distinguishes simulation. Focus always exposes Pause and Stop. Provide accessible labels and keyboard navigation; communicate state in text as well as colour.

## Validation and exception policy

| Input/boundary | Rule / error handling |
| --- | --- |
| Names/text | Trim names; profile 1-60, classroom/workspace/resource title 1-100, task/goal text 1-200 characters; descriptions at most 2000; reject control characters except permitted multiline whitespace |
| Durations/counts | Whole focus minutes 1-240; breaks 1-60; long-break interval 1-12; goals 1-10080 minutes; reject NaN, overflow, fractions and malformed numeric text |
| URLs | Parse URI; absolute HTTP/HTTPS with host, no credentials/control characters, maximum 2048 characters; reject file/javascript/data schemes; no requirement that the remote site be online |
| Paths/processes | Resolve/canonicalize, validate executable existence/type at selection/start, no unverified identifiers or protected targets; never interpolate into a shell command |
| Dates/zone | Valid `LocalDate`/`ZoneId`; past task due dates allowed and shown overdue; persisted timestamps/status/durations must be internally consistent |
| IDs/import/config | Known references, unique UUIDs, supported format/schema versions, bounded lists/file size; reject unexpected command-line flags and invalid enum values; no traversal through preset names |
| Standard exceptions | Handle `NumberFormatException` in form/config parsing; `IOException` including access errors at file boundaries; `SQLException` at JDBC boundary; `URISyntaxException` in URI validation; `InvalidPathException` for user paths; `SecurityException` around process/file access |
| Custom exceptions | `StudyDataException`: persistence/migration failure with cause; `FocusModeException`: invalid lifecycle or blocker startup failure; `DataImportException`: incompatible/invalid snapshot with field diagnostics |

Domain constructors reject illegal values with `IllegalArgumentException`; services/controllers translate expected failures into actionable messages. Log technical causes without exporting private user content. Do not catch `Exception` just to continue or silently substitute success. Expected absence (no profile/history) uses Optional/empty collections, not exceptions.

## Dependency baseline and sources

Proposed pinned baseline as checked on 15 November 2026; no dependencies have been installed or compiled in M0. Revalidate artifact resolution and Java 21 compatibility during M1; pin every build plugin then and every additional dependency when introduced. These are released versions, not a claim that this project already passes integration tests.

| Component | Proposed selection | Primary source |
| --- | --- | --- |
| Java | Maintained OpenJDK 21 distribution; record vendor and patch in build instructions | [JDK 21](https://openjdk.org/projects/jdk/21/) |
| JavaFX | `org.openjfx:javafx-controls:21.0.13` using OpenJFX Maven artifacts | [OpenJFX 21 release notes](https://gluonhq.com/products/javafx/openjfx-21-release-notes/), [build guidance](https://openjfx.io/openjfx-docs/) |
| Maven | Wrapper pinned to 3.9.16 | [Maven release](https://maven.apache.org/docs/3.9.16/release-notes.html) |
| JavaFX Maven plugin | `org.openjfx:javafx-maven-plugin:0.0.8` | [Official plugin](https://github.com/openjfx/javafx-maven-plugin) |
| SQLite JDBC | `org.xerial:sqlite-jdbc:3.54.1.0` | [Upstream releases](https://github.com/xerial/sqlite-jdbc/releases/tag/3.54.1.0) |
| JUnit | `org.junit:junit-bom:5.14.3` and `junit-jupiter` test scope | [JUnit 5 release notes](https://docs.junit.org/5.14.3/release-notes.html) |

No JUnit 6 migration: the requirement explicitly specifies JUnit 5. A JSON library in M7 and JNA in M5 will be selected and pinned from their upstream releases at those gates; they are not needed for M1. OpenJFX distribution/licensing must be recorded accurately; do not assume Oracle JavaFX binary licensing applies to all distributions.

## Target file structure

```text
AGENTS.md
README.md                         # M1 setup/run instructions
pom.xml                           # M1
mvnw, mvnw.cmd, .mvn/wrapper/      # M1 reproducible Maven bootstrap
docs/                             # M0 planning, later verification/evidence
src/main/java/com/studymode/       # packages above, created as needed
src/main/resources/styles/        # M1 dark CSS
src/main/resources/db/migration/   # versioned SQL, first profile schema in M1
src/main/resources/config/         # M1 default properties
src/test/java/com/studymode/       # domain/service/adapter tests
src/test/resources/               # fixtures; never real user data
```

Only AGENTS.md and the planning documents exist at M0. This tree is an implementation destination, not a claim of present files.
