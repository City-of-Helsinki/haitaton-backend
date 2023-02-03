package fi.hel.haitaton.hanke.liitteet

import java.util.*
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
interface AttachmentService {
    fun getHankeAttachments(hankeTunnus: String): List<HankeAttachment>

    fun get(uuid: UUID): HankeAttachment

    fun getData(id: UUID): ByteArray

    fun add(hankeTunnus: String, liite: MultipartFile): HankeAttachment

    fun removeAttachment(uuid: UUID)
}

class AttachmentUploadError(str: String) : RuntimeException("Attachment upload error: $str")

class AttachmentNotFoundException() : RuntimeException("Attachment not found")
