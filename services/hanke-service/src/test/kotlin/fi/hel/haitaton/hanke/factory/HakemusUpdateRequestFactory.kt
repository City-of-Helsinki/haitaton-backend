package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.hakemus.ContactRequest
import fi.hel.haitaton.hanke.hakemus.CustomerRequest
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsRequest
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.PostalAddressRequest
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.toJsonString
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

object HakemusUpdateRequestFactory {

    fun createBlankJohtoselvityshakemusUpdateRequest(): JohtoselvityshakemusUpdateRequest {
        return JohtoselvityshakemusUpdateRequest(
            name = "",
            postalAddress = PostalAddressRequest(StreetAddress("")),
            constructionWork = false,
            maintenanceWork = false,
            propertyConnectivity = false,
            emergencyWork = false,
            rockExcavation = false,
            workDescription = "",
            startTime = null,
            endTime = null,
            areas = null,
            customerWithContacts = null,
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null
        )
    }

    fun createFilledJohtoselvityshakemusUpdateRequest(): JohtoselvityshakemusUpdateRequest {
        return JohtoselvityshakemusUpdateRequest(
            name = "Testihakemus",
            postalAddress = PostalAddressRequest(StreetAddress("Kotikatu 1")),
            constructionWork = true,
            maintenanceWork = false,
            propertyConnectivity = false,
            emergencyWork = false,
            rockExcavation = false,
            workDescription = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            startTime = ZonedDateTime.now(TZ_UTC),
            endTime = ZonedDateTime.ofInstant(Instant.now().plus(5, ChronoUnit.DAYS), TZ_UTC),
            areas = listOf(ApplicationArea("Hankealue 1", GeometriaFactory.polygon)),
            customerWithContacts =
                createCustomerWithContactsRequest(
                    CustomerType.COMPANY,
                    null,
                    UUID.fromString("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f")
                ),
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null
        )
    }

    fun createJohtoselvityshakemusUpdateRequestFromApplicationEntity(
        applicationEntity: ApplicationEntity
    ): JohtoselvityshakemusUpdateRequest {
        val applicationData = applicationEntity.applicationData as CableReportApplicationData
        return JohtoselvityshakemusUpdateRequest(
            name = applicationData.name,
            postalAddress =
                applicationData.postalAddress?.let { PostalAddressRequest(it.streetAddress) }
                    ?: PostalAddressRequest(StreetAddress("")),
            constructionWork = applicationData.constructionWork,
            maintenanceWork = applicationData.maintenanceWork,
            propertyConnectivity = applicationData.propertyConnectivity,
            emergencyWork = applicationData.emergencyWork,
            rockExcavation = applicationData.rockExcavation ?: false,
            workDescription = applicationData.workDescription,
            startTime = applicationData.startTime,
            endTime = applicationData.endTime,
            areas = applicationData.areas,
        )
    }

    private fun createCustomerWithContactsRequest(
        customerType: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        vararg hankekayttajaIds: UUID
    ) =
        CustomerWithContactsRequest(
            CustomerRequest(
                yhteystietoId = yhteystietoId,
                type = customerType,
                name = "Testiyritys",
                email = "info@testiyritys.fi",
                phone = "0401234567",
                registryKey = "1234567-8",
            ),
            hankekayttajaIds.map { ContactRequest(it) }
        )

    fun JohtoselvityshakemusUpdateRequest.withCustomerWithContactsRequest(
        customerType: CustomerType,
        yhteystietoId: UUID? = UUID.randomUUID(),
        vararg hankekayttajaIds: UUID
    ) =
        this.copy(
            customerWithContacts =
                createCustomerWithContactsRequest(customerType, yhteystietoId, *hankekayttajaIds)
        )

    fun JohtoselvityshakemusUpdateRequest.withWorkDescription(workDescription: String) =
        this.copy(workDescription = workDescription)

    fun JohtoselvityshakemusUpdateRequest.withAreas(areas: List<ApplicationArea>) =
        this.copy(areas = areas)

    fun HakemusResponse.toUpdateRequest(): JohtoselvityshakemusUpdateRequest =
        this.applicationData.toJsonString().parseJson()
}
