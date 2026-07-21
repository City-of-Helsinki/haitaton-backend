# Jackson 3 Migration — Phase 1 & 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the two lowest-risk, fully-derisked pieces of the Jackson 2→3 migration described in `docs/superpowers/specs/2026-07-05-jackson3-migration-design.md`: porting a Jackson-2-only custom serializer that's already running (undocumented) under Jackson 3, and migrating the app's outbound WebClient consumers off the Jackson 2 shim.

**Architecture:** Jackson 3 is Spring Boot 4's native JSON stack; this app currently pins REST/WebClient traffic to genuine Jackson 2 via `spring.jackson.use-jackson2-defaults`, `spring.http.converters.preferred-json-mapper: jackson2`, the `spring-boot-jackson2` module, and a `jackson2WebClientCustomizer` bean. JPA JSON columns (hypersistence-utils) are already Jackson-3-only and unaffected by any of this. This plan removes the Jackson 2 dependency from the three WebClient consumers (`AlluClient`, `ProfiiliClient`, `FileScanClient`, which currently share one customized `WebClient.Builder`) and fixes a JSON-column custom serializer that only works today via undocumented Jackson 3 behavior rather than the officially supported API.

**Tech Stack:** Kotlin, Spring Boot 4.1.0, Jackson 3 (`tools.jackson.*`), Jackson 2 (`com.fasterxml.jackson.*`, kept for REST controllers until a later phase), hypersistence-utils 3.15.4, JUnit 5, MockWebServer 3, Testcontainers (Postgres), assertk.

## Global Constraints

- Do not touch `hypersistence-utils.properties`, `HypersistenceJsonSerializer.kt`'s `GeoJsonAwareObjectMapperSupplier`/`ObjectMapperCloningJsonSerializerSupplier`, or any JSON-column `@Type` mapping — out of scope, already Jackson 3.
- Do not remove `spring.jackson.use-jackson2-defaults`, `spring.http.converters.preferred-json-mapper: jackson2`, or the `spring-boot-jackson2` dependency in this plan — REST controllers stay on Jackson 2 until the later "flip the global default" phase.
- Every Jackson-3-native serializer/deserializer must import from `tools.jackson.*` (except `com.fasterxml.jackson.annotation.*`, which Jackson 3 keeps unchanged — confirmed via the official Jackson 3 migration guide).
- Full existing test suite (unit + integration) must stay green after every task.
- Liquibase changesets in this repo use `--liquibase formatted sql`; any inline SQL comment in a changeset body must use `/* ... */` block-comment syntax, not `--`, because Liquibase's formatted-SQL parser treats every `--`-prefixed line as an attempted directive (confirmed by a real parse failure hit while working on changeset 113 — not relevant to this plan's files, noted here as a standing project constraint).

---

### Task 1: Port `AuditLogEvent`'s date-time serializer to Jackson 3

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/logging/Persistence.kt:1-130`
- Test: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/logging/AuditLogServiceITests.kt`

**Interfaces:**
- Consumes: `AuditLogEvent.dateTime: OffsetDateTime` (existing field, unchanged type/name), the established Jackson-3-native serializer pattern from `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/HypersistenceJsonSerializer.kt` (`ValueSerializer<T>`/`ValueDeserializer<T>` from `tools.jackson.databind`, no-arg constructors, `serialize(value, gen, ctxt)` / `deserialize(p, ctxt)` signatures).
- Produces: `CustomOffsetDateTimeSerializer : ValueSerializer<OffsetDateTime>()` and `CustomOffsetDateTimeDeserializer : ValueDeserializer<OffsetDateTime>()` (same class names as today, so the `@JsonSerialize(using = ...)`/`@JsonDeserialize(using = ...)` references on `AuditLogEvent.dateTime` don't need to change their `using` argument — only their annotation import changes).

**Context:** `AuditLogEntryEntity.message` is a hypersistence-utils `JsonBinaryType` column, already Jackson-3-only today. `CustomOffsetDateTimeSerializer`/`Deserializer` currently extend Jackson 2's `com.fasterxml.jackson.databind.ser.std.StdSerializer`/`deser.std.StdDeserializer`, registered via Jackson 2's `com.fasterxml.jackson.databind.annotation.JsonSerialize`/`JsonDeserialize`. An empirical test (save an `AuditLogEntryEntity` via the real Testcontainers-backed `AuditLogService`, then read the raw column back via a native query) confirmed this Jackson-2-style serializer/deserializer pair is currently being invoked by the Jackson-3-based column machinery — but this isn't the officially documented/supported mechanism (Jackson 3's migration guide states `JsonSerializer`/`JsonDeserializer` were renamed to `ValueSerializer`/`ValueDeserializer`, and databind annotations like `@JsonSerialize`/`@JsonDeserialize` move to the `tools.jackson` package). Relying on whatever undocumented compatibility path currently makes this work is fragile. This task ports it to the supported Jackson 3 API, matching the `LngLatAltJackson3Serializer`/`Deserializer` pattern already in `HypersistenceJsonSerializer.kt`.

- [ ] **Step 1: Write a failing test asserting the exact stored JSON shape**

Add this test to `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/logging/AuditLogServiceITests.kt` (add the `EntityManager`-based native query alongside the existing `entityManager` field already autowired in that class):

```kotlin
@Test
fun `date_time is stored as a plain ISO-8601 offset string, not corrupted by the serializer port`() {
    val auditLogEntry =
        AuditLogEntry(
            userId = "1234-1234",
            userRole = UserRole.USER,
            operation = Operation.CREATE,
            status = Status.FAILED,
            failureDescription = "There was an error",
            objectType = ObjectType.YHTEYSTIETO,
            objectId = "333",
            objectAfter = "fake JSON",
        )
    TestUtils.addMockedRequestIp()
    val saved = auditLogService.createAll(listOf(auditLogEntry))[0]

    @Suppress("UNCHECKED_CAST")
    val raw =
        entityManager
            .createNativeQuery("SELECT message::text FROM audit_logs WHERE id = :id")
            .setParameter("id", saved.id)
            .singleResult as String

    // Regex: "date_time":"<ISO-8601 offset date-time>" with no extra wrapping, arrays, or nulls.
    val dateTimePattern =
        Regex(""""date_time":"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?[+-]\d{2}:\d{2}"""")
    assertThat(dateTimePattern.containsMatchIn(raw)).isTrue()
}
```

This needs two new imports in that file: `assertk.assertions.isTrue` and `assertk.assertThat` (check if already imported; `assertThat`/`isTrue` are likely already there from other tests in the file — add only what's missing).

- [ ] **Step 2: Run the test to confirm it currently passes (baseline) before the port**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.logging.AuditLogServiceITests"`
Expected: PASS (this confirms the current Jackson-2-style serializer produces the expected shape today, giving a safety net before changing the implementation).

- [ ] **Step 3: Port the serializer and deserializer to Jackson 3's native API**

In `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/logging/Persistence.kt`, replace the imports:

```kotlin
// Remove these:
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer

// Add these instead:
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
```

(`com.fasterxml.jackson.annotation.JsonInclude` and `com.fasterxml.jackson.annotation.JsonProperty` stay unchanged — Jackson 3 keeps that package as-is.)

Replace the two classes at the bottom of the file:

```kotlin
class CustomOffsetDateTimeSerializer : ValueSerializer<OffsetDateTime>() {

    override fun serialize(value: OffsetDateTime, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
}

/**
 * See [CustomOffsetDateTimeSerializer] for rationale.
 *
 * Based on: https://www.baeldung.com/jackson-serialize-dates#java-8-no-dependency
 */
class CustomOffsetDateTimeDeserializer : ValueDeserializer<OffsetDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OffsetDateTime =
        OffsetDateTime.parse(p.string, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
```

Note the signature changes from the Jackson 2 version: no `Class<OffsetDateTime?>?` constructor parameter (Jackson 3's `ValueSerializer`/`ValueDeserializer` don't need it — confirmed by the existing `LngLatAltJackson3Serializer`/`Deserializer` pattern in `HypersistenceJsonSerializer.kt`, which also takes none), `SerializerProvider` → `SerializationContext`, `p.text` → `p.string` (Jackson 3 renamed `JsonParser.getText()`'s canonical form to `getString()` — confirmed via `javap` on `tools.jackson.core.JsonParser` in the resolved `jackson-core` 3.1.4 jar), and both types are now non-nullable (`OffsetDateTime` not `OffsetDateTime?`) since `dateTime` on `AuditLogEvent` is already non-nullable.

- [ ] **Step 4: Run the full audit-log integration test class**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.logging.AuditLogServiceITests"`
Expected: PASS — including the new Step-1 test, proving the ported Jackson-3-native serializer produces the same wire format as the old Jackson-2-style one.

- [ ] **Step 5: Run spotless and the full unit+integration suite for the `logging` package**

Run: `./gradlew :services:hanke-service:spotlessCheck :services:hanke-service:test :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.logging.*"`
Expected: BUILD SUCCESSFUL, no formatting violations, no test failures.

- [ ] **Step 6: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/logging/Persistence.kt services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/logging/AuditLogServiceITests.kt
git commit -m "$(cat <<'EOF'
HAI-XXXX Port AuditLogEvent's date-time serializer to Jackson 3

CustomOffsetDateTimeSerializer/Deserializer extended Jackson 2's
StdSerializer/StdDeserializer, referenced via Jackson 2's
@JsonSerialize/@JsonDeserialize. The column they run on
(audit_logs.message) is already Jackson-3-only via hypersistence-utils;
they were only working through undocumented Jackson 3 behavior, not
the supported ValueSerializer/ValueDeserializer API. Port them to
match the pattern already used for LngLatAlt in
HypersistenceJsonSerializer.kt, and add a regression test asserting
the exact stored JSON shape (previously only isRecent() was checked).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Migrate `ProfiiliClient`'s `JsonNode` usage to Jackson 3

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClient.kt:1-135`
- Test: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `ProfiiliClient.getApiTokens(accessToken: String): JsonNode` and `getTokenApiUrl(): String` now return/use `tools.jackson.databind.JsonNode` internally — no change to their public signatures' semantics (both already return/consume plain strings or a `JsonNode` used only internally within the same file), so Task 3 doesn't need to know about this beyond "the codec applied to `ProfiiliClient`'s `WebClient` must be able to decode into `tools.jackson.databind.JsonNode`", which is exactly what removing the Jackson 2 customizer (Task 3) provides.

**Context:** `ProfiiliClient.getApiTokens()` and `getTokenApiUrl()` decode raw OAuth/OIDC discovery responses via `.bodyToMono(JsonNode::class.java)` using Jackson 2's `com.fasterxml.jackson.databind.JsonNode`, because that's what the `jackson2WebClientCustomizer`-configured `WebClient` currently decodes into. `Extensions.kt`'s `JsonNode::class.java.getResource(this)` and `Configuration.kt`'s comment mentioning `JsonNode` were checked and are **not** real dependencies — the former uses the class purely as an arbitrary classloader anchor (explicitly commented as such), the latter is just prose in a comment. Neither needs any change.

- [ ] **Step 1: Write a failing test that exercises `JsonNode` field access against Jackson 3's type**

`ProfiiliClientITest.kt` already exercises `getApiTokens`/`getTokenApiUrl` indirectly through `getVerifiedName` (see the existing `GetVerifiedName` nested test class). Add this focused test inside that same file, in the top-level test class (not nested), to pin the exact JSON-field-access behavior this migration must preserve:

```kotlin
@Test
fun `getVerifiedName still works when Profiili's token and discovery responses use nested JsonNode field access`() {
    // This exercises ProfiiliClient.getApiTokens() and getTokenApiUrl(), both of which parse
    // their response body into a JsonNode and pull a field back out with `["field"]?.asText()`.
    // A prior regression here would surface as a null/ClassCastException from that access, not
    // a compile error, since JsonNode is used structurally rather than through a typed DTO.
    mockGraphQl.enqueueSuccess(
        ProfiiliFactory.myProfileResponse(ProfiiliFactory.DEFAULT_FIRST_NAME).toJsonString()
    )

    val result = profiiliClient.getVerifiedName(GRANT)

    assertThat(result.firstName).isEqualTo(ProfiiliFactory.DEFAULT_FIRST_NAME)
}
```

(Check the exact existing helper names — `ProfiiliFactory.myProfileResponse`, `enqueueSuccess`, `GRANT` — against what's already used in the file's `GetVerifiedName` nested class before adding this; reuse those exact helpers rather than inventing new ones, since this test's whole point is to exercise the same `getApiTokens`/`getTokenApiUrl` path with the existing fixtures.)

- [ ] **Step 2: Run the test to confirm it passes today (baseline, Jackson 2)**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.profiili.ProfiiliClientITest"`
Expected: PASS (all existing tests plus the new one).

- [ ] **Step 3: Migrate the `JsonNode` import**

In `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClient.kt`, change:

```kotlin
import com.fasterxml.jackson.databind.JsonNode
```

to:

```kotlin
import tools.jackson.databind.JsonNode
```

No other code in this file needs to change — `.bodyToMono(JsonNode::class.java)`, `apiTokens["access_token"]?.asText()`, and `conf["token_endpoint"]?.asText()` all use `JsonNode`'s standard field-access (`get`/`asText`) API, which Jackson 3's `JsonNode` keeps (only the internal `TextNode` concrete class was renamed to `StringNode` per the Jackson 3 migration guide — this doesn't affect the `JsonNode` interface's own accessor methods).

- [ ] **Step 4: Run the test again to confirm it still passes under Jackson 3's `JsonNode`**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.profiili.ProfiiliClientITest"`
Expected: PASS. If it fails, do not proceed to Task 3 — this would mean Jackson 3's `WebClient` codec configuration (not yet changed in this task) is somehow already interfering, which needs investigating before continuing.

- [ ] **Step 5: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClient.kt services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt
git commit -m "$(cat <<'EOF'
HAI-XXXX Migrate ProfiiliClient's JsonNode usage to Jackson 3

getApiTokens() and getTokenApiUrl() decode raw OAuth/OIDC responses
via JsonNode. Port the import from com.fasterxml.jackson.databind to
tools.jackson.databind ahead of removing the Jackson 2 WebClient
codec customizer, since that customizer is what currently makes this
decode target resolvable.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Remove the Jackson 2 WebClient customizer

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/Configuration.kt:1-86`
- Modify: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt` (remove duplicated codec setup)
- Modify: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/allu/AlluClientITests.kt` (remove duplicated codec setup)
- Test: same two files, plus `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/attachment/common/FileScanClientITest.kt` (no code change needed there, but it's the regression check for the third affected client)

**Interfaces:**
- Consumes: Task 2's `ProfiiliClient.kt` (must already be on `tools.jackson.databind.JsonNode` before this task, or `getApiTokens`/`getTokenApiUrl` would fail to decode once the Jackson 2 customizer is gone).
- Produces: no new function signatures — this task only removes a `@Bean` and its imports. Nothing later in this plan depends on any new interface from this task.

**Context:** `jackson2WebClientCustomizer` in `Configuration.kt` is a single global `WebClientCustomizer` bean. Spring applies every `WebClientCustomizer` bean to every auto-configured `WebClient.Builder`, and this app has exactly one `WebClient.Builder` bean, shared by three consumers: `AlluClient`, `ProfiiliClient`, and `FileScanClient` (confirmed via `grep -rl "WebClient.Builder" services/hanke-service/src/main/kotlin`). None of the three have any other Jackson-2-specific dependency once Task 2 lands: `AlluClient` uses only fully-typed DTOs (no `JsonNode`), `FileScanClient` uses a plain two-field `FileScanResponse` data class with no secondary constructor, and `ProfiiliClient` was fixed in Task 2. So removing this one bean migrates all three consumers to Jackson 3 defaults in a single, clean step — there's no way to partially scope it without introducing per-client qualified `WebClient.Builder` beans, which isn't needed here since all three are equally ready.

`ProfiiliClientITest.kt` and `AlluClientITests.kt` currently hand-roll the exact same Jackson 2 codec setup this bean provides, because they construct their `WebClient`/`ProfiiliClient`/`AlluClient` directly rather than through Spring DI (confirmed in a prior code review). Once the bean is gone, that manual duplication has nothing left to replicate and should be removed — leaving both clients' default (Jackson 3) codecs in place, matching what production now does too.

- [ ] **Step 1: Write a failing test proving Allu request/response bodies still round-trip correctly without the customizer**

In `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/allu/AlluClientITests.kt`, find the existing test that exercises `create()` or `getApplicationInformation()` against the `MockWebServer` (there is at least one — this file already tests `AlluClient` end-to-end against a mock server). Add this assertion alongside it, capturing the actual raw request body Allu would receive and checking a representative date field's shape:

```kotlin
@Test
fun `date fields in outgoing Allu payloads are still ISO-8601 offset strings after removing the Jackson 2 customizer`() {
    val application = AlluFactory.createCableReportApplicationData(startTime = TESTIHENKILO_ZONED_DATE_TIME)
    mockWebServer.enqueue(MockResponse.Builder().code(200).body("1").build())

    alluClient.create(application)

    val recordedRequest = mockWebServer.takeRequest()
    val body = recordedRequest.body?.utf8() ?: ""
    val dateTimePattern =
        Regex(""""startTime":"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?[+-]\d{2}:\d{2}"""")
    assertThat(dateTimePattern.containsMatchIn(body)).isTrue()
}
```

(Check the exact existing factory/fixture names in this file first — `AlluFactory.createCableReportApplicationData`, the mock server field name, and whatever constant this file already uses for a fixed `ZonedDateTime` in other tests — and use those exact names rather than inventing new ones. This file already has comparable tests for `create()`; mirror the existing setup pattern precisely.)

- [ ] **Step 2: Run it to confirm it passes today (baseline, with the customizer + duplicated ITest codec setup still in place)**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.allu.AlluClientITests"`
Expected: PASS.

- [ ] **Step 3: Remove the `jackson2WebClientCustomizer` bean**

In `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/Configuration.kt`, delete:

```kotlin
    /**
     * Spring Boot 4's auto-configured WebClient.Builder defaults to Jackson 3 codecs. Keep it on
     * Jackson 2 for now, matching spring.jackson.use-jackson2-defaults, since WebClient consumers
     * such as ProfiiliClient still decode into com.fasterxml.jackson.databind.JsonNode.
     */
    @Bean
    fun jackson2WebClientCustomizer(objectMapper: ObjectMapper) = WebClientCustomizer { builder ->
        builder.codecs {
            it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper))
            it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper))
        }
    }
```

and remove the now-unused imports:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.webclient.WebClientCustomizer
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
```

(Keep everything else in the file unchanged — `alluClient()`, `webClientWithLargeBuffer()`, `createInsecureTrustingWebClient()` don't reference this bean.)

- [ ] **Step 4: Remove the duplicated Jackson 2 codec setup in `ProfiiliClientITest.kt`**

In `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt`, replace:

```kotlin
        // In production, the injected WebClient.Builder is customized by Configuration's
        // jackson2WebClientCustomizer bean. Replicate that here since this builder is created
        // directly, not through Spring's DI.
        val builder =
            WebClient.builder().codecs {
                it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(OBJECT_MAPPER))
                it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(OBJECT_MAPPER))
            }
        profiiliClient = ProfiiliClient(properties, builder, issuer)
```

with:

```kotlin
        profiiliClient = ProfiiliClient(properties, WebClient.builder(), issuer)
```

Remove the now-unused imports: `org.springframework.http.codec.json.Jackson2JsonDecoder`, `org.springframework.http.codec.json.Jackson2JsonEncoder`, and `fi.hel.haitaton.hanke.OBJECT_MAPPER` if nothing else in the file uses it (check first — `OBJECT_MAPPER` may be used elsewhere in the file for building expected request/response JSON; only remove the import if it's genuinely unused after this change). Also remove the file-level `@file:Suppress("DEPRECATION")` at the top of the file if it exists solely to suppress a warning about the now-deleted `Jackson2JsonEncoder`/`Jackson2JsonDecoder` usage — check the surrounding git blame/history first (it was added in commit `0ffa5921`, "Suppress deprecated Jackson2 codec warning in AlluClientITests" — check if that commit's suppression target was this file or `AlluClientITests.kt`; only remove it here if this file was its target and nothing else in the file needs it).

- [ ] **Step 5: Remove the equivalent duplication in `AlluClientITests.kt`**

In `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/allu/AlluClientITests.kt`, find and remove the analogous manual `.codecs { it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(OBJECT_MAPPER)) ... }` block (around line 86-88, per the imports at lines 63-64 found during this plan's research) the same way as Step 4, and remove the now-unused `Jackson2JsonEncoder`/`Jackson2JsonDecoder` imports and the `@file:Suppress("DEPRECATION")` annotation if this file was its target (per commit `0ffa5921`).

- [ ] **Step 6: Run the Allu, Profiili, and FileScan integration tests**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.allu.AlluClientITests" --tests "fi.hel.haitaton.hanke.profiili.ProfiiliClientITest" --tests "fi.hel.haitaton.hanke.attachment.common.FileScanClientITest"`
Expected: PASS, including the new Step-1 test — confirming Allu request bodies still serialize dates correctly, Profiili's `JsonNode`-based token/discovery parsing still works (Task 2 + this task combined), and `FileScanClient`'s plain-data-class round-trip is unaffected.

- [ ] **Step 7: Run the full test suite**

Run: `./gradlew :services:hanke-service:spotlessCheck :services:hanke-service:test :services:hanke-service:integrationTest`
Expected: BUILD SUCCESSFUL, 0 failures. This is the actual verification that removing a global bean didn't regress anything else in the app — trust this over any partial test run.

- [ ] **Step 8: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/Configuration.kt services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/allu/AlluClientITests.kt
git commit -m "$(cat <<'EOF'
HAI-XXXX Remove the Jackson 2 WebClient customizer

AlluClient, ProfiiliClient, and FileScanClient share the app's one
WebClient.Builder bean. Now that ProfiiliClient no longer needs
Jackson 2's JsonNode, none of the three have any remaining Jackson-2
dependency, so the jackson2WebClientCustomizer bean forcing all
outbound WebClient traffic onto Jackson 2 can go. REST controllers
are unaffected (they're governed separately by
spring.http.converters.preferred-json-mapper, still jackson2).
Drops the manual Jackson 2 codec duplication in ProfiiliClientITest
and AlluClientITests, which existed only to replicate this bean for
tests that build their client outside Spring DI.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## What's next

This plan covers the spec's Phase 0 (audit — completed as research while writing this plan, not as a separate task, since every finding turned out concrete enough to act on directly) and Phase 1 (outbound WebClient consumers). The spec's Phase 2 (REST controllers/DTOs), Phase 3 (flip the global default), and Phase 4 (cleanup & documentation) get their own follow-up plan once this one is merged — Phase 2's tasks depend on running the before/after JSON format diff against real REST endpoints, which is itself work to schedule after Task 3 lands, not something to guess at here.

## Self-Review Notes

- **Spec coverage:** This plan implements the spec's "Phase 0 — Audit" (folded into the tasks above as verified findings, not left as a vague step) and "Phase 1 — Migrate outbound WebClient consumers." The spec's Phases 2-4 are explicitly deferred to a follow-up plan (see "What's next" above), consistent with the spec's own incremental, risk-ordered sequencing.
- **Placeholder scan:** No TBD/TODO markers. Two steps (Task 3 Step 1's exact fixture names, Task 3 Steps 4-5's `@file:Suppress` removal) tell the implementer to check existing code before naming things, rather than inventing unverified names — this is a deliberate "verify against the real file" instruction, not a placeholder, since fixture/constant names in test files change over time and guessing wrong would produce broken code.
- **Type consistency:** `CustomOffsetDateTimeSerializer`/`Deserializer` keep their exact class names across Task 1 (so `AuditLogEvent`'s `using = ...` references don't need updating) and match the `ValueSerializer<T>`/`ValueDeserializer<T>` signature already established by `LngLatAltJackson3Serializer`/`Deserializer` in `HypersistenceJsonSerializer.kt`. `ProfiiliClient`'s `JsonNode` type is referenced identically before and after Task 2 (only the import package changes, not the type's usage). Task 3 introduces no new types.
