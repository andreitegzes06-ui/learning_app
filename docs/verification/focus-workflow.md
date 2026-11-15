# Focus workflow verification — 15 November 2026

Starting verified commit: d2af29a7e8d1910f5c7d768fb13284d294897d89. Existing components were extended and reused.

## Executed checks

```powershell
.\scripts\dev.ps1 -Task build -IncludeUiTests -RealDemoTest
```

Clean compile, JUnit and JAR packaging succeeded: **679 tests, 0 failures, 0 errors, 0 skipped**.

| Suite | Tests |
| --- | ---: |
| DomainValidationTest | 16 |
| FocusCompatibilityTest | 3 |
| FocusSessionWorkflowTest | 16 |
| JsonImportTypeValidationTest | 611 |
| JsonStudyDataTransferTest | 10 |
| SqliteFoundationTest | 7 |
| StartupConfigurationTest | 6 |
| StudyDataPersistenceTest | 8 |
| SimulatedDistractionBlockerTest | 1 |
| JavaFxSmokeTest | 1 |

Deterministic tests cover start/pause/resume, fractions, paused time, completion/capping, Finish, interruption, invalid inputs/transitions/references, duplicates, wall-clock changes, long gaps, close, checkpoint-only restart recovery, failed saves/retry and zone-aware statistics. Temporary databases are reopened; tests preserve captured labels, migrate schema 2 and round-trip format 1/2. Strict import regressions verify rejected data leaves storage unchanged.

A separate opt-in real-time test uses actual System.nanoTime(), a 10-second target and scheduled ticks, reopens SQLite and verifies exactly **10 focused seconds**, COMPLETED and demo=true. The actual-window JavaFX test uses injected time to exercise all ten destinations, duration errors/preview, Start/Pause/Resume/Finish/Stop, History/Dashboard, profile/classroom/export feedback and previous regressions. Automated checks do not constitute manual visual presentation acceptance.

Actual PlantUML is checked and regenerated to SVG; planned UML remains explicitly future design. Final Git identity is reported after committing.

## Demonstration

```powershell
.\scripts\dev.ps1 -Task run -Demo -DataDirectory .run\presentation-demo -StartPage focus
```

Create a profile in Settings, optionally create a classroom, return to Focus and reload choices. Demo/Test is visible; choose 10 seconds and Start. Real completion produces History 10 seconds / Demo / COMPLETED and, in a fresh directory, Dashboard 10 seconds and one completed session. Demonstrate Pause/Resume and Finish; Stop instead retains INTERRUPTED time excluded from completed totals. Restart with the same directory to verify persistence.

Normal launch: .\scripts\dev.ps1 -Task run -StartPage focus, with whole-minute input and 25/50-minute presets.

## Limits

No break cycles, workspace integration, Windows termination, rewards or goal creation UI. Stored/imported goals can be selected. History paging, filtering and charts remain future work. Crash recovery loses uncommitted time and grants no offline credit; gaps over 15 seconds pause conservatively. Storage floors total focused time to whole seconds. Signed runtime restrictions, OneDrive clean workaround, packaging and DPI/accessibility limits are documented in README.
