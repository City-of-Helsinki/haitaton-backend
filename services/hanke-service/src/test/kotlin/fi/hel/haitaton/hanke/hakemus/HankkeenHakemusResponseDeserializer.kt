package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.createObjectMapper
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.node.ObjectNode

class HankkeenHakemusResponseDeserializer : ValueDeserializer<HankkeenHakemusResponse>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): HankkeenHakemusResponse {
        val root = p.readValueAsTree<ObjectNode>()
        val alueType =
            when (ApplicationType.valueOf(root.path("applicationType").asString())) {
                ApplicationType.CABLE_REPORT -> JohtoselvitysHakemusalue::class
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusAlue::class
            }

        // Create a new object mapper without the custom deserializers.
        // Stops an infinite call loop on this method.
        // Deserialize all instances of Hakemusalue according to applicationType.
        val mapper =
            createObjectMapper()
                .rebuild()
                .addModule(
                    SimpleModule().addAbstractTypeMapping(Hakemusalue::class.java, alueType.java)
                )
                .build()

        return mapper.treeToValue(root, HankkeenHakemusResponse::class.java)
    }
}
