package fi.hel.haitaton.hanke.taydennys

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusDataResponse
import java.util.UUID

class TaydennysResponseDeserializer : JsonDeserializer<TaydennysResponse>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext
    ): TaydennysResponse {
        val root = parser.readValueAsTree<ObjectNode>()

        val dataClass =
            when (val typeString =
                root.path("applicationData").path("applicationType").textValue()) {
                ApplicationType.CABLE_REPORT.name -> JohtoselvitysHakemusDataResponse::class.java
                ApplicationType.EXCAVATION_NOTIFICATION.name ->
                    KaivuilmoitusDataResponse::class.java
                else -> throw IllegalArgumentException("Unsupported application type $typeString")
            }

        val dataNode = root.path("applicationData") as ObjectNode
        val hakemusData = OBJECT_MAPPER.treeToValue(dataNode, dataClass)
        return TaydennysResponse(
            UUID.fromString(root.path("id").textValue()),
            hakemusData,
        )
    }
}
