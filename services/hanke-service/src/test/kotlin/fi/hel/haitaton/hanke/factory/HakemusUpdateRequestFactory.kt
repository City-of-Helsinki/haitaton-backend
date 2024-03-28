package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.hakemus.ContactRequest
import fi.hel.haitaton.hanke.hakemus.CustomerRequest
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsRequest
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.PostalAddressRequest
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.toJsonString
import java.time.ZonedDateTime
import java.util.UUID

object HakemusUpdateRequestFactory {

    private const val DEFAULT_CUSTOMER_NAME = "Testiyritys"
    private const val DEFAULT_CUSTOMER_EMAIL = "info@testiyritys.fi"
    private const val DEFAULT_CUSTOMER_PHONE = "0401234567"
    private const val DEFAULT_CUSTOMER_REGISTRY_KEY = "3474137-6"

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
            endTime = ZonedDateTime.now(TZ_UTC).plusDays(5),
            areas = listOf(ApplicationArea("Hankealue 1", GeometriaFactory.polygon)),
            customerWithContacts =
                CustomerWithContactsRequest(
                    CustomerRequest(
                        yhteystietoId = UUID.randomUUID(),
                        type = CustomerType.COMPANY,
                        name = DEFAULT_CUSTOMER_NAME,
                        email = DEFAULT_CUSTOMER_EMAIL,
                        phone = DEFAULT_CUSTOMER_PHONE,
                        registryKey = DEFAULT_CUSTOMER_REGISTRY_KEY
                    ),
                    listOf(ContactRequest(UUID.fromString("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f")))
                ),
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null
        )
    }

    fun JohtoselvityshakemusUpdateRequest.withCustomer(
        type: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        name: String = DEFAULT_CUSTOMER_NAME,
        email: String = DEFAULT_CUSTOMER_EMAIL,
        phone: String = DEFAULT_CUSTOMER_PHONE,
        registryKey: String = DEFAULT_CUSTOMER_REGISTRY_KEY,
        vararg hankekayttajaIds: UUID
    ) =
        this.copy(
            customerWithContacts =
                CustomerWithContactsRequest(
                    CustomerRequest(
                        yhteystietoId = yhteystietoId,
                        type = type,
                        name = name,
                        email = email,
                        phone = phone,
                        registryKey = registryKey
                    ),
                    hankekayttajaIds.map { ContactRequest(it) }
                )
        )

    fun JohtoselvityshakemusUpdateRequest.withContractor(
        type: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        name: String = DEFAULT_CUSTOMER_NAME,
        email: String = DEFAULT_CUSTOMER_EMAIL,
        phone: String = DEFAULT_CUSTOMER_PHONE,
        registryKey: String = DEFAULT_CUSTOMER_REGISTRY_KEY,
        vararg hankekayttajaIds: UUID
    ) =
        this.copy(
            contractorWithContacts =
                CustomerWithContactsRequest(
                    CustomerRequest(
                        yhteystietoId = yhteystietoId,
                        type = type,
                        name = name,
                        email = email,
                        phone = phone,
                        registryKey = registryKey
                    ),
                    hankekayttajaIds.map { ContactRequest(it) }
                )
        )

    fun JohtoselvityshakemusUpdateRequest.withCustomer(
        type: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        vararg hankekayttajaIds: UUID
    ) =
        withCustomer(
            type = type,
            yhteystietoId = yhteystietoId,
            name = DEFAULT_CUSTOMER_NAME,
            email = DEFAULT_CUSTOMER_EMAIL,
            phone = DEFAULT_CUSTOMER_PHONE,
            registryKey = DEFAULT_CUSTOMER_REGISTRY_KEY,
            hankekayttajaIds = hankekayttajaIds
        )

    fun JohtoselvityshakemusUpdateRequest.withContractor(
        type: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        vararg hankekayttajaIds: UUID
    ) =
        withContractor(
            type = type,
            yhteystietoId = yhteystietoId,
            name = DEFAULT_CUSTOMER_NAME,
            email = DEFAULT_CUSTOMER_EMAIL,
            phone = DEFAULT_CUSTOMER_PHONE,
            registryKey = DEFAULT_CUSTOMER_REGISTRY_KEY,
            hankekayttajaIds = hankekayttajaIds
        )

    fun JohtoselvityshakemusUpdateRequest.withWorkDescription(workDescription: String) =
        this.copy(workDescription = workDescription)

    fun JohtoselvityshakemusUpdateRequest.withArea(area: ApplicationArea) =
        this.copy(areas = (areas ?: listOf()) + area)

    fun JohtoselvityshakemusUpdateRequest.withTimes(
        startTime: ZonedDateTime?,
        endTime: ZonedDateTime?
    ) = this.copy(startTime = startTime, endTime = endTime)

    fun JohtoselvityshakemusUpdateRequest.withRegistryKey(registryKey: String) =
        this.copy(
            customerWithContacts =
                this.customerWithContacts?.copy(
                    customer = this.customerWithContacts!!.customer.copy(registryKey = registryKey)
                )
        )

    fun HakemusResponse.toUpdateRequest(): JohtoselvityshakemusUpdateRequest =
        this.applicationData.toJsonString().parseJson()

    fun Hakemus.toUpdateRequest(): JohtoselvityshakemusUpdateRequest =
        this.applicationData.toJsonString().parseJson()
}
