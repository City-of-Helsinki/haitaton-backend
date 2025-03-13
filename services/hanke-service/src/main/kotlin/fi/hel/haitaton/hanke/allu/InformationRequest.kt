package fi.hel.haitaton.hanke.allu

import fi.hel.haitaton.hanke.hakemus.ApplicationType

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
        fun fromHaitatonFieldNames(names: List<String>, applicationType: ApplicationType) =
            names
                .mapNotNull {
                    InformationRequestFieldKey.fromHaitatonFieldName(it, applicationType)
                }
                .toSet()

        fun fromHaitatonFieldName(
            name: String,
            applicationType: ApplicationType,
        ): InformationRequestFieldKey? =
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
                "attachment" -> ATTACHMENT
                else -> {
                    when {
                        name.matches(Regex("areas\\[\\d+]")) &&
                            applicationType == ApplicationType.CABLE_REPORT -> GEOMETRY
                        name.matches(Regex("areas\\[\\d+]\\.tyoalueet\\[\\d+]")) -> GEOMETRY
                        name.matches(
                            Regex("areas\\[\\d+]\\.haittojenhallintasuunnitelma\\[[A-Z]+]")
                        ) -> ATTACHMENT
                        else -> null
                    }
                }
            }
    }
}
