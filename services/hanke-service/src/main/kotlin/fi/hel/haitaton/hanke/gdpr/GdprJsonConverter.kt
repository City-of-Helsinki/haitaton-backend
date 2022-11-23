package fi.hel.haitaton.hanke.gdpr

import com.fasterxml.jackson.databind.JsonNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationDto
import fi.hel.haitaton.hanke.allu.CableReportApplication
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import fi.hel.haitaton.hanke.profiili.UserInfo
import org.springframework.stereotype.Component

@Component
class GdprJsonConverter(private val organisaatioService: OrganisaatioService) {

    fun createGdprJson(
        applications: List<ApplicationDto>,
        hankkeet: List<Hanke>,
        userInfo: UserInfo
    ): CollectionNode? {
        val infos: Set<GdprInfo> =
            hankkeet
                .flatMap { getGdprInfosFromHanke(it, userInfo) }
                .union(
                    applications.flatMap {
                        getUserInfosFromApplication(it.applicationData, userInfo)
                    }
                )

        val combinedNodes = combineGdprInfos(infos, userInfo.userId)
        if (combinedNodes.isEmpty()) {
            return null
        }
        return CollectionNode("user", combinedNodes)
    }

    internal fun combineGdprInfos(infos: Collection<GdprInfo>, userId: String): List<Node> {
        if (infos.isEmpty()) {
            return listOf()
        }
        val names = infos.mapNotNull { it.name }.toSet()
        val phones = infos.mapNotNull { it.phone }.toSet()
        val emails = infos.mapNotNull { it.email }.toSet()
        val ipAddresses = infos.mapNotNull { it.ipAddress }.toSet()
        val organisations = infos.mapNotNull { it.organisation }.toSet()
        val addresses = infos.mapNotNull { it.address }.toSet()

        val idNode = StringNode("id", userId)
        val namesNode = combineStrings(names, "nimi", "nimet")
        val phonesNode = combineStrings(phones, "puhelinnumero", "puhelinnumerot")
        val emailsNode = combineStrings(emails, "sahkoposti", "sahkopostit")
        val ipAddressesNode = combineStrings(ipAddresses, "ipOsoite", "ipOsoitteet")
        val organisationsNode = combineOrganisations(organisations)
        val addressesNode = combineAddresses(addresses)
        return listOfNotNull(
            idNode,
            namesNode,
            phonesNode,
            emailsNode,
            ipAddressesNode,
            organisationsNode,
            addressesNode
        )
    }

    private fun combineStrings(data: Set<String>, keySingular: String, keyPlural: String): Node? {
        val nodes = data.map { StringNode(keySingular, it) }
        return combineNodes(nodes, keyPlural)
    }

    internal fun combineOrganisations(organisations: Set<GdprOrganisation>): Node? {
        val nodes = organisations.map { getOrganisationNode(it) }
        return combineNodes(nodes, "organisaatiot")
    }

    private fun getOrganisationNode(organisation: GdprOrganisation): CollectionNode =
        CollectionNode(
            "organisaatio",
            listOfNotNull(
                getIntNode("id", organisation.id),
                getStringNode("nimi", organisation.name),
                getStringNode("tunnus", organisation.registryKey),
                getStringNode("osasto", organisation.department),
            )
        )

    internal fun combineAddresses(addresses: Set<GdprAddress>): Node? {
        val nodes = addresses.map { getAddressNode(it) }
        return combineNodes(nodes, "osoitteet")
    }

    private fun combineNodes(nodes: List<Node>, pluralKey: String): Node? {
        return when (nodes.size) {
            0 -> null
            1 -> nodes.first()
            else -> CollectionNode(pluralKey, nodes)
        }
    }

    private fun getAddressNode(address: GdprAddress): CollectionNode =
        CollectionNode(
            "osoite",
            listOfNotNull(
                getStringNode("katuosoite", address.street),
                getStringNode("postitoimipaikka", address.city),
                getStringNode("postinumero", address.postalCode),
                getStringNode("maa", address.country),
            )
        )

    private fun getStringNode(key: String, value: String?): StringNode? =
        value?.let { StringNode(key, value) }

    private fun getIntNode(key: String, value: Int?): IntNode? = value?.let { IntNode(key, value) }

    internal fun getUserInfosFromApplication(
        applicationData: JsonNode,
        userInfo: UserInfo
    ): List<GdprInfo> {
        val parsedData =
            OBJECT_MAPPER.treeToValue(applicationData, CableReportApplication::class.java)

        return listOf(parsedData.customerWithContacts, parsedData.contractorWithContacts).flatMap {
            getGdprInfosFromCustomerWithContacts(it, userInfo)
        }
    }

    internal fun getGdprInfosFromCustomerWithContacts(
        customerWithContacts: CustomerWithContacts,
        userInfo: UserInfo
    ): List<GdprInfo> {
        val organisation = getOrganisationFromCustomer(customerWithContacts.customer)

        return getGdprInfosFromContacts(customerWithContacts.contacts, organisation, userInfo)
            .plus(getGdprInfoFromCustomer(customerWithContacts.customer, userInfo))
            .filterNotNull()
    }

    internal fun getOrganisationFromCustomer(customer: Customer): GdprOrganisation? =
        if (isOrganisation(customer.type)) {
            GdprOrganisation(name = customer.name, registryKey = customer.registryKey)
        } else {
            null
        }

    private fun isOrganisation(customerType: CustomerType?): Boolean =
        when (customerType) {
            CustomerType.COMPANY -> true
            CustomerType.ASSOCIATION -> true
            else -> false
        }

    internal fun getGdprInfosFromContacts(
        contacts: List<Contact>,
        organisation: GdprOrganisation?,
        userInfo: UserInfo,
    ): List<GdprInfo> =
        contacts.mapNotNull { getGdprInfosFromApplicationContact(it, organisation, userInfo) }

    internal fun getGdprInfosFromApplicationContact(
        contact: Contact,
        organisation: GdprOrganisation?,
        userInfo: UserInfo,
    ): GdprInfo? {
        if (matchFullName(contact.name, userInfo)) {
            return GdprInfo(
                name = contact.name,
                phone = contact.phone,
                email = contact.email,
                address = extractGdprOsoite(contact.postalAddress),
                organisation = organisation,
            )
        }
        return null
    }

    internal fun getGdprInfoFromCustomer(customer: Customer?, userInfo: UserInfo): GdprInfo? {
        if (customer?.type == CustomerType.PERSON) {
            if (matchFullName(customer.name, userInfo)) {
                return GdprInfo(
                    name = customer.name,
                    phone = customer.phone,
                    email = customer.email,
                    address = extractGdprOsoite(customer.postalAddress, customer.country)
                )
            }
        }
        return null
    }

    internal fun extractGdprOsoite(address: PostalAddress?, country: String? = null): GdprAddress? =
        address?.let { GdprAddress(it.streetAddress.streetName, it.city, it.postalCode, country) }

    /** Check if the [fullName] is the same name as the first name and last name in [userInfo]. */
    internal fun matchFullName(fullName: String?, userInfo: UserInfo): Boolean =
        matchFullName(fullName, userInfo.firstName, userInfo.lastName)

    internal fun matchFullName(fullName: String?, firstName: String, lastName: String): Boolean {
        if (fullName == null) return false

        return """(^|\s)${lastName}($|\s)"""
            .toRegex(RegexOption.IGNORE_CASE)
            .containsMatchIn(fullName) &&
            """\b${firstName}\b""".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(fullName)
    }

    internal fun getGdprInfosFromHanke(hanke: Hanke, userInfo: UserInfo): List<GdprInfo> {
        return (hanke.omistajat + hanke.rakennuttajat + hanke.toteuttajat + hanke.muut).mapNotNull {
            getGdprInfoFromHankeYhteystieto(it, userInfo)
        }
    }

    internal fun getGdprInfoFromHankeYhteystieto(
        yhteystieto: HankeYhteystieto,
        userInfo: UserInfo
    ): GdprInfo? {
        if (
            yhteystieto.etunimi != userInfo.firstName || yhteystieto.sukunimi != userInfo.lastName
        ) {
            return null
        }

        val organisaatio = extractOrganisation(yhteystieto)
        return GdprInfo(
            name = "${yhteystieto.etunimi} ${yhteystieto.sukunimi}",
            phone = yhteystieto.puhelinnumero,
            email = yhteystieto.email,
            organisation = organisaatio
        )
    }

    internal fun extractOrganisation(yhteystieto: HankeYhteystieto): GdprOrganisation? {
        if (yhteystieto.organisaatioId != null) {
            val organisaatio = organisaatioService.get(yhteystieto.organisaatioId!!)
            return organisaatio?.let {
                GdprOrganisation(
                    organisaatio.id,
                    organisaatio.nimi,
                    organisaatio.tunnus,
                    yhteystieto.osasto
                )
            }
        } else if (yhteystieto.organisaatioNimi != null) {
            return GdprOrganisation(
                name = yhteystieto.organisaatioNimi,
                department = yhteystieto.osasto
            )
        }
        return null
    }
}
