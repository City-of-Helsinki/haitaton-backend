# Repository Guidelines

## Project Structure & Modules
- Root Gradle project; main service in `services/hanke-service`.
- Source: `services/hanke-service/src/main/kotlin`; resources: `services/hanke-service/src/main/resources`.
- Tests: unit in `.../src/test/kotlin`; integration in `.../src/integrationTest/kotlin`.
- DB migrations: `.../main/resources/db/changelog` (add changesets and include in `db.changelog-master.yaml`).
- Local infra via `docker-compose.yml` (Postgres, Azurite, SMTP, ClamAV).

## Build, Test, and Run
- Build: `./gradlew build` (compile + tests).
- Unit tests: `./gradlew test`; integration: `./gradlew integrationTest`.
- Coverage: `./gradlew jacocoTestReport` (reports in `build/reports/jacoco`).
- Format: `./gradlew spotlessCheck` / `./gradlew spotlessApply`.
- Run API: `./gradlew :services:hanke-service:bootRun`.
- Dev stack: `docker-compose up -d` (or specific services like `db azurite smtp4dev clamav-api`).
- Git hooks: `./gradlew installGitHook`.

## Coding Style & Naming
- Kotlin with ktfmt via Spotless (Kotlin style, 2-space indents).
- Packages start with `fi.hel.haitaton`.
- Classes: `PascalCase`; methods/vars: `camelCase`.
- Tests mirror package; filenames end with `*Test.kt`.

## Testing Guidelines
- JUnit 5, MockK, Spring Boot Test, Testcontainers.
- Cover new/changed logic; keep tests deterministic and isolated.
- Use integration tests for DB/HTTP flows. Run `test` and `integrationTest` before PRs.

## Commit & Pull Request Guidelines
- Commits: small, atomic, imperative (e.g., "Add X", "Fix Y").
- PRs: clear description, linked issues, screenshots/logs for changes, and DB migration notes when applicable.
- CI (Azure Pipelines) must pass; code formatted and tests green.

## Security & Config Tips
- Base config lives in `.env.local` (committed). Override locally with `.env`; `.env` takes precedence during local runs.
- Do not commit secrets; prefer environment variables for sensitive values.
- `bootRun` sets safe defaults for dev; `docker-compose` provides dependencies.

## Agent-Specific Notes
- Modify only relevant modules; keep changes minimal and consistent with existing style.
- Prefer Gradle tasks over ad-hoc scripts; do not bypass Spotless or tests.
