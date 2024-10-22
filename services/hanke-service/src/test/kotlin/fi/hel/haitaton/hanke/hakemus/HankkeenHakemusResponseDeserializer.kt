package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import java.time.ZonedDateTime

class HankkeenHakemusResponseDeserializer : JsonDeserializer<HankkeenHakemusResponse>() {
    override fun deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
    ): HankkeenHakemusResponse {
        val root = jsonParser.readValueAsTree<ObjectNode>()
        val basicApplication = OBJECT_MAPPER.treeToValue(root, BasicResponse::class.java)
        val dataNode = root.path("applicationData") as ObjectNode
        val basicApplicationData =
            OBJECT_MAPPER.treeToValue(dataNode, BasicDataResponse::class.java)
        val areasNode = dataNode.path("areas")
        val areas =
            areasNode.map { areaNode ->
                deserializeHakemusalue(areaNode as ObjectNode, basicApplication.applicationType)
            }
        return basicApplication.toHankkeenHakemusResponse(
            basicApplicationData.toHankkeenHakemusDataResponse(areas))
    }

    private fun deserializeHakemusalue(
        areaNode: ObjectNode,
        applicationType: ApplicationType
    ): Hakemusalue {
        return when (applicationType) {
            ApplicationType.CABLE_REPORT ->
                OBJECT_MAPPER.treeToValue(areaNode, JohtoselvitysHakemusalue::class.java)
            ApplicationType.EXCAVATION_NOTIFICATION ->
                OBJECT_MAPPER.treeToValue(areaNode, KaivuilmoitusAlue::class.java)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BasicResponse(
        val id: Long,
        val alluid: Int?,
        val alluStatus: ApplicationStatus?,
        val applicationIdentifier: String?,
        val applicationType: ApplicationType,
    ) {
        fun toHankkeenHakemusResponse(hakemusData: HankkeenHakemusDataResponse) =
            HankkeenHakemusResponse(
                id,
                alluid,
                alluStatus,
                applicationIdentifier,
                applicationType,
                hakemusData,
            )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BasicDataResponse(
        val name: String,
        val startTime: ZonedDateTime?,
        val endTime: ZonedDateTime?,
        val pendingOnClient: Boolean,
    ) {
        fun toHankkeenHakemusDataResponse(areas: List<Hakemusalue>) =
            HankkeenHakemusDataResponse(
                name,
                startTime,
                endTime,
                pendingOnClient,
                if (areas.isNotEmpty()) areas else null,
            )
    }
}
