# Post-audit JSON import validation correction

Date: 15 November 2026. Scope: fix unsafe JSON type coercion without introducing any new application feature or changing format version 1.

## Cause and implementation

The existing Jackson configuration disabled `ALLOW_COERCION_OF_SCALARS`, but that feature does not control number/boolean conversions into Java String values. The audit confirmed that numeric `displayName: 123` was accepted as the string `"123"`.

- JsonStudyDataTransfer now explicitly rejects coercion for textual, integer, boolean and date targets, empty-value-to-null conversions, scalar/list unwrapping and enum ordinals.
- New package-private StudyJsonStructureValidator checks every raw JSON field before record construction. It follows the actual existing records and List element types, with an explicit nullable-property policy. Unknown/new unsupported types fail closed.
- Import remains parse -> structure/type checks -> typed record/domain validation -> relationship validation. Only a successfully validated snapshot can reach preview/replacement.
- Field errors identify the path and expected type. Binding errors retain their cause and report the affected path. The existing DataTransferController already displays DataImportException messages, so no UI flow or database change was needed.
- Existing domain models, database schema, JSON version and exported field layout are unchanged. Optional nulls/empty descriptions and genuine numeric-looking strings retain compatibility.

## Full verification

Command: `.\scripts\dev.ps1 -Task build -IncludeUiTests`

Final result: **BUILD SUCCESS — 597 tests, 0 failures, 0 errors, 0 skipped.** Java 21 clean compilation succeeds without the initial test-only deprecated JsonNode API usage; the final test uses the current properties API. Existing signed-JavaFX runtime and scoped OneDrive target cleanup remain unchanged.

| Suite | Tests | Result |
| --- | --- | --- |
| JsonImportTypeValidationTest | 549 | Passed |
| DomainValidationTest | 16 | Passed |
| JsonStudyDataTransferTest | 10 | Passed |
| SqliteFoundationTest | 7 | Passed |
| StartupConfigurationTest | 5 | Passed |
| StudyDataPersistenceTest | 8 | Passed |
| SimulatedDistractionBlockerTest | 1 | Passed |
| JavaFxSmokeTest | 1 | Passed in an actual Windows JavaFX window |

The new regression class includes named tests for numeric and boolean display names, malformed semantic values, undeclared boolean/string flags and empty-string-to-null conversions. Dynamic cases mutate an actual exported snapshot across its fields and nested collections to exercise wrong JSON types and each missing property. This is an end-to-end boundary test using inspectImport followed by importSnapshot, not a test of Jackson configuration internals.

Every rejected case asserts DataImportException, unchanged complete persisted snapshot, byte-for-byte unchanged SQLite database and absence of recovery backups (replacement never starts). Existing tests still prove rollback on SQL/backup failures after a valid import enters the replacement transaction.

Valid cases cover long integer values beyond the int range and null classroom-description normalization. End-to-end round trips cover all current entities, optional nullable IDs/dates, empty descriptions, interrupted recovery of unfinished sessions and actual string values `"123"`/`"true"`. Integer overflows/fractions, malformed objects/arrays/elements, unknown properties and required nulls are rejected.

## Boolean and null contract

There are no boolean-valued properties in format 1. `completed` flags, whether true/false or string `"true"`/`"false"`, are unknown properties and rejected. Existing numeric/text fields reject boolean tokens. The Jackson boolean coercion policy also rejects string/number conversion, but this change does not invent a boolean field for testing.

All record properties must be supplied. Explicit null is permitted only for session endedAt, task classroomId/goalId/dueDate/completedAt, and classroom description. Session status invariants still determine when endedAt must be non-null. Empty strings are not substitutes for null optional dates/IDs. A null optional classroom description retains its existing normalization to an empty string.

## Remaining scope and delivery

No known JSON field-type/structure gap remains in the current format after these checks. This is not a claim to validate future feature inputs or Windows executable/process identities; those planned workflows are unchanged. Future format changes must review supported token types and nullability explicitly.

Actual-code UML was updated and parser/source inventory checked. The current diagram has 56 top-level source types plus four nested types. Git commit hash is reported only after Git confirms the tested commit; no local toolchains, test databases or exports are committed.
