# Jackson 2 Full Retirement — Status Memo (paused 2026-08-13)

**Ticket:** HAI-3618
**Branch:** `HAI-3618/jackson3-full-retirement` (worktree: `.worktrees/HAI-3618-jackson3-full-retirement`)
**Plan:** `docs/superpowers/plans/2026-08-11-jackson3-full-retirement.md`

Work is paused mid-Task 16 (of 17). This memo is the handoff point — read this before resuming.

## TL;DR

Tasks 1-15 are done, committed, and verified — unit suite fully green (1283/1283), integration
suite green aside from Task 16's own blocker (see below). Task 16 (flipping Spring's Jackson
default from 2 to 3 — the plan's own highest-risk step) is **blocked**: its config/dependency
changes are done and compile/unit-test clean, but a real, Docker-backed `integrationTest` run
surfaces 66 failures that couldn't be reproduced or root-caused without a live Postgres. Task 16's
remaining steps (3-6) and Task 17 (cleanup/docs) have not started.

## Done (Tasks 1-15, all committed)

All committed to `HAI-3618/jackson3-full-retirement`, unit-test-verified, and — for Tasks 1-13 —
integration-test-verified on a real Docker-capable machine (not this sandbox, which cannot reach
Docker at all — every `integrationTest` attempt here fails with `Could not connect to Ryuk`).

1. **Tasks 1-13**: the original plan's migration of `OBJECT_MAPPER`/`createObjectMapper()` and its
   six main-source call sites (`GeometriatDao`, `AuditLogService`, `GdprController`, `Extensions`,
   `AccessRules`, `HypersistenceJsonSerializer`'s fallback path), plus the test-source cluster
   discovered mid-execution (`TestExtensions`, `JacksonTestExtension` + 4 deserializers, assertion
   helpers, stray imports), plus the `createObjectMapper()` GeoJSON `LngLatAlt` fix. Full detail is
   in the plan doc's own Self-Review Notes.
2. **Task 14** (added mid-execution): restored `MapperFeature.DEFAULT_VIEW_INCLUSION` in
   `createObjectMapper()`. Jackson 3 defaults this to disabled; the codebase's
   `ChangeLogView`/`NotInChangeLogView` audit-logging pattern (`toChangeLogJsonString()`,
   `Extensions.kt`) assumes Jackson 2's enabled default. Without this fix, `HankeKayttaja` and
   `Yhteyshenkilo` (both fully unannotated) serialized to effectively empty audit-log entries —
   real silent PII/audit-trail data loss, not a cosmetic issue.
3. **Task 15** (added mid-execution): fixed Jackson 3's stricter null/absent-primitive handling.
   `CustomerRequest.registryKeyHidden`, `InvoicingCustomerRequest.registryKeyHidden`, and
   `CreateKaivuilmoitusRequest`/`KaivuilmoitusUpdateRequest.requiredCompetence` (the last two found
   later, via `/code-review`, initially missed) got `@JsonSetter(nulls = Nulls.AS_EMPTY)`;
   `Geometriat.version` got an explicit `= 0` default. Deliberately did **not** use the global
   `KotlinFeature.NullIsSameAsDefault` fix the code review first suggested — verified empirically
   (decompiled `jackson-module-kotlin-3.1.5`/`jackson-databind-3.1.5`, 10-scenario scratch test)
   that it does not actually work for either bug shape; the real global mechanism is Jackson
   **core's** `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES`, which is broader than what was
   wanted (would silently zero-default *any* primitive field app-wide, not just the known ones).
   User chose to keep the per-field fixes over adopting that broader flag.
4. **Fixed test-correctness bugs uncovered along the way** (not production bugs, but worth knowing
   about since they explain otherwise-confusing CI history):
   - `Jackson3FormatDiffITest`'s 3 assertions did raw JSON *string* equality across Jackson 2 vs 3,
     which is stricter than the migration needs to guarantee (JSON member order carries no meaning
     per RFC 8259). Switched to structural (parsed-tree) comparison.
   - Same fix applied to `HankeErrorTest` and 6 controller-ITest assertions comparing
     `HankeErrorDetail.toJsonString()` output — `errorCode`/`errorMessage` field order differs
     between Jackson 2's and Jackson 3's bean introspection for that specific class shape
     (constructor param vs. plain getter).
   - `CustomerRequestDeserializeTest`'s "throws exception when value is nonsense in JSON" test had
     a **real, 2-year-old latent bug** (since PR #821, 2024): `assertFailure { jsonString.parseJson() }`
     reifies its generic type as `kotlin.Unit` (confirmed via decompiled bytecode —
     `TypeReference<kotlin.Unit>`), not `CustomerRequest`, because the lambda's return value is
     unused. Jackson 2 happened to throw `UnrecognizedPropertyException` when deserializing any
     multi-key JSON into `Unit` (every key looks unrecognized to a zero-property type) — which
     accidentally matched the test's assertion, for the wrong reason. Jackson 3 doesn't throw for
     that case, which is what unmasked the bug. Fixed by typing the call explicitly and asserting
     the exception a bad boolean value actually produces (`InvalidFormatException`).
   - Fixed import ordering broken across 5 files (`Utils.kt`, `GdprController.kt`, `Asserts.kt`,
     `AuditLogEntryEntityAsserts.kt`, `TestExtensions.kt`) — would fail `spotlessCheck` once that
     can run for real (see Known environment issues below).
5. **Result**: as of the last full run before Task 16, unit suite was **1283 tests, 0 failures**,
   and a real-Docker `integrationTest` run showed no failures beyond what Task 16 itself later
   introduced (see below).

## Blocked: Task 16, Step 3 onward

**What's done:** Step 1 (removed `spring.http.converters.preferred-json-mapper: jackson2` and
`spring.jackson.use-jackson2-defaults: true` from `application.yml`) and Step 2 (removed
`spring-boot-jackson2`, `com.fasterxml.jackson.core:jackson-databind`,
`com.fasterxml.jackson.module:jackson-module-kotlin` from `build.gradle.kts`) are done, committed,
and individually verified safe:
- Step 1 alone (before touching dependencies) was confirmed by the user not to break date
  formatting (`Jackson3FormatDiffITest` still passed) — the specific regression this step was most
  at risk of causing.
- `compileKotlin`/`compileTestKotlin`/`compileIntegrationTestKotlin` all succeed.
- Full unit suite (`:test`) still passes, 0 failures.
- Step 5 (classpath verification) confirmed `jackson-databind:2.21.5` remains transitively present
  (via `geojson-jackson` and various Spring Boot starters, pinned by the retained
  `ext["jackson-2-bom.version"]` override) and `spring-boot-jackson2` no longer appears anywhere.

**What's blocking:** Step 3's `integrationTest` run (done by the user on their own machine — this
sandbox cannot reach Docker at all) surfaced **66 failures**. Clustering the failure list:

1. **~50+ failures, one likely shared root cause**: `LngLatAltJackson3Deserializer` throwing
   `InputCoercionException` ("Current token (PROPERTY_NAME/START_ARRAY) not numeric") when
   deserializing geometry (`Polygon.coordinates`) nested inside real controller
   request/response bodies (`HakemusControllerITest`, `MuutosilmoitusControllerITest`,
   `TaydennysControllerITest`, `PublicHankeControllerITests`), plus a large secondary cluster
   where almost every "returns 400 for a specific validation error" test instead gets a generic
   `HAI0003` ("Invalid data") — consistent with the *incoming* request body's geometry failing to
   deserialize before validation logic ever runs, and a smaller cluster of MockK
   `verifySequence` failures that are themselves consistent with the same upstream failure (the
   authorizer mock never gets called because deserialization already failed). A few
   `JSONAssert`-style "could not find match" failures on coordinate arrays are plausibly the same
   root cause manifesting as a value mismatch instead of an exception.
2. **1 already-known, expected recurrence**: `HankeControllerITests > UpdateHanke > returns 400
   with validation errors when update fails validation` — the same `errorCode`/`errorMessage`
   field-ordering difference already documented and deliberately left out of scope throughout this
   plan (first noted in Task 7's report). Not new, just newly exposed because this specific ITest
   now exercises the real Jackson 3 stack for the first time.
3. **1-2 more `HankeControllerITests` failures** likely explained by cluster 1 (request-body
   geometry deserialization) too, not independently investigated.

**Investigation done (all inconclusive — the bug could not be reproduced):** built a
Docker-free, no-Spring-context repro harness and tested every Jackson mapper configuration in the
app in isolation, using the real class hierarchy (`KaivuilmoitusDataResponse` → `KaivuilmoitusAlue`
→ `Tyoalue` → `Polygon` → `LngLatAlt`) and real test factories (`HakemusResponseFactory`):
- `createObjectMapper()`/`OBJECT_MAPPER` directly: serializes and round-trips clean.
- The Spring Boot-autoconfigured `jsonMapper` bean, rebuilt faithfully by decompiling
  `spring-boot-jackson-4.1.0.jar`'s `JacksonAutoConfiguration` to confirm it really does call
  `MapperBuilder.findAndAddModules()` (SPI-based, picks up `jackson-module-kotlin`'s registered
  `KotlinModule` via `META-INF/services/tools.jackson.databind.JacksonModule`) plus our own
  `geoJsonJsonMapperBuilderCustomizer`: produced **byte-identical JSON** to `OBJECT_MAPPER` for the
  same object, and round-tripped clean through the actual `HakemusResponseDeserializer` test-helper
  logic.
- The hypersistence-utils JSONB-column mapper (`GeoJsonAwareObjectMapperSupplier`, Phase 1 code,
  untouched by this plan): also round-tripped clean.

Since every mapper configuration checks out clean with hand-built in-memory data, the corruption
most likely depends on something only present with a **live Postgres round-trip** (actual
persisted/reloaded entity data, Hibernate-managed object graphs, or some interaction only visible
under the full Spring context) — none of which this sandbox can exercise (no Docker access at
all; every `integrationTest` attempt fails immediately with `Could not connect to Ryuk`).

**Next step, if resuming:** get real data from a Docker-capable machine — either the actual raw
JSON response body from one failing request (e.g. add a temporary `println(response)` right before
the assertion in `HakemusControllerITest.kt`'s `Create > returns 200 and the created hakemus` test,
around line 1731, and share the output), or run the app locally against real Postgres (per Task
16's own Step 4, "manual smoke test") and hit the failing endpoint directly via curl. Do **not**
guess further at a fix without that data — this is exactly the scenario the plan itself flags as
highest-risk and calls for `superpowers:systematic-debugging`, not speculation.

**If Step 3 does eventually pass:** Step 4 (manual smoke test) and Step 6 (commit) are still
outstanding, and Task 17 (cleanup/docs) hasn't started at all.

## Known environment issues (not blockers, just friction — already understood)

- **This sandbox cannot reach Docker at all.** Every `integrationTest`/Testcontainers attempt here
  fails with `Could not connect to Ryuk at localhost:PORT`. All real integration-test verification
  this session came from the user running commands on their own machine.
- **`spotlessCheck`/`spotlessApply` cannot run from this git worktree.** JGit doesn't resolve the
  worktree's `.git` file (a pointer, not a directory) the way the plain `git` CLI does. Exclude
  with `-x spotlessCheck -x spotlessApply` when running `build`/`check` from this worktree; run
  spotless from the main repo checkout instead if you need it for real.
- **`installGitHook` also fails from this worktree**, for the same `.git`-file-vs-directory reason
  (`Copy` task can't write into `.git/hooks`). Exclude with `-x installGitHook`.
- So: the practical incantation for a real `build`/`check` from this worktree is
  `./gradlew clean build -x spotlessCheck -x spotlessApply -x installGitHook`.
- The Bash tool's shell has silently reset its working directory to the main repo checkout (not
  this worktree) at least twice this session, apparently after some background/subshell commands
  complete. Always `pwd`/`git branch --show-current` before trusting the working directory,
  especially after a long-running or backgrounded command.

## Files changed in this session (all committed except where noted)

Commits on `HAI-3618/jackson3-full-retirement`, newest first, from this session (see plan doc for
the earlier Tasks 1-13 commit history):
- Task 16 Steps 1-2 (application.yml, build.gradle.kts) — **committed as part of this pause**.
- Import-ordering fix (5 files) — code-review finding.
- `requiredCompetence` null-primitive fix (2 files) — code-review finding.
- `Jackson3FormatDiffITest`/`HankeErrorTest`/6 controller-ITest structural-comparison fixes.
- `CustomerRequestDeserializeTest`'s type-inference bug fix.
- Task 15 (registryKeyHidden ×2, Geometriat.version).
- Task 14 (`MapperFeature.DEFAULT_VIEW_INCLUSION`).
- Plan doc updates throughout, tracking all of the above.

No uncommitted changes remain as of this memo (verify with `git status` before resuming — this
memo itself and the Task 16 partial-progress commit are the last things done).
