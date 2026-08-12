package fi.hel.haitaton.hanke.test

import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusDataDeserializer
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponseDeserializer
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponseDeserializer
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemusResponse
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemusResponseDeserializer
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

/**
 * A [JsonMapper] for test use, augmented with the deserializers [JacksonTestExtension] registers
 * (see its doc comment). Test helpers that read [HakemusResponse], [HakemusDataResponse],
 * [HakemusData] or [HankkeenHakemusResponse] back from JSON - directly or nested inside another
 * response type, e.g. [fi.hel.haitaton.hanke.hakemus.HankkeenHakemuksetResponse] or
 * [fi.hel.haitaton.hanke.taydennys.TaydennysResponse] - should use this instead of [OBJECT_MAPPER].
 *
 * This can't just live on [OBJECT_MAPPER] itself: Jackson 3's [JsonMapper] is immutable
 * (builder-based), and [OBJECT_MAPPER] is a `val`, so there's no way to register the module there
 * in place, nor to swap [OBJECT_MAPPER] itself for a rebuilt instance. Defaults to the plain
 * [OBJECT_MAPPER] until [JacksonTestExtension] has run at least once.
 */
var TEST_OBJECT_MAPPER: JsonMapper = OBJECT_MAPPER
    private set

/**
 * Extension for customizing Jackson for test use. E.g. deserializing abstract classes we don't want
 * to deserialize in production code.
 *
 * Tests that extend [fi.hel.haitaton.hanke.IntegrationTest] or
 * [fi.hel.haitaton.hanke.ControllerTest] have this extension enabled by default. Others can use it
 * by annotating the test class with:
 * ```
 * @ExtendWith(JacksonTestExtension::class)
 * ```
 *
 * The registered deserializers land on [TEST_OBJECT_MAPPER], not [OBJECT_MAPPER] - see its doc
 * comment for why.
 */
class JacksonTestExtension : BeforeAllCallback {

    override fun beforeAll(context: ExtensionContext) {
        if (started) return

        val module = SimpleModule()
        module.addDeserializer(HakemusResponse::class.java, HakemusResponseDeserializer())
        module.addDeserializer(HakemusDataResponse::class.java, HakemusDataResponseDeserializer())
        module.addDeserializer(HakemusData::class.java, HakemusDataDeserializer())
        module.addDeserializer(
            HankkeenHakemusResponse::class.java,
            HankkeenHakemusResponseDeserializer(),
        )
        TEST_OBJECT_MAPPER = OBJECT_MAPPER.rebuild().addModule(module).build()
        started = true
    }

    companion object {
        private var started = false
    }
}
