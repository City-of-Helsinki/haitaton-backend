package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentContentService
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class AttachmentUploadService(
    private val hankeAttachmentService: HankeAttachmentService,
    private val hankeAttachmentContentService: HankeAttachmentContentService,
    private val scanClient: FileScanClient,
) {
    fun uploadHankeAttachment(
        hankeTunnus: String,
        attachment: MultipartFile,
    ): HankeAttachment {
        val hanke = hankeAttachmentService.hankeWithRoomForAttachment(hankeTunnus)

        val (filename, mediatype) = attachment.validNameAndType()

        scanAttachment(filename, attachment.bytes)

        val blobPath =
            hankeAttachmentContentService.upload(
                fileName = filename,
                contentType = mediatype,
                content = attachment.bytes,
                hankeId = hanke.id,
            )

        return hankeAttachmentService
            .saveAttachment(
                hankeTunnus = hanke.hankeTunnus,
                name = filename,
                type = mediatype.toString(),
                blobPath = blobPath,
            )
            .also { logger.info { "Added attachment ${it.id} to hanke ${hanke.hankeTunnus}" } }
    }

    private fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }
}
