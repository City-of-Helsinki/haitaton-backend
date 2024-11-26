package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.azure.Container.HAKEMUS_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.factory.TaydennysFactory.Companion.DEFAULT_ID
import fi.hel.haitaton.hanke.taydennys.Taydennys
import fi.hel.haitaton.hanke.test.USERNAME
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

@Component
class TaydennysAttachmentFactory(
    private val attachmentRepository: TaydennysAttachmentRepository,
    private val taydennysFactory: TaydennysFactory,
    private val fileClient: FileClient,
) {
    fun save(
        id: UUID? = null,
        fileName: String = FILE_NAME_PDF,
        contentType: String = CONTENT_TYPE,
        size: Long = DEFAULT_SIZE,
        createdByUser: String = USERNAME,
        createdAt: OffsetDateTime = CREATED_AT,
        attachmentType: ApplicationAttachmentType = MUU,
        taydennys: Taydennys = taydennysFactory.save(1L),
    ): TaydennysAttachmentBuilder {
        val entity =
            attachmentRepository.save(
                createEntity(
                    id,
                    fileName,
                    contentType,
                    size,
                    createdByUser,
                    createdAt,
                    attachmentType,
                    taydennys.hakemusId,
                    taydennys.id,
                )
            )
        return TaydennysAttachmentBuilder(entity, attachmentRepository, this)
    }

    fun saveContent(
        path: String,
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = MEDIA_TYPE,
        bytes: ByteArray = PDF_BYTES,
    ) {
        fileClient.upload(HAKEMUS_LIITTEET, path, filename, mediaType, bytes)
    }

    companion object {
        val defaultAttachmentId: UUID = UUID.fromString("5cba3a76-28ad-42aa-b7e6-b5c1775be81a")
        val MEDIA_TYPE = MediaType.APPLICATION_PDF
        val CONTENT_TYPE = MEDIA_TYPE.toString()
        val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-11-09T10:03:55+02:00")

        const val FILE_NAME = "file.pdf"

        fun create(
            id: UUID = defaultAttachmentId,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            size: Long = DEFAULT_SIZE,
            blobLocation: String = generateBlobPath(1L),
            createdByUserId: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            attachmentType: ApplicationAttachmentType = MUU,
            taydennysId: UUID = DEFAULT_ID,
        ): TaydennysAttachmentMetadata =
            TaydennysAttachmentMetadata(
                id = id,
                fileName = fileName,
                contentType = contentType,
                size = size,
                blobLocation = blobLocation,
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                taydennysId = taydennysId,
            )

        fun createEntity(
            id: UUID? = defaultAttachmentId,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            size: Long = DEFAULT_SIZE,
            createdByUserId: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            attachmentType: ApplicationAttachmentType = MUU,
            applicationId: Long = 1L,
            taydennysId: UUID = DEFAULT_ID,
        ): TaydennysAttachmentEntity =
            TaydennysAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                size = size,
                blobLocation = generateBlobPath(applicationId),
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                taydennysId = taydennysId,
            )

        fun createDto(
            attachmentId: UUID = defaultAttachmentId,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            size: Long = DEFAULT_SIZE,
            createdBy: String = USERNAME,
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            taydennysId: UUID = DEFAULT_ID,
            attachmentType: ApplicationAttachmentType = MUU,
        ): TaydennysAttachmentMetadataDto =
            TaydennysAttachmentMetadataDto(
                id = attachmentId,
                fileName = fileName,
                contentType = contentType,
                size = size,
                attachmentType = attachmentType,
                createdByUserId = createdBy,
                createdAt = createdAt,
                taydennysId = taydennysId,
            )
    }
}
