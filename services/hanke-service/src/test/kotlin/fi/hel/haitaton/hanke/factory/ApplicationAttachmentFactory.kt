package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.DUMMY_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container.HAKEMUS_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

@Component
class ApplicationAttachmentFactory(
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val contentService: ApplicationAttachmentContentService,
    private val contentRepository: ApplicationAttachmentContentRepository,
    private val applicationFactory: ApplicationFactory,
    private val fileClient: FileClient,
) {
    fun save(
        id: UUID? = null,
        fileName: String = FILE_NAME_PDF,
        contentType: String = CONTENT_TYPE,
        createdByUser: String = USERNAME,
        createdAt: OffsetDateTime = CREATED_AT,
        blobLocation: String? = null,
        attachmentType: ApplicationAttachmentType = MUU,
        application: ApplicationEntity = applicationFactory.saveApplicationEntity(USERNAME),
    ): ApplicationAttachmentBuilder {
        val entity =
            attachmentRepository.save(
                createEntity(
                    id,
                    fileName,
                    contentType,
                    blobLocation,
                    createdByUser,
                    createdAt,
                    attachmentType,
                    application.id!!,
                )
            )
        return ApplicationAttachmentBuilder(entity, attachmentRepository, this)
    }

    fun saveAttachment(applicationId: Long): ApplicationAttachmentEntity {
        val attachment = attachmentRepository.save(createEntity(applicationId = applicationId))
        contentService.save(attachment.id!!, DUMMY_DATA)
        return attachment
    }

    fun saveContentToDb(attachmentId: UUID, bytes: ByteArray): ApplicationAttachmentContentEntity {
        val entity = ApplicationAttachmentContentEntity(attachmentId, bytes)
        return contentRepository.save(entity)
    }

    fun saveContentToCloud(
        path: String,
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = MEDIA_TYPE,
        bytes: ByteArray = DEFAULT_DATA
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
            blobLocation: String? = null,
            createdByUserId: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            attachmentType: ApplicationAttachmentType = MUU,
            applicationId: Long = ApplicationFactory.DEFAULT_APPLICATION_ID,
        ): ApplicationAttachmentMetadata =
            ApplicationAttachmentMetadata(
                id = id,
                fileName = fileName,
                contentType = contentType,
                blobLocation = blobLocation,
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                applicationId = applicationId,
            )

        fun createEntity(
            id: UUID? = defaultAttachmentId,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            blobLocation: String? = null,
            createdByUserId: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            attachmentType: ApplicationAttachmentType = MUU,
            applicationId: Long = ApplicationFactory.DEFAULT_APPLICATION_ID,
        ): ApplicationAttachmentEntity =
            ApplicationAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                blobLocation = blobLocation,
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                applicationId = applicationId,
            )

        fun createMetadata(
            attachmentId: UUID = defaultAttachmentId,
            fileName: String = FILE_NAME,
            createdBy: String = currentUserId(),
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            applicationId: Long = 1L,
            attachmentType: ApplicationAttachmentType = MUU,
        ): ApplicationAttachmentMetadataDto =
            ApplicationAttachmentMetadataDto(
                id = attachmentId,
                fileName = fileName,
                createdByUserId = createdBy,
                createdAt = createdAt,
                applicationId = applicationId,
                attachmentType = attachmentType
            )

        fun createContent(
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            bytes: ByteArray = DUMMY_DATA,
        ) = AttachmentContent(fileName = fileName, contentType = contentType, bytes = bytes)
    }
}
