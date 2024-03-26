package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationType
import java.io.IOException

/**
 * Custom deserializer for Hakemus. Because applicationData is polymorphic, this can't be
 * deserialized without some sort of customization. The easy way would be to use JsonSubTypes like
 * in [HakemusUpdateRequest]. But that would be in use always, even in production code.
 *
 * We don't need to deserialize Hakemus data in production code, just in tests. That's easiest to do
 * with a custom serializer we register when the tests start.
 */
class HakemusDeserializer : JsonDeserializer<Hakemus>() {
    @Throws(IOException::class)
    override fun deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
    ): Hakemus {
        val root = jsonParser.readValueAsTree<ObjectNode>()
        val basicHakemus = OBJECT_MAPPER.treeToValue(root, BasicHakemus::class.java)

        val dataClass =
            when (basicHakemus.applicationType) {
                ApplicationType.CABLE_REPORT -> JohtoselvityshakemusData::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> TODO("Kaivuilmoitus not yet implemented")
            }

        val dataNode = root.path("applicationData") as ObjectNode
        val hakemusData = OBJECT_MAPPER.treeToValue(dataNode, dataClass)
        return basicHakemus.toHakemus(hakemusData)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BasicHakemus(
        val id: Long,
        val alluid: Int?,
        val alluStatus: ApplicationStatus?,
        val applicationIdentifier: String?,
        val applicationType: ApplicationType,
        val hankeTunnus: String,
        val hankeId: Int,
    ) {
        fun toHakemus(hakemusData: HakemusData) =
            Hakemus(
                id,
                alluid,
                alluStatus,
                applicationIdentifier,
                applicationType,
                hakemusData,
                hankeTunnus,
                hankeId,
            )
    }
}
