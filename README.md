# Study Mode

A Java 21 / JavaFX Windows desktop application for organising a local study environment. **Milestone 2: reliable SQLite persistence and JSON transfer.**

The application has working navigation across all nine screens. Settings creates/edits the persistent local profile and provides JSON export/import. Classrooms supports creating, listing, editing and deleting subjects. Other workflow screens remain explicitly labelled as unfinished. No timer, Windows enforcement or reward calculations run.

SQLite now stores profiles, sessions, classrooms/resources, workspaces and their selections, blocked-application metadata, goals, tasks, achievement definitions and recorded awards. The persistence APIs provide CRUD operations; the later workspace/task/session/reward workflow editors remain out of scope. First launch creates a user-writable data directory and database automatically; existing M1 databases migrate to schema 2 without losing profile/session facts.

For the complete schema, relationship rules and acceptance evidence, see [M2 persistence](docs/persistence-m2.md) and [M2 verification](docs/verification/m2.md).

## Build and launch

Install a Java **21** JDK and set `JAVA_HOME` to its installation directory. Maven is downloaded by the committed Apache Maven wrapper (3.9.16); a system Maven installation is not required. Initial builds need Internet access to Maven Central. Run these commands from the project directory in PowerShell:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd javafx:run
```

On this development machine, Windows Application Control blocked the unsigned native JavaFX libraries in Maven's OpenJFX distribution. An official BellSoft Liberica **JDK 21 Full** archive with valid BELLSOFT-signed JavaFX DLLs was used successfully for runtime tests. Use a policy-approved vendor runtime on similarly restricted Windows machines; do not disable security controls or unblock an unverified DLL. The SDK-only Gluon download also had unsigned native DLLs in this environment.

A convenience script uses `JAVA_HOME` or the project-local JDK downloaded for this development session. It keeps Maven downloads under ignored `.tools/`:

```powershell
.\scripts\dev.ps1 -Task build
.\scripts\dev.ps1 -Task run
.\scripts\dev.ps1 -Task ui-test
# Clean build including the actual-window smoke test:
.\scripts\dev.ps1 -Task build -IncludeUiTests
```

`.tools/` and its toolchains are intentionally not committed. A fresh clone needs its own JDK 21. If using a vendor full JDK, its bundled JavaFX runtime may carry a vendor patch suffix; compile dependencies remain pinned in the POM. Tested here: BellSoft Java 21.0.13.0.3+5 Full, bundled JavaFX 21.0.13.0.3; compile artifacts OpenJFX 21.0.13. Initial non-UI build also passed on Temurin 21.0.13+9.

## Startup options and local data

Default data directory: `%LOCALAPPDATA%\StudyMode`. SQLite and `settings.properties` live there, protected by a single-instance data-directory lock. No cloud account, Google login or administrator privileges are needed. Use a local, nonsynced data directory for normal use.

```powershell
# Isolated development data, with a fully quoted path assembled by the script:
.\scripts\dev.ps1 -Task run -DataDirectory .run\my-profile -StartPage settings

# A directly supplied Maven argument string:
.\mvnw.cmd javafx:run '-Djavafx.args=--start-page=statistics --window-width=1100 --window-height=720'
```

Supported flags use `--key=value`:

| Flag | Values |
| --- | --- |
| `--data-dir` | Absolute local directory; default uses LocalAppData |
| `--config` | Absolute path to an existing UTF-8 properties file |
| `--start-page` | `dashboard`, `focus`, `classrooms`, `workspaces`, `blocked`, `goals`, `statistics`, `achievements`, `settings` |
| `--blocker` | `simulated` only in M1; does not start simulation |
| `--window-width` / `--window-height` | Whole pixels, 900–3840 / 640–2160 |

Properties files support start-page, blocker and window dimensions. Precedence: bundled defaults < data-directory settings < explicit config file < command line. Unknown/duplicate flags and invalid settings are rejected. "Save current window size" saves only the dimensions you explicitly choose; runtime CLI overrides do not overwrite other settings. Startup page selection never starts focus or blocking. Named presets arrive in M7.

## Tests and architecture

```powershell
.\mvnw.cmd verify
# Requires an interactive Windows desktop and permitted JavaFX native libraries:
.\mvnw.cmd '-Dstudymode.ui.tests=true' verify
```

The suite tests domain/configuration validation, entity CRUD and reopen, migration preservation/rollback, SQL constraints, consistent snapshots, full JSON round trips, invalid/oversized imports, backup failure and SQL-failure rollback. The opt-in JavaFX test exercises nine destinations, profile creation/editing, classroom persistence, readable save errors, JSON export/error feedback, resizing and preferences. Tests use temporary data directories. After the audit validation fix, the suite has **597 tests with UI enabled** (the original 48 plus 549 new type/structure regression cases); ordinary runs intentionally skip the one GUI test. See [validation verification](docs/verification/json-validation.md).

| Location | Responsibility |
| --- | --- |
| `src/main/java/com/studymode/domain/` | Validated models, immutable lists/sets; no JavaFX or JDBC |
| `src/main/java/com/studymode/application/` | Core ports, profile service, custom errors |
| `src/main/java/com/studymode/persistence/` | SQLite adapters/migrations, properties and data-directory lock |
| `src/main/java/com/studymode/platform/` | Explicit OS-free simulation adapter |
| `src/main/java/com/studymode/ui/` | App shell, navigation, profile controller and feature status views |
| `src/main/java/com/studymode/bootstrap/` | Entry point, composition and shutdown |
| `src/main/resources/` | Dark CSS, default properties and migration SQL |
| `src/test/java/` | JUnit domain, persistence, configuration, platform and UI tests |
| `docs/` | Plans, actual/planned UML, traceability and verification |

The core interfaces are implemented by `SqliteStudySessionLedger` and `SimulatedDistractionBlocker`; profile persistence has its own `UserProfileStore` implementation. All application I/O uses a single background executor; UI updates return to the FX thread. No native process-enforcement code is included in M1.

## Current limits

- Focus cycles/history UI (M3), full resource/workspace editing, goals/tasks/statistics workflows, Windows enforcement (M5), rewards (M6) and named startup presets remain unimplemented and labelled. Their underlying records can already be persisted and transferred; no feature engine is implied by storage support.
- Session persistence is a tested foundation contract; snapshots, per-day checkpoints and indexed/paged history must be added in M3 while preserving existing facts. Period queries currently filter/sort loaded records in memory.
- JSON import is replacement, not merge: validation and preview precede confirmation; a verified recovery JSON backup is mandatory; all DB changes commit together or roll back. Unfinished imported sessions become interrupted, and locally unfinished sessions prevent import. Exports require a new filename outside the app data root; existing files are never overwritten. Backups/exports contain private data without encryption. M1 had no export format, so format 1 begins with M2.
- Self-contained Windows packaging and complete DPI/accessibility acceptance remain M8 work; launch through Maven.
- Maven clean fails to remove generated read-only directories in this OneDrive-backed checkout. The Windows convenience script's `build` task removes **only** the verified local `target` output using native PowerShell before Maven `clean verify`; it refuses linked target directories. No source or user data is removed. Prefer a nonsynced checkout for ordinary direct Maven clean builds.

See [M2 verification](docs/verification/m2.md), [traceability](docs/traceability.md), [actual-code UML](docs/uml-implemented.puml) and the [planning index](docs/README.md). Development rules: [AGENTS.md](AGENTS.md).
