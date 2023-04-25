package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val supportedFiletypes =
    mapOf(
        "application/pdf" to setOf("pdf"),
        "application/msword" to setOf("docx", "doc"),
        "image/jpeg" to setOf("jpg", "jpeg"),
        "image/png" to setOf("png"),
        "image/vnd.dwg" to setOf("dwg", "dws")
    )

private const val FILESIZE_MAXIMUM_MEGABYTES = 10

class AttachmentServiceImpl(
    val hankeRepository: HankeRepository,
    val hankeAttachmentRepository: HankeAttachmentRepository,
) : AttachmentService {

    fun getExtension(filename: String): String {
        return filename.split(".").last().trim()
    }

    fun sanitize(filename: String): String {
        return Regex("[^0-9a-zA-ZäåöÄÖÅ.\\s]").replace(filename.trim(), "")
    }

    @Transactional
    override fun add(hankeTunnus: String, liite: MultipartFile): AttachmentMetadata {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw AttachmentUploadException("Hanke not found")

        val fileName =
            liite.originalFilename ?: throw AttachmentUploadException("Attachment file name null")

        if (fileName.length > 128) {
            throw AttachmentUploadException("File name too long. Maximum is 128.")
        }

        val sanitizedFileName = sanitize(fileName)

        if (!contentTypeMatchesExtension(liite.contentType, getExtension(sanitizedFileName))) {
            throw AttachmentUploadException(
                "File $sanitizedFileName extension does not match content type ${liite.contentType}"
            )
        }

        if (liite.size > 1024 * 1024 * FILESIZE_MAXIMUM_MEGABYTES) {
            throw AttachmentUploadException(
                "File size should not exceed ${FILESIZE_MAXIMUM_MEGABYTES}MB"
            )
        }

        val attachment =
            HankeAttachmentEntity(
                id = null,
                fileName = sanitizedFileName,
                content = liite.bytes,
                createdAt = OffsetDateTime.now(),
                createdByUserId = currentUserId(),
                scanStatus = AttachmentScanStatus.OK,
                hanke = hanke,
            )

        return hankeAttachmentRepository.save(attachment).toMetadata()
    }

    @Transactional
    override fun removeAttachment(id: UUID) {
        hankeAttachmentRepository.deleteById(id)
        hankeAttachmentRepository.flush()
    }

    override fun getHankeAttachments(hankeTunnus: String): List<AttachmentMetadata> {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw HankeNotFoundException(hankeTunnus)
        return hanke.liitteet.map { it.toMetadata() }
    }

    override fun getMetadata(id: UUID): AttachmentMetadata = findAttachment(id).toMetadata()

    override fun getContent(id: UUID): ByteArray {
        val attachment = findAttachment(id)

        if (attachment.scanStatus == AttachmentScanStatus.OK) {
            return attachment.content
        } else {
            throw AttachmentNotFoundException()
        }
    }

    private fun findAttachment(id: UUID) =
        hankeAttachmentRepository.findById(id).orElseThrow { AttachmentNotFoundException() }

    private fun contentTypeMatchesExtension(contentType: String?, extension: String): Boolean {
        if (contentType.isNullOrBlank()) {
            return false
        }
        return supportedFiletypes[contentType]?.contains(extension) ?: false
    }
}
