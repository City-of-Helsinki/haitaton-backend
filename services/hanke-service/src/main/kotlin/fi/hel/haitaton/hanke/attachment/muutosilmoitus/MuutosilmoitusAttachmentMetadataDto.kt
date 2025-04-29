package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import java.time.OffsetDateTime
import java.util.UUID

data class MuutosilmoitusAttachmentMetadataDto(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val attachmentType: ApplicationAttachmentType,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val muutosilmoitusId: UUID,
)
