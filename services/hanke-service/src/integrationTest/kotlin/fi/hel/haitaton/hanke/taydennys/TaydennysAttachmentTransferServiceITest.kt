package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.prop
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TaydennysAttachmentTransferServiceITest(
    @Autowired private val taydennysAttachmentTransferService: TaydennysAttachmentTransferService,
    @Autowired private val taydennysAttachmentFactory: TaydennysAttachmentFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val taydennysAttachmentRepository: TaydennysAttachmentRepository,
    @Autowired private val hakemusAttachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val hakemusAttachmentContentService: ApplicationAttachmentContentService,
    @Autowired private val fileClient: MockFileClient,
) : IntegrationTest() {

    @BeforeEach
    fun setup() {
        fileClient.recreateContainers()
    }

    @Test
    fun `transfers attachment metadata from taydennys to hakemus`() {
        val taydennysAttachment = taydennysAttachmentFactory.save().withContent().value.toDomain()
        val hakemusEntity = hakemusFactory.builder().saveEntity()
        assertThat(taydennysAttachmentRepository.existsById(taydennysAttachment.id)).isTrue()
        assertThat(hakemusAttachmentRepository.findAll()).isEmpty()
        assertThat(
                hakemusAttachmentContentService.find(
                    taydennysAttachment.blobLocation,
                    taydennysAttachment.id,
                )
            )
            .isEqualTo(PDF_BYTES)

        taydennysAttachmentTransferService.transferAttachmentToHakemus(
            taydennysAttachment,
            hakemusEntity,
        )

        assertThat(taydennysAttachmentRepository.findAll()).isEmpty()
        val hakemusAttachment =
            hakemusAttachmentRepository.findByApplicationId(hakemusEntity.id).single().toDomain()
        assertThat(hakemusAttachment).all {
            prop(ApplicationAttachmentMetadata::fileName).isEqualTo(taydennysAttachment.fileName)
            prop(ApplicationAttachmentMetadata::contentType)
                .isEqualTo(taydennysAttachment.contentType)
            prop(ApplicationAttachmentMetadata::size).isEqualTo(taydennysAttachment.size)
            prop(ApplicationAttachmentMetadata::blobLocation)
                .isEqualTo(taydennysAttachment.blobLocation)
            prop(ApplicationAttachmentMetadata::createdByUserId)
                .isEqualTo(taydennysAttachment.createdByUserId)
            prop(ApplicationAttachmentMetadata::createdAt).isEqualTo(taydennysAttachment.createdAt)
            prop(ApplicationAttachmentMetadata::applicationId).isEqualTo(hakemusEntity.id)
            prop(ApplicationAttachmentMetadata::attachmentType)
                .isEqualTo(taydennysAttachment.attachmentType)
        }
        assertThat(hakemusAttachmentContentService.find(hakemusAttachment)).isEqualTo(PDF_BYTES)
    }
}
