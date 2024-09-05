package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusResponse
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType

class HakemusResponseDeserializer : JsonDeserializer<HakemusResponse>() {
    override fun deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
    ): HakemusResponse {
        val root = jsonParser.readValueAsTree<ObjectNode>()
        val basicHakemus = OBJECT_MAPPER.treeToValue(root, BasicResponse::class.java)

        val dataClass =
            when (basicHakemus.applicationType) {
                ApplicationType.CABLE_REPORT -> JohtoselvitysHakemusDataResponse::class.java
                ApplicationType.EXCAVATION_NOTIFICATION -> KaivuilmoitusDataResponse::class.java
            }

        val dataNode = root.path("applicationData") as ObjectNode
        val hakemusData = OBJECT_MAPPER.treeToValue(dataNode, dataClass)
        return basicHakemus.toHakemusResponse(hakemusData)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BasicResponse(
        val id: Long,
        val alluid: Int?,
        val alluStatus: ApplicationStatus?,
        val applicationIdentifier: String?,
        val applicationType: ApplicationType,
        val hankeTunnus: String,
        val valmistumisilmoitukset:
            Map<ValmistumisilmoitusType, List<ValmistumisilmoitusResponse>>?,
    ) {
        fun toHakemusResponse(hakemusData: HakemusDataResponse) =
            HakemusResponse(
                id,
                alluid,
                alluStatus,
                applicationIdentifier,
                applicationType,
                hakemusData,
                hankeTunnus,
                valmistumisilmoitukset,
            )
    }
}
