# Jackson 2 → Jackson 3 migration design

## Context

Spring Boot 4.1.0 (HAI-3618) defaults to Jackson 3 everywhere: Spring MVC, WebClient codecs, and
hypersistence-utils' JSON/JSONB column persistence. To keep that upgrade scoped and low-risk, the
app was deliberately kept on Jackson 2 for its own REST/WebClient surface via
`spring.jackson.use-jackson2-defaults`, `spring.http.converters.preferred-json-mapper: jackson2`,
the `spring-boot-jackson2` module, and a `jackson2WebClientCustomizer` bean — all part of Spring's
own officially documented transitional pattern for apps not ready to fully adopt Jackson 3.

hypersistence-utils-hibernate-73:3.15.4 (required for Hibernate 7) hard-depends on `tools.jackson`
(Jackson 3) with no Jackson 2 alternative — confirmed by inspecting its POM. So JSON/JSONB column
persistence is unavoidably Jackson 3 regardless of the app's own configuration. This forced split
has already caused three real bugs on the HAI-3618 branch: GeoJSON `LngLatAlt` corruption (Jackson 3
doesn't understand Jackson 2's `@JsonSerialize`/`@JsonDeserialize` annotations), an
`Autoliikenneluokittelu` constructor-ambiguity bug (Jackson 3's Kotlin module resolves
deserialization constructors differently than Jackson 2's), and a historia-trigger date-format
mismatch (epoch numbers vs. ISO-8601 strings, fixed via changesets 112/113).

Since hypersistence-utils cannot be forced back onto Jackson 2, the only way to eliminate the split
entirely is to move the REST/WebClient layer forward onto Jackson 3, matching what JSON columns
already use.

## Goal & end state

Fully retire the Jackson 2/3 dual-stack: remove `spring.jackson.use-jackson2-defaults`,
`spring.http.converters.preferred-json-mapper: jackson2`, the `spring-boot-jackson2` dependency, and
the `jackson2WebClientCustomizer` bean, leaving Spring Boot 4's native Jackson 3 as the only JSON
stack for REST and WebClient traffic. JPA JSON columns are already Jackson-3-only via
hypersistence-utils and need no change.

No fixed deadline drives this — it's a consistency/technical-debt migration, not a forced cutover.
Sequenced entirely by risk: smallest blast radius first, the public REST/frontend contract last.

## Phases

### Phase 0 — Audit (no code changes)

Produces the concrete checklist the later phases execute against:

- Inventory every direct use of `com.fasterxml.jackson.databind.JsonNode` (Jackson 2 type) —
  currently known: `ProfiiliClient.kt`, `Extensions.kt`, `Configuration.kt`.
- Inventory every class with a secondary constructor that could hit the same Jackson-3-Kotlin-module
  creator-ambiguity bug already fixed once in `TormaystarkasteluTulos.kt`'s
  `Autoliikenneluokittelu` — currently known: `HakemusService.kt`, `HankkeenHakemuksetResponse.kt`,
  `Persistence.kt`, `HankekayttajaDeleteService.kt`, `WhoamiResponse.kt`, `Exceptions.kt`.
- For each, classify whether it's actually **deserialized** (request body / WebClient response — at
  real risk of the creator-ambiguity bug) or only ever **serialized** (response DTO — safe, since
  that bug only affects the read path).
- Re-confirm (a prior grep found no hits, but this phase makes it deliberate) that geojson-jackson's
  `LngLatAlt` is never returned directly by a REST controller.

### Phase 1 — Migrate outbound WebClient consumers (Allu, Profiili)

Stop applying `jackson2WebClientCustomizer` to the Allu and Profiili `WebClient`s. Fix any
Phase-0-flagged deserialize-risk classes reachable from these clients. Port the `JsonNode` usages in
this path to `tools.jackson.databind.JsonNode`. Verify via `MockWebServer`-based integration tests
(the existing pattern in `AlluClientITests`/`ProfiiliClientITest`) that request/response bodies are
byte-for-byte what Allu/Profiili expect. This is the one real dry run against an external contract,
contained to code we control on both ends. Drop the manual Jackson2-codec duplication in those two
ITest files (already flagged as a minor cleanup in code review) once the customizer no longer exists
to replicate.

### Phase 2 — Migrate REST controllers/DTOs

Fix remaining Phase-0-flagged classes in the REST surface. Before flipping anything, capture actual
JSON output — especially `ZonedDateTime`/`OffsetDateTime` formatting, the proven risk area — from a
representative set of endpoints under the current Jackson 2 stack, then diff against Jackson 3's
output for the same payloads. Any difference is a decision point: frontend-safe as-is, or does the
frontend need a coordinated change first?

### Phase 3 — Flip the global default

Remove `spring.jackson.use-jackson2-defaults`, `spring.http.converters.preferred-json-mapper:
jackson2`, the `spring-boot-jackson2` dependency, and the `jackson2WebClientCustomizer` bean
entirely. Full regression run (unit + integration) plus a manual smoke test pass. This is the
highest-risk single commit in the migration — its own reviewed PR, deployed with extra attention to
error monitoring immediately after, so that if something unexpected breaks, reverting this one PR
restores the known-working dual-stack state without undoing Phases 1–2.

### Phase 4 — Cleanup & documentation

Consolidate the scattered "we have two Jacksons" comments (currently split across
`Configuration.kt`, `application.yml`, `HypersistenceJsonSerializer.kt`) into one note confirming the
app is single-Jackson. Capture "avoid secondary constructors on JSON-facing Kotlin data classes, or
annotate `@JsonCreator` explicitly" as a durable team guideline, given it's caused two real bugs.

## Risks & mitigations

- **Frontend date-format drift (Phase 2, highest impact).** Jackson 3's default date/time
  serialization shape may differ from Jackson 2's — the exact class of difference that broke the
  historia triggers. Mitigation: the before/after diff in Phase 2 is mandatory; any format
  difference is a cross-team coordination item with frontend before proceeding, not something to
  ship silently.

- **Allu external contract drift (Phase 1).** Allu is a real external government system; a
  wire-format change could silently corrupt a submitted permit application. Mitigation: MockWebServer
  request/response body assertions are the safety net — gaps in fixture coverage found during Phase 0
  get filled before the migration is trusted, not assumed away.

- **Recurrence of the JsonCreator ambiguity bug (Phases 1–2).** The Jackson 3 Kotlin module has
  already demonstrated non-obvious behavior vs. Jackson 2 once. Mitigation: Phase 0's
  deserialized-vs-serialize-only classification targets exactly this; any deserialized class with
  multiple constructors gets an explicit `@JsonCreator` added preemptively.

- **Phase 3 is a single flag flip with broad blast radius.** Mitigation: it lands only after Phases
  1–2 are fully verified and merged, as its own isolated, revertible PR.

- **hypersistence-utils JSON columns are out of scope but adjacent.** No change needed there (already
  Jackson 3); Phase 3 must not touch `hypersistence-utils.properties` or
  `HypersistenceJsonSerializer.kt`.

## Testing & verification strategy

- **Existing suite as baseline.** The full unit/integration suite (~2,660 tests) must stay green
  through every phase — no failures deferred to "fix later."
- **Format-diff snapshots (Phases 1–2).** One-time, targeted comparison of actual serialized JSON
  before vs. after each phase's change, not a general "add more tests" ask.
- **MockWebServer contract tests (Phase 1).** Extend fixture coverage where Phase 0's audit finds
  gaps, rather than assuming existing fixtures already cover every payload shape.
- **Manual smoke test (Phase 3).** A manual pass through the golden path (create/update/submit a
  hakemus, fetch it back, confirm dates render correctly) before considering Phase 3 done.
- **No new test infrastructure needed.** MockWebServer, the existing ITest harness, and
  Testcontainers-based integration tests already cover what's needed.

## Out of scope

- Any change to `hypersistence-utils.properties`, `HypersistenceJsonSerializer.kt`, or the JSON
  column persistence path — already Jackson 3, untouched by this migration.
- A fixed deadline or forced cutover — this is sequenced by risk, not by date.
