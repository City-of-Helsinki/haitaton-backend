package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.createObjectMapper

class HakemusResponseDeserializer : JsonDeserializer<HakemusResponse>() {
    override fun deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext,
    ): HakemusResponse {
        val root = jsonParser.readValueAsTree<ObjectNode>()
        val dataClass =
            when (ApplicationType.valueOf(root.path("applicationType").textValue())) {
                ApplicationType.CABLE_REPORT -> JohtoselvitysHakemusDataResponse::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusDataResponse::class.java
            }

        // Create a new object mapper without the custom deserializers.
        // Stops an infinite call loop on this method.
        // Ignore unknown properties, since HakemusWithExtrasResponse is deserialized to this class
        // in some tests.
        val mapper = createObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        // Deserialize all instances of HakemusDataResponse according to applicationType.
        mapper.registerModule(
            SimpleModule().addAbstractTypeMapping(HakemusDataResponse::class.java, dataClass)
        )

        return mapper.treeToValue(root, HakemusResponse::class.java)
    }
}
