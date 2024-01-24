package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentContentService
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
class HankeAttachmentFactory(
    val attachmentRepository: HankeAttachmentRepository,
    val hankeFactory: HankeFactory,
    val fileClient: FileClient,
) {
    fun save(
        id: UUID? = null,
        fileName: String = FILE_NAME_PDF,
        contentType: String = CONTENT_TYPE,
        size: Long = SIZE,
        createdByUser: String = USERNAME,
        createdAt: OffsetDateTime = CREATED_AT,
        hanke: HankeEntity = hankeFactory.builder(USERNAME).saveEntity()
    ): HankeAttachmentBuilder {
        val entity =
            attachmentRepository.save(
                createEntity(id, fileName, contentType, size, createdByUser, createdAt, hanke)
            )
        return HankeAttachmentBuilder(entity, attachmentRepository, this)
    }

    fun saveContentToCloud(
        path: String,
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = MEDIA_TYPE,
        bytes: ByteArray = DEFAULT_DATA
    ) {
        fileClient.upload(HANKE_LIITTEET, path, filename, mediaType, bytes)
    }

    companion object {

        val MEDIA_TYPE = MediaType.APPLICATION_PDF
        const val CONTENT_TYPE = MediaType.APPLICATION_PDF_VALUE
        val SIZE = DEFAULT_SIZE
        val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-11-09T10:03:55+02:00")

        fun create(
            attachmentId: UUID = ApplicationAttachmentFactory.defaultAttachmentId,
            fileName: String = ApplicationAttachmentFactory.FILE_NAME,
            contentType: String = CONTENT_TYPE,
            size: Long = SIZE,
            blobLocation: String = HankeAttachmentContentService.generateBlobPath(42),
            createdByUser: String = currentUserId(),
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            hankeId: Int = 42,
        ): HankeAttachmentMetadata =
            HankeAttachmentMetadata(
                id = attachmentId,
                fileName = fileName,
                contentType = contentType,
                size = size,
                blobLocation = blobLocation,
                createdByUserId = createdByUser,
                createdAt = createdAt,
                hankeId = hankeId,
            )

        fun createDto(
            attachmentId: UUID = ApplicationAttachmentFactory.defaultAttachmentId,
            fileName: String = ApplicationAttachmentFactory.FILE_NAME,
            contentType: String = CONTENT_TYPE,
            size: Long = SIZE,
            createdByUser: String = currentUserId(),
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            hankeTunnus: String = "HAI-1234",
        ): HankeAttachmentMetadataDto =
            HankeAttachmentMetadataDto(
                id = attachmentId,
                fileName = fileName,
                contentType = contentType,
                size = size,
                createdByUserId = createdByUser,
                createdAt = createdAt,
                hankeTunnus = hankeTunnus,
            )

        fun createEntity(
            id: UUID? = null,
            fileName: String = FILE_NAME_PDF,
            contentType: String = CONTENT_TYPE,
            size: Long = SIZE,
            createdByUser: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            hanke: HankeEntity = HankeFactory.createMinimalEntity()
        ) =
            HankeAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                size = size,
                createdByUserId = createdByUser,
                createdAt = createdAt,
                blobLocation = HankeAttachmentContentService.generateBlobPath(hanke.id),
                hanke = hanke,
            )
    }
}
