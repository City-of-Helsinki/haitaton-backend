package fi.hel.haitaton.hanke.taydennys

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import java.util.UUID

class TaydennysDeserializer : JsonDeserializer<Taydennys>() {
    override fun deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
    ): Taydennys {
        val root = jsonParser.readValueAsTree<ObjectNode>()

        val dataClass =
            when (val typeString = root.path("hakemusData").path("applicationType").textValue()) {
                ApplicationType.CABLE_REPORT.name -> JohtoselvityshakemusData::class.java
                ApplicationType.EXCAVATION_NOTIFICATION.name -> KaivuilmoitusData::class.java
                else -> throw IllegalArgumentException("Unsupported application type $typeString")
            }

        val dataNode = root.path("hakemusData") as ObjectNode
        val hakemusData = OBJECT_MAPPER.treeToValue(dataNode, dataClass)
        return Taydennys(
            UUID.fromString(root.path("id").textValue()),
            UUID.fromString(root.path("taydennyspyyntoId").textValue()),
            hakemusData,
        )
    }
}
