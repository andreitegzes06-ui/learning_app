# Persistence, files, and Windows integration (planned)

Current M5 amendment: the four existing workspace timing columns now drive real break cycles. Break state stays in-process and never enters the focus ledger. The retained M4 adapters implement explicit best-effort graceful window closing and dry run; they do not forcibly terminate processes. See [M5 contract and verification](break-cycles-m5.md) for current behavior; historical design proposals below do not override it.

M2 implementation update: the user's persistence milestone brings entity CRUD and JSON transfer forward. [persistence-m2.md](persistence-m2.md) is the actual schema/transaction/JSON contract. Original future-table proposals and M7 labels below remain design history, not a claim of implemented columns. Windows integration remains unimplemented.

## Local storage and configuration

Default data root: `%LOCALAPPDATA%\StudyMode\`. Resolve the environment value safely; if absent, show an actionable startup error or use an explicitly supplied `--data-dir`. Do not store writable data beside the installed executable or in the source tree. Unit/integration tests always supply a temporary root.

```text
StudyMode/
  studymode.db
  settings.properties
  presets/<name>.properties
  backups/<timestamp>-pre-import.json
  logs/studymode.log
```

Acquire an exclusive application lock in the data root so two Study Mode instances cannot start conflicting timers/writers. Release it on normal shutdown; OS lock release after a crash must allow the next startup. SQLite locking is still needed for external tools and contention. Apply bounded retry/busy timeout, never infinite retry on the FX thread.

Configuration precedence: built-in defaults < saved `settings.properties` < selected preset < explicit command-line flags. Save only user-edited persistent settings, not incidental runtime overrides. CLI: `--data-dir=<absolute-path>`, `--config=<absolute-properties-path>`, `--preset=<safe-name>`, `--blocker=simulated|windows`, `--start-page=dashboard|focus|classrooms|history|tasks|rewards|settings`, `--workspace=<UUID>`. M1 supports only data-dir, config, blocker=simulated and start-page=dashboard/settings; introduce the remaining flags with their features and reject unsupported values. Selection of workspace/page never starts a session.

Preset names must match `[A-Za-z0-9_-]{1,40}`. An explicitly supplied config file must exist; a missing optional default settings file is a valid first run. Unknown keys/flags, invalid values or invalid paths are errors with filename/key context. Write properties through a sibling temporary file and atomic replace where supported; handle unsupported atomic move explicitly. A settings load error must not silently overwrite the original file.

## SQLite strategy

Use JDBC, prepared statements, try-with-resources and explicit transactions. `SqliteDatabase` owns connection creation and transaction boundaries. Serialize writes on the persistence executor. Enable foreign keys on every connection, use a bounded busy timeout, and evaluate WAL on a local disk. Never copy only a live `.db` while WAL may contain committed data. Export through a consistent read transaction; use a documented SQLite backup mechanism or closed/checkpointed database for binary backups.

SQL migrations are numbered classpath resources, recorded in `schema_version`. Initial migration creates profile storage in M1; add feature tables only when needed. Back up before later destructive migrations, transact each migration, reject newer schemas, and retain failed databases for recovery. An unreadable/corrupt DB opens a recovery/error view, never an automatic fresh-profile overwrite.

| Planned table | Main columns and integrity rules | Milestone |
| --- | --- | --- |
| `schema_version` | version PK, applied_at; ordered migrations | M1 |
| `user_profile` | singleton slot UNIQUE/CHECK, id UUID PK, display_name, created_at, zone_id | M1 |
| `classroom` | id PK, name, description, archived | M2 |
| `study_resource` | id PK, classroom_id FK, title, uri, ordering | M2 |
| `focus_workspace` | id PK, classroom_id FK, name, focus/break durations, long-break interval, archived | M2 |
| `workspace_resource` | workspace_id/resource_id composite PK, checked common classroom | M2 |
| `blocked_application` | id PK, display_name, canonical_path UNIQUE, platform | M2 metadata; M5 enforcement |
| `workspace_blocked_application` | workspace_id/application_id composite PK | M2 metadata |
| `study_session` | id PK, profile/classroom/workspace FKs (nullable references as appropriate), snapshots, start/end, active_seconds, zone_id, status; one unfinished per profile | M3 |
| `session_day_focus` | session_id/study_date composite PK, active_seconds >= 0; sum reconciles with session active seconds | M3 |
| `study_goal` | id PK, profile_id FK, optional classroom_id, text, target_minutes, period, archived | M4 |
| `study_task` | id PK, profile_id FK, optional classroom_id/goal_id, text, due_date, completed_at | M4 |
| `achievement_award` | profile_id/achievement_code composite PK, awarded_at; definition codes versioned in code | M6 |
| `session_reward` | session_id PK/FK, xp >= 0, policy_version; idempotent award | M6 |

Enforce checks (positive durations, known statuses, nonnegative counters), uniqueness, FKs and indexes on session time/classroom and task status. Java UUIDs map to TEXT, instants to UTC ISO-8601 TEXT, durations to INTEGER seconds, calendar dates to ISO dates. Snapshots preserve historical names/preferences when referenced records are archived. Do not serialize Java objects/blobs as the primary schema.

On startup find unfinished sessions, disable all blocking, recover only committed active seconds and mark them interrupted. Never assume time while the app was closed was study time. Finalize session and per-day totals in one transaction. Awards may be retried separately using unique session keys; displayed values must derive from committed facts and not double count on retry.

## Export/import and privacy (M7)

Portable UTF-8 JSON contains `formatVersion`, export instant, profile, classrooms/resources, workspace preferences/blocklists, goals/tasks, sessions/day totals and reward records. It does not contain machine config, logs, database paths, live timer state, or any credentials. Provide a summary of private content before user-selected export. Files are not encrypted; communicate that plainly in the data screen.

Export reads a consistent snapshot, writes a temporary file, verifies successful serialization, then renames to the chosen target. Never truncate an existing export before success; confirm replacement. Import accepts only known supported formats, at most 20 MiB and 100,000 aggregate records initially; validate types, lengths, unique IDs, references, time/status consistency and reward consistency before mutation. JSON parser must reject unsafe polymorphic type construction. Recompute derived statistics/rewards from facts where needed.

Import is unavailable during an active/paused session. Preview counts and replacements; user confirms actual replacement. First write and verify a recoverable backup outside the replacement transaction. Replace all application records in one transaction; failure rolls back and preserves existing data. Imported unfinished sessions become interrupted, and foreign-machine blocklists remain inactive until revalidated/reselected locally. No import may activate process blocking. Verify round-trip equivalence and failure rollback with temporary DBs.

## Windows blocking implementation (M5)

Blocking means periodically attempting to terminate eligible running instances of explicitly chosen applications during active focus. It does not prevent every launch, hide applications, block websites, or guarantee access denial. Explain possible unsaved-work loss before the student activates a real blocklist. Break, Pause, Stop and app exit disable enforcement. Resource URLs are opened only when clicked; warn if a selected resource needs a browser that is itself blocked.

Use `DistractionBlocker` with two concrete implementations:

- `SimulatedDistractionBlocker` tracks selected targets and emits timestamped would-block/status events without interacting with OS processes. Deterministic, labelled, suitable for tests and non-Windows development.
- `WindowsDistractionBlocker` polls at a bounded cadence (initially one second) on a dedicated worker, uses `WindowsProcessGateway` for native operations, and consults `ProcessSafetyPolicy`. Select/pin JNA and JNA-platform at M5, verify required mappings, and write narrow native declarations only for absent mappings. Do not use `taskkill /IM`, shell interpolation, wildcard image matching, or automatic elevation.

Eligibility pipeline before every attempt:

1. Require explicit selection of an existing local `.exe`, resolved canonical absolute path; no wildcard or basename matching. Reject Windows/system directories, UNC/network paths and unverifiable reparse aliases.
2. Enumerate candidates and open a limited-rights native process handle. Query full executable identity, creation time, current-user SID, Windows session ID and critical status. Require same logged-in user/session; refuse protected/elevated/unknown targets.
3. Deny critical processes; deny Windows/system/security components; deny Study Mode PID, its executable/runtime paths, ancestors, and helpers. Protect shared Java runtime binaries conservatively, even when this prevents blocking another Java app. Deny policy always overrides a user selection.
4. Require verified canonical path equality with the selected target. Before acting, recheck cancellation and identity. Use the same native handle for verification and termination to avoid PID reuse targeting a different process. If rights or metadata are unavailable, skip and report; never infer safety from a name alone.
5. Attempt termination with only the required rights, close every handle, and report succeeded/skipped/access-denied/exited/failed per target. Limit repeat error messages. Never recursively terminate a process tree or allow the user to bypass safety policy.

Native API mapping/rights must be proven in Windows fixture tests. [Microsoft IsProcessCritical](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-isprocesscritical) documents critical-status querying; [QueryFullProcessImageNameW](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-queryfullprocessimagenamew) documents full image-path querying; [process security/access rights](https://learn.microsoft.com/en-us/windows/win32/procthread/process-security-and-access-rights) defines rights. These are design references, not evidence of an existing integration.

`stop()` invalidates the current enforcement generation before any persistence work, signals cancellation, and synchronizes against queued actions. Do not enqueue more termination calls after Stop is acknowledged; a native call already submitted may finish and must be reported. Keep native work bounded/off the UI thread and keep the stop control responsive even when a save fails. Crashes leave no service, firewall rule, registry policy or scheduled enforcement behind.

Unit tests inject fake process metadata and verify refusal of critical/self/ancestor/system/unknown/other-user/path-mismatch targets and stale generations. Opt-in Windows integration tests may launch an explicitly labelled disposable helper executable and terminate only that fixture. Test real cancellation and handle cleanup under standard-user permissions. Do not target the student's normal apps during automation. Non-Windows startup rejects `--blocker=windows` with a useful error; it must not silently claim Windows enforcement while simulating.
