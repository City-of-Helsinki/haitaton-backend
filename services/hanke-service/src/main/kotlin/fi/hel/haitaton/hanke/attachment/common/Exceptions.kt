package fi.hel.haitaton.hanke.attachment.common

import java.util.UUID

class AttachmentLimitReachedException(applicationId: Long, limit: Int) :
    RuntimeException("Attachment amount limit reached, limit=$limit, applicationId=$applicationId")

class AttachmentInvalidException(str: String) :
    RuntimeException("Attachment upload exception: $str")

class AttachmentNotFoundException(id: UUID?) : RuntimeException("Attachment not found, id=$id")

class AttachmentContentNotFoundException(id: UUID) :
    RuntimeException("Attachment content not found, id=$id")
