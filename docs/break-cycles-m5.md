# M5: break cycles and notifications

Implemented and verified on 16 November 2026. M5 extends the existing `FocusSessionService`; it preserves the focus ledger, checkpoint recovery, monotonic focus accounting, organization workflows and M4 blocker integration. The user authorized including the pre-existing uncommitted M4 work in this commit. No reward calculations or later workflows were added.

## Behavior

- Focus accepts 1–240 whole minutes in standard mode. Demo accepts 1–14400 real seconds and initially shows 10. The Pomodoro preset is 25/5/15 minutes with a long break every four completed focus periods; the deep-focus preset is 50/10/30, also every four.
- Short and long breaks each accept 1–60 whole minutes; long-break cadence accepts 1–12 completed focus periods. Demo accelerates neither focus credit nor break durations. Preset buttons retain their actual minute durations even in demo mode.
- After a successfully saved focus completion, a short or long break becomes ready. With automatic transitions enabled, the break starts immediately after persistence succeeds. Otherwise **Start break** starts it. **Skip break** discards the remaining break without adding focus time. Break completion enables Start; it never starts focus automatically or silently reauthorizes app closing.
- Pause/Resume applies to the displayed running/paused focus or break. Stop ends the cycle. Finish completes focus early using actual time; an early Finish counts as one completed focus period. Finish cannot complete a break.
- Both timers use the injected monotonic source, preserving fractional seconds across pauses. A heartbeat gap greater than 15 seconds pauses the interval and consumes/credits none of that gap. Wall-clock changes do not change elapsed time. A delayed focus completion pulse caps focus at its target and anchors the new break after the successful save, without assigning the delay to break time.
- Cycle counts continue for the same workspace UUID (or plain focus scope). Changing workspace, changing timing preferences, interruption, Stop, import reset or application restart resets the count. Skipping a break preserves completed-focus count.
- Windows monitoring stops on focus completion before a break is offered. Starting, pausing, resuming and skipping breaks never starts monitoring. Starting the next focus uses the existing explicit authorization rules.

## Persistence and history

Workspace editors expose all four existing `FocusPreferences` values. They save through the existing workspace port and SQLite columns and survive relaunch/export/import. Starting a saved workspace captures its saved timing values for the live cycle. Unsaved workspace edits do not affect Start. Automatic-break selection and notification/sound choices are per-launch controls, not saved timing values.

M5 needs no schema migration or JSON version change. This commit includes M4 schema 5 (application enablement) and strict JSON format 4; older formats retain their existing validated defaults.

History contains focus facts only and explicitly labels its actual-focus column and exclusions. Paused intervals, break intervals and time after interruption add zero focus seconds. Completed totals continue to derive solely from committed completed focus rows. Interrupted sessions retain their actual focused seconds and remain excluded from completed-study totals. No break rows or break duration are mixed into those facts.

Restart preserves workspace preferences and committed history, recovers unfinished focus sessions as interrupted using their last saved checkpoint, and never resumes a break or invents offline focus time. Break state/counts are intentionally in-process. Import replacement is guarded while focus, a ready/running/paused break or a pending save exists.

## Notifications and lifecycle

`StudyNotifications` is the application port. Focus completion emits once per session only after its save succeeds; retrying a failed completion emits only after successful retry. Break completion emits once. Disabled notifications suppress both popups and sounds. Optional audio uses the platform default beep on a dedicated daemon worker; machine mute/device settings can make it inaudible.

The Maven-launched desktop application has no registered native Windows toast transport. `JavaFxStudyNotifications` is the documented fallback: a nonmodal popup attached to the main window, shown across navigation pages for six seconds. Delivery is queued with `Platform.runLater`; audio is queued separately. No modal dialog, UI-thread I/O or blocking notification wait occurs. Popup/audio work is closed with the shell. Notifications are in-app only, not Windows Action Center notifications, and are unavailable after the window exits.

Achievement unlocking is not applicable yet: no achievement/reward engine runs before M6. The notification event contract includes `ACHIEVEMENT_UNLOCKED` for delivery after a future successful unlock. M5 does not manufacture unlocks, notify imported historical awards or implement reward policy.

## Verification

Final command, using the existing script to select the project-local Java 21 Full toolchain and Maven caches:

```powershell
.\scripts\dev.ps1 -Task build -IncludeUiTests -RealDemoTest -WindowsBlockerTests
```

The script invokes `mvnw.cmd --batch-mode --no-transfer-progress -Dmaven.repo.local=.tools/m2 -Dstudymode.ui.tests=true -Dstudymode.real.demo=true -Dstudymode.windows.tests=true clean verify` after removing only the checked, nonlinked generated target directory (the existing OneDrive clean workaround).

Final result: **BUILD SUCCESS; 792 tests, 0 failures, 0 errors, 0 skipped**, 39.986 seconds, completed 16 November 2026 at 08:30 UTC. Java 21 and Maven version enforcement passed; 83 application source files and 15 test source files compiled. The packaged JAR was produced in ignored `target/`.

- `BreakCycleWorkflowTest`: 17 deterministic SQLite-backed tests cover automatic/manual transitions, short/long selection, skip, both pause/resume paths, suspend/wall-clock behavior, accurate demo/focus facts, interruption, both restart paths, workspace reopen and switching, notifications/sound preferences, notification failure isolation, completion-save retry, import guard, exact-boundary completion, and blocker inactivity throughout breaks.
- `FocusSessionWorkflowTest`: all 16 tests passed, including the opt-in production-clock 10-second demo. It recorded exactly ten seconds, with no simulated 25-minute credit.
- `JavaFxSmokeTest`: actual Windows JavaFX window exercises navigation, previous workflows and new break controls, automatic completion, displayed break countdown/status and a visible nonmodal notification. The test also generates ignored window snapshots; the minimum-window focus layout was visually inspected. Workspace controls save through real SQLite; no live user data is used.
- Windows unit safety tests: all ten passed. The additional opt-in read-only native smoke verified process enumeration, executable path, process creation/ownership/session metadata and refusal of Study Mode's own runtime. No real application was closed.
- Existing domain, persistence, strict JSON validation, organization and simulation tests all passed.

Earlier checks are not represented as passes: the first full UI run failed because a new test looked up controls before laying out the navigated page; the harness was corrected and the actual UI workflow then passed. The first native run inside the tool sandbox failed with service-enumeration Access Denied. The final full command ran outside that sandbox with the normal user token and passed; no administrator token, elevation or Windows security change was requested. A restricted runtime can still refuse monitoring; production reports failure and leaves blocking inactive.

PlantUML parsed both diagrams successfully and rendered the implemented SVG with Smetana. The first default Graphviz render failed with a pipe error; explicitly selecting the bundled Java Smetana renderer resolved it. The SVG contains no error diagnostic. All 83 top-level source types appear in the actual diagram; all local links across 19 Markdown documents resolve, and `git diff --check` passes. See [actual UML](uml-implemented.puml), [planned future UML](uml-planned.puml) and [traceability](traceability.md).

## Limits and scope

Native Windows toast/Action Center integration and durable live break recovery are not implemented; the documented JavaFX fallback and restart-reset behavior are the M5 contract. Rewards and achievement policy remain M6. Existing M4 Windows close mode sends graceful window-close requests only; applications may ignore them or show unsaved-work prompts. M5's automated checks intentionally do not exercise closing ordinary applications or claim a disposable-fixture native close test. There are no outstanding failed M5 checks after the final run.
