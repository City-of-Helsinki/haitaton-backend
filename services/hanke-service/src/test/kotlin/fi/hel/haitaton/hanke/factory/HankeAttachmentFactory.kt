package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
class HankeAttachmentFactory(
    val attachmentRepository: HankeAttachmentRepository,
    val contentRepository: HankeAttachmentContentRepository,
    val hankeFactory: HankeFactory,
    val fileClient: FileClient,
) {
    fun save(
        id: UUID? = null,
        fileName: String = FILE_NAME_PDF,
        contentType: String = CONTENT_TYPE,
        createdByUser: String = USERNAME,
        createdAt: OffsetDateTime = CREATED_AT,
        blobLocation: String? = null,
        hanke: HankeEntity = hankeFactory.saveEntity()
    ): HankeAttachmentBuilder {
        val entity =
            attachmentRepository.save(
                createEntity(
                    id,
                    fileName,
                    contentType,
                    createdByUser,
                    createdAt,
                    blobLocation,
                    hanke
                )
            )
        return HankeAttachmentBuilder(entity, attachmentRepository, this)
    }

    fun saveContentToDb(attachmentId: UUID, bytes: ByteArray): HankeAttachmentContentEntity {
        val entity = HankeAttachmentContentEntity(attachmentId, bytes)
        return contentRepository.save(entity)
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
        val CONTENT_TYPE = MEDIA_TYPE.toString()
        val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-11-09T10:03:55+02:00")

        fun createEntity(
            id: UUID? = null,
            fileName: String = FILE_NAME_PDF,
            contentType: String = CONTENT_TYPE,
            createdByUser: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            blobLocation: String? = null,
            hanke: HankeEntity = HankeFactory.createMinimalEntity()
        ) =
            HankeAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                createdByUserId = createdByUser,
                createdAt = createdAt,
                blobLocation = blobLocation,
                hanke = hanke,
            )
    }
}
