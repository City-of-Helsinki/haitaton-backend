package fi.hel.haitaton.hanke.attachment

import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
interface AttachmentService {
    fun getHankeAttachments(hankeTunnus: String): List<AttachmentMetadata>

    fun getMetadata(id: UUID): AttachmentMetadata

    fun getContent(id: UUID): ByteArray

    fun add(hankeTunnus: String, liite: MultipartFile): AttachmentMetadata

    fun removeAttachment(id: UUID)
}

class AttachmentUploadException(str: String) :
    RuntimeException("Attachment upload exception: $str")

class AttachmentNotFoundException() : RuntimeException("Attachment not found")
