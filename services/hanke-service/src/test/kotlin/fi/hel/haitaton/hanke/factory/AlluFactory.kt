package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import org.springframework.http.MediaType

object AlluFactory {
    fun createAlluApplicationResponse(
        id: Int = 42,
        status: ApplicationStatus = ApplicationStatus.PENDING
    ) =
        AlluApplicationResponse(
            id = id,
            name = ApplicationFactory.DEFAULT_APPLICATION_NAME,
            applicationId = ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER,
            status = status,
            startTime = DateFactory.getStartDatetime(),
            endTime = DateFactory.getEndDatetime(),
            owner = null,
            kindsWithSpecifiers = mapOf(),
            terms = null,
            customerReference = null,
            surveyRequired = false
        )

    fun createAttachmentMetadata(
        id: Int? = null,
        mimeType: String = MediaType.APPLICATION_PDF_VALUE,
        name: String = "file.pdf",
        description: String = "Test description."
    ) =
        AttachmentMetadata(
            id = id,
            mimeType = mimeType,
            name = name,
            description = description,
        )
}
