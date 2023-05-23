package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import java.util.UUID.randomUUID
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE

private const val FILE_NAME = "file.pdf"

private val dummyData = "ABC".toByteArray()

object AttachmentFactory {
    fun applicationAttachmentEntity(
        id: UUID = randomUUID(),
        fileName: String = FILE_NAME,
        content: ByteArray = dummyData,
        contentType: String = APPLICATION_PDF_VALUE,
        createdByUserId: String = currentUserId(),
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        scanStatus: AttachmentScanStatus = OK,
        attachmentType: ApplicationAttachmentType = MUU,
        application: ApplicationEntity,
    ): ApplicationAttachmentEntity =
        ApplicationAttachmentEntity(
            id = id,
            fileName = fileName,
            content = content,
            contentType = contentType,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            scanStatus = scanStatus,
            attachmentType = attachmentType,
            application = application,
        )

    fun hankeAttachmentMetadata(
        attachmentId: UUID = randomUUID(),
        fileName: String = FILE_NAME,
        createdByUser: String = currentUserId(),
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        scanStatus: AttachmentScanStatus = OK,
        hankeTunnus: String = "HAI-1234",
    ): HankeAttachmentMetadata =
        HankeAttachmentMetadata(
            id = attachmentId,
            fileName = fileName,
            createdByUserId = createdByUser,
            createdAt = createdAt,
            scanStatus = scanStatus,
            hankeTunnus = hankeTunnus,
        )

    fun applicationAttachmentMetadata(
        attachmentId: UUID = randomUUID(),
        fileName: String = FILE_NAME,
        createdBy: String = currentUserId(),
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        scanStatus: AttachmentScanStatus = OK,
        applicationId: Long = 1L,
        attachmentType: ApplicationAttachmentType = MUU,
    ): ApplicationAttachmentMetadata =
        ApplicationAttachmentMetadata(
            id = attachmentId,
            fileName = fileName,
            createdByUserId = createdBy,
            createdAt = createdAt,
            scanStatus = scanStatus,
            applicationId = applicationId,
            attachmentType = attachmentType
        )
}
