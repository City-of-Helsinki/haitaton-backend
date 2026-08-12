package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.createObjectMapper
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.node.ObjectNode

class HakemusResponseDeserializer : ValueDeserializer<HakemusResponse>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): HakemusResponse {
        val root = p.readValueAsTree<ObjectNode>()
        val dataClass =
            when (ApplicationType.valueOf(root.path("applicationType").asString())) {
                ApplicationType.CABLE_REPORT -> JohtoselvitysHakemusDataResponse::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusDataResponse::class.java
            }

        // Create a new object mapper without the custom deserializers.
        // Stops an infinite call loop on this method.
        // Ignore unknown properties, since HakemusWithExtrasResponse is deserialized to this class
        // in some tests.
        // Deserialize all instances of HakemusDataResponse according to applicationType.
        val mapper =
            createObjectMapper()
                .rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(
                    SimpleModule()
                        .addAbstractTypeMapping(HakemusDataResponse::class.java, dataClass)
                )
                .build()

        return mapper.treeToValue(root, HakemusResponse::class.java)
    }
}
