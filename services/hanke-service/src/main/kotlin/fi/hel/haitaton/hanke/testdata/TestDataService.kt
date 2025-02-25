package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.hakemus.AlluUpdateService
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.paatos.PaatosEntity
import fi.hel.haitaton.hanke.paatos.PaatosRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@ConditionalOnProperty(name = ["haitaton.testdata.enabled"], havingValue = "true")
class TestDataService(
    private val hakemusRepository: HakemusRepository,
    private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    private val taydennysAttachmentRepository: TaydennysAttachmentRepository,
    private val paatosRepository: PaatosRepository,
    private val attachmentContentService: ApplicationAttachmentContentService,
    private val fileClient: FileClient,
    private val alluUpdateService: AlluUpdateService,
) {
    @Transactional
    fun unlinkApplicationsFromAllu() {
        logger.warn { "Unlinking all applications from Allu." }
        hakemusRepository.findAll().forEach {
            logger.warn { "Unlinking application from Allu. ${it.logString()}" }
            it.alluid = null
            it.alluStatus = null
            it.applicationIdentifier = null
            it.hakemusEntityData = it.hakemusEntityData.copy(paperDecisionReceiver = null)
        }
        logger.warn { "Removing all täydennyspyynnöt and täydennykset from Haitaton." }
        taydennysAttachmentRepository.findAll().forEach {
            attachmentContentService.delete(it.blobLocation)
        }
        taydennyspyyntoRepository.deleteAll()

        logger.warn { "Removing all päätökset and täydennykset." }
        paatosRepository.findAll().forEach { deletePaatosWithAttachments(it) }
    }

    fun triggerAlluUpdates() {
        logger.info { "Manually triggered Allu updates..." }
        alluUpdateService.checkApplicationStatuses()
        logger.info { "Manual Allu updates done." }
    }

    private fun deletePaatosWithAttachments(paatosEntity: PaatosEntity) {
        logger.warn { "Deleting paatos id=${paatosEntity.id} hakemusId=${paatosEntity.hakemusId}" }
        fileClient.delete(Container.PAATOKSET, paatosEntity.blobLocation)
        paatosRepository.delete(paatosEntity)
    }
}
