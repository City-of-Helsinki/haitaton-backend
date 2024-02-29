package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.hakemus.ContactRequest
import fi.hel.haitaton.hanke.hakemus.CustomerRequest
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsRequest
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.PostalAddressRequest
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
                CustomerWithContactsRequest(
                    // new customer without an id
                    CustomerRequest(
                        type = CustomerType.COMPANY,
                        name = "Testiyritys",
                        email = "info@testiyritys.fi",
                        phone = "123456789",
                        registryKey = "1234567-8",
                    ),
                    listOf(
                        // an existing contact person
                        ContactRequest(
                            hankekayttajaId =
                                UUID.fromString("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f")
                        )
                    )
                ),
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null
        )
    }

    fun createJohtoselvityshakemusUpdateRequestFromHakemus(
        hakemus: Hakemus
    ): JohtoselvityshakemusUpdateRequest {
        val applicationData = hakemus.applicationData as JohtoselvityshakemusData
        return JohtoselvityshakemusUpdateRequest(
            name = applicationData.name ?: "",
            postalAddress =
                applicationData.postalAddress?.let { PostalAddressRequest(it.streetAddress) }
                    ?: PostalAddressRequest(StreetAddress("")),
            constructionWork = applicationData.constructionWork ?: false,
            maintenanceWork = applicationData.maintenanceWork ?: false,
            propertyConnectivity = applicationData.propertyConnectivity ?: false,
            emergencyWork = applicationData.emergencyWork ?: false,
            rockExcavation = applicationData.rockExcavation ?: false,
            workDescription = applicationData.workDescription ?: "",
            startTime = applicationData.startTime,
            endTime = applicationData.endTime,
            areas = applicationData.areas,
            customerWithContacts =
                applicationData.customerWithContacts?.toCustomerWithContactsRequest(),
            contractorWithContacts =
                applicationData.contractorWithContacts?.toCustomerWithContactsRequest(),
            propertyDeveloperWithContacts =
                applicationData.propertyDeveloperWithContacts?.toCustomerWithContactsRequest(),
            representativeWithContacts =
                applicationData.representativeWithContacts?.toCustomerWithContactsRequest()
        )
    }

    fun Hakemusyhteystieto?.toCustomerWithContactsRequest() =
        this?.let {
            CustomerWithContactsRequest(
                CustomerRequest(
                    yhteystietoId = it.id,
                    type = it.tyyppi,
                    name = it.nimi,
                    email = it.sahkoposti ?: "",
                    phone = it.puhelinnumero ?: "",
                    registryKey = it.ytunnus
                ),
                it.yhteyshenkilot.map { yhteyshenkilo ->
                    ContactRequest(
                        hankekayttajaId = yhteyshenkilo.hankekayttajaId,
                    )
                }
            )
        }

    fun createCustomerWithContactsRequest(
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
}
