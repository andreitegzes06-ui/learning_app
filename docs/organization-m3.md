# Milestone 3 — Classrooms, workspaces and study goals

Implemented against verified commit 014eb84. This is the user's organization milestone; it follows the separately completed focus-session workflow and supersedes earlier planned milestone numbering. No subsequent milestone is started.

## Actual functionality

- Classrooms: create/edit/delete, validated name/description, chosen color, notes, educational resource add/update/remove/open, task add/complete/remove, completed study-time total and subject-specific completed/interrupted history. Link edits are staged until Save classroom. Tasks are immediate persistent operations on a saved classroom.
- Workspaces: create/edit/select/delete; associated classroom; focus and default break minutes; multiple classroom resource selections; executable blocklist selections; optional study application shortcuts. Start saved workspace configures the existing real FocusSessionService with its saved classroom/duration and optionally a chosen goal. Unsaved form changes are never mistaken for saved settings.
- Resources open through JavaFX HostServices only on explicit click. Study shortcuts open only on explicit click through ProcessBuilder with a single validated executable path, no shell/arguments/elevation. Missing programs show a useful error. Nothing opens automatically when starting a workspace.
- Goals: create/edit/delete, optional classroom assignment, target minutes, daily/weekly periods, actual progress display and linked focus Start. Assigned goals require the matching classroom; a goal's assignment cannot contradict its saved session history.
- Progress: only completed sessions explicitly linked to the goal count, with actual focused seconds. Daily uses the profile's current local day; weekly starts Monday in that zone. Interrupted, paused, unlinked and out-of-period time is excluded. Classroom history includes both final outcomes; its completed-time total excludes interruptions.
- Workspace break preferences and blocklists are real persisted configuration. Automatic break cycles and Windows enforcement remain outside this milestone. The UI states this explicitly; no fake desktop/blocking action is offered.

## Architecture and object-oriented evidence

The existing Classroom, StudyResource, StudyTask, FocusWorkspace, FocusPreferences, StudyGoal and StudySession models are reused. StudyOrganizationService coordinates the existing ClassroomCatalog, FocusWorkspaceStore, StudyPlanStore, UserProfileStore, StudySessionLedger and BlockedApplicationCatalog ports; their SQLite implementations are retained. FocusSessionService remains the sole timing engine.

ClassroomController is extended; WorkspaceController and GoalController provide the new workflows. StudyEditorActions handles the shared background/FX handoff. StudyApplicationLauncher performs the explicit desktop shortcut operation. Bootstrap wires dependencies; UI and application/domain concerns remain separate. No new interface exists solely for grading.

List<StudySession> drives classroom history and progress calculations. Set<BlockedApplication> drives real workspace selections, deduplication, overlap rejection, persistence and shortcut selection. The original DistractionBlocker interface and tested simulation adapter remain intact, without activating enforcement. These and the application-specific model/service/controller classes exceed the rubric's class/interface/collection requirements. The actual PlantUML/SVG includes the real dependencies and nested view-data records.

## Persistence and validation

V004 upgrades schema 3 to **4** additively: classroom color/notes, optional goal classroom FK and workspace_shortcut child table. Original IDs, descriptions, resources, preferences, tasks and session facts remain. Migration is transactional, ordered and repeat-safe; newer unknown schemas are rejected without reset.

JSON exports use **format 3**; strict original-shape validation accepts formats 1/2 and supplies trusted defaults before binding. New fields remain fully type-checked, required/null rules preserved; nullable goal classroom is explicitly declared. Shortcuts transfer as validated metadata and do not launch during import. Portability permits a saved/imported executable to be absent on another machine; new user selections must be existing absolute .exe files, unchanged imported selections can remain, and launch revalidates existence.

Names are trimmed, bounded and reject controls; new duplicate classroom/workspace/goal names are rejected ignoring case. New repeated resource URLs, repeated executable selections and shortcut/blocklist overlaps are rejected. Focus/break/goal targets are bounded whole numbers. URLs require HTTP(S), host and no credentials. Services re-read saved references before edit/start rather than silently recreating deleted entities. Existing legacy records remain readable; a classroom with old repeated URLs must remove duplicates before saving edits. Import validates structure/references before replacement and retains atomic backup/rollback behavior.

Deletion rules:
- Referenced classrooms are protected by workspace, assigned-goal and session FKs. An unreferenced classroom deletes its resources and detaches task classroom links after confirmation.
- A resource used by a workspace cannot be removed; the entire classroom edit rolls back.
- A goal referenced by a recorded session cannot be deleted; otherwise task goal links are detached.
- Deleting a workspace removes only its settings/selections/shortcuts; recorded session facts remain.
- Deleting a task never erases study sessions.

## Launch and demonstrate

```powershell
.\scripts\dev.ps1 -Task run -Demo -DataDirectory .run\organization-demo -StartPage classrooms
```

Create a profile in Settings on first launch. In Classrooms create a subject, choose color, add notes and an educational link, then Save classroom. Add and complete a task. In Goals create a daily 15-minute goal assigned to that classroom. In Workspaces create a one-minute workspace, choose the subject/resources and save; optionally select real executable files for metadata/shortcuts. Select the goal and press Start saved workspace.

The real focus screen shows a one-minute target even in Demo mode: workspace minutes remain actual minutes. For a quick presentation, let several seconds elapse and Finish early; only those seconds count. Inspect classroom history and goal progress, then restart using the same directory to show persistence. Alternatively the ordinary Focus demo input permits a 10-second session. No time is fabricated.

New dependencies were not introduced. Build/runtime prerequisites and signed JavaFX/OneDrive host constraints remain in the main README.

## Verification

```powershell
.\scripts\dev.ps1 -Task build -IncludeUiTests -RealDemoTest
```

Final clean Maven compile/JUnit/package: **748 tests, zero failures, errors or skips**.

The new StudyOrganizationWorkflowTest has 17 integration tests: classroom/workspace/goal CRUD and reopen, tasks, resource rollback and referenced deletion, workspace Start through the existing engine, classroom history, linked actual progress, Monday boundaries, study-zone midnight, invalid/duplicate/deleted inputs, missing paths, format 1/2/3 compatibility, unsupported versions, and preservation of an actual schema-3 database.

The actual-window JavaFX test now creates notes/link/task/goal/workspace, starts that workspace, verifies the configured focus duration, finishes using injected time, and checks real goal progress/classroom history. Previous profile/timer/dashboard/history/import/export/error checks remain. A separate real 10-second monotonic test still records/reloads exactly ten seconds. The strict JSON dynamic corpus now covers new fields and shortcut element structures while checking failed imports leave storage unchanged.

PlantUML syntax and SVG generation are checked; the diagram declares 79 real types (72 top-level source files and seven named nested types). No manual browser launch, real program shortcut execution or complete DPI/accessibility review is claimed by automated tests.

## Remaining scope

No Windows process termination, rewards, automatic breaks, statistics charts/paging, advanced task due-date/goal editing controls, named presets or packaged installer. These were not requested here. Goal periods use the existing DAILY/WEEKLY model; progress is current-period, rather than a lifetime total. Long histories are currently aggregated in memory off the FX thread. Full visual/scaling and clean-machine packaging acceptance remains future work.

The final Git commit hash is reported after successful verification and commit.

## Changed-file inventory

- [AGENTS.md](../AGENTS.md)
- [docs/architecture.md](../docs/architecture.md)
- [docs/focus-workflow.md](../docs/focus-workflow.md)
- [docs/milestones.md](../docs/milestones.md)
- [docs/organization-m3.md](../docs/organization-m3.md)
- [docs/persistence-m2.md](../docs/persistence-m2.md)
- [docs/README.md](../docs/README.md)
- [docs/traceability.md](../docs/traceability.md)
- [docs/uml-implemented.puml](../docs/uml-implemented.puml)
- [docs/uml-implemented.svg](../docs/uml-implemented.svg)
- [README.md](../README.md)
- [src/main/java/com/studymode/application/data/SnapshotValidator.java](../src/main/java/com/studymode/application/data/SnapshotValidator.java)
- [src/main/java/com/studymode/application/data/StudyDataSnapshot.java](../src/main/java/com/studymode/application/data/StudyDataSnapshot.java)
- [src/main/java/com/studymode/application/error/FocusModeException.java](../src/main/java/com/studymode/application/error/FocusModeException.java)
- [src/main/java/com/studymode/application/service/FocusSessionService.java](../src/main/java/com/studymode/application/service/FocusSessionService.java)
- [src/main/java/com/studymode/application/service/StudyOrganizationService.java](../src/main/java/com/studymode/application/service/StudyOrganizationService.java)
- [src/main/java/com/studymode/bootstrap/StudyModeApplication.java](../src/main/java/com/studymode/bootstrap/StudyModeApplication.java)
- [src/main/java/com/studymode/domain/classroom/Classroom.java](../src/main/java/com/studymode/domain/classroom/Classroom.java)
- [src/main/java/com/studymode/domain/planning/StudyGoal.java](../src/main/java/com/studymode/domain/planning/StudyGoal.java)
- [src/main/java/com/studymode/domain/workspace/FocusWorkspace.java](../src/main/java/com/studymode/domain/workspace/FocusWorkspace.java)
- [src/main/java/com/studymode/persistence/sqlite/SchemaMigrator.java](../src/main/java/com/studymode/persistence/sqlite/SchemaMigrator.java)
- [src/main/java/com/studymode/persistence/sqlite/SqliteClassroomCatalog.java](../src/main/java/com/studymode/persistence/sqlite/SqliteClassroomCatalog.java)
- [src/main/java/com/studymode/persistence/sqlite/SqliteFocusWorkspaceStore.java](../src/main/java/com/studymode/persistence/sqlite/SqliteFocusWorkspaceStore.java)
- [src/main/java/com/studymode/persistence/sqlite/SqliteStudyDataArchive.java](../src/main/java/com/studymode/persistence/sqlite/SqliteStudyDataArchive.java)
- [src/main/java/com/studymode/persistence/sqlite/SqliteStudyPlanStore.java](../src/main/java/com/studymode/persistence/sqlite/SqliteStudyPlanStore.java)
- [src/main/java/com/studymode/persistence/transfer/JsonStudyDataTransfer.java](../src/main/java/com/studymode/persistence/transfer/JsonStudyDataTransfer.java)
- [src/main/java/com/studymode/persistence/transfer/StudyJsonStructureValidator.java](../src/main/java/com/studymode/persistence/transfer/StudyJsonStructureValidator.java)
- [src/main/java/com/studymode/platform/desktop/StudyApplicationLauncher.java](../src/main/java/com/studymode/platform/desktop/StudyApplicationLauncher.java)
- [src/main/java/com/studymode/ui/AppShell.java](../src/main/java/com/studymode/ui/AppShell.java)
- [src/main/java/com/studymode/ui/controller/ClassroomController.java](../src/main/java/com/studymode/ui/controller/ClassroomController.java)
- [src/main/java/com/studymode/ui/controller/FocusSessionController.java](../src/main/java/com/studymode/ui/controller/FocusSessionController.java)
- [src/main/java/com/studymode/ui/controller/GoalController.java](../src/main/java/com/studymode/ui/controller/GoalController.java)
- [src/main/java/com/studymode/ui/controller/WorkspaceController.java](../src/main/java/com/studymode/ui/controller/WorkspaceController.java)
- [src/main/java/com/studymode/ui/view/MilestoneInfoView.java](../src/main/java/com/studymode/ui/view/MilestoneInfoView.java)
- [src/main/java/com/studymode/ui/view/StudyEditorActions.java](../src/main/java/com/studymode/ui/view/StudyEditorActions.java)
- [src/main/resources/db/migration/V004__organization.sql](../src/main/resources/db/migration/V004__organization.sql)
- [src/test/java/com/studymode/persistence/FocusCompatibilityTest.java](../src/test/java/com/studymode/persistence/FocusCompatibilityTest.java)
- [src/test/java/com/studymode/persistence/JsonImportTypeValidationTest.java](../src/test/java/com/studymode/persistence/JsonImportTypeValidationTest.java)
- [src/test/java/com/studymode/persistence/JsonStudyDataTransferTest.java](../src/test/java/com/studymode/persistence/JsonStudyDataTransferTest.java)
- [src/test/java/com/studymode/persistence/StudyDataPersistenceTest.java](../src/test/java/com/studymode/persistence/StudyDataPersistenceTest.java)
- [src/test/java/com/studymode/persistence/StudyOrganizationWorkflowTest.java](../src/test/java/com/studymode/persistence/StudyOrganizationWorkflowTest.java)
- [src/test/java/com/studymode/ui/JavaFxSmokeTest.java](../src/test/java/com/studymode/ui/JavaFxSmokeTest.java)
