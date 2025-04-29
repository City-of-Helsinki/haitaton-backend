package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.domain.Loggable
import java.util.UUID

class AttachmentLimitReachedException : RuntimeException {
    constructor(
        applicationId: Long
    ) : super(
        "Attachment amount limit reached, limit=$ALLOWED_ATTACHMENT_COUNT, applicationId=$applicationId"
    )

    constructor(
        entity: Loggable
    ) : super(
        "Attachment amount limit reached, limit=$ALLOWED_ATTACHMENT_COUNT, ${entity.logString()}"
    )
}

class AttachmentInvalidException(str: String) :
    RuntimeException("Attachment upload exception: $str")

class AttachmentNotFoundException(id: UUID?) : RuntimeException("Attachment not found, id=$id")

class ValtakirjaForbiddenException(id: UUID) :
    RuntimeException("Valtakirja download forbidden, id=$id")
