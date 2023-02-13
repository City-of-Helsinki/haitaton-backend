package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime

interface CableReportService {

    fun getCurrentStatus(applicationId: Int): ApplicationStatus?
    fun getApplicationStatusEvents(
        applicationId: Int,
        eventsAfter: ZonedDateTime
    ): List<ApplicationStatusEvent>

    fun getApplicationStatusHistories(
        applicationIds: List<Int>,
        eventsAfter: ZonedDateTime
    ): List<ApplicationHistory>

    fun create(cableReport: AlluCableReportApplicationData): Int
    fun update(applicationId: Int, cableReport: AlluCableReportApplicationData)

    fun addAttachment(applicationId: Int, metadata: AttachmentInfo, file: ByteArray)

    fun getInformationRequests(applicationId: Int): List<InformationRequest>
    fun respondToInformationRequest(
        applicationId: Int,
        requestId: Int,
        cableReport: AlluCableReportApplicationData,
        updatedFields: List<InformationRequestFieldKey>
    )

    fun getDecisionPDF(applicationId: Int): ByteArray
    fun getDecisionAttachments(applicationId: Int): List<AttachmentInfo>
    fun getDecisionAttachmentData(applicationId: Int, attachmentId: Int): ByteArray
    fun getApplicationInformation(applicationId: Int): AlluApplicationResponse
}
