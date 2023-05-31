package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime

interface CableReportService {

    fun getApplicationStatusHistories(
        alluApplicationIds: List<Int>,
        eventsAfter: ZonedDateTime
    ): List<ApplicationHistory>

    fun create(cableReport: AlluCableReportApplicationData): Int

    fun update(alluApplicationId: Int, cableReport: AlluCableReportApplicationData)

    fun addAttachment(alluApplicationId: Int, attachment: Attachment)

    fun addAttachments(alluApplicationId: Int, attachments: List<Attachment>)

    fun getInformationRequests(alluApplicationId: Int): List<InformationRequest>

    fun respondToInformationRequest(
        alluApplicationId: Int,
        requestId: Int,
        cableReport: AlluCableReportApplicationData,
        updatedFields: List<InformationRequestFieldKey>
    )

    fun getDecisionPdf(alluApplicationId: Int): ByteArray

    fun getDecisionAttachments(alluApplicationId: Int): List<AttachmentMetadata>

    fun getDecisionAttachmentData(alluApplicationId: Int, attachmentId: Int): ByteArray

    fun getApplicationInformation(alluApplicationId: Int): AlluApplicationResponse

    fun cancel(alluApplicationId: Int)

    fun sendSystemComment(alluApplicationId: Int, msg: String): Int
}
