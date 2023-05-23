package fi.hel.haitaton.hanke.gdpr

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withCustomer
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GdprJsonConverterTest {

    @Test
    fun `createGdprJson with empty list returns null`() {
        val result = GdprJsonConverter.createGdprJson(listOf(), "user")

        assertThat(result).isNull()
    }

    @Test
    fun `createGdprJson combines identical results when there are several infos`() {
        val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()
        val application = AlluDataFactory.createApplication(applicationData = applicationData)
        val otherApplication =
            application.withCustomer(
                AlluDataFactory.createCompanyCustomer(name = "Yhteystieto Oy", registryKey = null)
                    .withContacts(
                        AlluDataFactory.createContact(
                            phone = "12345678",
                            email = "teppo@yhteystieto.test",
                            orderer = true,
                        ),
                        AlluDataFactory.createContact(
                            firstName = "Toinen",
                            lastName = "Tyyppi",
                            phone = "987",
                            email = "toinen@yhteystieto.test",
                            orderer = false,
                        ),
                    )
            )
        val userid = "user"

        val result = GdprJsonConverter.createGdprJson(listOf(application, otherApplication), userid)

        assertThat(result?.key).isEqualTo("user")
        assertThat(result?.children).isNotNull().hasSize(5)
        val id = getStringNodeFromChildren(result, "id").value
        assertThat(id).isEqualTo(userid)
        val nimi = getStringNodeFromChildren(result, "nimi").value
        assertThat(nimi).isEqualTo(TEPPO_TESTI)
        val puhelinnumerot = getCollectionNodeFromChildren(result, "puhelinnumerot").children
        assertThat(puhelinnumerot.map { it.key }).each { it.isEqualTo("puhelinnumero") }
        assertThat(puhelinnumerot.map { (it as StringNode).value })
            .containsExactlyInAnyOrder("12345678", "04012345678")
        val sahkopostit = getCollectionNodeFromChildren(result, "sahkopostit").children
        assertThat(sahkopostit.map { it.key }).each { it.isEqualTo("sahkoposti") }
        assertThat(sahkopostit.map { (it as StringNode).value })
            .containsExactlyInAnyOrder(
                "teppo@yhteystieto.test",
                "teppo@example.test",
                "teppo@dna.test"
            )
        val organisaatiot = getCollectionNodeFromChildren(result, "organisaatiot").children
        assertThat(organisaatiot.map { it.key }).each { it.isEqualTo("organisaatio") }
        assertThat(organisaatiot.map { (it as CollectionNode).children })
            .containsExactlyInAnyOrder(
                listOf(StringNode("nimi", "Yhteystieto Oy")),
                listOf(StringNode("nimi", "Dna"), StringNode("tunnus", "3766028-0"))
            )
    }

    @Test
    fun `combineGdprInfos with empty infos returns empty list`() {
        val result = GdprJsonConverter.combineGdprInfos(listOf(), "1")

        assertThat(result).isEmpty()
    }

    @Test
    fun `combineGdprInfos with one info returns simple values`() {
        val result = GdprJsonConverter.combineGdprInfos(listOf(teppoGdprInfo()), "1")

        assertThat(result).hasSize(4)
        assertThat(result)
            .containsExactlyInAnyOrder(
                StringNode("id", "1"),
                StringNode("nimi", TEPPO_TESTI),
                StringNode("puhelinnumero", "04012345678"),
                StringNode("sahkoposti", "teppo@example.test"),
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

        assertThat(result).hasSize(4)
        assertThat(result)
            .containsExactlyInAnyOrder(
                StringNode("id", "1"),
                StringNode("nimi", TEPPO_TESTI),
                CollectionNode(
                    "puhelinnumerot",
                    listOf(
                        StringNode("puhelinnumero", "04012345678"),
                        StringNode("puhelinnumero", "123456")
                    )
                ),
                CollectionNode(
                    "sahkopostit",
                    listOf(
                        StringNode("sahkoposti", "teppo@example.test"),
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

    @Test
    fun `getUserInfosFromApplication gets customer and contact info from application`() {
        val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()

        val result = GdprJsonConverter.getCreatorInfoFromApplication(applicationData)

        assertThat(result).hasSize(2)
        val expectedInfos =
            arrayOf(
                GdprInfo(
                    name = TEPPO_TESTI,
                    phone = "04012345678",
                    email = "teppo@dna.test",
                    organisation = GdprOrganisation(name = "Dna", registryKey = "3766028-0"),
                ),
                GdprInfo(
                    name = TEPPO_TESTI,
                    phone = "04012345678",
                    email = "teppo@example.test",
                    organisation = null,
                ),
            )
        assertThat(result).containsExactlyInAnyOrder(*expectedInfos)
    }

    @Test
    fun `getCreatorInfoFromCustomerWithContacts returns GDPR infos from orderer contacts`() {
        val customerWithContacts =
            CustomerWithContacts(
                AlluDataFactory.createPersonCustomer(),
                listOf(
                    AlluDataFactory.createContact(orderer = true, phone = "0000"),
                    AlluDataFactory.createContact(orderer = false, phone = "1111"),
                    AlluDataFactory.createContact(orderer = true, phone = "2222"),
                    AlluDataFactory.createContact(orderer = false, phone = "3333"),
                )
            )

        val result = GdprJsonConverter.getCreatorInfoFromCustomerWithContacts(customerWithContacts)

        assertThat(result).hasSize(2)
        assertThat(result)
            .transform { gdprInfos -> gdprInfos.map { it.phone } }
            .containsExactlyInAnyOrder("0000", "2222")
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @EnumSource(names = ["COMPANY", "ASSOCIATION"])
    fun `getOrganisationFromCustomer returns organisation from organisation customers`(
        customerType: CustomerType,
    ) {
        val customer = AlluDataFactory.createCompanyCustomer().copy(type = customerType)

        val result = GdprJsonConverter.getOrganisationFromCustomer(customer)

        assertThat(result).isEqualTo(dnaGdprOrganisation(id = null, department = null))
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = ["COMPANY", "ASSOCIATION"])
    fun `getOrganisationFromCustomer with other customers returns null`(
        customerType: CustomerType,
    ) {
        val customer = AlluDataFactory.createPersonCustomer().copy(type = customerType)

        assertThat(GdprJsonConverter.getOrganisationFromCustomer(customer)).isNull()
    }

    @Test
    fun `getGdprInfosFromContacts with empty contacts returns empty list`() {
        val result = GdprJsonConverter.getGdprInfosFromContacts(listOf(), dnaGdprOrganisation())

        assertThat(result).isEmpty()
    }

    @Test
    fun `getGdprInfosFromContacts with contacts returns gdpr infos of contacts`() {
        val contacts =
            listOf(
                AlluDataFactory.createContact(),
                AlluDataFactory.createContact(
                    firstName = "Toinen",
                    lastName = "Testihenkilö",
                    email = "toinen@example.test"
                ),
                AlluDataFactory.createContact(
                    firstName = "Teppo",
                    lastName = "Toissijainen",
                    email = "toissijainen@example.test"
                ),
                AlluDataFactory.createContact(
                    firstName = TEPPO_TESTI.split(" ")[0],
                    lastName = TEPPO_TESTI.split(" ")[1],
                    email = "teppo@yksityinen.test"
                ),
            )

        val result =
            GdprJsonConverter.getGdprInfosFromContacts(
                contacts,
                dnaGdprOrganisation(),
            )

        assertThat(result).hasSize(4)
        val expectedResults =
            arrayOf(
                teppoGdprInfo(organisation = dnaGdprOrganisation()),
                teppoGdprInfo(
                    name = "Toinen Testihenkilö",
                    email = "toinen@example.test",
                    organisation = dnaGdprOrganisation()
                ),
                teppoGdprInfo(
                    name = "Teppo Toissijainen",
                    email = "toissijainen@example.test",
                    organisation = dnaGdprOrganisation()
                ),
                teppoGdprInfo(
                    email = "teppo@yksityinen.test",
                    organisation = dnaGdprOrganisation()
                ),
            )
        assertThat(result).containsExactlyInAnyOrder(*expectedResults)
    }

    @Test
    fun `getGdprInfosFromApplicationContact without organisation returns gdpr info`() {
        val contact = AlluDataFactory.createContact()

        val response = GdprJsonConverter.getGdprInfosFromApplicationContact(contact, null)

        val expectedResponse = teppoGdprInfo()
        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `getGdprInfosFromApplicationContact with organisation returns gdpr info`() {
        val contact = AlluDataFactory.createContact()

        val response =
            GdprJsonConverter.getGdprInfosFromApplicationContact(contact, dnaGdprOrganisation())

        val expectedResponse = teppoGdprInfo(organisation = dnaGdprOrganisation())
        assertThat(response).isEqualTo(expectedResponse)
    }

    private fun getStringNodeFromChildren(collection: CollectionNode?, name: String): StringNode =
        (collection?.children?.first { it.key == name } as StringNode)

    private fun getCollectionNodeFromChildren(
        collection: CollectionNode?,
        name: String
    ): CollectionNode = (collection?.children?.first { it.key == name } as CollectionNode)

    private fun teppoGdprInfo(
        name: String? = TEPPO_TESTI,
        phone: String? = "04012345678",
        email: String? = "teppo@example.test",
        ipAddress: String? = null,
        organisation: GdprOrganisation? = null,
    ) = GdprInfo(name, phone, email, ipAddress, organisation)

    private fun dnaGdprOrganisation(
        id: Int? = 1,
        name: String? = "DNA",
        registryKey: String? = "3766028-0",
        department: String? = "Osasto",
    ) = GdprOrganisation(id, name, registryKey, department)
}
