# Jackson 3 Migration — Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the spec's Phase 2 — "Migrate REST controllers/DTOs" — from `docs/superpowers/specs/2026-07-05-jackson3-migration-design.md`: fix the last real Jackson-2 dependency in the app's own main source, and empirically prove (not assume) that Spring Boot's auto-configured Jackson 3 `JsonMapper` — the bean Phase 3 will make the REST layer's only mapper — produces output compatible with what the REST layer emits today under Jackson 2, for the two proven risk areas (date/time formatting, GeoJSON `LngLatAlt`). This phase makes **no** change to `spring.jackson.use-jackson2-defaults` or `spring.http.converters.preferred-json-mapper` — flipping those is Phase 3's job, done later as its own isolated, revertible PR.

**Architecture:** Spring Boot 4.1 auto-configures a Jackson 3 `JsonMapper` bean whenever Jackson 3 is on the classpath — regardless of `preferred-json-mapper` — and `spring.jackson.use-jackson2-defaults=true` (already set in this app) configures that bean with Jackson-2-compatible defaults (disables `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS`/`WRITE_DURATIONS_AS_TIMESTAMPS`, `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`, `MapperFeature.DEFAULT_VIEW_INCLUSION`), confirmed via Spring Boot's own `JacksonAutoConfiguration` source and docs. This means the exact `JsonMapper` bean Phase 3 will hand to Spring MVC already exists in the app context today, already carries `Configuration.kt`'s `geoJsonJsonMapperBuilderCustomizer` bean (registered as a `JsonMapperBuilderCustomizer`, which Boot applies to every auto-configured `JsonMapper.Builder`). This plan autowires that real bean inside `IntegrationTest`-based tests and diffs its output against the current REST-facing `OBJECT_MAPPER` (Jackson 2) for real fixtures, rather than hand-constructing a parallel mapper that might silently diverge from what Boot actually builds.

**Tech Stack:** Kotlin, Spring Boot 4.1.0, Jackson 3 (`tools.jackson.*`), Jackson 2 (`com.fasterxml.jackson.*`, still backing REST controllers via the `use-jackson2-defaults` shim), JUnit 5, assertk, Testcontainers (Postgres), the existing `IntegrationTest`/`ControllerTest`/`TestExtensions.kt` test harness.

## Global Constraints

- Do not remove or change `spring.jackson.use-jackson2-defaults`, `spring.http.converters.preferred-json-mapper`, or the `spring-boot-jackson2` dependency in this plan — REST controllers stay on Jackson 2 until Phase 3.
- Do not touch `hypersistence-utils.properties`, `HypersistenceJsonSerializer.kt`, or any JSON-column `@Type` mapping — out of scope, already Jackson 3.
- Every Jackson-3-native type must import from `tools.jackson.*` (except `com.fasterxml.jackson.annotation.*`, which Jackson 3 keeps unchanged).
- Full existing test suite (unit + `integrationTest`) must stay green after every task, **and `./gradlew :services:hanke-service:spotlessCheck` must pass** — run it explicitly before every commit (a Phase 1 review caught a committed import-ordering violation that a targeted test run alone didn't catch).
- Any REST-facing Kotlin class that is actually **deserialized** from JSON (reachable from an `@RequestBody` parameter) and has a secondary constructor must get an explicit `@JsonCreator` on the constructor Jackson should use — the Jackson-3-Kotlin-module creator-ambiguity bug has already caused two real bugs on this branch (`Autoliikenneluokittelu`, `AuditLogEvent`).
- Any difference found between Jackson 2's and Jackson 3's serialized output for `ZonedDateTime`/`OffsetDateTime` is a decision point, not something to silently fix or ignore — document it plainly (this plan does not attempt frontend coordination; that follows in a later step if a difference is found).

---

### Task 1: Remove the last Jackson-2 dependency from `Extensions.kt`

`Extensions.kt:3,20` imports Jackson 2's `com.fasterxml.jackson.databind.JsonNode` purely as an arbitrary classloader anchor for `String.getResource()` — it has nothing to do with JSON parsing (the comment at line 18-19 confirms "the class here is arbitrary, could be any class"). This is the only remaining Jackson-2 `JsonNode` usage anywhere in `services/*/src/main/kotlin` (confirmed by a repo-wide search; `Configuration.kt`, the other file a stale prior audit flagged, no longer references Jackson 2 at all). Fix it by reusing `ChangeLogView`, a marker class already declared in the same file (line 34), removing the Jackson dependency entirely instead of swapping to Jackson 3's `JsonNode`.

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Extensions.kt:3,17-20`

**Interfaces:**
- Consumes: nothing new.
- Produces: `String.getResource()` keeps its existing signature (`fun String.getResource(): URL`) — every caller (`String.getResourceAsText()` at line 22, `TestExtensions.kt`'s `asJsonResource` helpers, `GeometriaFactory.polygon()`, etc.) is unaffected.

- [ ] **Step 1: Remove the Jackson 2 import and swap the anchor class**

In `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Extensions.kt`, delete line 3 (`import com.fasterxml.jackson.databind.JsonNode`), and change lines 17-20 from:

```kotlin
fun String.getResource() =
    // The class here is arbitrary, could be any class. Using ClassLoader might be cleaner, but it
    // would require changing every resource file path throughout the project.
    JsonNode::class.java.getResource(this)!!
```

to:

```kotlin
fun String.getResource() =
    // The class here is arbitrary, could be any class. Using ClassLoader might be cleaner, but it
    // would require changing every resource file path throughout the project.
    ChangeLogView::class.java.getResource(this)!!
```

(`ChangeLogView` is declared further down in this same file, at line 34 — `open class ChangeLogView`. Since it's declared after `getResource()` in the file, that's fine in Kotlin; top-level declaration order doesn't matter.)

- [ ] **Step 2: Run the full unit + integration test suite**

Run: `./gradlew :services:hanke-service:test :services:hanke-service:integrationTest`
Expected: all tests pass (this function is exercised indirectly by every test that loads a JSON fixture via `asJsonResource`/`getResourceAsText`, e.g. `GeometriaFactory.polygon()` — dozens of existing tests already cover this path).

- [ ] **Step 3: Run spotlessCheck**

Run: `./gradlew :services:hanke-service:spotlessCheck`
Expected: PASS (import removal only; no new imports added).

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Extensions.kt
git commit -m "HAI-3618 Remove the last Jackson 2 import from Extensions.kt"
```

---

### Task 2: Add a REST-facing Jackson 3 format-diff test harness

Create a new integration test that autowires Spring Boot's real, already-auto-configured Jackson 3 `JsonMapper` bean (the one `Configuration.kt`'s `geoJsonJsonMapperBuilderCustomizer` already customizes, and the one Phase 3 will hand to Spring MVC) and proves two things before it's used for real comparisons in Tasks 3-4: (a) the bean is actually present and injectable, and (b) `spring.jackson.use-jackson2-defaults=true` really does make it write dates as ISO-8601 strings rather than numeric timestamps, matching Jackson 2's current behavior — the single most important flag for this whole phase.

**Files:**
- Create: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt`

**Interfaces:**
- Consumes: `fi.hel.haitaton.hanke.IntegrationTest` (abstract base class, `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/IntegrationTest.kt`) for the full `@SpringBootTest` context (Testcontainers Postgres, `@ActiveProfiles("test")`); `fi.hel.haitaton.hanke.OBJECT_MAPPER` (Jackson 2, `Constants.kt:11`) as the "current REST behavior" baseline.
- Produces: nothing consumed by later tasks directly — Tasks 3 and 4 add more `@Test` methods to this same file.

- [ ] **Step 1: Write the failing test**

```kotlin
package fi.hel.haitaton.hanke.configuration

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.IntegrationTest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper

class Jackson3FormatDiffITest(@Autowired val jsonMapper: JsonMapper) : IntegrationTest() {

    @Test
    fun `the auto-configured Jackson 3 JsonMapper bean is present`() {
        assertThat(jsonMapper).isNotNull()
    }

    @Test
    fun `use-jackson2-defaults makes Jackson 3 write dates as ISO-8601 strings, not timestamps`() {
        val value = OffsetDateTime.of(2026, 7, 22, 10, 0, 0, 0, ZoneOffset.UTC)

        val json = jsonMapper.writeValueAsString(value)

        assertThat(json).contains("2026-07-22")
    }
}
```

- [ ] **Step 2: Run it to see it fail (or pass) for the right reason**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.configuration.Jackson3FormatDiffITest"`

Two acceptable outcomes at this stage:
- PASS: this is the expected result if the design doc's read of Spring Boot's docs is correct — `use-jackson2-defaults` already makes Jackson 3 ISO-8601-compatible. This is a real, load-bearing finding, not a formality — proceed to Task 3.
- FAIL with the JSON containing a large integer instead of `"2026-07-22..."`: this means `use-jackson2-defaults` does not (or not fully) propagate `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS=false` onto the injected bean in this app's actual configuration (as opposed to Spring Boot's own defaults tests). If this happens, **stop and report it** rather than silently patching around it — it changes the risk profile of the entire migration (Phase 2's `ZonedDateTime`/`OffsetDateTime` diff work in Task 3 becomes "yes there's a real difference," not "confirm no difference").

Either way, do not proceed to Step 3 until you've recorded which outcome actually happened — Task 3's remaining steps assume it passed.

- [ ] **Step 3: Run spotlessCheck**

Run: `./gradlew :services:hanke-service:spotlessCheck`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt
git commit -m "HAI-3618 Add a format-diff test harness for the REST-facing Jackson 3 JsonMapper"
```

---

### Task 3: Diff Jackson 2 vs Jackson 3 date/time serialization on real REST DTOs

Using the harness from Task 2, compare `OBJECT_MAPPER` (Jackson 2, today's real REST behavior) against the injected `jsonMapper` (Jackson 3) for two real DTOs that each carry exactly one of the two risk types (`ZonedDateTime`, `OffsetDateTime`) without any embedded GeoJSON (GeoJSON is Task 4's concern, kept separate so a failure in one doesn't mask the other): `fi.hel.haitaton.hanke.factory.HankeFactory.create()` (has `createdAt: ZonedDateTime?`, no geometry unless `.withHankealue()` is chained, which this test does not do) and a directly-constructed `fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto` (a plain data class, `attachment/common/Dto.kt:16-25`, with `createdAt: OffsetDateTime` — built directly rather than via `ApplicationAttachmentFactory`, since that factory is a Spring-managed `@Component` requiring a repository/`FileClient`/`ApplicationFactory` and the DTO itself needs no such wiring).

**Files:**
- Modify: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt`

**Interfaces:**
- Consumes: `fi.hel.haitaton.hanke.factory.HankeFactory.create(...)` (`services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/factory/HankeFactory.kt:135-160`, returns `Hanke`); `fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto` (`services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/attachment/common/Dto.kt:16-25`, a plain data class: `id: UUID, fileName: String, contentType: String, size: Long, attachmentType: ApplicationAttachmentType, createdByUserId: String, createdAt: OffsetDateTime, applicationId: Long`); `OBJECT_MAPPER` (`fi.hel.haitaton.hanke.OBJECT_MAPPER`).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing test**

Add to `Jackson3FormatDiffITest.kt` (new imports alongside the existing ones):

```kotlin
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.factory.HankeFactory
import java.util.UUID
```

```kotlin
    @Test
    fun `Hanke's ZonedDateTime fields serialize identically under Jackson 2 and Jackson 3`() {
        val hanke = HankeFactory.create()

        val jackson2Json = OBJECT_MAPPER.writeValueAsString(hanke)
        val jackson3Json = jsonMapper.writeValueAsString(hanke)

        assertThat(jackson3Json).isEqualTo(jackson2Json)
    }

    @Test
    fun `attachment metadata's OffsetDateTime field serializes identically under Jackson 2 and Jackson 3`() {
        val dto =
            ApplicationAttachmentMetadataDto(
                id = UUID.randomUUID(),
                fileName = "test.pdf",
                contentType = "application/pdf",
                size = 1234L,
                attachmentType = ApplicationAttachmentType.MUU,
                createdByUserId = "test-user",
                createdAt = OffsetDateTime.of(2026, 7, 22, 10, 0, 0, 0, ZoneOffset.UTC),
                applicationId = 1L,
            )

        val jackson2Json = OBJECT_MAPPER.writeValueAsString(dto)
        val jackson3Json = jsonMapper.writeValueAsString(dto)

        assertThat(jackson3Json).isEqualTo(jackson2Json)
    }
```

Before running, confirm `ApplicationAttachmentType.MUU` is the exact enum constant name (it's referenced this way elsewhere in the codebase, e.g. `ApplicationAttachmentFactory.kt`'s `attachmentType: ApplicationAttachmentType = MUU` default) and that `attachment/common/Dto.kt`'s package is exactly `fi.hel.haitaton.hanke.attachment.common` (confirmed above).

- [ ] **Step 2: Run it**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.configuration.Jackson3FormatDiffITest"`

If either assertion fails, the diff shown by assertk's `isEqualTo` failure message is the actual finding — **do not tweak the fixtures to make it pass**. Instead:
1. Record the exact before/after strings in this plan file's "Findings" note (add one below this task once run).
2. Determine whether the difference is purely formatting (e.g. offset representation `+00:00` vs `Z`) or a structural change.
3. Leave the test failing assertion in place only if you intend the failure to gate Phase 3 (recommended) — otherwise, if a same-team decision is made that the difference is safe, replace the equality assertion with one that documents and pins the accepted difference explicitly (e.g. compare specific substrings with a comment referencing this decision), not a silent broadening of the match.

- [ ] **Step 3: Run spotlessCheck**

Run: `./gradlew :services:hanke-service:spotlessCheck`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt
git commit -m "HAI-3618 Diff Jackson 2 vs Jackson 3 date/time serialization on real REST DTOs"
```

---

### Task 4: Verify GeoJSON `LngLatAlt` round-trips correctly through the REST-facing JsonMapper

`Configuration.kt`'s `geoJsonJsonMapperBuilderCustomizer` bean already registers `LngLatAltJackson3Serializer`/`Deserializer` (from `HypersistenceJsonSerializer.kt`) onto the app-wide `JsonMapper.Builder`, originally added to fix the WebClient path in Phase 1. Every `@RestController` in this app that accepts or returns geometry — confirmed extensively present in both directions, e.g. `CreateHankeRequest`/`ModifyHankealueRequest`/`TormaystarkasteluRequest`/`Hakemusalue` on the request side, `PublicHanke`/`Hanke`/`Geometriat` on the response side — transitively embeds `org.geojson.LngLatAlt` via `FeatureCollection`/`Polygon`. This task proves the same fix that already covers WebClient traffic also produces byte-identical output on the REST path, using a real `Polygon` fixture, rather than assuming the shared bean means shared behavior.

**Files:**
- Modify: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt`

**Interfaces:**
- Consumes: `fi.hel.haitaton.hanke.factory.GeometriaFactory.polygon()` (`services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/factory/GeometriaFactory.kt:13`, returns `org.geojson.Polygon` loaded from `/fi/hel/haitaton/hanke/geometria/polygon.json`).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing test**

Add to `Jackson3FormatDiffITest.kt`:

```kotlin
import fi.hel.haitaton.hanke.factory.GeometriaFactory
```

```kotlin
    @Test
    fun `a Polygon serialized by Jackson 2 deserializes and re-serializes identically via Jackson 3`() {
        val polygon = GeometriaFactory.polygon()

        val jackson2Json = OBJECT_MAPPER.writeValueAsString(polygon)
        val roundTripped = jsonMapper.readValue(jackson2Json, org.geojson.Polygon::class.java)
        val jackson3Json = jsonMapper.writeValueAsString(roundTripped)

        assertThat(jackson3Json).isEqualTo(jackson2Json)
    }
```

- [ ] **Step 2: Run it**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.configuration.Jackson3FormatDiffITest"`
Expected: PASS — this is the expected outcome given `geoJsonJsonMapperBuilderCustomizer` already applies to this exact bean. If it fails, the coordinate array shape is the first thing to inspect (this is the exact failure mode `LngLatAltJackson3Serializer`/`Deserializer` were written to prevent — re-read `HypersistenceJsonSerializer.kt`'s doc comment on `LngLatAltJackson3Serializer` before assuming a new bug).

- [ ] **Step 3: Run spotlessCheck**

Run: `./gradlew :services:hanke-service:spotlessCheck`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt
git commit -m "HAI-3618 Verify GeoJSON LngLatAlt round-trips through the REST-facing JsonMapper"
```

---

### Task 5: Re-audit REST-facing deserialized classes for the secondary-constructor creator-ambiguity bug

A prior Phase 0 audit flagged six files (`HakemusService.kt`, `HankkeenHakemuksetResponse.kt`, `Persistence.kt` (both the root domain file and `logging/Persistence.kt`), `HankekayttajaDeleteService.kt`, `WhoamiResponse.kt`, `Exceptions.kt`) as containing classes with secondary constructors. A fresh check (done while writing this plan) found every secondary-constructor class in that list is either (a) an exception type intercepted by `ControllerExceptionHandler.kt` before Jackson ever touches it, (b) a response-only DTO never deserialized, or (c) `AuditLogEvent` in `logging/Persistence.kt`, already fixed in Phase 1 (commit `0780fa90`) and no longer has a secondary constructor at all. **None of the six needs a change.** This task exists to do the same reachability check the design doc calls for, but starting from the actual, current set of `@RequestBody`-typed classes across all controllers — not the old flagged-file list, which was itself just a keyword grep that could have missed request-side classes outside those six files.

**Files:**
- No files are expected to change unless the search below finds something new. If it does, modify that file plus add a `@JsonCreator` annotation and a round-trip deserialization test in the corresponding existing `*ITest`/unit test file for that class.

**Interfaces:**
- N/A (audit task; see note below on what to do if it finds something).

- [ ] **Step 1: List every `@RequestBody`-typed parameter across all controllers**

Run:
```bash
grep -rn "@RequestBody" services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke --include=*.kt
```
Record the resulting list of request DTO types (e.g. `CreateHankeRequest`, `ModifyHankeRequest`, `HakemusUpdateRequest`, `TormaystarkasteluRequest`, `CreateJohtoselvityshakemusRequest`, `CreateKaivuilmoitusRequest`, etc. — the exact list depends on what the grep returns; do not assume this list is exhaustive without running it, since new endpoints may have been added since Phase 0).

- [ ] **Step 2: For each request DTO type, search its full field graph for secondary constructors**

For each type found in Step 1, recursively check every class reachable through its fields (not just the top-level type) for a `constructor(` block distinct from the primary constructor:

```bash
grep -rn "^\s*constructor(" services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke --include=*.kt
```

Cross-reference this list against the reachable-field graph of each request DTO from Step 1. For any match, confirm reachability precisely (a secondary constructor on a class that's never actually a field of a request DTO, directly or transitively, is not a real risk).

- [ ] **Step 3: For any newly-found deserialized class with a secondary constructor, add `@JsonCreator`**

If Step 2 finds a class not already covered by the six previously-fixed/-confirmed-safe files, add `@com.fasterxml.jackson.annotation.JsonCreator` to the constructor Jackson should use to deserialize it (the primary constructor, in every precedent case on this branch so far), following the exact pattern already used to fix `Autoliikenneluokittelu` in `TormaystarkasteluTulos.kt`. Write a focused deserialization round-trip unit test for that class (serialize with `OBJECT_MAPPER`, deserialize with the Task 2 `jsonMapper`, assert the result equals the original) in whichever existing test file already covers that class, or a new one colocated with it if none exists.

If Step 2 finds nothing new (the expected outcome, given the fresh check already done for this plan), record that explicitly: add one sentence to this plan file directly under this task, e.g. "Step 2 run on 2026-07-22: no new deserialized secondary-constructor classes found beyond the six already confirmed safe." Do not skip recording the outcome — a silent "nothing to do" reads as "not run" to the next person continuing this work.

**Step 2 run on 2026-07-22:** the `@RequestBody` grep found 12 request DTO types across all controllers (`CreateHankeRequest`, `ModifyHankeRequest`, `HakemusUpdateRequest`, `TormaystarkasteluRequest`, `NewUserRequest`, `PermissionUpdate`, `Tunnistautuminen`, `ContactUpdate`, `KayttajaUpdate`, `CreateHakemusRequest`, `DateReportRequest`, `HakemusSendRequest`); the secondary-constructor grep found matches only in the same six files as the prior audit (`HankekayttajaDeleteService.kt`, `TormaystarkasteluTulos.kt`, `WhoamiResponse.kt`, `attachment/common/Exceptions.kt`, `HakemusService.kt`, `HankkeenHakemuksetResponse.kt`) — `logging/Persistence.kt`'s `AuditLogEvent` no longer appears, confirming its secondary constructor was fully removed. Tracing every request DTO's field graph (including nested types like `ModifyHankeYhteystietoRequest`, `ModifyHankealueRequest`, `CustomerWithContactsRequest`, `InvoicingCustomerRequest`, `PostalAddressRequest`, etc.) found none of them reaching any of the six flagged classes, directly or transitively. No new deserialized secondary-constructor classes found beyond the six already confirmed safe.

- [ ] **Step 4: Run the full test suite and spotlessCheck**

Run: `./gradlew :services:hanke-service:test :services:hanke-service:integrationTest :services:hanke-service:spotlessCheck`
Expected: all PASS.

- [ ] **Step 5: Commit**

If Step 3 made no code changes (only the plan-file note from Step 3's fallback), commit just that note:
```bash
git add docs/superpowers/plans/2026-07-22-jackson3-migration-phase2.md
git commit -m "HAI-3618 Re-confirm no new secondary-constructor deserialization risk in REST DTOs"
```
If Step 3 did add a fix, commit the fix and its test together instead, with a commit message naming the specific class fixed.

---

## What's next

This plan covers the spec's Phase 2 (migrate REST controllers/DTOs, verify via format-diff testing rather than assumption). It does **not** flip `spring.jackson.use-jackson2-defaults`/`spring.http.converters.preferred-json-mapper` — that's Phase 3, and it should only start once every task above is merged and, critically, once any date-format or GeoJSON difference found in Tasks 3-4 has been explicitly resolved (either confirmed harmless, or fixed) rather than left as an open question. Phase 4 (cleanup & documentation of the "two Jacksons" comments, capturing the secondary-constructor guideline as a durable team note) follows Phase 3.

## Self-Review Notes

- **Spec coverage:** This plan implements the spec's "Phase 2 — Migrate REST controllers/DTOs" section in full: the mandatory before/after JSON diff for date/time formatting (Tasks 2-3), the GeoJSON re-verification the Phase 1 plan's "What's next" section explicitly called for (Task 4), and the remaining Phase-0-flagged-class cleanup (Task 1, Task 5). It deliberately does not touch Phase 3 (flip the default) or Phase 4 (cleanup/docs), consistent with the spec's own risk-ordered sequencing.
- **Placeholder scan:** No TBD/TODO markers. Task 3's "verify `ApplicationAttachmentFactory`'s exact constructor before finalizing" instruction and Task 5's grep-driven audit are deliberate "check the real code first" steps, not placeholders — fixture/constructor shapes and the exact set of `@RequestBody` types are the kind of thing that drifts over time and would produce broken code if guessed. Task 5's "if nothing found, record that explicitly" instruction exists precisely to prevent a silent no-op from looking unattempted.
- **Type consistency:** `jsonMapper: JsonMapper` (from `tools.jackson.databind.json.JsonMapper`) is introduced in Task 2 and referenced identically (same property name, same type) in Tasks 3 and 4, all as additional `@Test` methods on the same `Jackson3FormatDiffITest` class — no new class or renamed property is introduced partway through. `OBJECT_MAPPER` is used as the Jackson 2 baseline consistently across Tasks 2-4, matching its existing production type (`com.fasterxml.jackson.databind.ObjectMapper`, `Constants.kt:11`).
