# Study Mode development rules

## Scope and milestone gate
- Build a real Windows desktop application with Java 21, JavaFX, Maven, SQLite, JUnit 5, and JavaFX CSS.
- Work on exactly the milestone explicitly requested by the user. Stop after that milestone; a plan is not authorization to implement subsequent milestones.
- The initial planning stage was documentation only. M1 delivered the foundation. The user's M2 request now authorizes SQLite storage/CRUD for all domain entities, migrations, JSON export/import, profile startup persistence and relevant UI/tests. Do not implement the Windows blocker, reward calculations or unrelated later workflows.
- Preserve working features. Do not silently defer requirements; record incomplete work and failed checks in docs/traceability.md and the milestone report.
- The user's instructions take precedence over this file. Ask about material ambiguity while continuing independent work within the authorized scope.

## Design and implementation
- Follow docs/architecture.md and docs/persistence-and-windows.md. Prefer small application-specific classes and constructor injection; avoid generic Manager classes, global mutable state, and unnecessary frameworks.
- Keep domain and application logic independent of JavaFX, JDBC, and Windows APIs. Adapters implement application ports. Controllers delegate business operations to services.
- Add only working controls. The user explicitly requests all nine navigation destinations in M1: show honest feature-status screens for later workflows, labelled with their milestone. Do not ship inert action buttons, fake metrics or simulated production data.
- Validate at domain/service boundaries as well as in forms. Use prepared SQL and transactions. Never silently reset an unreadable database or overwrite invalid imports.
- Keep I/O and process operations off the JavaFX application thread. Shut down scheduled work, native handles, and connections on exit.
- Pin released dependency/plugin versions compatible with Java 21; use no snapshots or version ranges. Recheck upstream guidance when introducing dependencies.
- Use genuine collections and interfaces for application needs, not rubric padding. Update rubric evidence and actual-code UML as implementation evolves.

## Windows safety: mandatory
- Blocking is best-effort, explicitly selected, visible, and immediately stoppable. Simulated blocking is for development/tests and must be labelled.
- Never alter Windows security settings, request administrator privileges automatically, terminate critical/system processes, or target Study Mode, its runtime, ancestors, or helpers.
- Match a verified executable path, not an image-name wildcard or arbitrary shell command. Refuse unknown identity, critical status, ownership, or inaccessible processes.
- Stop must cancel future blocking operations even if persistence fails. No persistent OS changes, services, or startup enforcement.
- Never use real applications as automated test termination targets; use explicitly launched disposable fixtures for opt-in Windows tests.

## Verification and completion
- For every implementation milestone, compile and run the relevant JUnit tests (`.\mvnw.cmd clean verify` once the wrapper exists), perform its UI/Windows smoke checks, and record exact results.
- A missing tool or failed check is a blocker, not a passing result. Documentation-only stages use document/link/diagram consistency checks; compilation/tests are not applicable without source/build files.
- Test meaningful behaviour: validation, persistence round trips, timer transitions, safety decisions, and rollback. Use temporary data directories and injected clocks/platform adapters.
- Keep docs/uml-planned.puml explicitly marked as future design. When source begins, add docs/uml-implemented.puml containing only actual classes, interfaces, and relationships, and keep it current.
- Commit completed milestones to Git with a descriptive message. Never claim a commit succeeded without checking Git. Never discard unrelated changes or rewrite history.
- Do not commit local databases, user configuration, exports, logs, credentials, build outputs, or installed toolchains.
- At milestone end, report files/behaviour changed, verification evidence, limitations, and commit ID; stop and await the user's next milestone request.
