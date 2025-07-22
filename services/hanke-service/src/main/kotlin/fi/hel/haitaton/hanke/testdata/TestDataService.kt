package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentRepository
import fi.hel.haitaton.hanke.hakemus.AlluUpdateScheduler
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
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
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val muutosilmoitusAttachmentRepository: MuutosilmoitusAttachmentRepository,
    private val attachmentContentService: ApplicationAttachmentContentService,
    private val fileClient: FileClient,
    private val alluUpdateService: AlluUpdateScheduler,
    private val randomHankeGenerator: RandomHankeGenerator,
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

        logger.warn { "Removing all päätökset." }
        paatosRepository.findAll().forEach { deletePaatosWithAttachments(it) }

        logger.warn { "Removing all muutosilmoitukset." }
        muutosilmoitusAttachmentRepository.findAll().forEach {
            attachmentContentService.delete(it.blobLocation)
        }
        muutosilmoitusRepository.deleteAll()
    }

    fun triggerAlluUpdates() {
        logger.info { "Manually triggered Allu updates..." }
        alluUpdateService.checkApplicationHistories()
        logger.info { "Manual Allu updates done." }
    }

    @Transactional
    fun createRandomPublicHanke(count: Int): Int {
        logger.warn { "Creating $count random public hanke..." }
        var created = 0
        repeat(count) { index ->
            randomHankeGenerator.createRandomHankeWithRelations(index)

            if (index % 25 == 0) {
                logger.info { "Created ${index + 1} hanke..." }
            }

            created++
        }
        logger.warn { "Created $created random public hanke." }
        return created
    }

    private fun deletePaatosWithAttachments(paatosEntity: PaatosEntity) {
        logger.warn { "Deleting paatos id=${paatosEntity.id} hakemusId=${paatosEntity.hakemusId}" }
        fileClient.delete(Container.PAATOKSET, paatosEntity.blobLocation)
        paatosRepository.delete(paatosEntity)
    }
}
