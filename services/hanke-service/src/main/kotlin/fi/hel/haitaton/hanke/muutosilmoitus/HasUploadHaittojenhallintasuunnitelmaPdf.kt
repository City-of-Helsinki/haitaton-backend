package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import fi.hel.haitaton.hanke.hakemus.HakemusService
import java.time.LocalDateTime
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Interface for sharing `uploadHaittojenhallintasuunnitelmaPdf` between services without passing
 * dependencies and static names as parameters.
 */
interface HasUploadHaittojenhallintasuunnitelmaPdf {
    val alluClient: AlluClient
    val hakemusService: HakemusService

    val entityNameForLogs: String
    val hhsPdfFilename: String

    fun hhsDescription(now: LocalDateTime): String

    fun uploadHaittojenhallintasuunnitelmaPdf(
        hakemus: HakemusIdentifier,
        hanke: HankeEntity,
        data: HakemusData,
    ) {
        createHaittojenhallintasuunnitelmaPdf(hanke, data)?.let {
            try {
                alluClient.addAttachment(hakemus.alluid!!, it)
            } catch (e: Exception) {
                logger.error(e) {
                    "Error while uploading haittojenhallintasuunnitelma PDF attachment for $entityNameForLogs. Continuing anyway. ${hakemus.logString()}"
                }
            }
        }
    }

    private fun createHaittojenhallintasuunnitelmaPdf(
        hanke: HankeEntity,
        data: HakemusData,
    ): Attachment? =
        hakemusService.getHaittojenhallintasuunnitelmaPdf(
            hanke,
            data,
            hhsPdfFilename,
            hhsDescription(LocalDateTime.now()),
        )
}
