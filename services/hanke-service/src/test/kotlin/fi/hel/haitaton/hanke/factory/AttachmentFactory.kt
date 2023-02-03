package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID

object AttachmentFactory {
    fun hankeAttachment(
        attachmentId: UUID = UUID.randomUUID(),
        fileName: String = "file.pdf",
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

    fun applicationAttachment(
        attachmentId: UUID = UUID.randomUUID(),
        fileName: String = "file.pdf",
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
