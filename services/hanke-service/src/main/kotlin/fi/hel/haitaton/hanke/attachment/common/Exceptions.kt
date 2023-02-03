package fi.hel.haitaton.hanke.attachment.common

class AttachmentUploadException(str: String) :
    RuntimeException("Attachment upload exception: $str")

class AttachmentNotFoundException() : RuntimeException("Attachment not found")
