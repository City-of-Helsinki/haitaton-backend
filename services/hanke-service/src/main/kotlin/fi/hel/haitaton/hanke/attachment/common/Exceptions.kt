package fi.hel.haitaton.hanke.attachment.common

import java.util.UUID

class AttachmentInvalidException(str: String) :
    RuntimeException("Attachment upload exception: $str")

class AttachmentNotFoundException(id: UUID?) : RuntimeException("Attachment '$id' not found")
