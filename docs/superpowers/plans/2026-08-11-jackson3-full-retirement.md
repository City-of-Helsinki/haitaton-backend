# Jackson 2 Full Retirement (Phase 3/4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the app's own use of Jackson 2 entirely — the REST/MVC layer (already deferred from
the SB4 upgrade) and the hand-built `OBJECT_MAPPER` singleton — leaving Jackson 3 as the only JSON
stack the app itself configures or calls.

**Architecture:** Sequenced by risk, exactly as `docs/superpowers/specs/2026-08-11-jackson3-full-retirement-design.md`
lays out: verify two previously-flagged Jackson 3 gaps are actually fixed (Task 1), migrate the
`OBJECT_MAPPER` singleton and its six call sites one file at a time since each touches a different
subsystem (security, GDPR, audit, geometry — Tasks 2-8), then flip the two Spring config flags and
remove the app's own Jackson 2 dependency declarations as one isolated, high-visibility change
(Task 9), then cleanup (Task 10). Every task after Task 1 depends on the previous one being merged
and green.

**Tech Stack:** Kotlin, Spring Boot 4.1.0, Jackson 3 (`tools.jackson.*`) via
`tools.jackson.module:jackson-module-kotlin:3.1.5`, JUnit 5, assertk, Testcontainers.

## Global Constraints

- Ticket: HAI-3618 (continuing the same ticket as the Spring Boot 4.1.0 upgrade and Phases 1-2).
- Base branch: `HAI-3618/jackson3-full-retirement`, branched from `dev`.
- Full unit/integration suite must stay green after every task — no failures deferred to a later
  task.
- `com.fasterxml.jackson.module:jackson-module-jaxb-annotations` is NOT touched by this plan — it's
  required by `logstash-logback-encoder`, unrelated to the app's own Jackson usage.
- `jackson-databind` (2.x) will remain on the runtime classpath transitively after this plan
  completes (pulled in independently by `logstash-logback-encoder` and `geojson-jackson`) — that's
  expected and out of scope; the goal is the app no longer declaring or calling it directly.
- `HypersistenceJsonSerializer.kt`'s primary Jackson-3 JSON-column serialization path (the
  `LngLatAlt` serializer/deserializer, the `ObjectMapperSupplier`) is NOT touched — only its
  `OBJECT_MAPPER.convertValue()` fallback path (Task 8) changes.

---

## Task 1: Verify the two known Jackson 3 gaps are resolved, add regression coverage

**Files:**
- Modify: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt`

**Interfaces:**
- Consumes: `fi.hel.haitaton.hanke.OBJECT_MAPPER` (Jackson 2, unchanged in this task),
  `tools.jackson.databind.json.JsonMapper` (Spring-autowired `jsonMapper` bean, unchanged),
  `fi.hel.haitaton.hanke.factory.ApplicationFactory.createExcavationNotificationArea()` (existing
  factory, returns a `KaivuilmoitusAlue` with a populated `haittojenhallintasuunnitelma` map).
- Produces: nothing new for later tasks — this task only adds regression coverage and confirms
  the codebase is safe to build on.

Context: the Spring Boot 4.1.0 upgrade commit (`b7e6e3c1`) flagged two "known remaining gaps (not
yet root-caused)": a `Map<Haittojenhallintatyyppi, String>` round-trip failure under Kaivuilmoitus
areas, and a `ProfiiliClientITest` `WebTestClient`/`CodecException` issue. Investigation while
writing this plan found neither reproduces against the current codebase:

- `ProfiiliClientITest` doesn't use `WebTestClient` at all (it builds a real `ProfiiliClient`
  against `MockWebServer` via `WebClient.builder()`) and the whole suite passes as-is — no code
  change needed for this one.
- A scratch reproduction of the full `KaivuilmoitusAlue` DTO, round-tripped through a bare
  `JsonMapper.builder().addModule(kotlinModule())` (plus the same `LngLatAlt`
  serializer/deserializer the app's `geoJsonJsonMapperBuilderCustomizer` bean registers),
  round-trips the `haittojenhallintasuunnitelma` map correctly with no exception.

Both were very likely fixed incidentally by later commits in the same HAI-3618 series (the Jackson
3 Kotlin-module/GeoJSON fixes done for Task 2/3 of Phases 1-2). This task locks that in with a
permanent regression test and a live-suite confirmation, rather than open-ended debugging.

- [ ] **Step 1: Add the enum-keyed map regression test**

Add this test to `Jackson3FormatDiffITest.kt` (append inside the existing `class
Jackson3FormatDiffITest`, alongside the other `@Test` methods):

```kotlin
    @Test
    fun `KaivuilmoitusAlue's enum-keyed haittojenhallintasuunnitelma map round-trips identically under Jackson 2 and Jackson 3`() {
        val alue = fi.hel.haitaton.hanke.factory.ApplicationFactory.createExcavationNotificationArea()

        val jackson2Json = OBJECT_MAPPER.writeValueAsString(alue)
        val roundTripped =
            jsonMapper.readValue(jackson2Json, fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue::class.java)
        val jackson3Json = jsonMapper.writeValueAsString(roundTripped)

        assertThat(jackson3Json).isEqualTo(jackson2Json)
        assertThat(roundTripped.haittojenhallintasuunnitelma).isEqualTo(alue.haittojenhallintasuunnitelma)
    }
```

This follows the file's existing pattern exactly (see the `Polygon` round-trip test already in the
file) — serialize via Jackson 2's `OBJECT_MAPPER`, deserialize + re-serialize via the
Spring-autowired Jackson 3 `jsonMapper`, and assert both the JSON string and the map itself are
identical.

- [ ] **Step 2: Run it (requires Docker for the Testcontainers Postgres instance this test class needs)**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.configuration.Jackson3FormatDiffITest"`
Expected: PASS (all tests in the class, including the new one).

If this fails, STOP — do not proceed to Task 2. Use `superpowers:systematic-debugging` to root-cause
before continuing; the rest of this plan assumes Jackson 3 handles this map shape correctly.

- [ ] **Step 3: Run the tests that originally surfaced the gaps, to confirm no residual issue**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.UpdateHakemusITest" --tests "fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusControllerITest" --tests "fi.hel.haitaton.hanke.taydennys.TaydennysControllerITest"`

(These are the current class names for what the SB4 upgrade commit referred to as
`UpdateHakemusITest`/`UpdateMuutosilmoitusITest`/`UpdateTaydennysITest` — confirm the exact
class/test names still match via `find services/hanke-service/src/integrationTest -iname
"*Update*ITest*"` before running, since file names may have shifted since that commit.)

Expected: PASS. If any fail, STOP and use `superpowers:systematic-debugging` before continuing —
do not proceed to Task 2 with a known-broken update path.

- [ ] **Step 4: Run `ProfiiliClientITest` to confirm it's unaffected (no Docker needed)**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.profiili.ProfiiliClientITest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/configuration/Jackson3FormatDiffITest.kt
git commit -m "HAI-3618 Add regression test confirming Kaivuilmoitus areas map round-trip is fixed"
```

---

## Task 2: Migrate `createObjectMapper()`/`OBJECT_MAPPER` to Jackson 3

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Utils.kt`
- Test: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/UtilsKtTest.kt`

**Interfaces:**
- Consumes: `tools.jackson.databind.json.JsonMapper`, `tools.jackson.module.kotlin.kotlinModule()`
  (verified available via the existing `tools.jackson.module:jackson-module-kotlin:3.1.5`
  dependency already in `build.gradle.kts`).
- Produces: `fun createObjectMapper(): JsonMapper` (return type changes from
  `com.fasterxml.jackson.databind.ObjectMapper` to `tools.jackson.databind.json.JsonMapper` — every
  task after this one that touches `OBJECT_MAPPER` relies on it being a `JsonMapper`, not the old
  `ObjectMapper`). `Constants.kt`'s `val OBJECT_MAPPER = createObjectMapper()` needs no change — its
  type is inferred.

Verified while writing this plan: Jackson 3's core `JsonMapper` already serializes
`ZonedDateTime`/`OffsetDateTime` as ISO-8601 strings by default (confirmed via a scratch test) — no
`JavaTimeModule` registration or `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` disabling needed,
unlike Jackson 2. This simplifies `createObjectMapper()` considerably.

- [ ] **Step 1: Write the failing test**

Add to `UtilsKtTest.kt` (new top-level test, alongside the existing `@Nested inner class
MergeDataInto`):

```kotlin
    @Nested
    inner class CreateObjectMapper {
        @Test
        fun `serializes ZonedDateTime as an ISO-8601 string, not a timestamp`() {
            val mapper = createObjectMapper()

            val json =
                mapper.writeValueAsString(
                    mapOf("t" to java.time.ZonedDateTime.parse("2026-01-01T10:00:00Z"))
                )

            assertThat(json).contains("2026-01-01T10:00:00Z")
        }

        @Test
        fun `serializes and deserializes a Kotlin data class`() {
            val mapper = createObjectMapper()
            data class Sample(val name: String, val count: Int)

            val json = mapper.writeValueAsString(Sample("test", 3))
            val roundTripped = mapper.readValue(json, Sample::class.java)

            assertThat(roundTripped).isEqualTo(Sample("test", 3))
        }
    }
```

Add `import assertk.assertions.contains` to `UtilsKtTest.kt`'s imports if not already present (it
isn't, based on the current import list).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :services:hanke-service:test --tests "fi.hel.haitaton.hanke.UtilsKtTest"`
Expected: FAIL (compile error — `createObjectMapper()` currently returns a Jackson 2 `ObjectMapper`,
and this test's assertions target behavior; more precisely, this specific test will actually PASS
against the current Jackson 2 implementation since Jackson 2 also produces ISO-8601 dates when
`WRITE_DATES_AS_TIMESTAMPS` is disabled — the point of this step is to confirm the test compiles and
passes against the OLD implementation too, establishing a baseline before the change, then re-run
after Step 3 to confirm it still passes against the NEW implementation).

- [ ] **Step 3: Migrate `createObjectMapper()`**

In `Utils.kt`, replace:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
```

with:

```kotlin
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
```

and replace:

```kotlin
fun createObjectMapper(): ObjectMapper =
    jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
```

with:

```kotlin
fun createObjectMapper(): JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :services:hanke-service:test --tests "fi.hel.haitaton.hanke.UtilsKtTest"`
Expected: PASS.

- [ ] **Step 5: Run the full unit test suite to check for fallout**

Run: `./gradlew :services:hanke-service:test`
Expected: PASS. `OBJECT_MAPPER`'s type has changed from `ObjectMapper` to `JsonMapper` — this step
catches any other unit-test code that referenced Jackson-2-specific methods on `OBJECT_MAPPER` not
yet covered by Tasks 3-8 (which migrate the known call sites in `main/kotlin`; this step is the
safety net for anything in `test/kotlin` that also touches it directly).

- [ ] **Step 6: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Utils.kt \
        services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/UtilsKtTest.kt
git commit -m "HAI-3618 Migrate createObjectMapper()/OBJECT_MAPPER to Jackson 3"
```

---

## Task 3: Migrate `GeometriatDao.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/geometria/GeometriatDao.kt:200-201`
- Test: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/geometria/GeometriatDaoITest.kt`

**Interfaces:**
- Consumes: `OBJECT_MAPPER` (now `JsonMapper`, from Task 2), `tools.jackson.module.kotlin.readValue`
  (reified extension function — verified working identically to Jackson 2's equivalent via a
  scratch test during plan-writing).
- Produces: no new interface — this is a like-for-like internal implementation change; callers of
  `GeometriatDao` are unaffected.

- [ ] **Step 1: Update the import**

In `GeometriatDao.kt`, find the import line for `com.fasterxml.jackson.module.kotlin.readValue` and
replace it with:

```kotlin
import tools.jackson.module.kotlin.readValue
```

The call sites themselves (`OBJECT_MAPPER.readValue(geojson)` and
`OBJECT_MAPPER.readValue(paramjson)` at lines 200-201) need no change — the reified extension
function has an identical signature in the Jackson 3 Kotlin module.

- [ ] **Step 2: Run the existing integration test to verify no regression**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.geometria.GeometriatDaoITest"`
Expected: PASS. This is the existing test coverage for this file — no new test needed, this is a
like-for-like API port.

- [ ] **Step 3: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/geometria/GeometriatDao.kt
git commit -m "HAI-3618 Migrate GeometriatDao off Jackson 2's readValue extension"
```

---

## Task 4: Migrate `AuditLogService.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/logging/AuditLogService.kt:109`
- Test: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/logging/AuditLogServiceITests.kt`

**Interfaces:**
- Consumes: `OBJECT_MAPPER.readTree(String)` — verified working identically on Jackson 3's
  `JsonMapper` via a scratch test (returns a `tools.jackson.databind.JsonNode`, whose `equals()`
  behaves the same way as Jackson 2's `JsonNode.equals()` for structural comparison).
- Produces: no new interface — internal implementation change only.

- [ ] **Step 1: Check for a direct `com.fasterxml.jackson` import to remove**

Run: `grep -n "^import com.fasterxml.jackson" services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/logging/AuditLogService.kt`

If it only imports `fi.hel.haitaton.hanke.OBJECT_MAPPER` (not any `com.fasterxml.jackson.*` type
directly), no import change is needed here — `readTree()`'s return type is inferred, and the
`==` comparison at line 109 doesn't need an explicit `JsonNode` import.

- [ ] **Step 2: Run the existing integration test to verify no regression**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.logging.AuditLogServiceITests"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/logging/AuditLogService.kt
git commit -m "HAI-3618 Confirm AuditLogService's readTree diffing works on Jackson 3 OBJECT_MAPPER"
```

(If Step 1 found no import to change, this commit will be empty/no-op on this file specifically —
skip committing if `git diff` shows nothing for this file, since Task 2 already changed
`OBJECT_MAPPER`'s underlying type and this file needs no source change at all.)

---

## Task 5: Migrate `GdprController.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/gdpr/GdprController.kt:170`
- Test: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/gdpr/GdprControllerITests.kt`

**Interfaces:**
- Consumes: `OBJECT_MAPPER.valueToTree<ObjectNode>(Any)` — verified working identically on Jackson
  3's `JsonMapper` via a scratch test; `ObjectNode`'s `get(String)`/`[]` operator (used as
  `[...]["permissions"]` in the existing code) also verified working the same way.
- Produces: no new interface — internal implementation change only.

- [ ] **Step 1: Update the `ObjectNode` import**

Find the import for `ObjectNode` in `GdprController.kt` (currently
`com.fasterxml.jackson.databind.node.ObjectNode`, per the file's import list) and replace with:

```kotlin
import tools.jackson.databind.node.ObjectNode
```

The call site itself (`.let { OBJECT_MAPPER.valueToTree<ObjectNode>(it) }["permissions"]` at line
170) needs no change — `valueToTree` has an identical generic signature on Jackson 3's `JsonMapper`.

- [ ] **Step 2: Run the existing integration test to verify no regression**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.gdpr.GdprControllerITests"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/gdpr/GdprController.kt
git commit -m "HAI-3618 Migrate GdprController's ObjectNode import to Jackson 3"
```

---

## Task 6: Migrate `Extensions.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Extensions.kt:11,26-27`
- Test: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/ExtensionsKtTest.kt`

**Interfaces:**
- Consumes: `OBJECT_MAPPER.writeValueAsString(Any?)`, `OBJECT_MAPPER.writerWithView(Class<*>)` —
  both verified working identically on Jackson 3's `JsonMapper` via a scratch test (the
  `@JsonView`-annotated field filtering behaves the same way, since `@JsonView` itself is from the
  shared `com.fasterxml.jackson.annotation` package, unchanged between Jackson 2 and 3).
- Produces: `toJsonString()` and `toChangeLogJsonString()` — used elsewhere in the codebase for
  audit logging; their string output format is unchanged (byte-for-byte, since both are ISO-8601
  by default in both Jackson 2 with the disabled-timestamps config and Jackson 3's default), so no
  downstream caller needs updating.

- [ ] **Step 1: Verify no direct `com.fasterxml.jackson` import needs changing**

`Extensions.kt`'s current imports (per the file's contents) don't import any `com.fasterxml.jackson`
type directly — `OBJECT_MAPPER`, `writeValueAsString`, and `writerWithView` are all called without
needing an explicit type import in this file. Confirm with:

```bash
grep -n "^import com.fasterxml.jackson" services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Extensions.kt
```

Expected: no output. If there is output, update that import to its `tools.jackson` equivalent
before continuing.

- [ ] **Step 2: Run the existing unit test to verify no regression**

Run: `./gradlew :services:hanke-service:test --tests "fi.hel.haitaton.hanke.ExtensionsKtTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Extensions.kt
git commit -m "HAI-3618 Confirm Extensions.kt's toJsonString/toChangeLogJsonString work on Jackson 3 OBJECT_MAPPER"
```

(As with Task 4, if Step 1 found nothing to change, `git diff` on this file will be empty — skip
the commit if so, since Task 2 already did the underlying type change.)

---

## Task 7: Migrate `AccessRules.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/security/AccessRules.kt:44`
- Test: any existing controller integration test asserting the 401 body shape (e.g.
  `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/HankeControllerITests.kt`,
  which references `HAI0001` per the codebase search done while writing this plan).

**Interfaces:**
- Consumes: `OBJECT_MAPPER.writeValueAsString(Any?)` — verified in Task 6.
- Produces: no new interface. This is the security-critical path (writes the 401 response body
  directly to the raw servlet response, bypassing Spring MVC's converters entirely) — the design
  doc calls this out as needing its own careful verification.

- [ ] **Step 1: Verify no direct `com.fasterxml.jackson` import needs changing**

```bash
grep -n "^import com.fasterxml.jackson" services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/security/AccessRules.kt
```

Expected: no output (the file only imports `fi.hel.haitaton.hanke.OBJECT_MAPPER`, per its current
contents). If there is output, update it to the `tools.jackson` equivalent.

- [ ] **Step 2: Run a test that exercises the unauthenticated 401 path**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.HankeControllerITests"`
Expected: PASS, including whichever test in that class asserts the `HAI0001` error body for an
unauthenticated request.

- [ ] **Step 3: Manually verify the 401 response body format is unchanged**

Since this path writes directly to the raw servlet response (bypassing Spring MVC's converters,
and thus bypassing any of the automated format-diff testing done in Phase 2), do a manual spot
check: run the app locally (or via an integration test with a `MockMvc`/real HTTP call), make an
unauthenticated request to any protected endpoint, and confirm the response body is still
`{"errorCode":"HAI0001","errorMessage":"..."}`-shaped (matching `HankeError`'s existing JSON shape)
with HTTP status 401 and `Content-Type: application/json`.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/security/AccessRules.kt
git commit -m "HAI-3618 Confirm AccessRules' 401 error body serialization works on Jackson 3 OBJECT_MAPPER"
```

---

## Task 8: Migrate `HypersistenceJsonSerializer.kt`'s `OBJECT_MAPPER` fallback off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/HypersistenceJsonSerializer.kt:38`

**Interfaces:**
- Consumes: `OBJECT_MAPPER.convertValue(value, value.javaClass)` — verified working identically on
  Jackson 3's `JsonMapper` via a scratch test.
- Produces: no new interface. `ObjectMapperCloningJsonSerializer.clone()` (used internally by
  hypersistence-utils for Hibernate dirty-checking snapshots) keeps its exact behavior — this task
  only changes which Jackson version backs the fallback path for non-`Serializable` values. The
  file's primary Jackson-3 JSON-column serialization path (`LngLatAltJackson3Serializer` etc.) is
  untouched, per the Global Constraints.

- [ ] **Step 1: Verify no import change is needed**

`HypersistenceJsonSerializer.kt` already imports `fi.hel.haitaton.hanke.OBJECT_MAPPER` (not a
direct `com.fasterxml.jackson` type for this call), and `value.javaClass` needs no Jackson import
at all. Confirm:

```bash
grep -n "^import com.fasterxml.jackson" services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/HypersistenceJsonSerializer.kt
```

Expected: no output.

- [ ] **Step 2: Run the full integration suite's JSON-column-related tests**

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.HistoriaTriggerITest"`

(Confirm this class name still matches via `find services/hanke-service/src/integrationTest -iname
"*Historia*ITest*"` first, since this exercises the historia trigger + JSON column path most
directly per the Phase 1/2 migration history.)

Expected: PASS.

- [ ] **Step 3: Run the full test suite as a final check for this migration phase**

Run: `./gradlew :services:hanke-service:test :services:hanke-service:integrationTest`
Expected: PASS (0 failures). This is the checkpoint before Task 9 — `OBJECT_MAPPER` and all six of
its call sites are now Jackson 3, with the Spring config flags still unchanged (still Jackson 2 for
MVC), so this confirms Phase 3b is fully done and safe before touching the global default.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/HypersistenceJsonSerializer.kt
git commit -m "HAI-3618 Confirm HypersistenceJsonSerializer's convertValue fallback works on Jackson 3 OBJECT_MAPPER"
```

---

## Task 9: Flip the global Jackson default (Phase 3c)

**Files:**
- Modify: `services/hanke-service/src/main/resources/application.yml`
- Modify: `services/hanke-service/build.gradle.kts`

**Interfaces:**
- Consumes: nothing new — this task removes configuration, it doesn't add code.
- Produces: nothing new — Spring MVC's `@ResponseBody`/`@RequestBody` handling now uses Boot's
  auto-configured Jackson 3 `JsonMapper` bean instead of the Jackson 2 compat path. No caller-facing
  interface changes; this is the highest-risk task in the plan precisely because its correctness
  depends on everything verified in Tasks 1-8, not on any new code of its own.

This is the single highest-risk change in the whole migration, per the design doc. Do not start
this task until Tasks 1-8 are all merged and green.

- [ ] **Step 1: Remove the two Spring config properties**

In `application.yml`, remove:

```yaml
  http:
    converters:
      # Spring Boot 4 defaults to Jackson 3's HttpMessageConverter when both Jackson versions are
      # on the classpath. Keep Jackson 2 for this upgrade; see the jackson.use-jackson2-defaults
      # note below.
      preferred-json-mapper: jackson2
  jackson:
    # Spring Boot 4 defaults to Jackson 3. Keep Jackson 2 behavior for this upgrade; migrating
    # to Jackson 3 is tracked separately since it touches nearly every JSON surface in the app.
    use-jackson2-defaults: true
```

(These sit under the `spring:` key, alongside `spring.datasource`, `spring.jpa`, etc. — remove only
these two nested blocks, not the whole `spring:` section.)

- [ ] **Step 2: Remove the Jackson 2 dependency declarations**

In `build.gradle.kts`, remove these three lines (leave the comment above them if it still makes
sense standalone, otherwise remove the comment too since it specifically explains why Jackson 2 was
kept):

```kotlin
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
```

Leave `implementation("tools.jackson.module:jackson-module-kotlin:3.1.5")` and
`implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations")` untouched — the
former is Jackson 3's own Kotlin module (still needed), the latter is for
`logstash-logback-encoder` (per the Global Constraints, out of scope for this plan).

Also remove the now-unused `ext["jackson-2-bom.version"] = "2.21.5"` line near the top of the file
— check first whether anything else in the file still references `jackson-2-bom`:

```bash
grep -n "jackson-2-bom" services/hanke-service/build.gradle.kts
```

If the only remaining reference is the `ext[...]` declaration itself after this task's other
removals, delete that line too. If something else still references it (e.g. a dependency
constraint pinning the transitive Jackson 2 version for CVE reasons), leave it — that constraint is
now pinning `logstash-logback-encoder`'s and `geojson-jackson`'s transitive Jackson 2, not the
app's own dependency, and is still doing useful work.

- [ ] **Step 3: Rebuild and run the full test suite**

Run: `./gradlew :services:hanke-service:test :services:hanke-service:integrationTest`
Expected: PASS (0 failures). If anything fails here, this is exactly the scenario the design doc
flags as highest-risk — use `superpowers:systematic-debugging` rather than guessing at a fix, and
consider that reverting this one task's commit restores the known-working state from Task 8.

- [ ] **Step 4: Manual smoke test**

Run the app locally against a local Postgres (see `docker-compose.yml`/README for the standard
local dev setup) and manually: create a hakemus, update it, submit it, fetch it back, and confirm
dates render correctly in the response (matching the format the frontend expects — this was the
Phase 2 format-diff testing's main concern, and Phase 2 already confirmed no format difference, but
this is the first time that configuration actually takes effect for real traffic).

- [ ] **Step 5: Verify the classpath — Jackson 2 databind is still present, just transitive now**

```bash
./gradlew :services:hanke-service:dependencies --configuration runtimeClasspath | grep "com.fasterxml.jackson.core:jackson-databind"
```

Expected: still present, but only reachable via `net.logstash.logback:logstash-logback-encoder` and
`de.grundid.opendatalab:geojson-jackson` in the tree output — not via `spring-boot-jackson2`
(which no longer appears at all) and not as a direct dependency of `services:hanke-service` itself.
This confirms the removal actually took effect and matches the Global Constraints' expectation.

- [ ] **Step 6: Commit**

```bash
git add services/hanke-service/src/main/resources/application.yml services/hanke-service/build.gradle.kts
git commit -m "HAI-3618 Flip Spring's Jackson default from 2 to 3, remove the app's own Jackson 2 dependencies"
```

---

## Task 10: Cleanup & documentation (Phase 4)

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/Configuration.kt`
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/HypersistenceJsonSerializer.kt`
- Modify: `CLAUDE.md` (or wherever this repo keeps team engineering guidelines — check for an
  existing conventions doc first)

**Interfaces:**
- Consumes: nothing — this task is comments and documentation only, no code behavior changes.
- Produces: nothing new.

- [ ] **Step 1: Update `Configuration.kt`'s customizer comment**

The `geoJsonJsonMapperBuilderCustomizer` bean's doc comment (lines ~47-54) currently explains why
the GeoJSON serializer/deserializer is needed "on the app-wide JsonMapper.Builder that Boot
auto-configures and that WebClient's codecs are built from" as if this were one JSON stack among
several. Update it to state plainly that this is now the app's only JSON stack (REST, WebClient,
and — via the same `LngLatAlt` serializer reused by `HypersistenceJsonSerializer.kt` — JPA JSON
columns too), since the Jackson 2 compat path this comment used to contrast against is gone.

- [ ] **Step 2: Update `HypersistenceJsonSerializer.kt`'s file-level context**

Check the file's existing doc comments for any remaining "we have two Jacksons" framing (the
`ObjectMapperCloningJsonSerializer` class comment currently explains the Serializable-vs-not split
without mentioning Jackson 2 vs 3 directly, so likely needs no change — verify by reading the
current comment text before editing).

- [ ] **Step 3: Document the enum-keyed-map and secondary-constructor gotchas as a team guideline**

Find this repo's existing engineering-guidelines location (check `CLAUDE.md` at the repo root
first; if none exists, ask where the team keeps this kind of note rather than guessing a new
location). Add a short note along these lines:

> When adding a Kotlin data class or enum-keyed `Map` that gets serialized to JSON (REST DTOs, JPA
> JSON columns), be aware Jackson 3's Kotlin module has twice caused subtly different behavior than
> Jackson 2: a secondary-constructor deserialization ambiguity (fixed for `Autoliikenneluokittelu`,
> see git history) and — suspected but not confirmed as a real bug, since it didn't reproduce when
> investigated — enum-keyed `Map` round-tripping. If you hit unexpected (de)serialization behavior
> on a Kotlin data class, check `@JsonCreator` explicitness on secondary constructors first.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/Configuration.kt \
        services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/HypersistenceJsonSerializer.kt \
        CLAUDE.md
git commit -m "HAI-3618 Update comments and add team guideline now that the app is single-Jackson"
```

---

## Self-Review Notes

- **Spec coverage:** Phase 3a → Task 1. Phase 3b → Tasks 2-8 (one per file, matching the design
  doc's six call sites plus the foundational `Utils.kt` change). Phase 3c → Task 9. Phase 4 → Task
  10. All design doc sections have a corresponding task.
- **Corrections found and folded in while writing this plan** (both already applied to the design
  doc, commit `9d8cdab3`): the two "known gaps" don't reproduce against current code (verified via
  scratch tests, not assumed) — Task 1 is verify-and-lock-in rather than open-ended debugging.
  Jackson 2's `jackson-databind` stays on the classpath transitively via `logstash-logback-encoder`
  and `geojson-jackson` regardless of this plan — Task 9's goal statement and Step 5 reflect that
  honestly rather than claiming full classpath removal.
- **Type consistency:** `createObjectMapper(): JsonMapper` (Task 2) is consumed identically by name
  in Tasks 3, 5, 7, 8's import-check steps and Task 4/6's no-op-if-nothing-found steps — no
  signature drift between tasks.
- **No placeholders:** every code-bearing step has real, verified code (each Jackson 3 API call —
  `writeValueAsString`, `writerWithView`, reified `readValue`, `readTree`, `valueToTree`,
  `convertValue` — was checked against the actual `tools.jackson.module:jackson-module-kotlin:3.1.5`
  dependency via scratch tests during plan-writing, not assumed from Jackson 2 familiarity).
