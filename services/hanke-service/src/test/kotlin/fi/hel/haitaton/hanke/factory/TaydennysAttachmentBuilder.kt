package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository

data class TaydennysAttachmentBuilder(
    val value: TaydennysAttachmentEntity,
    val attachmentRepository: TaydennysAttachmentRepository,
    val attachmentFactory: TaydennysAttachmentFactory,
)
