# Milestone 2 persistence contract

Historical M2 contract: the current focus milestone adds schema 3 and JSON format 2, with strict legacy format 1 compatibility. Session classroom/goal references now restrict deletion, and imports clear sessions before referenced entities. See [current focus contract](focus-workflow.md). The schema 2 and format 1 descriptions below record the original M2 baseline.

The user's M2 persistence request supersedes the original classroom/workspace-only M2 plan and brings JSON transfer forward from M7. No Windows blocker or reward engine is implemented. Existing domain models and singleton local-profile identity are preserved.

## Database and transaction boundaries

Default writable root remains `%LOCALAPPDATA%\StudyMode`; CLI data-directory overrides are supported. Database adapters create missing parent directories and translate filesystem failures through SQLException/StudyDataException. Bootstrap also acquires the existing exclusive data-directory lock. No Program Files write permission is assumed.

Every connection enables foreign keys and a three-second busy timeout. Public adapter operations use short-lived connections and explicit commit/rollback, including multi-query reads. Prepared statements bind all entity data and IDs. Only trusted migration SQL, constant PRAGMAs and a fixed deletion-order table whitelist use SQL identifiers without bindings. JDBC resources are closed with try-with-resources. Rollback also runs for domain/import exceptions; rollback failures are retained as suppressed exceptions.

`SchemaMigrator` reads `PRAGMA user_version`, verifies ordered `schema_version` history and applies missing SQL resources in one transaction. Fresh startup applies V001 then V002; M1 upgrades apply only V002. Unknown newer schemas, inconsistent history and corrupt databases are rejected without reset. V002 is additive and does not rebuild M1 profile/session tables. Any future destructive migration requires a pre-migration backup as stated in AGENTS.md.

| Entity / table | Adapter / CRUD contract |
| --- | --- |
| `user_profile` | UserProfileStore / SqliteUserProfileStore: singleton read, insert/edit preserving UUID and creation time, delete when unreferenced |
| `study_session` | StudySessionLedger / SqliteStudySessionLedger: save/checkpoint, read by ID/period, delete; finalized facts remain immutable and finalization repeat-safe |
| `classroom`, `study_resource` | ClassroomCatalog / SqliteClassroomCatalog: create/read/edit/delete classroom aggregate, ordered resources; resource UUID ownership cannot move silently |
| `blocked_application` | BlockedApplicationCatalog / SqliteBlockedApplicationCatalog: selections keyed by normalized case-insensitive absolute executable path; metadata only |
| `focus_workspace`, `workspace_resource`, `workspace_application` | FocusWorkspaceStore / SqliteFocusWorkspaceStore: atomic aggregate CRUD; composite foreign keys require resources from the workspace's classroom |
| `study_goal`, `study_task` | StudyPlanStore / SqliteStudyPlanStore: create/read/edit/delete goals and tasks, due dates and explicit completion facts |
| `achievement`, `achievement_award` | AchievementStore / SqliteAchievementStore: definition and award-fact CRUD with unique profile/code pair; no automatic award generation or XP engine |

Domain constructors validate names, URLs, preferences, timestamps and other values. SQL adds NOT NULL, PK/UNIQUE, range/enum checks and foreign keys. Profiles remain one per data directory; classrooms/workspaces/application selections are local catalog records rather than multi-account data. Sessions/goals/tasks/awards refer to the single local profile. The schema intentionally does not invent workspace/classroom references absent from the current StudySession model; those arrive with session snapshots later.

## Edit and delete behaviour

- Classroom save updates resource rows by stable ID, preserves order, and deletes only omitted resources. Removing a resource selected by a workspace is rejected; the entire edit rolls back, including name/description changes.
- Classroom delete cascades its resources and clears optional classroom links on tasks. Referencing workspaces block deletion; remove or reconfigure them first.
- Workspace save replaces its selected resources/applications and preferences in one transaction. An invalid resource or unknown app restores the previous selections on rollback. Applications must already exist in the catalog and their metadata is not overwritten implicitly.
- Workspace deletion cascades join rows only. Referenced blocked applications cannot be deleted until removed from workspaces. Editing their display label updates the shared catalog metadata.
- Goal deletion clears optional task goal links; tasks are retained. A profile with sessions/goals/tasks/awards cannot be deleted. Achievement definitions with awards cannot be deleted until their awards are removed. Explicit session/task/award deletion is available in the persistence API; no bulk deletion UI was added beyond confirmed classroom deletion and confirmed import replacement.
- Session monotonic checkpoints and immutable finalized facts from M1 remain intact. Exact period filtering is still in memory; indexed/paged history is later work.

## JSON format 1 and recovery

`StudyDataSnapshot` contains a version, export instant, profiles, sessions, classrooms/resources, applications, workspace selections/preferences, goals/tasks, achievement definitions and award facts. UUIDs, dates, zones and instants are typed. Paths are saved metadata, not executable commands. Machine settings, live blocker state, credentials and logs are excluded. Exports/backups are unencrypted local JSON and the UI says so.

Jackson 2.20.1 databind and Java-time module are pinned; [upstream release](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.20.1). Deserialization does not enable polymorphic/default typing. The parser rejects duplicate object keys, unknown/missing fields, trailing documents, null primitive values, numeric-string and fractional-integer coercion. Domain construction and SnapshotValidator reject invalid values, duplicate entity/resource IDs, duplicate workspace selections, missing references, multiple profiles and multiple unfinished sessions for a profile. List-based transfer selections are validated before conversion into sets so duplicate input is not silently discarded.

### Strict field types (post-audit correction)

The audit found that disabling `ALLOW_COERCION_OF_SCALARS` alone did not reject a numeric `displayName`. It governs non-textual scalar coercion; see the [Jackson feature contract](https://javadoc.io/static/com.fasterxml.jackson.core/jackson-databind/2.20.1/com/fasterxml/jackson/databind/MapperFeature.html). Imports now follow four distinct boundaries: bounded/strict JSON parsing -> StudyJsonStructureValidator -> typed record binding -> domain/reference validation. No domain records are constructed before the entire document's token shapes are checked.

StudyJsonStructureValidator follows the existing format-1 record components and generic List element types, without instantiating those records. Objects must be objects with exactly the declared properties; lists must be arrays with valid non-null elements; text, UUIDs, URIs, dates, zones and enum identifiers must use JSON strings. Primitive int/long values must use integral JSON number tokens within their Java ranges. Numeric strings, booleans, fractional tokens (including `25.0`), scalar-to-array conversions and unknown fields are rejected. Format 1 has **no boolean properties**: flags such as `completed` are not substitutes for nullable completion timestamps, and neither boolean nor string-valued versions are accepted.

All properties must be present, even if their value may be null. Explicit null is allowed only for session endedAt (subject to session-status invariants), task classroomId/goalId/dueDate/completedAt, and the optional classroom description. A null description continues to normalize to empty text, preserving existing behaviour; empty descriptions are valid. Empty strings must not become null UUIDs/dates/objects. Actual text such as `"123"` or `"true"` remains valid in free-text fields. Explicit Jackson coercion rules provide additional protection for textual, integer, boolean and date targets, empty-value conversions and enum ordinals.

Validation failures include a field path and expected shape, for example `$.profiles[0].displayName: expected a JSON string; found number`. Record-binding failures report the affected path without echoing the whole private record. The existing DataTransferController displays these DataImportException messages. Invalid input is rejected before replacement/backup starts. Integration regressions compare both the full snapshot and byte-for-byte SQLite contents after each rejected import attempt. The format version and export layout remain unchanged.

Limits: 20 MiB per input/output snapshot, 100,000 entity records, nesting depth 40, string length 4096, numeric token length 100. Imports use a bounded byte read even if the file changes during reading. Snapshot data is immutable and is revalidated before replacement, so confirmation applies to the inspected data rather than a subsequently modified file.

Export reads a consistent SQLite transaction, validates the result, writes/flushed-forces a sibling temporary file and moves it to a **new** JSON filename. Existing files are never replaced. Export inside the application data root is refused (including resolved path aliases). A missing destination directory or failed write returns a clear error. The UI deliberately asks for a new file rather than offering overwrite.

Import is validated first, then the UI previews counts and asks for explicit replacement confirmation. The application is disabled during the I/O operation to avoid conflicting profile edits. Inside one SQLite transaction:

1. Read the current consistent snapshot; reject locally active/paused sessions.
2. Save and reread/validate an unencrypted recovery file at `backups/pre-import-<UUID>.json`. If this fails, perform no DELETE.
3. Delete records in a fixed dependency order and insert the fully validated replacement through the same prepared entity writers.
4. Convert imported unfinished sessions to INTERRUPTED, retaining saved active time and a safe end instant; never resume focus/blocking or calculate rewards.
5. Commit once. Any SQL, validation or I/O-related failure rolls back all database mutations. A successful recovery backup may remain after a failed transaction and is safe to inspect/import later.

After success, Settings and the sidebar reload the imported profile and Dashboard count. Other catalog views reload on navigation. Settings/configuration files are unchanged. Recovery is performed by selecting the backup JSON through the same validated import flow; no manual database copying is required.

## UI scope

The profile form remains backed by real SQLite storage. Classroom controls now create, reload, edit and confirm-delete subjects; imported resources are retained on edits and their titles/count are visible. Full interactive URL/resource editing and workspace/task workflows remain deferred. Settings exposes working JSON export and import/preview/confirmation. All DB/file I/O runs on the existing serial background executor and returns feedback on the FX thread. Invalid input preserves form content; SQL failures show readable messages and keep existing records.
