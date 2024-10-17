package fi.hel.haitaton.hanke.test

import com.fasterxml.jackson.databind.module.SimpleModule
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusDeserializer
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponseDeserializer
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemuksetResponse
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemuksetResponseDeserializer
import fi.hel.haitaton.hanke.taydennys.Taydennys
import fi.hel.haitaton.hanke.taydennys.TaydennysDeserializer
import fi.hel.haitaton.hanke.taydennys.TaydennysResponse
import fi.hel.haitaton.hanke.taydennys.TaydennysResponseDeserializer
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
        module.addDeserializer(Taydennys::class.java, TaydennysDeserializer())
        module.addDeserializer(TaydennysResponse::class.java, TaydennysResponseDeserializer())
        module.addDeserializer(
            HankkeenHakemuksetResponse::class.java, HankkeenHakemuksetResponseDeserializer())
        OBJECT_MAPPER.registerModule(module)
    }

    companion object {
        private var started = false
    }
}
