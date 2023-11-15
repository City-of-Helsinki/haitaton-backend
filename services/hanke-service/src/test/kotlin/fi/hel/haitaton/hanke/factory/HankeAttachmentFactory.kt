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

interface HankeAttachmentFactory {
    val hankeAttachmentRepository: HankeAttachmentRepository
    val hankeAttachmentContentRepository: HankeAttachmentContentRepository
    val hankeFactory: HankeFactory
    val fileClient: FileClient

    fun saveAttachment(
        blobLocation: String? = null,
        filename: String = FILE_NAME_PDF,
        contentType: String = CONTENT_TYPE,
        hanke: HankeEntity = hankeFactory.saveEntity(),
    ): HankeAttachmentEntity {
        val attachment =
            createEntity(
                blobLocation = blobLocation,
                fileName = filename,
                contentType = contentType,
                hanke = hanke
            )
        return hankeAttachmentRepository.save(attachment)
    }

    fun saveContentToDb(attachmentId: UUID, bytes: ByteArray): HankeAttachmentContentEntity {
        val entity = HankeAttachmentContentEntity(attachmentId, bytes)
        return hankeAttachmentContentRepository.save(entity)
    }

    fun saveContentToCloud(
        path: String,
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = MEDIA_TYPE,
        bytes: ByteArray = DEFAULT_DATA
    ) {
        fileClient.upload(HANKE_LIITTEET, path, filename, mediaType, bytes)
    }

    fun HankeAttachmentEntity.withDbContent(
        bytes: ByteArray = DEFAULT_DATA
    ): HankeAttachmentEntity {
        saveContentToDb(id!!, bytes)
        return this
    }

    fun HankeAttachmentEntity.withCloudContent(
        path: String = "${hanke.id}/${id}",
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = Companion.MEDIA_TYPE,
        bytes: ByteArray = DEFAULT_DATA
    ): HankeAttachmentEntity {
        blobLocation = path
        hankeAttachmentRepository.save(this)
        saveContentToCloud(path, filename, mediaType, bytes)
        return this
    }

    companion object {

        val MEDIA_TYPE = MediaType.APPLICATION_PDF
        val CONTENT_TYPE = MEDIA_TYPE.toString()
        val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-11-09T10:03:55+02:00")

        fun createEntity(
            id: UUID? = null,
            fileName: String = FILE_NAME_PDF,
            contentType: String = CONTENT_TYPE,
            blobLocation: String? = null,
            hanke: HankeEntity = HankeFactory.createMinimalEntity()
        ) =
            HankeAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                createdByUserId = USERNAME,
                createdAt = CREATED_AT,
                blobLocation = blobLocation,
                hanke = hanke,
            )
    }
}
