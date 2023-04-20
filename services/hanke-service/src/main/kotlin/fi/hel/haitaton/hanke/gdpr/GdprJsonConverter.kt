package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import fi.hel.haitaton.hanke.profiili.UserInfo
import org.springframework.stereotype.Component

@Component
class GdprJsonConverter(private val organisaatioService: OrganisaatioService) {

    fun createGdprJson(
        applications: List<Application>,
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

        val idNode = StringNode("id", userId)
        val namesNode = combineStrings(names, "nimi", "nimet")
        val phonesNode = combineStrings(phones, "puhelinnumero", "puhelinnumerot")
        val emailsNode = combineStrings(emails, "sahkoposti", "sahkopostit")
        val ipAddressesNode = combineStrings(ipAddresses, "ipOsoite", "ipOsoitteet")
        val organisationsNode = combineOrganisations(organisations)
        return listOfNotNull(
            idNode,
            namesNode,
            phonesNode,
            emailsNode,
            ipAddressesNode,
            organisationsNode,
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

    private fun combineNodes(nodes: List<Node>, pluralKey: String): Node? {
        return when (nodes.size) {
            0 -> null
            1 -> nodes.first()
            else -> CollectionNode(pluralKey, nodes)
        }
    }

    private fun getStringNode(key: String, value: String?): StringNode? =
        value?.let { StringNode(key, value) }

    private fun getIntNode(key: String, value: Int?): IntNode? = value?.let { IntNode(key, value) }

    internal fun getUserInfosFromApplication(
        applicationData: ApplicationData,
        userInfo: UserInfo
    ): List<GdprInfo> {
        return when (applicationData) {
            is CableReportApplicationData ->
                listOfNotNull(
                        applicationData.customerWithContacts,
                        applicationData.contractorWithContacts
                    )
                    .flatMap { getGdprInfosFromCustomerWithContacts(it, userInfo) }
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
        if (contact.fullName() == userInfo.name) {
            return GdprInfo(
                name = contact.fullName(),
                phone = contact.phone,
                email = contact.email,
                organisation = organisation,
            )
        }
        return null
    }

    internal fun getGdprInfoFromCustomer(customer: Customer?, userInfo: UserInfo): GdprInfo? {
        if (customer?.type == CustomerType.PERSON && customer.name == userInfo.name) {
            return GdprInfo(
                name = customer.name,
                phone = customer.phone,
                email = customer.email,
            )
        }
        return null
    }

    internal fun getGdprInfosFromHanke(hanke: Hanke, userInfo: UserInfo): List<GdprInfo> {
        return hanke.extractYhteystiedot().mapNotNull {
            getGdprInfoFromHankeYhteystieto(it, userInfo)
        }
    }

    internal fun getGdprInfoFromHankeYhteystieto(
        yhteystieto: HankeYhteystieto,
        userInfo: UserInfo
    ): GdprInfo? {
        if (yhteystieto.nimi != userInfo.name) {
            return null
        }

        val organisaatio = extractOrganisation(yhteystieto)
        return GdprInfo(
            name = yhteystieto.nimi,
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
