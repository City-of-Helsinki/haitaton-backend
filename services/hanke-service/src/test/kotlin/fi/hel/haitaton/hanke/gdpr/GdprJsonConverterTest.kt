package fi.hel.haitaton.hanke.gdpr

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_PHONE
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TESTIHENKILO
import org.junit.jupiter.api.Test

class GdprJsonConverterTest {

    @Test
    fun `combineGdprInfos with empty infos returns empty list`() {
        val result = GdprJsonConverter.combineGdprInfos(listOf(), "1")

        assertThat(result).isEmpty()
    }

    @Test
    fun `combineGdprInfos with one info returns simple values`() {
        val result = GdprJsonConverter.combineGdprInfos(listOf(teppoGdprInfo()), "1")

        assertThat(result)
            .containsExactlyInAnyOrder(
                StringNode("id", "1"),
                StringNode("etunimi", TEPPO),
                StringNode("sukunimi", TESTIHENKILO),
                StringNode("puhelinnumero", TEPPO_PHONE),
                StringNode("sahkoposti", TEPPO_EMAIL),
            )
    }

    @Test
    fun `combineGdprInfos with several infos combines identical values`() {
        val infos =
            listOf(
                teppoGdprInfo(),
                teppoGdprInfo(email = "toinen@example.test"),
                teppoGdprInfo(phone = "123456", email = "kolmas@example.test")
            )

        val result = GdprJsonConverter.combineGdprInfos(infos, "1")

        assertThat(result)
            .containsExactlyInAnyOrder(
                StringNode("id", "1"),
                StringNode("etunimi", TEPPO),
                StringNode("sukunimi", TESTIHENKILO),
                CollectionNode(
                    "puhelinnumerot",
                    listOf(
                        StringNode("puhelinnumero", TEPPO_PHONE),
                        StringNode("puhelinnumero", "123456")
                    )
                ),
                CollectionNode(
                    "sahkopostit",
                    listOf(
                        StringNode("sahkoposti", TEPPO_EMAIL),
                        StringNode("sahkoposti", "toinen@example.test"),
                        StringNode("sahkoposti", "kolmas@example.test")
                    )
                ),
            )
    }

    @Test
    fun `combineOrganisations with empty set returns null`() {
        val organisations = setOf<GdprOrganisation>()

        assertThat(GdprJsonConverter.combineOrganisations(organisations)).isNull()
    }

    @Test
    fun `combineOrganisations returns single organisation as a collection node`() {
        val organisations = setOf(dnaGdprOrganisation())

        val result = GdprJsonConverter.combineOrganisations(organisations)

        assertThat(result).isNotNull()
        result as CollectionNode
        assertThat(result.key).isEqualTo("organisaatio")
        assertThat(result.children).isNotNull()
        assertThat(result.children).hasSize(4)
        assertThat(result.children.map { it.key })
            .containsExactlyInAnyOrder("id", "nimi", "tunnus", "osasto")
    }

    @Test
    fun `combineOrganisations combines multiple organisations under a collection node`() {
        val organisations = setOf(dnaGdprOrganisation(), dnaGdprOrganisation(department = null))

        val result = GdprJsonConverter.combineOrganisations(organisations)

        assertThat(result).isNotNull()
        result as CollectionNode
        assertThat(result.key).isEqualTo("organisaatiot")
        assertThat(result.children).isNotNull()
        assertThat(result.children).hasSize(2)
        assertThat(result.children[0].key).isEqualTo("organisaatio")
        assertThat(result.children[1].key).isEqualTo("organisaatio")
    }

    private fun teppoGdprInfo(
        firstName: String? = TEPPO,
        lastName: String? = TESTIHENKILO,
        phone: String? = TEPPO_PHONE,
        email: String? = TEPPO_EMAIL,
        ipAddress: String? = null,
        organisation: GdprOrganisation? = null,
    ) = GdprInfo(firstName, lastName, phone, email, ipAddress, organisation)

    private fun dnaGdprOrganisation(
        id: Int? = 1,
        name: String? = "DNA",
        registryKey: String? = "3766028-0",
        department: String? = "Osasto",
    ) = GdprOrganisation(id, name, registryKey, department)
}
