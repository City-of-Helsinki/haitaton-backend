package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluExcavationNotificationData
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import java.time.ZonedDateTime
import org.geojson.GeometryCollection
import org.springframework.http.MediaType

object AlluFactory {
    fun createAlluApplicationResponse(
        id: Int = 42,
        status: ApplicationStatus = ApplicationStatus.PENDING,
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        applicationId: String = ApplicationFactory.DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER,
        startTime: ZonedDateTime = DateFactory.getStartDatetime(),
        endTime: ZonedDateTime = DateFactory.getEndDatetime(),
    ) =
        AlluApplicationResponse(
            id = id,
            name = name,
            applicationId = applicationId,
            status = status,
            startTime = startTime,
            endTime = endTime,
            owner = null,
            kindsWithSpecifiers = mapOf(),
            terms = null,
            customerReference = null,
            surveyRequired = false)

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

    fun createCableReportApplicationData(
        identificationNumber: String = "HAI-123",
        pendingOnClient: Boolean = false,
        name: String = "Haitaton hankkeen nimi",
        constructionWork: Boolean = true,
        workDescription: String = "Kaivuhommiahan nää tietty",
        clientApplicationKind: String = "Telekaapelin laittoa",
        startTime: ZonedDateTime = ZonedDateTime.now().plusDays(1L),
        endTime: ZonedDateTime = ZonedDateTime.now().plusDays(22L),
        geometry: GeometryCollection = GeometriaFactory.collection(),
        customerWithContacts: CustomerWithContacts =
            CustomerWithContacts(customer = customer, contacts = listOf(hannu)),
        contractorWithContacts: CustomerWithContacts =
            CustomerWithContacts(customer = customer, contacts = listOf(kerttu)),
    ) =
        AlluCableReportApplicationData(
            identificationNumber = identificationNumber,
            pendingOnClient = pendingOnClient,
            name = name,
            postalAddress = null,
            constructionWork = constructionWork,
            maintenanceWork = false,
            propertyConnectivity = false,
            emergencyWork = false,
            workDescription = workDescription,
            clientApplicationKind = clientApplicationKind,
            startTime = startTime,
            endTime = endTime,
            geometry = geometry,
            area = null,
            customerWithContacts = customerWithContacts,
            contractorWithContacts = contractorWithContacts,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null,
            invoicingCustomer = null,
            customerReference = null,
            trafficArrangementImages = null,
        )

    fun createExcavationNotificationData(
        workPurpose: String = "I am a dwarf and I'm diggin' a hole. A diggy, diggy hole."
    ) =
        AlluExcavationNotificationData(
            identificationNumber = "HAI24-55",
            pendingOnClient = false,
            name = "Diggy diggy hole",
            workPurpose = workPurpose,
            clientApplicationKind = workPurpose,
            constructionWork = null,
            maintenanceWork = null,
            emergencyWork = null,
            cableReports = null,
            placementContracts = null,
            startTime = DateFactory.getStartDatetime(),
            endTime = DateFactory.getEndDatetime(),
            geometry = GeometryCollection(),
            area = null,
            postalAddress = null,
            customerWithContacts =
                CustomerWithContacts(customer = customer, contacts = listOf(hannu)),
            contractorWithContacts =
                CustomerWithContacts(customer = customer, contacts = listOf(kerttu)),
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null,
            invoicingCustomer = null,
            customerReference = null,
            additionalInfo = null,
            pksCard = null,
            selfSupervision = null,
            propertyConnectivity = null,
            trafficArrangementImages = null,
            trafficArrangements = null,
            trafficArrangementImpediment = null,
        )

    val customer =
        Customer(
            type = CustomerType.COMPANY,
            name = "Haitaton Oy Ab",
            postalAddress = null,
            country = "FI",
            email = "info@haitaton.fi",
            phone = "042-555-6125",
            registryKey = "101010-FAKE",
            ovt = null,
            invoicingOperator = null,
            sapCustomerNumber = null)
    val hannu =
        Contact(
            name = "Hannu Haitaton",
            email = "hannu@haitaton.fi",
            phone = "042-555-5216",
            orderer = true)
    val kerttu =
        Contact(
            name = "Kerttu Haitaton",
            email = "kerttu@haitaton.fi",
            phone = "042-555-2182",
            orderer = false)
}
