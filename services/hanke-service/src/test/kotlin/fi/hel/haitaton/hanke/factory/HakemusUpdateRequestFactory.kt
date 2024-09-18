package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createCableReportApplicationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createExcavationNotificationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createTyoalue
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.ContactRequest
import fi.hel.haitaton.hanke.hakemus.CustomerRequest
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsRequest
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.Hakemusalue
import fi.hel.haitaton.hanke.hakemus.InvoicingCustomerRequest
import fi.hel.haitaton.hanke.hakemus.InvoicingPostalAddressRequest
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.PostalAddressRequest
import fi.hel.haitaton.hanke.hakemus.StreetAddress
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.toJsonString
import java.time.ZonedDateTime
import java.util.UUID

object HakemusUpdateRequestFactory {

    private const val DEFAULT_CUSTOMER_NAME = "Testiyritys"
    private const val DEFAULT_CUSTOMER_EMAIL = "info@testiyritys.fi"
    private const val DEFAULT_CUSTOMER_PHONE = "0401234567"
    private const val DEFAULT_CUSTOMER_REGISTRY_KEY = "3474137-6"
    private const val DEFAULT_CUSTOMER_REFERENCE = "Asiakkaan viite"
    private val DEFAULT_INVOICE_CUSTOMER_ADDRESS =
        InvoicingPostalAddressRequest(StreetAddress("Testikatu 1"), "00100", "Helsinki")
    internal const val DEFAULT_OVT = "003734741376"

    fun createBlankUpdateRequest(type: ApplicationType): HakemusUpdateRequest =
        when (type) {
            ApplicationType.CABLE_REPORT -> createBlankJohtoselvityshakemusUpdateRequest()
            ApplicationType.EXCAVATION_NOTIFICATION -> createBlankKaivuilmoitusUpdateRequest()
        }

    fun createBlankJohtoselvityshakemusUpdateRequest(): JohtoselvityshakemusUpdateRequest =
        JohtoselvityshakemusUpdateRequest(
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
            representativeWithContacts = null,
        )

    fun createBlankKaivuilmoitusUpdateRequest(): KaivuilmoitusUpdateRequest =
        KaivuilmoitusUpdateRequest(
            name = "",
            workDescription = "",
            constructionWork = false,
            maintenanceWork = false,
            emergencyWork = false,
            cableReportDone = false,
            rockExcavation = null,
            cableReports = null,
            placementContracts = null,
            startTime = null,
            endTime = null,
            areas = null,
            customerWithContacts = null,
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null,
            invoicingCustomer = null,
            additionalInfo = null,
        )

    fun createFilledUpdateRequest(type: ApplicationType): HakemusUpdateRequest =
        when (type) {
            ApplicationType.CABLE_REPORT -> createFilledJohtoselvityshakemusUpdateRequest()
            ApplicationType.EXCAVATION_NOTIFICATION -> createFilledKaivuilmoitusUpdateRequest()
        }

    fun createFilledJohtoselvityshakemusUpdateRequest(): JohtoselvityshakemusUpdateRequest =
        JohtoselvityshakemusUpdateRequest(
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
            areas =
                listOf(createCableReportApplicationArea("Hankealue 1", GeometriaFactory.polygon())),
            customerWithContacts =
                createCustomerWithContactsRequest(
                    CustomerType.COMPANY,
                    null,
                    UUID.fromString("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f"),
                ),
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null,
        )

    fun createFilledKaivuilmoitusUpdateRequest(): KaivuilmoitusUpdateRequest =
        KaivuilmoitusUpdateRequest(
            name = "Testihakemus",
            workDescription = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            constructionWork = true,
            maintenanceWork = false,
            emergencyWork = false,
            cableReportDone = false,
            rockExcavation = false,
            startTime = ZonedDateTime.now(TZ_UTC),
            endTime = ZonedDateTime.now(TZ_UTC).plusDays(5),
            areas =
                listOf(
                    createExcavationNotificationArea(
                        "Hankealue 1",
                        tyoalueet = listOf(createTyoalue(GeometriaFactory.polygon())),
                    )),
            customerWithContacts =
                createCustomerWithContactsRequest(
                    CustomerType.COMPANY,
                    null,
                    UUID.fromString("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f"),
                ),
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null,
            invoicingCustomer =
                createInvoicingCustomerRequest(
                    CustomerType.COMPANY,
                    "Testiyritys",
                    DEFAULT_CUSTOMER_REGISTRY_KEY,
                    DEFAULT_OVT,
                    "Välittäjän tunnus",
                    DEFAULT_CUSTOMER_REFERENCE,
                    DEFAULT_INVOICE_CUSTOMER_ADDRESS,
                    DEFAULT_CUSTOMER_EMAIL,
                    DEFAULT_CUSTOMER_PHONE,
                ),
            additionalInfo = "Lisätiedot",
        )

    fun createCustomerWithContactsRequest(
        customerType: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        registryKey: String?,
        vararg hankekayttajaIds: UUID
    ) =
        CustomerWithContactsRequest(
            createCustomer(
                yhteystietoId = yhteystietoId,
                type = customerType,
                registryKey = registryKey,
            ),
            hankekayttajaIds.map { ContactRequest(it) },
        )

    fun createCustomerWithContactsRequest(
        customerType: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        vararg hankekayttajaIds: UUID
    ) =
        createCustomerWithContactsRequest(
            customerType = customerType,
            yhteystietoId = yhteystietoId,
            registryKey = DEFAULT_CUSTOMER_REGISTRY_KEY,
            hankekayttajaIds = hankekayttajaIds,
        )

    fun createCustomer(
        yhteystietoId: UUID? = UUID.randomUUID(),
        type: CustomerType = CustomerType.COMPANY,
        name: String = DEFAULT_CUSTOMER_NAME,
        email: String = DEFAULT_CUSTOMER_EMAIL,
        phone: String = DEFAULT_CUSTOMER_PHONE,
        registryKey: String? = DEFAULT_CUSTOMER_REGISTRY_KEY,
        registryKeyHidden: Boolean = false,
    ) =
        CustomerRequest(
            yhteystietoId = yhteystietoId,
            type = type,
            name = name,
            email = email,
            phone = phone,
            registryKey = registryKey,
            registryKeyHidden = registryKeyHidden,
        )

    private fun createInvoicingCustomerRequest(
        customerType: CustomerType = CustomerType.COMPANY,
        name: String = DEFAULT_CUSTOMER_NAME,
        registryKey: String = DEFAULT_CUSTOMER_REGISTRY_KEY,
        ovt: String? = null,
        invoicingOperator: String? = null,
        customerReference: String? = null,
        postalAddressRequest: InvoicingPostalAddressRequest = DEFAULT_INVOICE_CUSTOMER_ADDRESS,
        email: String = DEFAULT_CUSTOMER_EMAIL,
        phone: String = DEFAULT_CUSTOMER_PHONE,
    ) =
        InvoicingCustomerRequest(
            type = customerType,
            name = name,
            registryKey = registryKey,
            ovt = ovt,
            invoicingOperator = invoicingOperator,
            customerReference = customerReference,
            postalAddress = postalAddressRequest,
            email = email,
            phone = phone,
        )

    fun HakemusUpdateRequest.withCustomerWithContactsRequest(
        type: CustomerType,
        yhteystietoId: UUID? = UUID.randomUUID(),
        vararg hankekayttajaIds: UUID
    ) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest ->
                this.copy(
                    customerWithContacts =
                        createCustomerWithContactsRequest(type, yhteystietoId, *hankekayttajaIds))
            is KaivuilmoitusUpdateRequest ->
                this.copy(
                    customerWithContacts =
                        createCustomerWithContactsRequest(type, yhteystietoId, *hankekayttajaIds))
        }

    fun HakemusUpdateRequest.withCustomer(
        type: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        name: String = DEFAULT_CUSTOMER_NAME,
        email: String = DEFAULT_CUSTOMER_EMAIL,
        phone: String = DEFAULT_CUSTOMER_PHONE,
        registryKey: String = DEFAULT_CUSTOMER_REGISTRY_KEY,
        vararg hankekayttajaIds: UUID
    ) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest ->
                this.copy(
                    customerWithContacts =
                        CustomerWithContactsRequest(
                            CustomerRequest(
                                yhteystietoId = yhteystietoId,
                                type = type,
                                name = name,
                                email = email,
                                phone = phone,
                                registryKey = registryKey,
                            ),
                            hankekayttajaIds.map { ContactRequest(it) },
                        ))
            is KaivuilmoitusUpdateRequest ->
                this.copy(
                    customerWithContacts =
                        CustomerWithContactsRequest(
                            CustomerRequest(
                                yhteystietoId = yhteystietoId,
                                type = type,
                                name = name,
                                email = email,
                                phone = phone,
                                registryKey = registryKey,
                            ),
                            hankekayttajaIds.map { ContactRequest(it) },
                        ))
        }

    fun HakemusUpdateRequest.withName(name: String) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest -> this.copy(name = name)
            is KaivuilmoitusUpdateRequest -> this.copy(name = name)
        }

    fun HakemusUpdateRequest.withWorkDescription(workDescription: String) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest -> this.copy(workDescription = workDescription)
            is KaivuilmoitusUpdateRequest -> this.copy(workDescription = workDescription)
        }

    fun HakemusUpdateRequest.withRequiredCompetence(requiredCompetence: Boolean) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest -> this
            is KaivuilmoitusUpdateRequest -> this.copy(requiredCompetence = requiredCompetence)
        }

    fun HakemusUpdateRequest.withDates(startTime: ZonedDateTime?, endTime: ZonedDateTime?) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest ->
                this.copy(startTime = startTime, endTime = endTime)
            is KaivuilmoitusUpdateRequest -> this.copy(startTime = startTime, endTime = endTime)
        }

    fun HakemusUpdateRequest.withArea(area: Hakemusalue?) = withAreas(area?.let { listOf(it) })

    fun HakemusUpdateRequest.withAreas(areas: List<Hakemusalue>?) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest ->
                this.copy(areas = areas?.map { it as JohtoselvitysHakemusalue })
            is KaivuilmoitusUpdateRequest ->
                this.copy(areas = areas?.map { it as KaivuilmoitusAlue })
        }

    fun HakemusUpdateRequest.withTimes(startTime: ZonedDateTime?, endTime: ZonedDateTime?) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest ->
                this.copy(startTime = startTime, endTime = endTime)
            is KaivuilmoitusUpdateRequest -> this.copy(startTime = startTime, endTime = endTime)
        }

    fun HakemusUpdateRequest.withRegistryKey(registryKey: String) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest ->
                this.copy(
                    customerWithContacts =
                        this.customerWithContacts?.copy(
                            customer =
                                this.customerWithContacts!!
                                    .customer
                                    .copy(registryKey = registryKey)))
            is KaivuilmoitusUpdateRequest ->
                this.copy(
                    customerWithContacts =
                        this.customerWithContacts?.copy(
                            customer =
                                this.customerWithContacts!!
                                    .customer
                                    .copy(registryKey = registryKey)))
        }

    fun KaivuilmoitusUpdateRequest.withInvoicingCustomer(
        type: CustomerType? = CustomerType.COMPANY,
        name: String? = DEFAULT_CUSTOMER_NAME,
        registryKey: String? = DEFAULT_CUSTOMER_REGISTRY_KEY,
        ovt: String? = null,
        invoicingOperator: String? = null,
        customerReference: String? = null,
        postalAddressRequest: InvoicingPostalAddressRequest? = DEFAULT_INVOICE_CUSTOMER_ADDRESS,
        email: String? = DEFAULT_CUSTOMER_EMAIL,
        phone: String? = DEFAULT_CUSTOMER_PHONE,
    ) =
        this.copy(
            invoicingCustomer =
                InvoicingCustomerRequest(
                    type = type,
                    name = name,
                    registryKey = registryKey,
                    ovt = ovt,
                    invoicingOperator = invoicingOperator,
                    customerReference = customerReference,
                    postalAddress = postalAddressRequest,
                    email = email,
                    phone = phone,
                ))

    fun HakemusUpdateRequest.withContractor(
        type: CustomerType = CustomerType.COMPANY,
        yhteystietoId: UUID? = UUID.randomUUID(),
        name: String = DEFAULT_CUSTOMER_NAME,
        email: String = DEFAULT_CUSTOMER_EMAIL,
        phone: String = DEFAULT_CUSTOMER_PHONE,
        registryKey: String = DEFAULT_CUSTOMER_REGISTRY_KEY,
        vararg hankekayttajaIds: UUID
    ) =
        when (this) {
            is JohtoselvityshakemusUpdateRequest ->
                this.copy(
                    contractorWithContacts =
                        CustomerWithContactsRequest(
                            CustomerRequest(
                                yhteystietoId = yhteystietoId,
                                type = type,
                                name = name,
                                email = email,
                                phone = phone,
                                registryKey = registryKey,
                            ),
                            hankekayttajaIds.map { ContactRequest(it) },
                        ))
            is KaivuilmoitusUpdateRequest ->
                this.copy(
                    contractorWithContacts =
                        CustomerWithContactsRequest(
                            CustomerRequest(
                                yhteystietoId = yhteystietoId,
                                type = type,
                                name = name,
                                email = email,
                                phone = phone,
                                registryKey = registryKey,
                            ),
                            hankekayttajaIds.map { ContactRequest(it) },
                        ))
        }

    fun HakemusUpdateRequest.withCustomer(
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
            hankekayttajaIds = hankekayttajaIds,
        )

    fun HakemusUpdateRequest.withContractor(
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
            hankekayttajaIds = hankekayttajaIds,
        )

    fun JohtoselvityshakemusUpdateRequest.withWorkDescription(workDescription: String) =
        this.copy(workDescription = workDescription)

    fun JohtoselvityshakemusUpdateRequest.withTimes(
        startTime: ZonedDateTime?,
        endTime: ZonedDateTime?
    ) = this.copy(startTime = startTime, endTime = endTime)

    fun JohtoselvityshakemusUpdateRequest.withRegistryKey(registryKey: String) =
        this.copy(
            customerWithContacts =
                this.customerWithContacts?.copy(
                    customer =
                        this.customerWithContacts!!.customer.copy(registryKey = registryKey)))

    fun Hakemus.toUpdateRequest(): HakemusUpdateRequest =
        this.toResponse().applicationData.toJsonString().parseJson()
}
