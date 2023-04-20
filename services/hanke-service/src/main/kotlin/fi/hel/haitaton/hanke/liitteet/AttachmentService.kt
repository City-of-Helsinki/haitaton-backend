package fi.hel.haitaton.hanke.liitteet

import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
interface AttachmentService {
    fun getHankeAttachments(hankeTunnus: String): List<AttachmentMetadata>

    fun get(uuid: UUID): AttachmentMetadata

    fun getContent(id: UUID): ByteArray

    fun add(hankeTunnus: String, liite: MultipartFile): AttachmentMetadata

    fun removeAttachment(uuid: UUID)
}

class AttachmentUploadError(str: String) : RuntimeException("Attachment upload error: $str")

class AttachmentNotFoundException() : RuntimeException("Attachment not found")
