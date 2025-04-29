package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.createObjectMapper

class HankkeenHakemusResponseDeserializer : JsonDeserializer<HankkeenHakemusResponse>() {
    override fun deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext,
    ): HankkeenHakemusResponse {
        val root = jsonParser.readValueAsTree<ObjectNode>()
        val alueType =
            when (ApplicationType.valueOf(root.path("applicationType").textValue())) {
                ApplicationType.CABLE_REPORT -> JohtoselvitysHakemusalue::class
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusAlue::class
            }

        // Create a new object mapper without the custom deserializers.
        // Stops an infinite call loop on this method.
        val mapper = createObjectMapper()

        // Deserialize all instances of Hakemusalue according to applicationType.
        mapper.registerModule(
            SimpleModule().addAbstractTypeMapping(Hakemusalue::class.java, alueType.java)
        )

        return mapper.treeToValue(root, HankkeenHakemusResponse::class.java)
    }
}
