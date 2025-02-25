package fi.hel.haitaton.hanke.attachment.common

import java.util.UUID

class AttachmentLimitReachedException : RuntimeException {
    constructor(
        applicationId: Long,
        limit: Int,
    ) : super("Attachment amount limit reached, limit=$limit, applicationId=$applicationId")

    constructor(
        taydennysId: UUID,
        limit: Int,
    ) : super("Attachment amount limit reached, limit=$limit, taydennysId=$taydennysId")
}

class AttachmentInvalidException(str: String) :
    RuntimeException("Attachment upload exception: $str")

class AttachmentNotFoundException(id: UUID?) : RuntimeException("Attachment not found, id=$id")

class ValtakirjaForbiddenException(id: UUID) :
    RuntimeException("Valtakirja download forbidden, id=$id")
