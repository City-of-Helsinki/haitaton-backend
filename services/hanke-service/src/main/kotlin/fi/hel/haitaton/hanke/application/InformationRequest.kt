package fi.hel.haitaton.hanke.application

data class InformationRequest(
        val applicationId: Int,
        val informationRequestId: Int,
        val fields: List<InformationRequestField>
)

data class InformationRequestField(val requestDescription: String, val fieldKey: InformationRequestFieldKey)

enum class InformationRequestFieldKey {
    CUSTOMER,
    INVOICING_CUSTOMER,
    PROPERTY_DEVELOPER,
    CONTRACTOR,
    REPRESENTATIVE,
    GEOMETRY,
    START_TIME,
    END_TIME,
    IDENTIFICATION_NUMBER,
    CLIENT_APPLICATION_KIND,
    APPLICATION_KIND,
    POSTAL_ADDRESS,
    WORK_DESCRIPTION,
    PROPERTY_IDENTIFICATION_NUMBER,
    ATTACHMENT,
    AREA,
    OTHER
}
