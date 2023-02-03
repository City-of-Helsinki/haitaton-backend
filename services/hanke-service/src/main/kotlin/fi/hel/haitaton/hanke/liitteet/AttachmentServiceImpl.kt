package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.getCurrentTimeUTCAsLocalTime
import java.util.*
import javax.persistence.EntityNotFoundException
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val supportedFiletypes =
    setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "image/jpeg",
        "image/png",
        "image/vnd.dwg"
    )

private const val FILESIZE_MAXIMUM_MEGABYTES = 10

class AttachmentServiceImpl(
    val hankeRepository: HankeRepository,
    val attachmentRepository: AttachmentRepository,
) : AttachmentService {
    @Transactional
    override fun add(hankeTunnus: String, liite: MultipartFile): HankeAttachment {
        if (!supportedFiletypes.contains(liite.contentType)) {
            throw AttachmentUploadError("Filetype not supported: " + liite.contentType)
        }

        if (liite.size > 1024 * 1024 * FILESIZE_MAXIMUM_MEGABYTES) {
            throw AttachmentUploadError(
                "File size should not exceed ${FILESIZE_MAXIMUM_MEGABYTES}Mb"
            )
        }

        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw AttachmentUploadError("Hanke not found")

        val attachment =
            HankeAttachmentEntity(
                name = liite.name,
                data = liite.bytes,
                created = getCurrentTimeUTCAsLocalTime(),
                username = currentUserId(),
                hanke = hanke,
                tila = AttachmentTila.OK // FIXME this should be set to PENDING before launch
            )

        val savedAttachment = attachmentRepository.save(attachment)
        val result = savedAttachment.toAttachment()
        return result
    }

    @Transactional
    override fun removeAttachment(uuid: UUID) {
        attachmentRepository.deleteById(uuid)
        attachmentRepository.flush()
    }

    override fun getHankeAttachments(hankeTunnus: String): List<HankeAttachment> {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw AttachmentUploadError("Hanke not found")
        val allByHanke = attachmentRepository.findAllByHanke(hanke)
        return allByHanke.map { it.toAttachment() }
    }

    override fun get(uuid: UUID): HankeAttachment {
        try {
            val attachment = attachmentRepository.getOne(uuid)
            return attachment.toAttachment()
        } catch (ex: EntityNotFoundException) {
            throw AttachmentNotFoundException()
        } catch (ex: JpaObjectRetrievalFailureException) {
            throw AttachmentNotFoundException()
        }
    }

    override fun getData(id: UUID): ByteArray {
        try {
            val attachment = attachmentRepository.getOne(id)
            if (attachment.tila == AttachmentTila.OK) {
                return attachment.data
            } else {
                throw AttachmentNotFoundException()
            }
        } catch (ex: EntityNotFoundException) {
            throw AttachmentNotFoundException()
        }
    }
}
