package fi.hel.haitaton.hanke.test

import com.fasterxml.jackson.databind.module.SimpleModule
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
        OBJECT_MAPPER.registerModule(module)
    }

    companion object {
        private var started = false
    }
}
