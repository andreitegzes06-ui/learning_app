# M1 file inventory

Initial repository was empty, with M0 planning files still uncommitted. This first source commit includes that baseline and the M1 foundation. Toolchains, caches, build outputs and smoke-test data are excluded.

| File | Kind |
| --- | --- |
| `.gitattributes` | Project/build tooling and rules |
| `.gitignore` | Project/build tooling and rules |
| `.mvn/wrapper/maven-wrapper.properties` | Project/build tooling and rules |
| `AGENTS.md` | Project/build tooling and rules |
| `docs/architecture.md` | Documentation / diagram |
| `docs/milestones.md` | Documentation / diagram |
| `docs/persistence-and-windows.md` | Documentation / diagram |
| `docs/README.md` | Documentation / diagram |
| `docs/requirements.md` | Documentation / diagram |
| `docs/traceability.md` | Documentation / diagram |
| `docs/uml-implemented.puml` | Documentation / diagram |
| `docs/uml-implemented.svg` | Documentation / diagram |
| `docs/uml-planned.puml` | Documentation / diagram |
| `docs/verification/m0.md` | Documentation / diagram |
| `docs/verification/m1-files.md` | Documentation / diagram |
| `docs/verification/m1.md` | Documentation / diagram |
| `mvnw` | Project/build tooling and rules |
| `mvnw.cmd` | Project/build tooling and rules |
| `pom.xml` | Project/build tooling and rules |
| `README.md` | Project/build tooling and rules |
| `scripts/dev.ps1` | Project/build tooling and rules |
| `src/main/java/com/studymode/application/error/FocusModeException.java` | Application Java source |
| `src/main/java/com/studymode/application/error/StudyDataException.java` | Application Java source |
| `src/main/java/com/studymode/application/port/DistractionBlocker.java` | Application Java source |
| `src/main/java/com/studymode/application/port/StudySessionLedger.java` | Application Java source |
| `src/main/java/com/studymode/application/port/UserProfileStore.java` | Application Java source |
| `src/main/java/com/studymode/application/service/ProfileService.java` | Application Java source |
| `src/main/java/com/studymode/bootstrap/StudyModeApplication.java` | Application Java source |
| `src/main/java/com/studymode/bootstrap/StudyModeLauncher.java` | Application Java source |
| `src/main/java/com/studymode/domain/classroom/Classroom.java` | Application Java source |
| `src/main/java/com/studymode/domain/classroom/StudyResource.java` | Application Java source |
| `src/main/java/com/studymode/domain/planning/GoalPeriod.java` | Application Java source |
| `src/main/java/com/studymode/domain/planning/StudyGoal.java` | Application Java source |
| `src/main/java/com/studymode/domain/planning/StudyTask.java` | Application Java source |
| `src/main/java/com/studymode/domain/profile/UserProfile.java` | Application Java source |
| `src/main/java/com/studymode/domain/reward/Achievement.java` | Application Java source |
| `src/main/java/com/studymode/domain/reward/AchievementAward.java` | Application Java source |
| `src/main/java/com/studymode/domain/study/SessionStatus.java` | Application Java source |
| `src/main/java/com/studymode/domain/study/StudySession.java` | Application Java source |
| `src/main/java/com/studymode/domain/Validation.java` | Application Java source |
| `src/main/java/com/studymode/domain/workspace/BlockedApplication.java` | Application Java source |
| `src/main/java/com/studymode/domain/workspace/FocusPreferences.java` | Application Java source |
| `src/main/java/com/studymode/domain/workspace/FocusWorkspace.java` | Application Java source |
| `src/main/java/com/studymode/persistence/config/DataDirectoryLock.java` | Application Java source |
| `src/main/java/com/studymode/persistence/config/StartupConfiguration.java` | Application Java source |
| `src/main/java/com/studymode/persistence/config/StartupConfigurationLoader.java` | Application Java source |
| `src/main/java/com/studymode/persistence/sqlite/SchemaMigrator.java` | Application Java source |
| `src/main/java/com/studymode/persistence/sqlite/SqliteDatabase.java` | Application Java source |
| `src/main/java/com/studymode/persistence/sqlite/SqliteStudySessionLedger.java` | Application Java source |
| `src/main/java/com/studymode/persistence/sqlite/SqliteUserProfileStore.java` | Application Java source |
| `src/main/java/com/studymode/platform/simulation/SimulatedDistractionBlocker.java` | Application Java source |
| `src/main/java/com/studymode/ui/AppShell.java` | Application Java source |
| `src/main/java/com/studymode/ui/controller/ProfileController.java` | Application Java source |
| `src/main/java/com/studymode/ui/NavigationPage.java` | Application Java source |
| `src/main/java/com/studymode/ui/view/MilestoneInfoView.java` | Application Java source |
| `src/main/java/com/studymode/ui/view/ViewComponents.java` | Application Java source |
| `src/main/resources/config/defaults.properties` | Application resource |
| `src/main/resources/db/migration/V001__foundation.sql` | Application resource |
| `src/main/resources/styles/dark.css` | Application resource |
| `src/test/java/com/studymode/domain/DomainValidationTest.java` | JUnit test |
| `src/test/java/com/studymode/persistence/SqliteFoundationTest.java` | JUnit test |
| `src/test/java/com/studymode/persistence/StartupConfigurationTest.java` | JUnit test |
| `src/test/java/com/studymode/platform/SimulatedDistractionBlockerTest.java` | JUnit test |
| `src/test/java/com/studymode/ui/JavaFxSmokeTest.java` | JUnit test |

Total tracked-intent files: 64. Main Java files: 35. JUnit source files: 5.
