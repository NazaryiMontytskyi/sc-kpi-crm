# ADR-0001: Build tool and versions

- Status: Accepted
- Date: 2026-10-07

## Context
CONTEXT.md §8.3 requires the build tool and exact versions to be fixed in an ADR. CLAUDE.md (Project setup) targets Spring Boot 4.1.x.

## Decision
Maven (via the committed wrapper `./mvnw`) is the build tool, as a single-module project at the repository root. `src/` holds the Java backend only.

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 (Spring Framework 7) |
| Spring Modulith | 2.1.1 (BOM) |
| MapStruct | 1.6.3 (with `lombok-mapstruct-binding` 0.2.0, processor ordered after Lombok) |
| springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) | 3.0.3 (the 3.x line targets Spring Boot 4; 2.x targets Boot 3) |
| PostgreSQL | 16 (`postgres:16` in compose and Testcontainers) |
| Flyway | managed by Boot 4.1.1 |

## Consequences
- Boot 3 APIs/starters are not used (Boot 4 starters such as `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`).
- Upgrading springdoc or Modulith must be recorded here.
- Gradle is not used; the choice is Maven for convention-over-configuration and Initializr default.

## Addendum: time zone and Modulith JPA registry
- The JVM default zone is forced to UTC (`TimeZone.setDefault` in `CrmApplication.main`, `-Duser.timezone=UTC` for surefire and `spring-boot:run`). Reason: on some Windows hosts the JVM zone is the legacy id `Europe/Kiev`, which PostgreSQL 16 rejects (`invalid value for parameter "TimeZone"`). Times are stored in UTC and displayed in `Europe/Kyiv` (CONTEXT.md §6).
- `spring-modulith-starter-jpa` is not included in INC-001: its `event_publication` entity fails `ddl-auto=validate` without a migration. The JPA event-publication registry, with its own Flyway migration, is added by the first increment that needs persisted events (INC-003 audit pipeline).
