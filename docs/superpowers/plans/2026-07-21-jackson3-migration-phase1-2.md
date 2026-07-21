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
- Produces: `ProfiiliClient.getApiTokens(accessToken: String): JsonNode` and `getTokenApiUrl(): String` now return/use `tools.jackson.databind.JsonNode` internally — no change to their public signatures' semantics. `ProfiiliClientITest.kt`'s manually-built `WebClient` (see Step 2.5 below) is fixed as part of *this* task, not Task 3 — Task 3 only needs to know that `ProfiiliClientITest.kt` is already done and should not touch it again.

**Context:** `ProfiiliClient.getApiTokens()` and `getTokenApiUrl()` decode raw OAuth/OIDC discovery responses via `.bodyToMono(JsonNode::class.java)` using Jackson 2's `com.fasterxml.jackson.databind.JsonNode`, because that's what the `jackson2WebClientCustomizer`-configured `WebClient` currently decodes into in production. `Extensions.kt`'s `JsonNode::class.java.getResource(this)` and `Configuration.kt`'s comment mentioning `JsonNode` were checked and are **not** real dependencies — the former uses the class purely as an arbitrary classloader anchor (explicitly commented as such), the latter is just prose in a comment. Neither needs any change.

**Important correction (found during implementation of this task):** `ProfiiliClientITest.kt` does **not** go through Spring DI or the production `jackson2WebClientCustomizer` bean at all — it builds its own `WebClient` directly in `setUp()` and manually hand-rolls the identical Jackson 2 codec setup (see Step 2.5). This means the import migration in Step 3 cannot be verified against this test until that manual codec setup is also updated — the test's behavior depends entirely on what it builds itself, not on anything in `Configuration.kt`. Step 2.5 (added after an implementer hit this as a real test failure, not a hypothetical) fixes this as part of Task 2, not Task 3. Task 3's plan text below has been corrected to no longer touch `ProfiiliClientITest.kt`.

- [ ] **Step 1: Write a failing test that exercises `JsonNode` field access against Jackson 3's type**

`ProfiiliClientITest.kt` already exercises `getApiTokens`/`getTokenApiUrl` indirectly through `getVerifiedName` (see the existing `GetVerifiedName` nested test class). Add this focused test inside that same file, in the top-level test class (not nested), to pin the exact JSON-field-access behavior this migration must preserve:

```kotlin
@Test
fun `getVerifiedName still works when Profiili's token and discovery responses use nested JsonNode field access`() {
    // This exercises ProfiiliClient.getApiTokens() and getTokenApiUrl(), both of which parse
    // their response body into a JsonNode and pull a field back out with `["field"]?.asText()`.
    // A prior regression here would surface as a null/ClassCastException from that access, not
    // a compile error, since JsonNode is used structurally rather than through a typed DTO.
    mockApiToken()
    mockGraphQl.enqueueSuccess(
        ProfiiliResponse(ProfiiliData(MyProfile(ProfiiliFactory.DEFAULT_NAMES)))
    )

    val result = profiiliClient.getVerifiedName(ACCESS_TOKEN)

    assertThat(result.firstName).isEqualTo(ProfiiliFactory.DEFAULT_FIRST_NAME)
}
```

(These are the *verified* real helper names in the file, confirmed by reading it directly — `mockApiToken()` (a private helper at the bottom of the file), `ACCESS_TOKEN` (a private const, not `GRANT`), and `ProfiiliResponse(ProfiiliData(MyProfile(ProfiiliFactory.DEFAULT_NAMES)))` passed to `mockGraphQl.enqueueSuccess(...)` — this exact pattern is already used by another test in the file's `GetVerifiedName` nested class. Add this new test at the top level, not nested, alongside the class's other members.)

- [ ] **Step 2: Run the test to confirm it passes today (baseline, Jackson 2)**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.profiili.ProfiiliClientITest"`
Expected: PASS (all existing tests plus the new one).

- [ ] **Step 3: Change the codec setup and the `JsonNode` import together (they are not independently testable — see below)**

**Why these two changes must land in one step, not two:** the decode-target type (`JsonNode`, from whichever Jackson generation `ProfiiliClient.kt` imports) and the codec actually doing the decoding (whichever Jackson generation is wired into the `WebClient`) must match, or deserialization fails with a "no Creators" `InvalidDefinitionException` — this is exactly the failure an implementer hit when only the import was changed first. Changing only the codec first (leaving the import on Jackson 2) fails the same way in reverse. Do both changes below before running the test again.

**3a.** In `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClient.kt`, change:

```kotlin
import com.fasterxml.jackson.databind.JsonNode
```

to:

```kotlin
import tools.jackson.databind.JsonNode
```

No other code in this file needs to change — `.bodyToMono(JsonNode::class.java)`, `apiTokens["access_token"]?.asText()`, and `conf["token_endpoint"]?.asText()` all use `JsonNode`'s standard field-access (`get`/`asText`) API, which Jackson 3's `JsonNode` keeps (only the internal `TextNode` concrete class was renamed to `StringNode` per the Jackson 3 migration guide — this doesn't affect the `JsonNode` interface's own accessor methods).

**3b.** In `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt`, this file builds its `WebClient` directly in `setUp()`, bypassing Spring DI and the production `jackson2WebClientCustomizer` bean entirely — it hand-rolls the identical Jackson 2 codec setup itself:

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

Replace it with:

```kotlin
        profiiliClient = ProfiiliClient(properties, WebClient.builder(), issuer)
```

Once the `WebClient.Builder` has no codec override, Boot 4's default (Jackson 3) codecs apply, matching what `ProfiiliClient.kt` now expects after 3a.

Remove the now-unused imports `org.springframework.http.codec.json.Jackson2JsonDecoder` and `org.springframework.http.codec.json.Jackson2JsonEncoder`. Do **not** remove the `fi.hel.haitaton.hanke.OBJECT_MAPPER` import — it's still used elsewhere in the file (e.g. `OBJECT_MAPPER.readValue(body)` for asserting request bodies). Check whether `@file:Suppress("DEPRECATION")` at the top of the file was added solely to silence a warning about the now-deleted `Jackson2JsonEncoder`/`Jackson2JsonDecoder` usage — if nothing else in the file triggers a deprecation warning, remove that annotation too; if anything else does, leave it and note why in your report.

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.profiili.ProfiiliClientITest"`
Expected: PASS, with both 3a and 3b applied together. If it fails, do not try to "fix" it by reverting just one side — diagnose against the actual error message, since a partial revert reintroduces the exact type mismatch this step exists to avoid.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClient.kt services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt
git commit -m "$(cat <<'EOF'
HAI-XXXX Migrate ProfiiliClient's JsonNode usage to Jackson 3

getApiTokens() and getTokenApiUrl() decode raw OAuth/OIDC responses
via JsonNode. The decode-target type and the WebClient's codec
generation must match, so this ports the import from
com.fasterxml.jackson.databind to tools.jackson.databind and, in the
same change, removes ProfiiliClientITest's manual Jackson 2 codec
override (it built its WebClient outside Spring DI and replicated
the production jackson2WebClientCustomizer bean by hand, so it
couldn't just inherit this fix from a later task).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Remove the Jackson 2 WebClient customizer

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/Configuration.kt:1-86`
- Modify: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/allu/AlluClientITests.kt` (remove duplicated codec setup)
- Test: same file, plus `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt` and `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/attachment/common/FileScanClientITest.kt` (no code change needed in either — they're the regression check for the other two affected clients)

**Note:** `ProfiiliClientITest.kt`'s duplicated Jackson 2 codec setup was already removed in Task 2 (it turned out not independently separable from the `JsonNode` import change — see that task's plan text for why). Do not touch that file in this task beyond running its tests as a regression check.

**Interfaces:**
- Consumes: Task 2's `ProfiiliClient.kt` and `ProfiiliClientITest.kt` (both must already be on Jackson 3 before this task).
- Produces: no new function signatures — this task only removes a `@Bean` and its imports. Nothing later in this plan depends on any new interface from this task.

**Context:** `jackson2WebClientCustomizer` in `Configuration.kt` is a single global `WebClientCustomizer` bean. Spring applies every `WebClientCustomizer` bean to every auto-configured `WebClient.Builder`, and this app has exactly one `WebClient.Builder` bean, shared by three consumers: `AlluClient`, `ProfiiliClient`, and `FileScanClient` (confirmed via `grep -rl "WebClient.Builder" services/hanke-service/src/main/kotlin`). None of the three have any other Jackson-2-specific dependency once Task 2 lands: `AlluClient` uses only fully-typed DTOs (no `JsonNode`), `FileScanClient` uses a plain two-field `FileScanResponse` data class with no secondary constructor, and `ProfiiliClient` was fixed in Task 2. So removing this one bean migrates all three consumers to Jackson 3 defaults in a single, clean step — there's no way to partially scope it without introducing per-client qualified `WebClient.Builder` beans, which isn't needed here since all three are equally ready.

`AlluClientITests.kt` currently hand-rolls the exact same Jackson 2 codec setup this bean provides, because it constructs its `WebClient`/`AlluClient` directly rather than through Spring DI (confirmed in a prior code review, and independently in Task 2 for the equivalent `ProfiiliClientITest.kt` case). Once the bean is gone, that manual duplication has nothing left to replicate and should be removed — leaving the client's default (Jackson 3) codecs in place, matching what production now does too.

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

- [ ] **Step 4: Remove the equivalent duplication in `AlluClientITests.kt`**

In `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/allu/AlluClientITests.kt`, find and remove the manual `.codecs { it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(OBJECT_MAPPER)) ... }` block (around line 86-88, per the imports at lines 63-64 found during this plan's research) the same way Task 2 did for the equivalent block in `ProfiiliClientITest.kt`, and remove the now-unused `Jackson2JsonEncoder`/`Jackson2JsonDecoder` imports and the `@file:Suppress("DEPRECATION")` annotation if this file was its target (per commit `0ffa5921`, "Suppress deprecated Jackson2 codec warning in AlluClientITests" — check first whether anything else in the file still needs that suppression before removing it).

- [ ] **Step 5: Run the Allu, Profiili, and FileScan integration tests**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.allu.AlluClientITests" --tests "fi.hel.haitaton.hanke.profiili.ProfiiliClientITest" --tests "fi.hel.haitaton.hanke.attachment.common.FileScanClientITest"`
Expected: PASS, including the new Step-1 test — confirming Allu request bodies still serialize dates correctly, Profiili's `JsonNode`-based token/discovery parsing still works (verified in Task 2, re-checked here as a regression), and `FileScanClient`'s plain-data-class round-trip is unaffected.

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew :services:hanke-service:spotlessCheck :services:hanke-service:test :services:hanke-service:integrationTest`
Expected: BUILD SUCCESSFUL, 0 failures. This is the actual verification that removing a global bean didn't regress anything else in the app — trust this over any partial test run.

- [ ] **Step 7: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/Configuration.kt services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/allu/AlluClientITests.kt
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
- **Placeholder scan:** No TBD/TODO markers. Several steps (Task 3 Step 1's exact fixture names, Task 3 Step 4's `@file:Suppress` removal) tell the implementer to check existing code before naming things, rather than inventing unverified names — this is a deliberate "verify against the real file" instruction, not a placeholder, since fixture/constant names in test files change over time and guessing wrong would produce broken code.
- **Revision note (added after Task 2's implementer hit a real blocker):** the original Task 2/Task 3 split assumed `ProfiiliClient.kt`'s `JsonNode` import could be migrated independently of `ProfiiliClientITest.kt`'s codec setup. It can't — they're two halves of one atomic change (decode-target type vs. the codec that produces it), and the test bypasses Spring DI entirely so it can't inherit a fix from a later task touching `Configuration.kt`. Task 2 now owns both halves for the Profiili case; Task 3 was updated to no longer touch `ProfiiliClientITest.kt`.
- **Type consistency:** `CustomOffsetDateTimeSerializer`/`Deserializer` keep their exact class names across Task 1 (so `AuditLogEvent`'s `using = ...` references don't need updating) and match the `ValueSerializer<T>`/`ValueDeserializer<T>` signature already established by `LngLatAltJackson3Serializer`/`Deserializer` in `HypersistenceJsonSerializer.kt`. `ProfiiliClient`'s `JsonNode` type is referenced identically before and after Task 2 (only the import package changes, not the type's usage). Task 3 introduces no new types.
