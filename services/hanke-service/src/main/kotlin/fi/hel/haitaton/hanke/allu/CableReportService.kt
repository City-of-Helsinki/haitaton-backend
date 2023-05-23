package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime

interface CableReportService {

    fun getApplicationStatusHistories(
        applicationIds: List<Int>,
        eventsAfter: ZonedDateTime
    ): List<ApplicationHistory>

    fun create(cableReport: AlluCableReportApplicationData): Int

    fun update(applicationId: Int, cableReport: AlluCableReportApplicationData)

    fun addAttachment(applicationId: Int, attachment: Attachment)

    fun addAttachments(alluId: Int, attachments: List<Attachment>)

    fun getInformationRequests(applicationId: Int): List<InformationRequest>

    fun respondToInformationRequest(
        applicationId: Int,
        requestId: Int,
        cableReport: AlluCableReportApplicationData,
        updatedFields: List<InformationRequestFieldKey>
    )

    fun getDecisionPdf(applicationId: Int): ByteArray

    fun getDecisionAttachments(applicationId: Int): List<AttachmentMetadata>

    fun getDecisionAttachmentData(applicationId: Int, attachmentId: Int): ByteArray

    fun getApplicationInformation(applicationId: Int): AlluApplicationResponse

    fun cancel(applicationId: Int)
}
