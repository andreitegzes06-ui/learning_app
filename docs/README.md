# Study Mode planning baseline

Milestone 2 persistence, 15 November 2026. The initial planning baseline remains below; runtime scope and evidence are tracked separately. Start with the [application README](../README.md) for build/launch instructions.

| Document | Purpose |
| --- | --- |
| [Requirements and inspection](requirements.md) | Supplied baseline, repository findings, assumptions, conflicts, and risks |
| [Architecture](architecture.md) | Packages, domain entities, services, UI, validation, and dependencies |
| [Persistence and Windows](persistence-and-windows.md) | Schema, files, recovery, export/import, and process safety |
| [Milestones](milestones.md) | Ordered implementation scope, checks, and explicit gates |
| [Traceability checklist](traceability.md) | Requirement ownership, milestone, acceptance evidence, and status |
| [Planned UML](uml-planned.puml) | PlantUML target architecture; not an implemented-class diagram |
| [M0 verification](verification/m0.md) | Document checks and explicit testing limitations |
| [M1 verification](verification/m1.md) | Implemented foundation, tests, launch and known host issues |
| [M1 file inventory](verification/m1-files.md) | Created/modified source, resources, tests and documentation |
| [M2 persistence](persistence-m2.md) | Actual schema, CRUD/delete policies, JSON format and recovery |
| [M2 verification](verification/m2.md) | Acceptance evidence, tests and scope limits |
| [Implemented UML](uml-implemented.puml) / [SVG](uml-implemented.svg) | Only actual classes/interfaces/relationships through M2 |

Project development rules live in [AGENTS.md](../AGENTS.md).

The user's original brief and subsequent M1 request are the requirements sources. No separate marking document was supplied. The target design and planned UML include future work; the actual-code UML and M1 report describe only implemented code. Traceability preserves pending requirements.
