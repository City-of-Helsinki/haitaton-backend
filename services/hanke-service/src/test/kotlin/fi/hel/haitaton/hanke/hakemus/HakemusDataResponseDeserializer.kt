package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.OBJECT_MAPPER
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.node.ObjectNode

class HakemusDataResponseDeserializer : ValueDeserializer<HakemusDataResponse>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): HakemusDataResponse {
        val root = p.readValueAsTree<ObjectNode>()

        val dataClass =
            when (ApplicationType.valueOf(root.path("applicationType").asString())) {
                ApplicationType.CABLE_REPORT -> JohtoselvitysHakemusDataResponse::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusDataResponse::class.java
            }

        return OBJECT_MAPPER.treeToValue(root, dataClass)
    }
}
