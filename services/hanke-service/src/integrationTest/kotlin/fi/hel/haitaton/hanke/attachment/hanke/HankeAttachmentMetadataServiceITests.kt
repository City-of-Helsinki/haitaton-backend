package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.USERNAME
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE

class HankeAttachmentMetadataServiceITests(
    @Autowired private val hankeAttachmentMetadataService: HankeAttachmentMetadataService,
    @Autowired private val hankeAttachmentRepository: HankeAttachmentRepository,
    @Autowired private val hankeFactory: HankeFactory,
) : IntegrationTest() {

    @Nested
    inner class GetMetadataList {
        @Test
        fun `getMetadataList should return related metadata list`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            (1..2).forEach { _ ->
                hankeAttachmentRepository.save(
                    HankeAttachmentEntity(
                        id = null,
                        fileName = FILE_NAME_PDF,
                        contentType = APPLICATION_PDF_VALUE,
                        size = DEFAULT_SIZE,
                        createdByUserId = USERNAME,
                        createdAt = OffsetDateTime.now(),
                        blobLocation = "${hanke.id}/${UUID.randomUUID()}",
                        hanke = hanke,
                    )
                )
            }

            val result = hankeAttachmentMetadataService.getMetadataList(hanke.hankeTunnus)

            assertThat(result).hasSize(2)
            assertThat(result).each { d ->
                d.transform { it.id }.isNotNull()
                d.transform { it.fileName }.endsWith(FILE_NAME_PDF)
                d.transform { it.createdByUserId }.isEqualTo(USERNAME)
                d.transform { it.createdAt }.isNotNull()
                d.transform { it.hankeTunnus }.isEqualTo(hanke.hankeTunnus)
                d.transform { it.contentType }.isEqualTo(APPLICATION_PDF_VALUE)
                d.transform { it.size }.isEqualTo(DEFAULT_SIZE)
            }
        }
    }

    @Nested
    inner class SaveAttachment {
        @Test
        fun `Should return metadata of saved attachment`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val blobPath = blobPath(hanke.id)

            val result =
                hankeAttachmentMetadataService.saveAttachment(
                    hankeTunnus = hanke.hankeTunnus,
                    name = FILE_NAME_PDF,
                    type = APPLICATION_PDF_VALUE,
                    size = DEFAULT_SIZE,
                    blobPath = blobPath,
                )

            assertThat(result).all {
                prop(HankeAttachmentMetadataDto::id).isNotNull()
                prop(HankeAttachmentMetadataDto::createdByUserId).isEqualTo(USERNAME)
                prop(HankeAttachmentMetadataDto::fileName).isEqualTo(FILE_NAME_PDF)
                prop(HankeAttachmentMetadataDto::createdAt).isRecent()
                prop(HankeAttachmentMetadataDto::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(HankeAttachmentMetadataDto::contentType).isEqualTo(APPLICATION_PDF_VALUE)
                prop(HankeAttachmentMetadataDto::size).isEqualTo(DEFAULT_SIZE)
            }
            assertThat(hankeAttachmentRepository.findAll()).single().all {
                prop(HankeAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
                prop(HankeAttachmentEntity::fileName).isEqualTo(FILE_NAME_PDF)
                prop(HankeAttachmentEntity::createdAt).isRecent()
                prop(HankeAttachmentEntity::blobLocation).isEqualTo(blobPath)
                prop(HankeAttachmentEntity::contentType).isEqualTo(APPLICATION_PDF_VALUE)
                prop(HankeAttachmentEntity::size).isEqualTo(DEFAULT_SIZE)
            }
        }

        @Test
        fun `Should throw if attachment amount is exceeded`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            (1..ALLOWED_ATTACHMENT_COUNT)
                .map { HankeAttachmentFactory.createEntity(hanke = hanke) }
                .let(hankeAttachmentRepository::saveAll)

            assertFailure {
                    hankeAttachmentMetadataService.saveAttachment(
                        hanke.hankeTunnus,
                        FILE_NAME_PDF,
                        APPLICATION_PDF_VALUE,
                        DEFAULT_SIZE,
                        blobPath(hanke.id),
                    )
                }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: Attachment limit reached")
                }
        }

        @Test
        fun `Should fail when there is no related hanke`() {
            assertFailure {
                    hankeAttachmentMetadataService.saveAttachment(
                        hankeTunnus = "HAI-123",
                        name = FILE_NAME_PDF,
                        type = APPLICATION_PDF_VALUE,
                        size = DEFAULT_SIZE,
                        blobPath = blobPath(123)
                    )
                }
                .hasClass(HankeNotFoundException::class)

            assertThat(hankeAttachmentRepository.findAll()).isEmpty()
        }

        private fun blobPath(hankeId: Int) = HankeAttachmentContentService.generateBlobPath(hankeId)
    }

    @Nested
    inner class HankeWithRoomForAttachment {
        @Test
        fun `When there is still room, should return hanke info`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()

            val result =
                hankeAttachmentMetadataService.hankeWithRoomForAttachment(hanke.hankeTunnus)

            assertThat(result).all {
                prop(HankeIdentifier::id).isEqualTo(hanke.id)
                prop(HankeIdentifier::hankeTunnus).isEqualTo(hanke.hankeTunnus)
            }
        }

        @Test
        fun `When allowed attachment amount is exceeded should throw`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            val attachments =
                (1..ALLOWED_ATTACHMENT_COUNT).map {
                    HankeAttachmentFactory.createEntity(hanke = hanke)
                }
            hankeAttachmentRepository.saveAll(attachments)

            assertFailure {
                    hankeAttachmentMetadataService.hankeWithRoomForAttachment(hanke.hankeTunnus)
                }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: Attachment limit reached")
                }
        }
    }
}
