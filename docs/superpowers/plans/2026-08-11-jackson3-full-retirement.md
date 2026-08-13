# Jackson 2 Full Retirement (Phase 3/4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the app's own use of Jackson 2 entirely — the REST/MVC layer (already deferred from
the SB4 upgrade) and the hand-built `OBJECT_MAPPER` singleton — leaving Jackson 3 as the only JSON
stack the app itself configures or calls.

**Architecture:** Sequenced by risk, exactly as `docs/superpowers/specs/2026-08-11-jackson3-full-retirement-design.md`
lays out: verify two previously-flagged Jackson 3 gaps are actually fixed (Task 1), migrate the
`OBJECT_MAPPER` singleton and its six main-source call sites one file at a time since each touches a
different subsystem (security, GDPR, audit, geometry — Tasks 2-5, 11-13), migrate a cluster of
test-only Jackson 2 usage discovered during Task 5's review that the design doc's original file
scan missed (Tasks 6-7, 9-10; renumbered during execution — see Self-Review Notes), fix a
`createObjectMapper()` GeoJSON gap discovered during Task 7's review (Task 8), then flip the two
Spring config flags and remove the app's own Jackson 2 dependency declarations as one isolated,
high-visibility change (Task 14), then cleanup (Task 15). Every task after Task 1 depends on the
previous one being merged and green.

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
  `OBJECT_MAPPER.convertValue()` fallback path (Task 13) changes.
- Tasks 6-7, 9-10 were added mid-execution (discovered during Task 5's review) to cover a cluster
  of test-source Jackson 2 usage the design doc's original file scan missed. Task 8 was added
  mid-execution (discovered during Task 7's review) to fix a `createObjectMapper()` GeoJSON gap.
  See Self-Review Notes.

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

## Task 6: Migrate `TestExtensions.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/TestExtensions.kt`

**Interfaces:**
- Consumes: `tools.jackson.module.kotlin.readValue` (reified extension — verified via scratch test
  during plan-writing).
- Produces: `asJsonResource()`, `andReturnBody()`, `parseJson()` — used across much of the test
  suite. Their signatures are unchanged; only the underlying Jackson version changes.

Context (discovered during Task 5's review, not in the original design doc's file scan — that scan
only covered `src/main/kotlin`): this file's `com.fasterxml.jackson.module.kotlin.readValue` import
no longer resolves against `OBJECT_MAPPER` now that Task 2 changed its type to Jackson 3's
`JsonMapper`. Every call site here uses either an explicit type parameter (`fun <T>
String.asJsonResource(type: Class<T>)`) or a `reified T` on the enclosing function — both patterns
were verified to work with `tools.jackson.module.kotlin.readValue` via scratch tests (this is the
"safe" pattern, unlike `GeometriatDao.kt`'s property-setter-inside-`apply` case that needed a
helper function in Task 3).

- [ ] **Step 1: Update the import**

Replace:

```kotlin
import com.fasterxml.jackson.module.kotlin.readValue
```

with:

```kotlin
import tools.jackson.module.kotlin.readValue
```

No other change — `readValue` is called at lines 23, 26, 30, and 37, all via a `reified T` or
explicit `Class<T>` parameter, which the Jackson 3 Kotlin module's `readValue` supports identically.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :services:hanke-service:compileTestKotlin`
Expected: this specific file's errors are gone. The build may still fail overall due to Tasks 7-9
not being done yet (`JacksonTestExtension.kt` and the deserializer classes it registers) — if so,
confirm via the error output that no remaining error mentions `TestExtensions.kt`.

- [ ] **Step 3: Run a test that exercises these helpers, once Task 9 unblocks full compilation**

This file's helpers are used broadly; a full verification run isn't meaningful until Tasks 7-9 also
land (the test module won't compile as a whole until then). Note this in your report rather than
skipping verification silently — Task 9's Step 3 (full suite run) is the real gate for this file.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/TestExtensions.kt
git commit -m "HAI-3618 Migrate TestExtensions.kt off Jackson 2's readValue extension"
```

---

## Task 7: Migrate `JacksonTestExtension.kt` and its four registered deserializers off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/test/JacksonTestExtension.kt`
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusDataDeserializer.kt`
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusDataResponseDeserializer.kt`
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusResponseDeserializer.kt`
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HankkeenHakemusResponseDeserializer.kt`

**Interfaces:**
- Consumes: `tools.jackson.databind.ValueDeserializer<T>` (Jackson 3's renamed replacement for
  Jackson 2's `JsonDeserializer<T>` — an already-working example exists in this codebase at
  `HypersistenceJsonSerializer.kt`'s `LngLatAltJackson3Deserializer`, use it as the reference
  pattern), `tools.jackson.core.JsonParser`, `tools.jackson.databind.DeserializationContext`,
  `tools.jackson.databind.module.SimpleModule`, `tools.jackson.databind.node.ObjectNode`.
- Produces: no new interface visible outside this file cluster — `JacksonTestExtension` is a JUnit5
  `BeforeAllCallback` enabled by default on `IntegrationTest`/`ControllerTest` (see its own doc
  comment), so this task's correctness gates most of the integration test suite being able to
  compile and run at all.

Context (discovered during Task 5's review): these five files form one tightly-coupled unit —
`JacksonTestExtension` registers all four deserializers as a `SimpleModule` onto `OBJECT_MAPPER` via
`OBJECT_MAPPER.registerModule(module)`. This call itself no longer compiles: Jackson 3's `JsonMapper`
is **immutable** (builder-based) — unlike Jackson 2's `ObjectMapper`, there is no `registerModule()`
or `disable()` instance method to call directly. Verified via scratch test during plan-writing: the
correct pattern is `mapper.rebuild().addModule(someModule).build()` (or `.disable(feature)` in the
same chain), which returns a **new** `JsonMapper` rather than mutating the existing one in place.

This is the trickiest task in this migration — budget more care than the mechanical import swaps in
Tasks 3-6. Each deserializer follows the same shape (read the request as a tree, pick a concrete
subtype based on a discriminator field, delegate to a mapper for the rest) — port `HakemusDataDeserializer.kt`
first as the smallest example, confirm the pattern compiles and behaves correctly, then apply the
same pattern to the other three.

- [ ] **Step 1: Port `HakemusDataDeserializer.kt`**

Current code:

```kotlin
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER

class HakemusDataDeserializer : JsonDeserializer<HakemusData>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): HakemusData {
        val root = parser.readValueAsTree<ObjectNode>()

        val dataClass =
            when (ApplicationType.valueOf(root.path("applicationType").textValue())) {
                ApplicationType.CABLE_REPORT -> JohtoselvityshakemusData::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusData::class.java
            }

        return OBJECT_MAPPER.treeToValue(root, dataClass)
    }
}
```

Replace with:

```kotlin
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.node.ObjectNode

class HakemusDataDeserializer : ValueDeserializer<HakemusData>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): HakemusData {
        val root = p.readValueAsTree<ObjectNode>()

        val dataClass =
            when (ApplicationType.valueOf(root.path("applicationType").asString())) {
                ApplicationType.CABLE_REPORT -> JohtoselvityshakemusData::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusData::class.java
            }

        return OBJECT_MAPPER.treeToValue(root, dataClass)
    }
}
```

(`textValue()` → `asString()`: same deprecation reasoning as Task 5. Parameter names `p`/`ctxt`
match `LngLatAltJackson3Deserializer`'s existing convention — not required, but keeps the codebase
consistent.)

- [ ] **Step 2: Verify `HakemusDataDeserializer.kt` compiles in isolation**

Run: `./gradlew :services:hanke-service:compileTestKotlin`
Expected: no error mentioning `HakemusDataDeserializer.kt` specifically (errors from the other three
still-unmigrated deserializers and `JacksonTestExtension.kt` are expected at this point — check only
that this file's own error is gone).

- [ ] **Step 3: Port the remaining three deserializers using the same pattern**

`HakemusDataResponseDeserializer.kt` follows the identical shape to Step 1 — apply the same
`JsonDeserializer`→`ValueDeserializer`, `com.fasterxml.jackson.*`→`tools.jackson.*`,
`textValue()`→`asString()` changes.

`HakemusResponseDeserializer.kt` and `HankkeenHakemusResponseDeserializer.kt` additionally build a
second, modified mapper mid-deserialization (currently: `createObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)`
then `mapper.registerModule(SimpleModule().addAbstractTypeMapping(...))`). Since `createObjectMapper()`
already returns a Jackson 3 `JsonMapper` (from Task 2) and `JsonMapper` is immutable, chain both
changes through `.rebuild()` in one go, verified via scratch test during plan-writing:

```kotlin
val mapper =
    createObjectMapper()
        .rebuild()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .addModule(SimpleModule().addAbstractTypeMapping(HakemusDataResponse::class.java, dataClass))
        .build()

return mapper.treeToValue(root, HakemusResponse::class.java)
```

(Both `.disable(...)` and `.addModule(...)` are `JsonMapper.Builder` methods, chainable in either
order, terminated by `.build()` to get the actual usable `JsonMapper`.) Import
`tools.jackson.databind.DeserializationFeature` and `tools.jackson.databind.module.SimpleModule`
(not the `com.fasterxml.jackson.*` equivalents).

- [ ] **Step 4: Migrate `JacksonTestExtension.kt`**

Current code registers the module via a direct, now-nonexistent mutating call:

```kotlin
import com.fasterxml.jackson.databind.module.SimpleModule
...
val module = SimpleModule()
module.addDeserializer(HakemusResponse::class.java, HakemusResponseDeserializer())
module.addDeserializer(HakemusDataResponse::class.java, HakemusDataResponseDeserializer())
module.addDeserializer(HakemusData::class.java, HakemusDataDeserializer())
module.addDeserializer(HankkeenHakemusResponse::class.java, HankkeenHakemusResponseDeserializer())
OBJECT_MAPPER.registerModule(module)
```

`OBJECT_MAPPER` is a `val` (declared in `Constants.kt`), so it can't be reassigned to the rebuilt
mapper — and every other file in this codebase reads `OBJECT_MAPPER` expecting it to already have
this module registered once `JacksonTestExtension` has run. Rather than trying to mutate or
reassign `OBJECT_MAPPER` (not possible — Jackson 3 mappers are immutable and `OBJECT_MAPPER` is a
`val`), this needs a different mechanism: introduce a mutable, extension-scoped holder that the four
custom-deserializer-touching test files (`TestExtensions.kt`'s `asJsonResource`/`parseJson`/
`andReturnBody`, and anywhere else in tests that expects these deserializers to be active) read from
instead of `OBJECT_MAPPER` directly, OR — the smaller-blast-radius option — check whether any
production code path actually depends on `OBJECT_MAPPER` having these test-only deserializers
registered (it should not, since they're test-only types like `HakemusResponse`/`HankkeenHakemusResponse`
which don't need custom deserialization in production, only in test assertions comparing
API responses back into domain objects).

**Stop and think before implementing:** this is a real architectural question the brief cannot
answer for you with a mechanical rule, because the right fix depends on how widely `OBJECT_MAPPER`
(post-`JacksonTestExtension`) is actually relied upon elsewhere in the test suite versus how
localized it could be. Investigate with:

```bash
grep -rn "OBJECT_MAPPER" services/hanke-service/src/test/kotlin services/hanke-service/src/integrationTest/kotlin | grep -v "^Binary"
```

If it turns out to be narrowly used (a handful of call sites reading `HakemusResponse`/
`HakemusDataResponse`/`HakemusData`/`HankkeenHakemusResponse` back from JSON), the cleanest fix is
likely a dedicated test-only `val TEST_OBJECT_MAPPER: JsonMapper` (built once via
`OBJECT_MAPPER.rebuild().addModule(module).build()`) that those specific call sites use instead of
`OBJECT_MAPPER`, with `JacksonTestExtension` populating it. If it's broadly relied upon (many
unrelated call sites implicitly expecting the module to be there), report this as a concern rather
than guessing — this may need the human's input on the right architecture, since it's more invasive
than anything else in this plan.

- [ ] **Step 5: Verify compilation and run affected tests**

Run: `./gradlew :services:hanke-service:compileTestKotlin :services:hanke-service:compileIntegrationTestKotlin`
Expected: no errors remaining in any of the five files this task touches.

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.hakemus.HakemusControllerITest"` (or
whichever test class most directly exercises deserialization via these four custom deserializers —
check `JacksonTestExtension`'s doc comment and grep for usages of `HakemusResponse`/
`HankkeenHakemusResponse` in test assertions to confirm you've picked a representative test).
Expected: PASS, modulo the sandbox's Docker limitation (report which outcome you actually got).

- [ ] **Step 6: Commit**

```bash
git add services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/test/JacksonTestExtension.kt \
        services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusDataDeserializer.kt \
        services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusDataResponseDeserializer.kt \
        services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusResponseDeserializer.kt \
        services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/HankkeenHakemusResponseDeserializer.kt
git commit -m "HAI-3618 Migrate JacksonTestExtension and its custom deserializers to Jackson 3"
```

---

## Task 8: Fix `createObjectMapper()`'s missing GeoJSON `LngLatAlt` serializer/deserializer

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Utils.kt`

**Interfaces:**
- Consumes: `fi.hel.haitaton.hanke.configuration.LngLatAltJackson3Serializer`,
  `fi.hel.haitaton.hanke.configuration.LngLatAltJackson3Deserializer` (already-merged, working
  classes from Phase 1 of the earlier Jackson 3 migration — see `HypersistenceJsonSerializer.kt`),
  `tools.jackson.databind.module.SimpleModule`, `org.geojson.LngLatAlt`.
- Produces: `createObjectMapper()` now serializes/deserializes GeoJSON coordinates in the same
  flat-array format as every other Jackson 3 mapper in the app (`Configuration.kt`'s
  `geoJsonJsonMapperBuilderCustomizer` bean, `HypersistenceJsonSerializer.kt`'s JSON-column mapper).
  `OBJECT_MAPPER`, `TEST_OBJECT_MAPPER` (built from it in Task 7), and the internal mappers built
  inside `HakemusResponseDeserializer`/`HankkeenHakemusResponseDeserializer` (also built from
  `createObjectMapper()`, Task 7) all inherit this fix automatically — no other file needs to
  change.

Context: discovered during Task 7's review, not anticipated when this plan (or the original Task 2)
was written. `createObjectMapper()` (Task 2) only added `kotlinModule()` — it never got the same
`LngLatAlt` fix that `Configuration.kt` and `HypersistenceJsonSerializer.kt` already apply to their
own Jackson 3 mappers, because at the time Task 2 was planned, nobody had traced whether
`OBJECT_MAPPER` itself ever handles GeoJSON. It does — directly (`GeometriatDao.kt`, Task 3, reads
and writes real GeoJSON `Feature`/`Polygon` data from/to Postgres) and indirectly (test fixtures via
`GeometriaFactory`, `HakemusFactory`, etc., all read/written through `OBJECT_MAPPER`-backed helpers).

The controller independently reproduced this before adding this task: a `Polygon` with `LngLatAlt`
coordinates, round-tripped through `OBJECT_MAPPER` as it stands, serializes coordinates as bean
objects (`{"additionalElements":[],"altitude":"NaN","latitude":2.0,"longitude":1.0}`) instead of the
correct GeoJSON position array (`[1.0,2.0]`) — the exact corruption already documented in
`HypersistenceJsonSerializer.kt`'s class comment ("Postgres then rejects it: 'coordinates in GeoJSON
are not sufficiently nested'"). This is a real, production-reachable defect via `GeometriatDao.kt`,
not just a test-fixture inconvenience. The fix itself was also independently verified by the
controller via a scratch test before writing this task: adding the same `SimpleModule` used in
`Configuration.kt` to `createObjectMapper()` produces the correct flat-array format.

- [ ] **Step 1: Add the `LngLatAlt` module to `createObjectMapper()`**

In `Utils.kt`, add the import:

```kotlin
import fi.hel.haitaton.hanke.configuration.LngLatAltJackson3Deserializer
import fi.hel.haitaton.hanke.configuration.LngLatAltJackson3Serializer
import org.geojson.LngLatAlt
import tools.jackson.databind.module.SimpleModule
```

and replace:

```kotlin
fun createObjectMapper(): JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
```

with:

```kotlin
fun createObjectMapper(): JsonMapper =
    JsonMapper.builder()
        .addModule(kotlinModule())
        .addModule(
            SimpleModule()
                .addSerializer(LngLatAlt::class.java, LngLatAltJackson3Serializer())
                .addDeserializer(LngLatAlt::class.java, LngLatAltJackson3Deserializer())
        )
        .build()
```

(This is the same `SimpleModule` construction already used in `Configuration.kt`'s
`geoJsonJsonMapperBuilderCustomizer` — verified via scratch test during plan-writing that it
produces the correct flat-array GeoJSON format when applied here too.)

- [ ] **Step 2: Verify the fix directly**

Add a temporary test (or run one interactively and discard it — your choice, as long as you verify
before moving on) confirming a `Polygon`/`LngLatAlt` round-trips through `OBJECT_MAPPER` with the
correct array shape, e.g.:

```kotlin
val polygon = org.geojson.Polygon(listOf(org.geojson.LngLatAlt(1.0, 2.0), org.geojson.LngLatAlt(3.0, 4.0), org.geojson.LngLatAlt(5.0, 6.0), org.geojson.LngLatAlt(1.0, 2.0)))
val json = OBJECT_MAPPER.writeValueAsString(polygon)
// Expect: {"type":"Polygon","coordinates":[[[1.0,2.0],[3.0,4.0],[5.0,6.0],[1.0,2.0]]]}
// NOT: {"type":"Polygon","coordinates":[[{"longitude":1.0,"latitude":2.0,...}, ...]]}
```

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew :services:hanke-service:test`
Expected: PASS. This confirms the fix doesn't regress anything already covered by unit tests
(Tasks 2, 6, 7, 8's own unit tests among them).

- [ ] **Step 4: Re-verify Task 3's and Task 7's affected integration tests**

This fix specifically unblocks tests that were failing due to this exact bug — confirm it actually
does, don't just assume:

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.geometria.GeometriatDaoITest"`
Expected: PASS (Task 3 already reported this passing, but that was before this bug was known to
exist — re-confirm now that real Docker/Postgres execution with correctly-formatted GeoJSON works
end-to-end, not just that no Jackson-2-type-error exists).

Run: `./gradlew :services:hanke-service:integrationTest --tests "fi.hel.haitaton.hanke.hakemus.HakemusControllerITest"`
Expected: previously 60/98 failed (Task 7's report), with 58 of those attributed specifically to
this bug. After this fix, expect that count to drop substantially — if failures remain, check
whether they're the two other, already-identified pre-existing issues Task 7's report flagged (a
stale `ObjectNode` import in `HakemusControllerITest.kt` itself, and one JSON field-ordering
assertion) rather than a sign this fix didn't work. Both of those are already in scope for Task 9
(the `ObjectNode` import) or explicitly out of scope (the field-ordering issue, noted as a
pre-existing Jackson 2→3 difference unrelated to any task in this plan).

- [ ] **Step 5: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Utils.kt
git commit -m "HAI-3618 Add missing LngLatAlt GeoJSON serializer/deserializer to createObjectMapper()"
```

---

## Task 9: Migrate `test/Asserts.kt` and `test/AuditLogEntryEntityAsserts.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/test/Asserts.kt`
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/test/AuditLogEntryEntityAsserts.kt`

**Interfaces:**
- Consumes: `tools.jackson.databind.JsonNode`, `tools.jackson.databind.node.NullNode`,
  `tools.jackson.databind.node.TextNode`, `tools.jackson.databind.node.ObjectNode` — all used only
  as plain type references (`Assert<JsonNode>`, `hasClass(NullNode::class)`,
  `isInstanceOf(TextNode::class)`, a cast to `ObjectNode`), not through any generic-inference-heavy
  API call, so this should be a straightforward import swap. Depends on Task 6's `parseJson()` (used
  at `AuditLogEntryEntityAsserts.kt:68,85`) already being Jackson 3.
- Produces: no new interface — these are test assertion helpers, signatures unchanged.

- [ ] **Step 1: Update `Asserts.kt`'s imports**

Replace:

```kotlin
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
```

with:

```kotlin
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.TextNode
```

No other change expected — `JsonNode.get(path)` (line 117), `hasClass(NullNode::class)` (line 111),
and `isInstanceOf(TextNode::class).transform { node: JsonNode -> node.textValue() }` (line 113-114)
all operate on plain type references. Note: `node.textValue()` at line 114 is the same deprecated
API as Task 5/7 — replace with `node.asString()` while you're here, matching the established
pattern.

- [ ] **Step 2: Update `AuditLogEntryEntityAsserts.kt`'s import**

Replace:

```kotlin
import com.fasterxml.jackson.databind.node.ObjectNode
```

with:

```kotlin
import tools.jackson.databind.node.ObjectNode
```

The call site `OBJECT_MAPPER.readTree(it) as ObjectNode` (line 74) needs no other change — `readTree`
already returns a Jackson 3 `JsonNode` (from Task 2), and the cast target now matches.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :services:hanke-service:compileTestKotlin`
Expected: no errors mentioning either file. (Full green depends on Tasks 6-7 and 9 also being
done — check only that these two files' own errors are gone.)

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/test/Asserts.kt \
        services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/test/AuditLogEntryEntityAsserts.kt
git commit -m "HAI-3618 Migrate test assertion helpers off Jackson 2"
```

---

## Task 10: Migrate the remaining stray Jackson 2 imports off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/CustomerRequestDeserializeTest.kt`
- Modify: `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusControllerITest.kt`

**Interfaces:**
- Consumes: `tools.jackson.databind.node.ObjectNode`. Both files' call sites use an explicit `val x:
  Type = ...` declaration (e.g. `val json: ObjectNode = OBJECT_MAPPER.valueToTree(customer)`) — the
  same pattern verified safe in Tasks 2 and 5's scratch tests, not the property-setter-inside-`apply`
  pattern that needed a helper in Task 3.
- Produces: no new interface — these are test-only call sites.

This is the last of the newly-discovered test-source cluster (found during Task 5's review) — after
this task, a full `grep -rln "com\.fasterxml\.jackson" --include="*.kt" services/hanke-service/src/`
should show only files using the shared, unaffected `com.fasterxml.jackson.annotation.*` package
(safe, unchanged between Jackson 2 and 3 — not part of this migration).

**`ProfiiliClientITest.kt` update:** originally in scope for this task (a `readValue` import swap),
but Task 7's implementer already fixed it in commit `61444538` — it fully blocked
`compileIntegrationTestKotlin` from running at all, so fixing it couldn't wait for this task. Verify
it's already done (`grep -n "^import.*jackson" services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/profiili/ProfiiliClientITest.kt`
should show `tools.jackson.module.kotlin.readValue`, not `com.fasterxml.jackson.*`) rather than
re-doing it.

- [ ] **Step 1: Update `CustomerRequestDeserializeTest.kt`**

Replace `import com.fasterxml.jackson.databind.node.ObjectNode` with
`import tools.jackson.databind.node.ObjectNode`. Leave `import
com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException` as-is for now — check whether this
exception type is actually thrown by Jackson 3 too (it's likely from a still-unmigrated deserializer
test path); if a test in this file asserts on catching this specific exception class and it's
actually thrown by Jackson-3-side code by the time this task runs, note it in your report rather
than silently leaving a mismatched exception type.

- [ ] **Step 2: Update `HakemusControllerITest.kt`**

Replace `import com.fasterxml.jackson.databind.node.ObjectNode` with
`import tools.jackson.databind.node.ObjectNode`. Call sites at the lines found during plan-writing
(`val json: ObjectNode = OBJECT_MAPPER.valueToTree(customer)`, appearing multiple times) need no
other change. Task 7's report specifically flagged this file's stale import as the cause of a
`ClassCastException` in one test ("returns 200 and the created hakemus when the hakemus is a
kaivuilmoitus") — confirm that specific test passes after this fix.

- [ ] **Step 3: Confirm `ProfiiliClientITest.kt` needs no further change**

Per the note above — just verify, don't re-edit.

- [ ] **Step 4: Verify compilation and confirm the codebase-wide grep is clean**

Run: `./gradlew :services:hanke-service:compileTestKotlin :services:hanke-service:compileIntegrationTestKotlin`
Expected: PASS with zero errors, assuming Tasks 6-9 are also done — this is the first point in the
plan where the whole module should compile cleanly again end-to-end.

Run: `grep -rln "com\.fasterxml\.jackson\.databind\|com\.fasterxml\.jackson\.module\.kotlin\|com\.fasterxml\.jackson\.core\.Json\|com\.fasterxml\.jackson\.datatype" --include="*.kt" services/hanke-service/src/`
Expected: no output (the only remaining `com.fasterxml.jackson` references anywhere should be
`com.fasterxml.jackson.annotation.*`, which this migration deliberately leaves alone).

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew :services:hanke-service:test :services:hanke-service:integrationTest`
Expected: PASS. This is the real checkpoint before Task 14 (the global flip) — confirms the entire
codebase, main and test sources alike, is Jackson-3-only for everything except the Spring MVC
config flags themselves. With Task 8's `LngLatAlt` fix and this task both done, `HakemusControllerITest`
should now be fully green (or close to it — re-check Task 8's Step 4 notes on the one remaining
pre-existing, unrelated field-ordering issue if anything still fails here).

- [ ] **Step 6: Commit**

```bash
git add services/hanke-service/src/test/kotlin/fi/hel/haitaton/hanke/hakemus/CustomerRequestDeserializeTest.kt \
        services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusControllerITest.kt
git commit -m "HAI-3618 Migrate remaining stray Jackson 2 imports to Jackson 3"
```

---

## Task 11: Migrate `Extensions.kt` off Jackson 2

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

## Task 12: Migrate `AccessRules.kt` off Jackson 2

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/security/AccessRules.kt:44`
- Test: any existing controller integration test asserting the 401 body shape (e.g.
  `services/hanke-service/src/integrationTest/kotlin/fi/hel/haitaton/hanke/HankeControllerITests.kt`,
  which references `HAI0001` per the codebase search done while writing this plan).

**Interfaces:**
- Consumes: `OBJECT_MAPPER.writeValueAsString(Any?)` — verified in Task 11.
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

## Task 13: Migrate `HypersistenceJsonSerializer.kt`'s `OBJECT_MAPPER` fallback off Jackson 2

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
Expected: PASS (0 failures). This is the checkpoint before Task 14 — `OBJECT_MAPPER`, all six of its
original main-source call sites, and the Task 6-9 test-source cluster are now Jackson 3, with the
Spring config flags still unchanged (still Jackson 2 for MVC), so this confirms Phase 3b is fully
done and safe before touching the global default.

- [ ] **Step 4: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/configuration/HypersistenceJsonSerializer.kt
git commit -m "HAI-3618 Confirm HypersistenceJsonSerializer's convertValue fallback works on Jackson 3 OBJECT_MAPPER"
```

---

## Task 14: Fix `createObjectMapper()`'s missing `MapperFeature.DEFAULT_VIEW_INCLUSION`

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Utils.kt`

**Interfaces:**
- Consumes: `tools.jackson.databind.MapperFeature`.
- Produces: `createObjectMapper()` now includes `@JsonView`-unannotated properties in every view,
  matching Jackson 2's default. `OBJECT_MAPPER` and everything built from `createObjectMapper()`
  (Task 7's `TEST_OBJECT_MAPPER`, the internal mappers inside
  `HakemusResponseDeserializer`/`HankkeenHakemusResponseDeserializer`) inherit this fix
  automatically.

Context: discovered while investigating `HankeKayttajaLoggingServiceTest`'s failure (initially
mis-triaged as the primitive-null-strictness gap below — it is not). Jackson 3 defaults
`MapperFeature.DEFAULT_VIEW_INCLUSION` to disabled (opt-in views); Jackson 2 defaulted it to
enabled (opt-out views). The codebase's `ChangeLogView`/`NotInChangeLogView` pattern
(`toChangeLogJsonString()`, `Extensions.kt`) was built entirely around Jackson 2's opt-out
semantics: only fields that should be *excluded* from the audit log are annotated
(`@JsonView(NotInChangeLogView::class)`); everything else was assumed included by default.

This is a real, production-reachable defect: a read-only audit confirmed `HankeKayttaja` (the
domain class logged for `ObjectType.HANKE_KAYTTAJA` — none of its 11 fields have `@JsonView`) and
`Yhteyshenkilo` (nested inside `HankeYhteystieto.yhteyshenkilot`, also fully unannotated) both
serialize to effectively empty audit-log entries under Jackson 3 as it stood, silently dropping
name/email/phone/role/permission/PII from the audit trail. `Geometriat.featureCollection`
(third-party `org.geojson.FeatureCollection`, no view annotations on its own internals) loses its
actual coordinates from geometry audit entries the same way. Flagged to the user given the
severity (silent audit-trail data loss), who approved restoring the mapper default globally.

Separately flagged, NOT covered by this task: `HakemusData`/`JohtoselvityshakemusData`/
`KaivuilmoitusData` (the actual logged types for Hakemus/Muutosilmoitus/Taydennys) have zero
`@JsonView` annotations anywhere. This task's fix restores their prior (correct) behavior too since
it's a mapper-level default, but their apparent total reliance on default-inclusion was not audited
in depth — worth a follow-up look if audit-log correctness for Hakemus entries is ever in question.

- [x] **Step 1: Enable `MapperFeature.DEFAULT_VIEW_INCLUSION` on `createObjectMapper()`**

In `Utils.kt`, add the import `tools.jackson.databind.MapperFeature` and add
`.enable(MapperFeature.DEFAULT_VIEW_INCLUSION)` to the builder chain in `createObjectMapper()`
(before `.build()`).

- [x] **Step 2: Verify the correct Jackson 3 builder API**

Confirmed via Context7 docs (`jackson-databind` 3.x) that `JsonMapper.builder().enable(MapperFeature
f)` is the correct signature, and independently via a temporary scratch test (written, run, deleted
— never committed) demonstrating the default-inclusion behavior flips as described above.

- [x] **Step 3: Compile and run the targeted failing test**

Ran `./gradlew :services:hanke-service:compileKotlin :services:hanke-service:compileTestKotlin` —
BUILD SUCCESSFUL. Ran
`./gradlew :services:hanke-service:test --tests "fi.hel.haitaton.hanke.logging.*LoggingServiceTest"`
— all pass, including the previously-failing `HankeKayttajaLoggingServiceTest`.

- [x] **Step 4: Run the full unit test suite, confirm no regressions**

Ran `./gradlew :services:hanke-service:test` — 1283 tests, failures dropped from 5 to 4.
`HankeKayttajaLoggingServiceTest` no longer fails; no new failures introduced. The 4 remaining
failures are the pre-existing `HankeErrorTest` field-ordering issue and the two primitive-null-
strictness cases covered by Task 15 below — unrelated to this fix.

- [ ] **Step 5: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/Utils.kt
git commit -m "HAI-3618 Restore MapperFeature.DEFAULT_VIEW_INCLUSION in createObjectMapper()"
```

---

## Task 15: Fix Jackson 3's stricter null/absent-primitive handling in affected DTOs

**Files:**
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusUpdateRequest.kt`
- Modify: `services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/geometria/Geometriat.kt`

**Interfaces:**
- Consumes: `com.fasterxml.jackson.annotation.JsonSetter`, `com.fasterxml.jackson.annotation.Nulls`
  (unchanged annotation package, shared between Jackson 2/3).
- Produces: no signature changes — `CustomerRequest.registryKeyHidden`,
  `InvoicingCustomerRequest.registryKeyHidden`, and `Geometriat.version` deserialize the same way
  they did under Jackson 2.

Context: Jackson 3's Kotlin module (`tools.jackson.module.kotlin`) is stricter than Jackson 2's
about primitive constructor parameters. Two distinct shapes of this gap were confirmed via scratch
tests (written, run, deleted — never committed):
- Explicit JSON `null` into a primitive **with** a Kotlin default (e.g.
  `val registryKeyHidden: Boolean = false`): Jackson 2 silently used the default; Jackson 3 throws
  `MismatchedInputException: Cannot map 'null' into type 'boolean'`.
- A JSON key entirely **absent** for a primitive **without** a Kotlin default (e.g.
  `Geometriat.version: Int`, no `=`): Jackson 2 silently fell back to the JVM zero-value; Jackson 3
  throws `MismatchedInputException: Missing required creator property 'version'`.

User decided: treat Jackson 3's stricter behavior as correct and fix the affected DTOs/fields
directly, rather than loosening `createObjectMapper()`'s global config to match Jackson 2's old
silent-defaulting behavior (unlike Task 14, which is a legitimate global mapper-config fix — this
is per-field validation strictness, not a broken default).

- [ ] **Step 1: Fix `CustomerRequest.registryKeyHidden` and `InvoicingCustomerRequest.registryKeyHidden`**

In `HakemusUpdateRequest.kt`, both properties already carry the doc comment "Value is false when
read from JSON with null or empty value" — add `@JsonSetter(nulls = Nulls.AS_EMPTY)` to make that
documented behavior actually hold under Jackson 3. Verified via scratch test: this annotation
resolves both the explicit-null and absent-key cases for a `Boolean` primitive, matching Jackson
2's old silent-default behavior exactly.

- [ ] **Step 2: Fix `Geometriat.version`**

In `Geometriat.kt`, `version: Int` has no Kotlin default and is documented "set by the service" —
give it an explicit `= 0` default so an absent JSON key (as in the `hankeGeometriat-delete.json`
test fixture) deserializes the same way it did under Jackson 2.

- [ ] **Step 3: Run the previously-failing tests**

Run:
`./gradlew :services:hanke-service:test --tests "fi.hel.haitaton.hanke.hakemus.CustomerRequestDeserializeTest" --tests "fi.hel.haitaton.hanke.geometria.GeometriatServiceTest"`
Expected: `RegistryKeyHidden > is false when null in JSON` and
`save Geometriat OK - without features (delete)` both pass. `RegistryKeyHidden > throws exception
when value is nonsense in JSON` is a separate, unrelated pre-existing bug (flagged by Task 10's
review) — do not expect this fix to resolve it, and do not fold a fix for it into this task without
flagging it first.

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew :services:hanke-service:test`
Expected: only the known pre-existing `HankeErrorTest`/field-ordering failures remain (and the
unrelated `RegistryKeyHidden` "nonsense" test above, if still unaddressed).

- [ ] **Step 5: Commit**

```bash
git add services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/hakemus/HakemusUpdateRequest.kt \
        services/hanke-service/src/main/kotlin/fi/hel/haitaton/hanke/geometria/Geometriat.kt
git commit -m "HAI-3618 Fix Jackson 3's stricter null/absent-primitive handling in affected DTOs"
```

---

## Task 16: Flip the global Jackson default (Phase 3c)

**Files:**
- Modify: `services/hanke-service/src/main/resources/application.yml`
- Modify: `services/hanke-service/build.gradle.kts`

**Interfaces:**
- Consumes: nothing new — this task removes configuration, it doesn't add code.
- Produces: nothing new — Spring MVC's `@ResponseBody`/`@RequestBody` handling now uses Boot's
  auto-configured Jackson 3 `JsonMapper` bean instead of the Jackson 2 compat path. No caller-facing
  interface changes; this is the highest-risk task in the plan precisely because its correctness
  depends on everything verified in Tasks 1-13, not on any new code of its own.

This is the single highest-risk change in the whole migration, per the design doc. Do not start
this task until Tasks 1-13 are all merged and green.

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
consider that reverting this one task's commit restores the known-working state from Task 13.

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

## Task 17: Cleanup & documentation (Phase 4)

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

- **Spec coverage:** Phase 3a → Task 1. Phase 3b → Tasks 2-5 and 11-13 (one per file, matching the
  design doc's six original call sites plus the foundational `Utils.kt` change) plus Tasks 6-7, 9-10
  (the test-source cluster added mid-execution — see below) and Task 8 (the `createObjectMapper()`
  GeoJSON fix, also added mid-execution). Phase 3c → Task 16. Phase 4 → Task 17. All design doc
  sections have a corresponding task. Tasks 14-15 (the `DEFAULT_VIEW_INCLUSION` fix and the
  primitive-null-strictness DTO fixes) were added mid-execution, discovered during post-Task-13
  investigation — not part of the original design doc's scope, but blocking Task 16 (the global
  flip) per the plan's own Global Constraints.
- **Corrections found and folded in while writing this plan** (both already applied to the design
  doc, commit `9d8cdab3`): the two "known gaps" don't reproduce against current code (verified via
  scratch tests, not assumed) — Task 1 is verify-and-lock-in rather than open-ended debugging.
  Jackson 2's `jackson-databind` stays on the classpath transitively via `logstash-logback-encoder`
  and `geojson-jackson` regardless of this plan — Task 14's goal statement and Step 5 reflect that
  honestly rather than claiming full classpath removal.
- **Scope corrections found during execution, not during planning** (unlike the two corrections
  above):
  - Task 5's review surfaced a whole cluster of test-source Jackson 2 usage — `TestExtensions.kt`,
    `JacksonTestExtension.kt`, four custom `JsonDeserializer` classes it registers, two assertion
    helper files, and three files with stray imports — that the original plan-writing file scan
    missed entirely, because that scan only covered `src/main/kotlin`. Tasks 6-7 and 9-10 were
    inserted to cover this cluster, renumbering the original Tasks 6-10 to 11-15. Flagged to the
    user before the new tasks were added, who approved expanding the plan in place.
  - Task 7's review surfaced a second, unrelated gap: `createObjectMapper()` (Task 2, already
    merged) never got the GeoJSON `LngLatAlt` serializer/deserializer that `Configuration.kt` and
    `HypersistenceJsonSerializer.kt` already register on their own Jackson 3 mappers. The
    controller independently reproduced real coordinate corruption (bean objects instead of GeoJSON
    arrays) before adding Task 8 to fix it, since this affects an already-merged, already-reviewed
    task and has production-reachable impact via `GeometriatDao.kt` (Task 3), not just tests.
    Flagged to the user, who approved fixing it immediately as a new task before continuing.
  - Both are real gaps in the original plan's completeness, not text-level mistakes like the two
    corrections above — the plan's file-discovery process (Task 1's scan, this task's design)
    simply didn't reach these two areas until execution surfaced them.
  - Post-Task-13 investigation (into 3 test failures left unresolved pending user review) surfaced
    two more, distinct gaps. First, `HankeKayttajaLoggingServiceTest`'s failure was initially
    mis-triaged (Task 10's report) as the same primitive-null-strictness issue as the other two —
    it is not. Independent re-investigation found the real cause: Jackson 3 defaults
    `MapperFeature.DEFAULT_VIEW_INCLUSION` to disabled, breaking the `ChangeLogView`/
    `NotInChangeLogView` audit-logging pattern's reliance on Jackson 2's opt-out default. A
    read-only audit confirmed this silently drops PII/audit-relevant fields (`HankeKayttaja`,
    `Yhteyshenkilo`, `Geometriat.featureCollection`) from real audit-log entries — flagged to the
    user given the severity, who approved restoring the mapper default globally (Task 14). Second,
    the actual primitive-null-strictness gap (affecting `CustomerRequestDeserializeTest` and
    `GeometriatServiceTest`) was confirmed real and scoped into Task 15, per the user's explicit
    decision to fix the affected DTOs rather than loosen the global mapper config for that case.
- **Type consistency:** `createObjectMapper(): JsonMapper` (Task 2) is consumed identically by name
  in Tasks 3, 5, 6, 10, 12, 13's import-check steps and Task 4/11's no-op-if-nothing-found steps —
  no signature drift between tasks. Task 8's fix to `createObjectMapper()` is inherited automatically
  by every task that already depends on it (`OBJECT_MAPPER`, Task 7's `TEST_OBJECT_MAPPER`, the
  internal mappers built inside `HakemusResponseDeserializer`/`HankkeenHakemusResponseDeserializer`)
  without any of those tasks needing to change. Task 7's `ValueDeserializer`/`JsonMapper.rebuild()`
  pattern matches the already-merged `LngLatAltJackson3Deserializer` reference implementation named
  in that task — the same reference implementation Task 8 reuses directly.
- **No placeholders:** every code-bearing step has real, verified code (each Jackson 3 API call —
  `writeValueAsString`, `writerWithView`, reified `readValue`, `readTree`, `valueToTree`,
  `convertValue`, `readValueAsTree`, `treeToValue`, `addAbstractTypeMapping`,
  `rebuild()`/`disable()`/`addModule()`/`build()` — was checked against the actual
  `tools.jackson.module:jackson-module-kotlin:3.1.5` dependency via scratch tests during
  plan-writing or mid-execution, not assumed from Jackson 2 familiarity). Task 7's Step 4
  (`JacksonTestExtension`'s `OBJECT_MAPPER` mutation) is the one exception flagged explicitly as
  needing the implementer's own investigation and judgment rather than a pre-verified snippet — the
  right fix depends on how widely a rebuilt mapper needs to be shared, which isn't knowable from
  static reading alone.
