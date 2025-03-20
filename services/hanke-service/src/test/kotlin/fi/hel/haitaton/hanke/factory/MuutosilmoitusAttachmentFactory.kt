package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentEntity
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentRepository
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory.Companion.DEFAULT_ID
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.test.USERNAME
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

@Component
class MuutosilmoitusAttachmentFactory(
    private val attachmentRepository: MuutosilmoitusAttachmentRepository,
    private val muutosilmoitusFactory: MuutosilmoitusFactory,
    private val fileClient: FileClient,
) {
    fun save(
        muutosilmoitus: MuutosilmoitusEntity,
        id: UUID? = null,
        fileName: String = FILE_NAME_PDF,
        contentType: MediaType = MEDIA_TYPE,
        size: Long = DEFAULT_SIZE,
        createdByUser: String = USERNAME,
        createdAt: OffsetDateTime = CREATED_AT,
        attachmentType: ApplicationAttachmentType = MUU,
        content: ByteArray? = PDF_BYTES,
    ) =
        save(
            id = id,
            fileName = fileName,
            contentType = contentType,
            size = size,
            createdByUser = createdByUser,
            createdAt = createdAt,
            attachmentType = attachmentType,
            muutosilmoitus = muutosilmoitus.toDomain(),
            content = content,
        )

    fun save(
        id: UUID? = null,
        fileName: String = FILE_NAME_PDF,
        contentType: MediaType = MEDIA_TYPE,
        size: Long = DEFAULT_SIZE,
        createdByUser: String = USERNAME,
        createdAt: OffsetDateTime = CREATED_AT,
        attachmentType: ApplicationAttachmentType = MUU,
        muutosilmoitus: Muutosilmoitus = muutosilmoitusFactory.builder().saveEntity().toDomain(),
        content: ByteArray? = PDF_BYTES,
    ): MuutosilmoitusAttachmentEntity {
        val entity =
            createEntity(
                id,
                fileName,
                contentType.toString(),
                size,
                createdByUser,
                createdAt,
                attachmentType,
                muutosilmoitus.hakemusId,
                muutosilmoitus.id,
            )

        if (content != null) {
            entity.size = content.size.toLong()
            saveContentToCloud(entity.blobLocation, fileName, contentType, content)
        }

        return attachmentRepository.save(entity)
    }

    fun saveContentToCloud(
        path: String,
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = HankeAttachmentFactory.MEDIA_TYPE,
        bytes: ByteArray = PDF_BYTES,
    ) {
        fileClient.upload(Container.HAKEMUS_LIITTEET, path, filename, mediaType, bytes)
    }

    companion object {
        val DEFAULT_ATTACHMENT_ID: UUID = UUID.fromString("5cba3a76-28ad-42aa-b7e6-b5c1775be81a")
        val MEDIA_TYPE = MediaType.APPLICATION_PDF
        val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-11-09T08:03:55Z")

        const val FILE_NAME = "file.pdf"

        fun create(
            id: UUID = DEFAULT_ATTACHMENT_ID,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            size: Long = DEFAULT_SIZE,
            blobLocation: String = generateBlobPath(1L),
            createdByUserId: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            attachmentType: ApplicationAttachmentType = MUU,
            muutosilmoitusId: UUID = DEFAULT_ID,
        ): MuutosilmoitusAttachmentMetadata =
            MuutosilmoitusAttachmentMetadata(
                id = id,
                fileName = fileName,
                contentType = contentType,
                size = size,
                blobLocation = blobLocation,
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                muutosilmoitusId = muutosilmoitusId,
            )

        fun createEntity(
            id: UUID? = DEFAULT_ATTACHMENT_ID,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            size: Long = DEFAULT_SIZE,
            createdByUserId: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            attachmentType: ApplicationAttachmentType = MUU,
            applicationId: Long = 1L,
            muutosilmoitusId: UUID = DEFAULT_ID,
        ): MuutosilmoitusAttachmentEntity =
            MuutosilmoitusAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                size = size,
                blobLocation = generateBlobPath(applicationId),
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                muutosilmoitusId = muutosilmoitusId,
            )
    }
}
