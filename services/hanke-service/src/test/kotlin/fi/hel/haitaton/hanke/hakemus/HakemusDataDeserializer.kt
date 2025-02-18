package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER

class HakemusDataDeserializer : JsonDeserializer<HakemusData>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): HakemusData {
        val root = parser.readValueAsTree<ObjectNode>()

        val dataClass =
            when (val typeString = root.path("applicationType").textValue()) {
                ApplicationType.CABLE_REPORT.name -> JohtoselvityshakemusData::class.java
                ApplicationType.EXCAVATION_NOTIFICATION.name -> KaivuilmoitusData::class.java
                else -> throw IllegalArgumentException("Unsupported application type $typeString")
            }

        val hakemusData = OBJECT_MAPPER.treeToValue(root, dataClass)
        return hakemusData
    }
}
