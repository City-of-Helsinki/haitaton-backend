package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.getCurrentTimeUTCAsLocalTime
import java.util.UUID
import javax.persistence.EntityNotFoundException
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException
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
    val attachmentRepository: AttachmentRepository,
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
                ?: throw AttachmentUploadError("Hanke not found")

        if (!supportedFiletypes.contains(liite.contentType)) {
            throw AttachmentUploadError("Content type not supported: " + liite.contentType)
        }

        if (liite.name.length > 128) {
            throw AttachmentUploadError("File name too long. Maximum is 128.")
        }

        val extension = getExtension(liite.name)
        if (!supportedFiletypes.get(liite.contentType)!!.contains(extension)) {
            throw AttachmentUploadError(
                "File extension does not match content type ${liite.contentType}"
            )
        }

        if (liite.size > 1024 * 1024 * FILESIZE_MAXIMUM_MEGABYTES) {
            throw AttachmentUploadError(
                "File size should not exceed ${FILESIZE_MAXIMUM_MEGABYTES}MB"
            )
        }

        val sanitizedFilename = sanitize(liite.name)

        val attachment =
            HankeAttachmentEntity(
                name = sanitizedFilename,
                content = liite.bytes,
                createdAt = getCurrentTimeUTCAsLocalTime(),
                createdByUserId = currentUserId(),
                hanke = hanke,
                scanStatus =
                    AttachmentScanStatus.OK // FIXME this should be set to PENDING before launch
            )

        val savedAttachment = attachmentRepository.save(attachment)
        return savedAttachment.toAttachment()
    }

    @Transactional
    override fun removeAttachment(uuid: UUID) {
        attachmentRepository.deleteById(uuid)
        attachmentRepository.flush()
    }

    override fun getHankeAttachments(hankeTunnus: String): List<AttachmentMetadata> {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw AttachmentUploadError("Hanke not found")
        val allByHanke = attachmentRepository.findAllByHanke(hanke)
        return allByHanke.map { it.toAttachment() }
    }

    override fun get(uuid: UUID): AttachmentMetadata {
        try {
            val attachment = attachmentRepository.getOne(uuid)
            return attachment.toAttachment()
        } catch (ex: EntityNotFoundException) {
            throw AttachmentNotFoundException()
        } catch (ex: JpaObjectRetrievalFailureException) {
            throw AttachmentNotFoundException()
        }
    }

    override fun getContent(id: UUID): ByteArray {
        try {
            val attachment = attachmentRepository.getOne(id)
            if (attachment.scanStatus == AttachmentScanStatus.OK) {
                return attachment.content
            } else {
                throw AttachmentNotFoundException()
            }
        } catch (ex: EntityNotFoundException) {
            throw AttachmentNotFoundException()
        }
    }
}
