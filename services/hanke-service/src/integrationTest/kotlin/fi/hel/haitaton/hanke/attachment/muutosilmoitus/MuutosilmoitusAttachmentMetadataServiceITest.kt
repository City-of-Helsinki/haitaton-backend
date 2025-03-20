package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.factory.MuutosilmoitusAttachmentFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MuutosilmoitusAttachmentMetadataServiceITest(
    @Autowired
    private val muutosilmoitusAttachmentMetadataService: MuutosilmoitusAttachmentMetadataService,
    @Autowired private val muutosilmoitusAttachmentFactory: MuutosilmoitusAttachmentFactory,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
    @Autowired private val muutosilmoitusAttachmentRepository: MuutosilmoitusAttachmentRepository,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val hakemusAttachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val hakemusAttachmentService: ApplicationAttachmentService,
    @Autowired private val hakemusAttachmentContentService: ApplicationAttachmentContentService,
) : IntegrationTest() {

    @Test
    fun `transfers attachment metadata from muutosilmoitus to hakemus`() {
        val muutosilmoitus = muutosilmoitusFactory.builder().saveEntity()
        val muutosilmoitusAttachments =
            listOf(
                muutosilmoitusAttachmentFactory
                    .save(muutosilmoitus, fileName = "first.pdf")
                    .toDomain(),
                muutosilmoitusAttachmentFactory
                    .save(muutosilmoitus, fileName = "second.pdf")
                    .toDomain(),
            )
        val otherMuutosilmoitus = muutosilmoitusFactory.builder(alluId = 4144).save()
        val otherAttachment =
            muutosilmoitusAttachmentFactory.save(muutosilmoitus = otherMuutosilmoitus)
        val hakemusEntity = hakemusRepository.getReferenceById(muutosilmoitus.hakemusId)
        assertThat(hakemusAttachmentRepository.findAll()).isEmpty()
        assertThat(muutosilmoitusAttachments)
            .extracting { hakemusAttachmentContentService.find(it.blobLocation, it.id) }
            .containsExactly(PDF_BYTES, PDF_BYTES)

        muutosilmoitusAttachmentMetadataService.transferAttachmentsToHakemus(
            muutosilmoitus,
            hakemusEntity,
        )

        assertThat(muutosilmoitusAttachmentRepository.findAll())
            .single()
            .prop(MuutosilmoitusAttachmentEntity::id)
            .isEqualTo(otherAttachment.id)
        val hakemusAttachments = hakemusAttachmentService.getMetadataList(muutosilmoitus.hakemusId)
        assertThat(hakemusAttachments).hasSize(muutosilmoitusAttachments.size)
        muutosilmoitusAttachments.forEach { muutosilmoitusAttachment ->
            val hakemusAttachment =
                hakemusAttachments.single { it.fileName == muutosilmoitusAttachment.fileName }
            assertThat(hakemusAttachment).all {
                prop(ApplicationAttachmentMetadata::contentType)
                    .isEqualTo(muutosilmoitusAttachment.contentType)
                prop(ApplicationAttachmentMetadata::size).isEqualTo(muutosilmoitusAttachment.size)
                prop(ApplicationAttachmentMetadata::blobLocation)
                    .isEqualTo(muutosilmoitusAttachment.blobLocation)
                prop(ApplicationAttachmentMetadata::createdByUserId)
                    .isEqualTo(muutosilmoitusAttachment.createdByUserId)
                prop(ApplicationAttachmentMetadata::createdAt)
                    .isEqualTo(muutosilmoitusAttachment.createdAt)
                prop(ApplicationAttachmentMetadata::applicationId).isEqualTo(hakemusEntity.id)
                prop(ApplicationAttachmentMetadata::attachmentType)
                    .isEqualTo(muutosilmoitusAttachment.attachmentType)
            }
        }
        assertThat(muutosilmoitusAttachments)
            .extracting { hakemusAttachmentContentService.find(it.blobLocation, it.id) }
            .containsExactly(PDF_BYTES, PDF_BYTES)
    }
}
