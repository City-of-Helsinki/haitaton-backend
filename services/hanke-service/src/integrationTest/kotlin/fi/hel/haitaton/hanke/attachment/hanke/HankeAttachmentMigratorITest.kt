package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.DUMMY_DATA
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator.parseMediaType
import fi.hel.haitaton.hanke.attachment.common.DownloadResponse
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentWithContent
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.test.Asserts.isValidBlobLocation
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
class HankeAttachmentMigratorITest(
    @Autowired private val migrator: HankeAttachmentMigrator,
    @Autowired private val attachmentFactory: HankeAttachmentFactory,
    @Autowired private val fileClient: MockFileClient
) : DatabaseTest() {

    @Nested
    inner class FindAttachmentWithDatabaseContent {
        @Test
        fun `Should return null if no attachments in content table`() {
            val result = migrator.findAttachmentWithDatabaseContent()

            assertThat(result).isNull()
        }

        @Test
        fun `Should return un-migrated attachment if there are any`() {
            val attachment = attachmentFactory.save().withDbContent().value

            val result: HankeAttachmentWithContent? = migrator.findAttachmentWithDatabaseContent()

            assertThat(result).isNotNull().all {
                prop(HankeAttachmentWithContent::id).isNotNull().isEqualTo(attachment.id)
                prop(HankeAttachmentWithContent::hankeId).isEqualTo(attachment.hanke.id)
                prop(HankeAttachmentWithContent::content).all {
                    prop(AttachmentContent::fileName).isEqualTo(attachment.fileName)
                    prop(AttachmentContent::contentType).isEqualTo(attachment.contentType)
                    prop(AttachmentContent::bytes).isEqualTo(DEFAULT_DATA)
                }
            }
        }
    }

    @Nested
    inner class Migrate {
        @Test
        fun `Should upload input attachment into blob storage and return blob address`() {
            val attachment = attachmentFactory.save().withDbContent().value
            val unMigratedAttachment = unMigrated(attachment.id!!, attachment.hanke.id)
            val hankeId = unMigratedAttachment.hankeId

            val blobPath = migrator.migrate(unMigratedAttachment)

            assertThat(blobPath).isNotNull().isValidBlobLocation(hankeId)

            assertThat(getBlob(blobPath))
                .prop(DownloadResponse::contentType)
                .isEqualTo(parseMediaType(attachment.contentType))
        }
    }

    @Nested
    inner class SetBlobPathAndRemoveOldContent {
        @Test
        fun `Should update attachment entity's blob path and remove file content in db`() {
            val originalAttachment = attachmentFactory.save().withDbContent().value
            val id = originalAttachment.id!!
            val blobPath = blobPath(originalAttachment.hanke.id)

            migrator.setBlobPathAndCleanup(id, blobPath)

            val updatedAttachment = attachmentFactory.attachmentRepository.findByIdOrNull(id)
            assertThat(updatedAttachment).isNotNull().all {
                prop(HankeAttachmentEntity::blobLocation).isEqualTo(blobPath)
                prop(HankeAttachmentEntity::fileName).isEqualTo(originalAttachment.fileName)
                prop(HankeAttachmentEntity::contentType).isEqualTo(originalAttachment.contentType)
                prop(HankeAttachmentEntity::createdByUserId)
                    .isEqualTo(originalAttachment.createdByUserId)
            }
            val oldCount = attachmentFactory.contentRepository.count()
            assertThat(oldCount).isEqualTo(0)
        }

        @Test
        fun `Should delete uploaded blob if updating metadata or cleanup fails`() {
            val hankeId = 123
            val attachmentId = UUID.randomUUID()
            val attachment = unMigrated(attachmentId, hankeId)
            val blobPath = migrator.migrate(attachment)
            assertThat(getBlob(blobPath)).isNotNull()

            migrator.setBlobPathAndCleanup(attachment.id, blobPath)

            val blobs = fileClient.listBlobs(HANKE_LIITTEET)
            assertThat(blobs).isEmpty()
        }
    }

    private fun unMigrated(id: UUID, hankeId: Int, bytes: ByteArray = DUMMY_DATA) =
        HankeAttachmentWithContent(
            id = id,
            hankeId = hankeId,
            content = AttachmentFactory.attachmentContent(bytes = bytes)
        )

    private fun blobPath(id: Int) = HankeAttachmentContentService.generateBlobPath(id)

    private fun getBlob(blobPath: String) = fileClient.download(HANKE_LIITTEET, blobPath)
}
