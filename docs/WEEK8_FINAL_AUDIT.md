# Week 8 final audit

Date: **16 November 2026**. Scope: finalize the existing M5 desktop application for examination. **No M6 implementation, redesign, new dependency or GitHub push is included.** The in-class presentation is still pending.

Baseline inspected: `54d878739b7b749978e4c82fd3f9eb133637ade5`, branch `main`, initially clean. The tracked inventory contained 134 files: 83 application Java files, 15 test/fixture Java files, seven runtime resources, 20 documentation files and nine root/build/script files. Inspection covered the domain, application ports/services, SQLite/configuration/JSON adapters, native/simulation boundaries, UI/controllers, migrations, CSS, build/wrapper/scripts, tests and existing scope documents. Ignored toolchains/caches/runtime data were inventoried, not treated as submission source.

## Rubric decision

PASS means demonstrated for the currently implemented scope, not a guarantee of an examiner's award. PARTIAL means preparation/evidence exists but the full criterion or feature is not demonstrated. FAIL means the requirement is not met. The six technical rubric criteria have passing implementation evidence; the two presentation points remain unearned until class presentation.

| Criterion | Status | Exact implementation location | Explanation and test evidence |
| --- | --- | --- | --- |
| 1. In-class presentation (2 points) | **PARTIAL - NOT PRESENTED** | [Presentation notes](WEEK8_PRESENTATION_NOTES.md); production launcher [StudyModeLauncher.java:7](../src/main/java/com/studymode/bootstrap/StudyModeLauncher.java#L7) | Rehearsal instructions and a working app are prepared. `JavaFxSmokeTest` and a separate actual app launch/restart support readiness only. Neither is evidence of presenting in class. |
| 2. UML: four classes, two interfaces, two real collections and relationships (3 points) | **PASS** | [Grading UML source](WEEK8_UML.puml), [PNG](WEEK8_UML.png), [PDF](../output/pdf/WEEK8_UML.pdf); [FocusSessionService.java:13](../src/main/java/com/studymode/application/service/FocusSessionService.java#L13), [SqliteDatabase.java:12](../src/main/java/com/studymode/persistence/sqlite/SqliteDatabase.java#L12), [SqliteUserProfileStore.java:12](../src/main/java/com/studymode/persistence/sqlite/SqliteUserProfileStore.java#L12), [SqliteStudySessionLedger.java:16](../src/main/java/com/studymode/persistence/sqlite/SqliteStudySessionLedger.java#L16); [UserProfileStore.java:6](../src/main/java/com/studymode/application/port/UserProfileStore.java#L6), [StudySessionLedger.java:8](../src/main/java/com/studymode/application/port/StudySessionLedger.java#L8); [Classroom.java:7](../src/main/java/com/studymode/domain/classroom/Classroom.java#L7), [FocusWorkspace.java:9](../src/main/java/com/studymode/domain/workspace/FocusWorkspace.java#L9) | Ten actual classes/records and two application interfaces are drawn. The four named ordinary classes already exceed the minimum without relying on records. The SQLite adapters implement the interfaces. `List<StudyResource>` drives ordered classroom links; `Set<BlockedApplication>` drives unique workspace blocklist membership. `DomainValidationTest.classroomResourcesAreDefensiveAndUnique` and `workspaceUsesPathDeduplicationAndEnforcesClassroomMembership`, plus persistence/organization tests, prove actual use and defensive copies. |
| 3. Save/read application data from files (1 point) | **PASS** | [SqliteUserProfileStore.java:16](../src/main/java/com/studymode/persistence/sqlite/SqliteUserProfileStore.java#L16) and [line 24](../src/main/java/com/studymode/persistence/sqlite/SqliteUserProfileStore.java#L24); [SqliteDatabase.java:17](../src/main/java/com/studymode/persistence/sqlite/SqliteDatabase.java#L17); [JsonStudyDataTransfer.java:53](../src/main/java/com/studymode/persistence/transfer/JsonStudyDataTransfer.java#L53), [line 60](../src/main/java/com/studymode/persistence/transfer/JsonStudyDataTransfer.java#L60); [StartupConfigurationLoader.java:51](../src/main/java/com/studymode/persistence/config/StartupConfigurationLoader.java#L51) | Actual SQLite file persistence, explicit JSON export/import and UTF-8 properties reads/writes. `SqliteFoundationTest.profileSurvivesCreateEditAndReopenWithIdentityIntact`, `StudyDataPersistenceTest.automaticallyCreatesWritableDirectoryAndPersistsEveryEntityAcrossReopen` and `JsonStudyDataTransferTest.exportsEveryEntityImportsAcrossDatabasesAndCreatesReadableRecoveryBackup` pass. Actual process restart also preserved the audit profile and history. |
| 4. Configuration files/arguments change startup (1 point) | **PASS** | [StartupConfigurationLoader.java:23](../src/main/java/com/studymode/persistence/config/StartupConfigurationLoader.java#L23), [defaults.properties](../src/main/resources/config/defaults.properties); [StudyModeApplication.java:44](../src/main/java/com/studymode/bootstrap/StudyModeApplication.java#L44) | CLI and properties determine page, demo mode, blocker mode and dimensions; data-dir chooses a separate file store. Six `StartupConfigurationTest` tests cover precedence, persistence, explicit files, invalid flags/values and locking. Actual launch used Demo/Test, Windows dry-run and an isolated directory; on restart `--start-page=history` opened History. A missing profile intentionally routes first use to Settings. |
| 5. Three standard Java exceptions handled meaningfully (1 point) | **PASS** | [FocusSessionController.java:89](../src/main/java/com/studymode/ui/controller/FocusSessionController.java#L89): `NumberFormatException`; [DataTransferController.java:49](../src/main/java/com/studymode/ui/controller/DataTransferController.java#L49), [line 68](../src/main/java/com/studymode/ui/controller/DataTransferController.java#L68), [line 75](../src/main/java/com/studymode/ui/controller/DataTransferController.java#L75): `IOException`; [SqliteDatabase.java:45](../src/main/java/com/studymode/persistence/sqlite/SqliteDatabase.java#L45): `SQLException`; extra [StudyResource.java:23](../src/main/java/com/studymode/domain/classroom/StudyResource.java#L23): `URISyntaxException` | Numeric input gets corrective form feedback; file errors report failure and cancel unsafe replacement; SQL failures roll back and preserve the cause/data. Malformed URLs become meaningful validation errors. Evidence: invalid startup/numeric tests, JavaFX invalid focus input, JSON backup/export failure tests, SQL rollback/constraint tests and malformed-resource domain tests. These are distinct exception types with different recovery behavior. |
| 6. Two useful custom exceptions (1 point) | **PASS** | [StudyDataException.java:3](../src/main/java/com/studymode/application/error/StudyDataException.java#L3), [FocusModeException.java:3](../src/main/java/com/studymode/application/error/FocusModeException.java#L3), extra [DataImportException.java:4](../src/main/java/com/studymode/application/error/DataImportException.java#L4); use sites [SqliteDatabase.java:31](../src/main/java/com/studymode/persistence/sqlite/SqliteDatabase.java#L31), [FocusSessionService.java:79](../src/main/java/com/studymode/application/service/FocusSessionService.java#L79), [SnapshotValidator.java:12](../src/main/java/com/studymode/application/data/SnapshotValidator.java#L12) | `StudyDataException` carries actionable persistence errors; `FocusModeException` carries invalid lifecycle/prerequisite/safety errors. A third custom exception rejects imports. UI catches display feedback and permit correction/retry. `FocusSessionWorkflowTest` verifies invalid transitions and save-failure recovery; persistence and import suites verify rollback and useful errors. |
| 7. Validate all user input (1 point) | **PASS for exposed M5 inputs** | [Validation.java:9](../src/main/java/com/studymode/domain/Validation.java#L9); [UserProfile.java:9](../src/main/java/com/studymode/domain/profile/UserProfile.java#L9); [FocusPreferences.java:6](../src/main/java/com/studymode/domain/workspace/FocusPreferences.java#L6); [StudyResource.java:9](../src/main/java/com/studymode/domain/classroom/StudyResource.java#L9); [StudyOrganizationService.java:37](../src/main/java/com/studymode/application/service/StudyOrganizationService.java#L37); [StudyJsonStructureValidator.java:33](../src/main/java/com/studymode/persistence/transfer/StudyJsonStructureValidator.java#L33) | Validation exists in forms and domain/service/import boundaries, with prepared SQL and database constraints. Coverage includes names/notes, numeric ranges, zones, links, selection membership, duplicates, executable paths, startup flags and JSON types/references/limits. Domain, organization, configuration, 679 strict JSON regression cases and actual-window invalid input checks pass. This does not claim validation for unimplemented future features. |

## Input-surface checklist

| Exposed input | Validation and failure behavior | Evidence |
| --- | --- | --- |
| Profile display name/time zone | Required, trimmed name, 1-60 code points, no control characters; selected valid `ZoneId`; error shown without save | `Validation`, `UserProfile`, `ProfileController.save`; domain tests, profile reopen and JavaFX empty-name test |
| Classroom name/description/notes/color | Name 1-100, duplicate-name rejection; notes/description <=2000 with restricted control characters; color `#RRGGBB` | `Classroom`, `StudyOrganizationService.saveClassroom`; domain/organization/round-trip tests |
| Educational links | Title 1-100; bounded parsed HTTP/HTTPS URI with host, no credentials/control characters, valid port; duplicate URLs rejected | `StudyResource.fromUrl`, classroom controller/service; malformed/unsafe URL tests and actual JavaFX resource edit |
| Workspace name/classroom/resources/apps | Name/ranges; existing classroom and resource membership; unique selections; absolute `.exe` metadata and existing new selections; shortcut/blocklist conflict rejected | `FocusWorkspace`, `BlockedApplication`, organization service/store; workspace CRUD, relationship rollback, missing/conflicting executable tests |
| Focus/short break/long break/cadence | Whole standard focus minutes 1-240; demo seconds 1-14400; breaks 1-60 minutes; cadence 1-12. Invalid transitions/references/unsaved state refused | `FocusPreferences`, `FocusSessionService`, focus/workspace forms; timer/break suites and JavaFX `0` input test |
| Goal/task inputs | Text 1-200, goal target 1-10080 minutes, known period/classroom/profile, duplicate active tasks and goal names rejected | `StudyGoal`, `StudyTask`, organization service and forms; organization validation/CRUD tests |
| Executable enablement/assignment/launch | File picker and absolute `.exe` metadata; known saved selection; duplicate path and workspace membership checks; native identity/safety checks before acting; missing shortcut shown as failure | `BlockedApplicationService`, workspace/blocklist controllers, `StudyApplicationLauncher`, Windows adapters; native refusal and fake-gateway safety tests |
| Startup/configuration/window size | Known keys/pages/modes, no duplicate flags, exact booleans, whole bounded sizes, absolute supplied paths; invalid existing config not overwritten | `StartupConfigurationLoader`, `AppShell` size save; six configuration tests |
| JSON/files | Strict property/token types, unique identifiers, known relationships, valid domain values; 20 MiB/100,000 record/depth limits; new export filename outside data root; verified backup before replacement; no import during active focus/break/pending save | Structure/snapshot validators, transfer/archive, focus import guard; strict JSON, rollback and break import-guard tests |

## Verification and observed behavior

Initial full verification of the baseline passed **792 tests, zero failures/errors/skips** in 42.564 seconds at 08:37:50 UTC. The actual app was then launched with:

```powershell
.\scripts\dev.ps1 -Task run -Demo -Blocker windows-dry-run -DataDirectory .run/week8-audit -StartPage history
```

The separate synthetic audit directory was not committed. It initially opened Settings because there was no profile. A synthetic profile was created and saved. Live controls showed a running focus session, a pause frozen at ten seconds, Resume, and Finish with 22 actual seconds. A manual break counted down while focused time stayed at 22 seconds; Skip ended the break. A separate short demo fact of two actual seconds was also present after rehearsal interaction, not represented as a full planned ten-second completion.

Both launches were closed using the app's normal Close control; Maven reported normal exit. On reopening the same directory with the same History argument, the actual JavaFX table showed the same profile and two completed DEMO rows of **22 seconds and 2 seconds**. Read-only SQLite inspection agreed. This was a real process restart, not a renamed in-memory object. No personal data directory was opened or changed.

| Workflow | Audit status | Evidence and limits |
| --- | --- | --- |
| Build/startup | **PASS** | Java 21 compilation and Maven verification; actual `StudyModeLauncher` process/window appeared, loaded Settings/History appropriately and exited normally |
| Profile persistence | **PASS** | Live save/restart and profile create/edit/reopen JUnit tests preserve identity |
| Classroom/workspace | **PASS** | Real-window `JavaFxSmokeTest` creates/edits classroom links/notes/tasks and workspace timing fields, starts saved workspace through existing engine; database CRUD/reopen/relationship tests pass |
| Focus, pause/resume/finish/interruption | **PASS** | Actual live pause/resume/finish plus deterministic transition, fractional time, stop, save-failure and shutdown/recovery tests; JavaFX Stop records INTERRUPTED |
| History/restart | **PASS** | Actual restarted History table matches durable facts; checkpoint-only interrupted recovery and finalized-fact immutability tests pass |
| Break cycles | **PASS** | Live manual break/skip kept focus unchanged; 17 deterministic break tests cover automatic/manual, short/long cadence, pause/resume/skip, restart, imports, save retry and notification settings; actual JavaFX auto-break/completion popup checks pass |
| Ten-second demo accuracy | **PASS** | Opt-in production-clock test waits real time and saves/reloads exactly ten seconds. Demo is explicitly marked; no synthetic 25-minute credit |
| Windows blocker | **PARTIAL as native closing acceptance** | Ten fake-gateway safety/cancellation tests and one real read-only native identity/refusal check pass. Actual app runs Windows dry-run mode. No ordinary app was selected or closed; a native disposable-fixture close test was not performed, so successful native closing is not claimed |
| Notifications | **PASS for documented fallback** | Nonmodal JavaFX popup verified by actual-window test; enabled/disabled and sound preference forwarding tested. Audible hardware output/Windows Action Center delivery is not claimed |

### Final build and test run

Final command after the only application-code correction, with every opt-in test enabled:

```powershell
.\scripts\dev.ps1 -Task build -IncludeUiTests -RealDemoTest -WindowsBlockerTests
```

The script invokes the existing wrapper `clean verify`, with local Maven cache and `studymode.ui.tests`, `studymode.real.demo`, `studymode.windows.tests` all true. It removes only the verified nonlinked `target` directory first, using the existing OneDrive clean workaround. Native checks run with the normal user token outside the tool sandbox because that sandbox restricts service enumeration; no administrator token or security change is used.

**Final result: BUILD SUCCESS; 792 tests, 0 failures, 0 errors, 0 skipped; 42.295 seconds; completed 16 November 2026 at 08:52:18 UTC.** Java 21/Maven version enforcement passed, all 83 application and 15 test/fixture sources compiled, and the JAR was produced. No application code changed after this final verification.

| Suite | Test cases |
| --- | ---: |
| Domain validation | 16 |
| Break-cycle workflow | 17 |
| Focus compatibility | 3 |
| Focus workflow, including real ten-second demo | 16 |
| Strict JSON types/fields | 679 |
| JSON transfer/backup/rollback | 10 |
| SQLite foundation | 7 |
| Startup configuration | 6 |
| All-entity persistence | 8 |
| Classroom/workspace/goal workflows | 17 |
| Simulation | 1 |
| Windows gateway safety/cancellation | 10 |
| Read-only native Windows smoke | 1 |
| Actual JavaFX window workflow | 1 |
| **Total** | **792** |

Local detailed reports are generated under ignored `target/surefire-reports/`; no machine-specific test reports are committed. The count includes dynamic/parameterized regression cases, not 792 separate handwritten test methods.

## Final UML and artifact verification

The grading source draws ten actual classes/records and two implemented application interfaces, with associations, interface realization, UUID references, attributes/methods and two useful collection types. It is a deliberately readable subset; [uml-implemented.puml](uml-implemented.puml) retains the full 83-top-level-type view, and [uml-planned.puml](uml-planned.puml) remains explicitly future design.

Deliverables: [PNG](WEEK8_UML.png), [SVG](WEEK8_UML.svg), [editable PlantUML](WEEK8_UML.puml), and [one-page A3 PDF](../output/pdf/WEEK8_UML.pdf). PlantUML uses Smetana (no native Graphviz requirement). PNG dimensions are 2454 x 2880; the PDF contains the same diagram at printable scale. PDF metadata has no personal author name, embedded JavaScript or encryption. Both the PNG and rendered PDF were visually inspected for readable labels, complete boxes and unclipped arrows. `pdfinfo`/`pypdf` confirm one A3 page with the diagram image.

The first PDF conversion attempt found that optional `svglib` was unavailable. It was replaced by ReportLab embedding the high-resolution PNG; no project dependency was added. The resulting PDF was rendered with Poppler and visually verified. No unavailable conversion was reported as a successful vector export.

## Changes made for grading

- Corrected only `MilestoneInfoView`'s Statistics status text/badge: goal progress already exists on Goals, while detailed charts and history filters do not. The old text incorrectly described implemented goal progress as future work. No workflow/service/schema/API changed.
- Strengthened `.gitignore` for local `.env` secrets, alternate SQLite filenames/sidecars, class files, diagnostic dumps, certificate/private-key containers, local backup and temporary folders. Existing `.tools`, `.run`, `target`, database/log/settings/export exclusions remain.
- Marked PDF/PNG grading deliverables as binary in `.gitattributes` so Windows Git newline conversion cannot alter their bytes; SVG remains UTF-8 text.
- Added this audit, presentation notes, grading UML source/PNG/SVG/PDF, and README/traceability links. The PDF and UML images are intentional grading deliverables, not accidental build artifacts.
- No compilation, startup, persistence or timer defect was found requiring an engine change. No tests, dependencies or future features were added for rubric padding.

## Submission hygiene and commit boundary

Tracked files were checked for private-key/credential patterns and database/cache/build/runtime artifacts. The source's `https://user:password@example.com` value is an intentionally rejected synthetic URL test, not a credential; native `token` variables are process metadata/cancellation values. No real credentials were found. Historical repository paths and synthetic test names are documentation/test fixtures, not copied application user records.

`git check-ignore` verifies `.run/week8-audit/studymode.db`, `.tools/plantuml.jar`, `target/study-mode-0.1.0.jar`, `.env`, `.env.local`, alternate `.sqlite`/`.sqlite3` files, `.class`, local key containers and backup paths are excluded. Only named grading/source changes are staged. No ignored runtime file, local profile/configuration, cache, database or installed toolchain belongs in the final commit. Local UML/PDF previews remain in ignored `.run`.

The final commit is made on `main`; its exact SHA and post-commit working-tree state are reported in the completion reply (a commit cannot embed its own SHA without changing it). The examination audit itself made no remote push. The user subsequently authorized a normal upload to `https://github.com/andreitegzes06-ui/learning_app.git`; publication must preserve remote history and is verified separately in the completion reply.

## Unfinished features and examination limits

- **In-class presentation remains pending.** Rehearsal/testing does not earn the presentation criterion.
- No XP, levels, computed streaks, reward policy or achievement unlock engine. Existing achievement definition/award storage is only persistence support; M6 has not begun.
- Detailed charts, statistics screen workflow, history filters/paging, advanced task scheduling and named startup preset management remain unfinished. Dashboard summaries and linked goal progress do work.
- Notifications use the documented in-app JavaFX fallback. No native Windows toast/Action Center transport is installed. Optional sound depends on machine audio/mute settings.
- Break state/counts and automatic-transition/notification choices reset on restart; the four workspace timing values persist. Crash recovery credits only committed focus checkpoints, not all unsaved final seconds or offline time.
- Native blocking is best-effort graceful window closing, not forced termination or guaranteed prevention. Apps may ignore it or show save prompts. Native fixture closing is not verified by this audit. No security settings, services or startup enforcement are installed.
- Launch through Maven/Java 21; a self-contained Windows installer and full DPI/accessibility matrix are not completed. Verify the classroom toolchain before presentation.

These limits do not justify marking future workflows complete. They are preserved rather than expanding scope for Week 8.
