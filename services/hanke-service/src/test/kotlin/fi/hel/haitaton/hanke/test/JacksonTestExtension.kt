package fi.hel.haitaton.hanke.test

import com.fasterxml.jackson.databind.module.SimpleModule
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusDeserializer
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponseDeserializer
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
        module.addDeserializer(Hakemus::class.java, HakemusDeserializer())
        module.addDeserializer(HakemusResponse::class.java, HakemusResponseDeserializer())
        OBJECT_MAPPER.registerModule(module)
    }

    companion object {
        private var started = false
    }
}
