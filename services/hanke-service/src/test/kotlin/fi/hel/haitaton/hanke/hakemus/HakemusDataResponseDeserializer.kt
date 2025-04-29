package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER

class HakemusDataResponseDeserializer : JsonDeserializer<HakemusDataResponse>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): HakemusDataResponse {
        val root = parser.readValueAsTree<ObjectNode>()

        val dataClass =
            when (ApplicationType.valueOf(root.path("applicationType").textValue())) {
                ApplicationType.CABLE_REPORT -> JohtoselvitysHakemusDataResponse::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusDataResponse::class.java
            }

        return OBJECT_MAPPER.treeToValue(root, dataClass)
    }
}
