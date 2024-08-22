package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.permissions.HankeKayttaja

object GdprJsonConverter {

    fun createGdprJson(
        kayttajat: List<HankeKayttaja>,
        hankeYhteystiedot: List<HankeYhteystieto>,
        hakemusyhteystiedot: List<Hakemusyhteystieto>,
        userId: String
    ): CollectionNode? {
        val basicInfos = kayttajat.map { getGdprInfosFromHankekayttaja(it) }
        val hankeOrganisaatiot =
            hankeYhteystiedot
                .map { getOrganisationFromHankeyhteystieto(it) }
                .map { GdprInfo(organisation = it) }
        val hakemusOrganisaatiot =
            hakemusyhteystiedot
                .map { getOrganisationFromHakemusyhteystieto(it) }
                .map { GdprInfo(organisation = it) }

        val combinedNodes =
            combineGdprInfos(basicInfos + hankeOrganisaatiot + hakemusOrganisaatiot, userId)
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

    private fun getGdprInfosFromHankekayttaja(kayttaja: HankeKayttaja) =
        GdprInfo(
            firstName = kayttaja.etunimi,
            lastName = kayttaja.sukunimi,
            phone = kayttaja.puhelinnumero,
            email = kayttaja.sahkoposti
        )

    private fun getOrganisationFromHankeyhteystieto(yhteystieto: HankeYhteystieto) =
        GdprOrganisation(
            name = yhteystieto.nimi,
            registryKey = yhteystieto.ytunnus,
            department = yhteystieto.osasto
        )

    private fun getOrganisationFromHakemusyhteystieto(yhteystieto: Hakemusyhteystieto) =
        GdprOrganisation(
            name = yhteystieto.nimi,
            registryKey = yhteystieto.ytunnus,
        )
}
