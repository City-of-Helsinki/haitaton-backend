# Jackson 2 full retirement (Phase 3/4) design

## Context

`docs/superpowers/specs/2026-07-05-jackson3-migration-design.md` scoped the Jackson 2 → 3
migration in five phases. Phases 0-2 are done: outbound WebClient consumers (Allu, Profiili) and
JPA JSON columns (via hypersistence-utils) are Jackson-3-only. The app's own REST/MVC surface is
still deliberately kept on Jackson 2 via `spring.jackson.use-jackson2-defaults`,
`spring.http.converters.preferred-json-mapper: jackson2`, and the `spring-boot-jackson2` module —
Spring's documented transitional pattern, adopted to keep the Spring Boot 4.1.0 upgrade (HAI-3618)
scoped and low-risk.

That original spec's Phase 3 ("flip the global default") assumed removing the two config
properties and the `spring-boot-jackson2` module would be sufficient to complete the retirement.
It isn't. A hand-built Jackson 2 `ObjectMapper` singleton (`OBJECT_MAPPER` in `Constants.kt`,
constructed by `createObjectMapper()` in `Utils.kt` via `jacksonObjectMapper()`) is used directly
by application code, independent of Spring's Jackson autoconfiguration:

- `AccessRules.kt` — serializes the 401 auth-failure body by writing straight to the raw servlet
  response (`response.writer.print(OBJECT_MAPPER.writeValueAsString(...))`), bypassing Spring
  MVC's HttpMessageConverters entirely. `preferred-json-mapper` has no effect on this path.
- `GdprController.kt` — builds the GDPR response tree via `OBJECT_MAPPER.valueToTree<ObjectNode>()`.
- `AuditLogService.kt` — diffs before/after audit-log JSON via `OBJECT_MAPPER.readTree()`.
- `Extensions.kt` — `toJsonString()`/`toChangeLogJsonString()` helpers used for audit logging.
- `GeometriatDao.kt` — reads GeoJSON geometry from the database via `OBJECT_MAPPER.readValue()`.
- `HypersistenceJsonSerializer.kt` — the JPA JSON-column serializer (built during Phase 1/2) still
  falls back to `OBJECT_MAPPER.convertValue()` for values that are already `Serializable`. The
  original spec's "out of scope" note for this file was written before this dependency was found;
  it's now in scope for retiring `OBJECT_MAPPER` itself, though the file's main Jackson-3 JSON
  column path stays untouched.

Additionally, the Spring Boot 4.1.0 upgrade left two known, not-yet-root-caused Jackson 3 gaps that
must be resolved before the app can be trusted as Jackson-3-only:

- A `Map<Haittojenhallintatyyppi, String>` field nested under Kaivuilmoitus areas fails to
  round-trip correctly in a handful of update+read tests (`UpdateHakemusITest`,
  `UpdateMuutosilmoitusITest`, `UpdateTaydennysITest`, all `$WithKaivuilmoitus`).
- `ProfiiliClientITest`'s manually-built `WebTestClient` hits a Jackson-3-`JsonNode`
  `CodecException` that the injected `WebClient.Builder` customizer fixes elsewhere.

## Goal & end state

Jackson 3 (`tools.jackson.*`) is the only JSON stack anywhere in `services/hanke-service`: REST,
WebClient (already done), JPA columns (already done), and the `OBJECT_MAPPER` singleton and its
call sites. `jackson-databind`, `jackson-module-kotlin` (2.x), and `spring-boot-jackson2` are
removed from `build.gradle.kts` entirely — not just unused-by-default, but gone from the
classpath.

Tracked under HAI-3618, continuing the same ticket as the Spring Boot 4.1.0 upgrade and Phases 1-2.
Based on a new branch off `dev` (which already has the SB4 upgrade, Phases 1-2, and the
Testcontainers 2.x migration merged) — not on the `main-catchup` branch preparing to bring that
work to `main`.

## Phases

### Phase 3a — Root-cause and fix the two known gaps (blocking)

Must land, fully fixed (not skipped/suppressed), before Phase 3b or 3c starts — both represent
already-detected Jackson 3 correctness gaps, and trusting Jackson 3 as the sole JSON stack requires
closing them first.

- **Kaivuilmoitus areas map round-trip**: investigate why Jackson 3's Kotlin module fails to
  round-trip a `Map<Haittojenhallintatyyppi, String>` (enum-keyed map) correctly. Enum-keyed maps
  are a known trouble spot for Kotlin data-class serialization; likely a missing/incompatible
  enum-key (de)serializer, in the same family as the `Autoliikenneluokittelu`
  constructor-ambiguity bug already fixed once during Phase 1. Fix the root cause and add a
  regression test covering the map round-trip directly (not just via the broader ITest scenarios
  that surfaced it).
- **`ProfiiliClientITest` `WebTestClient` `CodecException`**: the test's manually-built
  `WebTestClient` doesn't go through the app's configured `WebClient.Builder`
  (`geoJsonJsonMapperBuilderCustomizer`, added in Phase 1), so it never gets Jackson 3 configured
  the way the real `ProfiiliClient` does. Fix by building the test's client through the same
  `WebClient.Builder` bean/customizer path instead of constructing it ad hoc.

### Phase 3b — Migrate `OBJECT_MAPPER` off Jackson 2

Independent of the Spring config flags — Spring MVC's own autoconfigured `ObjectMapper`/`JsonMapper`
bean is separate from this manual singleton, so this phase carries no MVC-visible risk and can be
verified in isolation before touching the global default.

- Rewrite `createObjectMapper()` (`Utils.kt`) to build a Jackson 3 `JsonMapper`, matching the
  existing configuration intent (Kotlin module, Java-time module, no epoch timestamps).
- Update the 6 call sites to their Jackson 3 equivalents: `AccessRules.kt`, `GdprController.kt`,
  `AuditLogService.kt`, `Extensions.kt`, `GeometriatDao.kt`, `HypersistenceJsonSerializer.kt`.
  Confirm Jackson 3's `JsonMapper` has an equivalent to the `convertValue()` fallback path used in
  `HypersistenceJsonSerializer.kt`.
- Verify with existing unit/integration tests covering audit logging, GDPR responses, geometry
  persistence, and the 401 error body — no new test infrastructure expected here, this is a
  like-for-like API port.

### Phase 3c — Flip the global default

Remove `spring.jackson.use-jackson2-defaults`, `spring.http.converters.preferred-json-mapper:
jackson2`, the `spring-boot-jackson2` module, and the `jackson-databind`/`jackson-module-kotlin`
(2.x) dependencies from `build.gradle.kts`. Highest-risk single change in the whole migration — its
own isolated, revertible PR, landing only after 3a and 3b are merged and green. Full regression run
(unit + integration) plus a manual smoke test pass (create/update/submit a hakemus, fetch it back,
confirm dates render correctly) before considering this phase done.

### Phase 4 — Cleanup & documentation

As originally scoped: consolidate the scattered "we have two Jacksons" comments (`Configuration.kt`,
`application.yml`, `HypersistenceJsonSerializer.kt`) into one note confirming the app is
single-Jackson. Capture the enum-keyed-map and secondary-constructor Jackson-3-Kotlin-module
gotchas as a durable team guideline, given this family of bug has now recurred multiple times.

## Risks & mitigations

- **Phase 3a gaps might reveal a deeper Jackson 3 Kotlin-module limitation**, not a simple fix.
  Mitigation: root-cause first; if a real limitation is found, that's a decision point (workaround
  vs. reconsidering the migration) rather than something to route around silently.
- **`OBJECT_MAPPER` call sites touch security (`AccessRules.kt`) and compliance (`GdprController.kt`,
  `AuditLogService.kt`) code.** Mitigation: each call site gets its own reviewed change plus its
  existing test coverage re-run; no batching multiple call sites into a single unverified commit.
- **Phase 3c is a single flag flip with broad blast radius**, same risk noted in the original spec.
  Mitigation unchanged: lands only after 3a/3b are fully verified and merged, as its own isolated,
  revertible PR, with a manual smoke test before considering it done.
- **hypersistence-utils JSON columns must stay untouched.** Phase 3b's change to
  `HypersistenceJsonSerializer.kt` is scoped strictly to the `OBJECT_MAPPER.convertValue()` fallback
  path — the file's primary Jackson-3 JSON-column serialization logic is not touched.

## Testing & verification strategy

- Full unit/integration suite must stay green through every sub-phase — no failures deferred.
- Phase 3a's two fixes each get a dedicated regression test targeting the specific failure mode,
  not just relying on the broader ITests that originally surfaced them.
- Phase 3b is a like-for-like API port verified by existing test coverage; no new test
  infrastructure needed.
- Phase 3c gets a manual smoke test pass (hakemus create/update/submit/fetch, verify date
  rendering) in addition to the automated suite.

## Out of scope

- Any further change to hypersistence-utils' primary JSON-column serialization path beyond the
  `OBJECT_MAPPER` fallback noted above.
- A fixed deadline or forced cutover — sequenced by risk (3a → 3b → 3c → 4), same as the original
  spec's philosophy.
