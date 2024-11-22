package fi.hel.haitaton.hanke.allu

data class InformationRequest(
    val applicationId: Int,
    val informationRequestId: Int,
    val fields: List<InformationRequestField>,
)

data class InformationRequestField(
    val requestDescription: String,
    val fieldKey: InformationRequestFieldKey,
)

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
    OTHER;

    companion object {
        fun fromHaitatonFieldName(name: String): InformationRequestFieldKey? =
            when (name) {
                "name" -> null
                "postalAddress" -> POSTAL_ADDRESS
                "constructionWork" -> OTHER
                "maintenanceWork" -> OTHER
                "propertyConnectivity" -> OTHER
                "emergencyWork" -> OTHER
                "rockExcavation" -> OTHER
                "workDescription" -> OTHER
                "startTime" -> START_TIME
                "endTime" -> END_TIME
                "customerWithContacts" -> CUSTOMER
                "contractorWithContacts" -> CONTRACTOR
                "propertyDeveloperWithContacts" -> PROPERTY_DEVELOPER
                "representativeWithContacts" -> REPRESENTATIVE
                "invoicingCustomer" -> INVOICING_CUSTOMER
                else -> {
                    if (name.startsWith("areas")) GEOMETRY else null
                }
            }
    }
}
