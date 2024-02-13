package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts

object GdprJsonConverter {

    fun createGdprJson(applications: List<Application>, userId: String): CollectionNode? {
        val infos: Set<GdprInfo> =
            applications.flatMap { getCreatorInfoFromApplication(it.applicationData) }.toSet()

        val combinedNodes = combineGdprInfos(infos, userId)
        if (combinedNodes.isEmpty()) {
            return null
        }
        return CollectionNode("user", combinedNodes)
    }

    internal fun combineGdprInfos(infos: Collection<GdprInfo>, userId: String): List<Node> {
        if (infos.isEmpty()) {
            return listOf()
        }
        val firstNames = infos.mapNotNull { it.firstName }.toSet()
        val lastNames = infos.mapNotNull { it.lastName }.toSet()
        val phones = infos.mapNotNull { it.phone }.toSet()
        val emails = infos.mapNotNull { it.email }.toSet()
        val ipAddresses = infos.mapNotNull { it.ipAddress }.toSet()
        val organisations = infos.mapNotNull { it.organisation }.toSet()

        val idNode = StringNode("id", userId)
        val firstNamesNode = combineStrings(firstNames, "etunimi", "etunimet")
        val lastNamesNode = combineStrings(lastNames, "sukunimi", "sukunimet")
        val phonesNode = combineStrings(phones, "puhelinnumero", "puhelinnumerot")
        val emailsNode = combineStrings(emails, "sahkoposti", "sahkopostit")
        val ipAddressesNode = combineStrings(ipAddresses, "ipOsoite", "ipOsoitteet")
        val organisationsNode = combineOrganisations(organisations)
        return listOfNotNull(
            idNode,
            firstNamesNode,
            lastNamesNode,
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

    internal fun getCreatorInfoFromApplication(applicationData: ApplicationData): List<GdprInfo> {
        return when (applicationData) {
            is CableReportApplicationData ->
                applicationData
                    .customersWithContacts()
                    .filter { it.contacts.any { contact -> contact.orderer } }
                    .flatMap { getCreatorInfoFromCustomerWithContacts(it) }
        }
    }

    internal fun getCreatorInfoFromCustomerWithContacts(
        customerWithContacts: CustomerWithContacts,
    ): List<GdprInfo> {
        val organisation = getOrganisationFromCustomer(customerWithContacts.customer)

        val orderers = customerWithContacts.contacts.filter { it.orderer }

        return getGdprInfosFromContacts(orderers, organisation)
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
    ): List<GdprInfo> = contacts.map { getGdprInfosFromApplicationContact(it, organisation) }

    internal fun getGdprInfosFromApplicationContact(
        contact: Contact,
        organisation: GdprOrganisation?,
    ): GdprInfo {
        return GdprInfo(
            firstName = contact.firstName,
            lastName = contact.lastName,
            phone = contact.phone,
            email = contact.email,
            organisation = organisation,
        )
    }
}
