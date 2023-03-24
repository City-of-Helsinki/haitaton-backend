package fi.hel.haitaton.hanke.gdpr

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.UserInfoFactory.teppoUserInfo
import fi.hel.haitaton.hanke.organisaatio.Organisaatio
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import fi.hel.haitaton.hanke.profiili.UserInfo
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

internal class GdprJsonConverterTest {

    private val organisaatioService: OrganisaatioService = mockk()
    private val gdprJsonConverter: GdprJsonConverter = GdprJsonConverter(organisaatioService)

    @Test
    fun `createGdprJson returns null when no names match`() {
        val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()
        val application =
            AlluDataFactory.createApplication(
                applicationData = applicationData,
                hankeTunnus = "HAI-1234"
            )
        val hanke =
            HankeFactory.create().withYhteystiedot(
                omistajat = listOf(1, 2),
                arvioijat = listOf(3, 2),
                toteuttajat = listOf(4, 2)
            ) { it.organisaatioId = null }
        val userInfo = teppoUserInfo(firstName = "Other")

        val result = gdprJsonConverter.createGdprJson(listOf(application), listOf(hanke), userInfo)

        assertNull(result)
    }

    @Test
    fun `createGdprJson combines identical results when there are several infos`() {
        val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()
        val application =
            AlluDataFactory.createApplication(
                applicationData = applicationData,
                hankeTunnus = "HAI-1234"
            )
        val hanke =
            HankeFactory.create().withYhteystiedot(
                omistajat = listOf(1, 2),
                arvioijat = listOf(3, 2),
                toteuttajat = listOf(4, 2)
            ) {
                it.organisaatioId = null
                it.etunimi = "Teppo"
                it.sukunimi = "Testihenkilö"
                it.puhelinnumero = "12345678"
                it.email = "teppo@yhteystieto.test"
                it.organisaatioNimi = "Yhteystieto Oy"
                it.osasto = null
            }
        val userInfo = teppoUserInfo()

        val result = gdprJsonConverter.createGdprJson(listOf(application), listOf(hanke), userInfo)

        assertEquals("user", result?.key)
        assertEquals(5, result?.children?.size)
        val id = getStringNodeFromChildren(result, "id").value
        assertEquals(userInfo.userId, id)
        val nimi = getStringNodeFromChildren(result, "nimi").value
        assertEquals("Teppo Testihenkilö", nimi)
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

    private fun getStringNodeFromChildren(collection: CollectionNode?, name: String): StringNode =
        (collection?.children?.first { it.key == name } as StringNode)

    private fun getCollectionNodeFromChildren(
        collection: CollectionNode?,
        name: String
    ): CollectionNode = (collection?.children?.first { it.key == name } as CollectionNode)

    @Test
    fun `combineGdprInfos with empty infos returns empty list`() {
        val result = gdprJsonConverter.combineGdprInfos(listOf(), "1")

        assertThat(result).isEmpty()
    }

    @Test
    fun `combineGdprInfos with one info returns simple values`() {
        val result = gdprJsonConverter.combineGdprInfos(listOf(teppoGdprInfo()), "1")

        assertEquals(4, result.size)
        assertThat(result)
            .containsExactlyInAnyOrder(
                StringNode("id", "1"),
                StringNode("nimi", "Teppo Testihenkilö"),
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

        val result = gdprJsonConverter.combineGdprInfos(infos, "1")

        assertEquals(4, result.size)
        assertThat(result)
            .containsExactlyInAnyOrder(
                StringNode("id", "1"),
                StringNode("nimi", "Teppo Testihenkilö"),
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

        assertNull(gdprJsonConverter.combineOrganisations(organisations))
    }

    @Test
    fun `combineOrganisations returns single organisation as a collection node`() {
        val organisations = setOf(dnaGdprOrganisation())

        val result = gdprJsonConverter.combineOrganisations(organisations)

        assertNotNull(result)
        result as CollectionNode
        assertEquals("organisaatio", result.key)
        assertNotNull(result.children)
        assertEquals(4, result.children.size)
        assertThat(result.children.map { it.key })
            .containsExactlyInAnyOrder("id", "nimi", "tunnus", "osasto")
    }

    @Test
    fun `combineOrganisations combines multiple organisations under a collection node`() {
        val organisations = setOf(dnaGdprOrganisation(), dnaGdprOrganisation(department = null))

        val result = gdprJsonConverter.combineOrganisations(organisations)

        assertNotNull(result)
        result as CollectionNode
        assertEquals("organisaatiot", result.key)
        assertNotNull(result.children)
        assertEquals(2, result.children.size)
        assertEquals("organisaatio", result.children[0].key)
        assertEquals("organisaatio", result.children[1].key)
    }

    @Test
    fun `getUserInfosFromApplication gets customer and contact info from application`() {
        val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()

        val result = gdprJsonConverter.getUserInfosFromApplication(applicationData, teppoUserInfo())

        assertEquals(3, result.size) // Once as customer and twice as contact
        val expectedInfos =
            arrayOf(
                GdprInfo(
                    name = "Teppo Testihenkilö",
                    phone = "04012345678",
                    email = "teppo@example.test",
                ),
                GdprInfo(
                    name = "Teppo Testihenkilö",
                    phone = "04012345678",
                    email = "teppo@example.test",
                ),
                GdprInfo(
                    name = "Teppo Testihenkilö",
                    phone = "04012345678",
                    email = "teppo@dna.test",
                    organisation = GdprOrganisation(name = "Dna", registryKey = "3766028-0"),
                )
            )
        assertThat(result).containsExactlyInAnyOrder(*expectedInfos)
    }

    @Test
    fun `getGdprInfosFromCustomerWithContacts returns GDPR infos from both customer and contacts`() {
        val customerWithContacts =
            CustomerWithContacts(
                AlluDataFactory.createPersonCustomer(),
                listOf(
                    AlluDataFactory.createContact(),
                    AlluDataFactory.createContact(phone = "0000")
                )
            )

        val result =
            gdprJsonConverter.getGdprInfosFromCustomerWithContacts(
                customerWithContacts,
                teppoUserInfo()
            )

        assertEquals(3, result.size)
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @EnumSource(names = ["COMPANY", "ASSOCIATION"])
    fun `getOrganisationFromCustomer returns organisation from organisation customers`(
        customerType: CustomerType,
    ) {
        val customer = AlluDataFactory.createCompanyCustomer().copy(type = customerType)

        val result = gdprJsonConverter.getOrganisationFromCustomer(customer)

        assertEquals(dnaGdprOrganisation(id = null, department = null), result)
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = ["COMPANY", "ASSOCIATION"])
    fun `getOrganisationFromCustomer with other customers returns null`(
        customerType: CustomerType,
    ) {
        val customer = AlluDataFactory.createPersonCustomer().copy(type = customerType)

        assertNull(gdprJsonConverter.getOrganisationFromCustomer(customer))
    }

    @Test
    fun `getGdprInfosFromContacts with empty contacts returns empty list`() {
        val result =
            gdprJsonConverter.getGdprInfosFromContacts(
                listOf(),
                dnaGdprOrganisation(),
                teppoUserInfo()
            )

        assertEquals(listOf<GdprInfo>(), result)
    }

    @Test
    fun `getGdprInfosFromContacts with contacts returns gdpr infos of contacts with matching names`() {
        val contacts =
            listOf(
                AlluDataFactory.createContact(),
                AlluDataFactory.createContact(
                    name = "Toinen Testihenkilö",
                    email = "toinen@example.test"
                ),
                AlluDataFactory.createContact(
                    name = "Teppo Toissijainen",
                    email = "toissijainen@example.test"
                ),
                AlluDataFactory.createContact(
                    name = "Teppo Testihenkilö",
                    email = "teppo@yksityinen.test"
                ),
            )

        val result =
            gdprJsonConverter.getGdprInfosFromContacts(
                contacts,
                dnaGdprOrganisation(),
                teppoUserInfo()
            )

        assertEquals(2, result.size)
        val expectedResults =
            arrayOf(
                teppoGdprInfo(organisation = dnaGdprOrganisation()),
                teppoGdprInfo(
                    email = "teppo@yksityinen.test",
                    organisation = dnaGdprOrganisation()
                ),
            )
        assertThat(result).containsExactlyInAnyOrder(*expectedResults)
    }

    @Test
    fun `getGdprInfosFromApplicationContact with another name returns null`() {
        val otherContact = AlluDataFactory.createContact(name = "Another name")
        val teppoContact = AlluDataFactory.createContact()
        val otherUserInfo = teppoUserInfo(firstName = "Another")
        val teppoUserInfo = teppoUserInfo()

        assertNull(
            gdprJsonConverter.getGdprInfosFromApplicationContact(otherContact, null, teppoUserInfo)
        )
        assertNull(
            gdprJsonConverter.getGdprInfosFromApplicationContact(teppoContact, null, otherUserInfo)
        )
    }

    @Test
    fun `getGdprInfosFromApplicationContact with matching name returns gdpr info`() {
        val contact = AlluDataFactory.createContact()

        val response =
            gdprJsonConverter.getGdprInfosFromApplicationContact(contact, null, teppoUserInfo())

        val expectedResponse = teppoGdprInfo()
        assertEquals(expectedResponse, response)
    }

    @Test
    fun `getGdprInfosFromApplicationContact with organisation`() {
        val contact = AlluDataFactory.createContact()

        val response =
            gdprJsonConverter.getGdprInfosFromApplicationContact(
                contact,
                dnaGdprOrganisation(),
                teppoUserInfo()
            )

        val expectedResponse = teppoGdprInfo(organisation = dnaGdprOrganisation())
        assertEquals(expectedResponse, response)
    }

    @Test
    fun `getGdprInfoFromCustomer with null customer returns null`() {
        assertNull(gdprJsonConverter.getGdprInfoFromCustomer(null, teppoUserInfo()))
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @EnumSource(names = ["PERSON"], mode = EnumSource.Mode.EXCLUDE)
    fun `getGdprInfoFromCustomer with non-person type returns null`(type: CustomerType) {
        assertNull(
            gdprJsonConverter.getGdprInfoFromCustomer(
                AlluDataFactory.createCompanyCustomer().copy(type = type),
                teppoUserInfo()
            )
        )
    }

    @Test
    fun `getGdprInfoFromCustomer with person customer returns GdprInfo`() {
        val response =
            gdprJsonConverter.getGdprInfoFromCustomer(
                AlluDataFactory.createPersonCustomer(),
                teppoUserInfo()
            )

        assertEquals(teppoGdprInfo(), response)
    }

    @Test
    fun `matchFullName with UserInfo matches the names from the UserInfo`() {
        val userInfo = UserInfo(userId = "id", firstName = "Rane", lastName = "Rakentaja")

        assertTrue(gdprJsonConverter.matchFullName("Rane Rakentaja", userInfo))
        assertFalse(gdprJsonConverter.matchFullName("Ranelin Rakentaja", userInfo))
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @CsvSource(
        "Rane Rakentaja,Rane,Rakentaja",
        "Rakentaja Rane,Rane,Rakentaja",
        "Rane Rakentaja,rane,rakentaja",
        "rane rakentaja,Rane,Rakentaja",
        "Rane Petteri Rakentaja,Rane,Rakentaja",
        "Rane-Petteri Rakentaja,Rane,Rakentaja",
        "Rane-Petteri Rakentaja,Rane-Petteri,Rakentaja",
        "Rane Rakentaja-Tarkistaja,Rane,Rakentaja-Tarkistaja",
    )
    fun `matchFullName with positive cases`(fullName: String, firstName: String, lastName: String) {
        assertTrue(gdprJsonConverter.matchFullName(fullName, firstName, lastName))
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @CsvSource(
        "Ranelin Rakentaja,Rane,Rakentaja",
        "Rane Rakentaja,Ranelin,Rakentaja",
        "Rane Rakentaja,Rane,Rakentajainen",
        "Rane Rakentajainen,Rane,Rakentaja",
        "Rane Rakentaja-Tarkistaja,Rane,Rakentaja",
        "Rane Rakentaja,Rane,Rakentaja-Tarkistaja",
        "Rane Rakentaja,Rane-Petteri,Rakentaja",
        "Rane Rakentaja,Rane Petteri,Rakentaja",
    )
    fun `matchFullName with negative cases`(fullName: String, firstName: String, lastName: String) {
        assertFalse(gdprJsonConverter.matchFullName(fullName, firstName, lastName))
    }

    @Test
    fun `matchFullName with null name returns false`() {
        assertFalse(gdprJsonConverter.matchFullName(null, "Rane", "Rakentaja"))
    }

    @Test
    fun `getGdprInfosFromHanke returns GdprInfos for Yhteystieto with matching names`() {
        val hanke =
            HankeFactory.create().withYhteystiedot(
                omistajat = listOf(1, 2),
                arvioijat = listOf(3, 2),
                toteuttajat = listOf(4, 2)
            ) { it.organisaatioId = null }
        val userInfo = UserInfo("id", "etu2", "suku2")

        val response = gdprJsonConverter.getGdprInfosFromHanke(hanke, userInfo)

        assertEquals(3, response.size)
        val expectedGdprInfo =
            GdprInfo(
                name = "etu2 suku2",
                phone = "0102222222",
                email = "email2",
                organisation = GdprOrganisation(name = "org2", department = "osasto2"),
            )
        assertThat(response).containsExactly(expectedGdprInfo, expectedGdprInfo, expectedGdprInfo)
    }

    @Test
    fun `getGdprInfoFromHankeYhteystieto with matching names creates GdprInfo`() {
        val yhteystieto = HankeYhteystietoFactory.create()
        val userInfo = teppoUserInfo()
        every { organisaatioService.get(1) }.returns(dnaOrganisaatio())

        val response = gdprJsonConverter.getGdprInfoFromHankeYhteystieto(yhteystieto, userInfo)

        val expectedResponse = teppoGdprInfo(organisation = dnaGdprOrganisation())
        assertEquals(expectedResponse, response)
        verify(exactly = 1) { organisaatioService.get(1) }
    }

    @Test
    fun `getGdprInfoFromHankeYhteystieto when yhteystieto has no organisaatio id returns GdprInfo`() {
        val yhteystieto = HankeYhteystietoFactory.create(organisaatioId = null)
        val userInfo = teppoUserInfo()
        every { organisaatioService.get(1) }.returns(dnaOrganisaatio())

        val response = gdprJsonConverter.getGdprInfoFromHankeYhteystieto(yhteystieto, userInfo)

        val expectedOrganisation = GdprOrganisation(name = "Organisaatio", department = "Osasto")
        val expectedResponse = teppoGdprInfo(organisation = expectedOrganisation)
        assertEquals(expectedResponse, response)
        verify { organisaatioService wasNot Called }
    }

    @Test
    fun `getGdprInfoFromHankeYhteystieto with different first name`() {
        val yhteystieto = HankeYhteystietoFactory.create().copy(etunimi = "Jaska")
        val userInfo = teppoUserInfo()
        every { organisaatioService.get(1) }.returns(dnaOrganisaatio())

        val response = gdprJsonConverter.getGdprInfoFromHankeYhteystieto(yhteystieto, userInfo)

        assertNull(response)
        verify { organisaatioService wasNot Called }
    }

    @Test
    fun `getGdprInfoFromHankeYhteystieto with different last name`() {
        val userInfo = teppoUserInfo()
        val yhteystieto = HankeYhteystietoFactory.create().copy(sukunimi = "Jokunen")
        every { organisaatioService.get(1) }.returns(dnaOrganisaatio())

        val response = gdprJsonConverter.getGdprInfoFromHankeYhteystieto(yhteystieto, userInfo)

        assertNull(response)
        verify { organisaatioService wasNot Called }
    }

    @Test
    fun `extractOrganisation with existing organisation id and name uses id`() {
        val yhteystieto =
            HankeYhteystietoFactory.create()
                .copy(organisaatioId = 1, organisaatioNimi = "Toinen organisaatio")
        every { organisaatioService.get(1) }.returns(dnaOrganisaatio())

        val response = gdprJsonConverter.extractOrganisation(yhteystieto)

        assertNotNull(response)
        assertEquals(1, response?.id)
        assertEquals("3766028-0", response?.registryKey)
        assertEquals("DNA", response?.name)
        assertEquals("Osasto", response?.department)
        verify(exactly = 1) { organisaatioService.get(1) }
    }

    @Test
    fun `extractOrganisation with just organisation id uses id`() {
        val yhteystieto =
            HankeYhteystietoFactory.create().copy(organisaatioId = 1, organisaatioNimi = null)
        every { organisaatioService.get(1) }.returns(dnaOrganisaatio())

        val response = gdprJsonConverter.extractOrganisation(yhteystieto)

        assertNotNull(response)
        assertEquals(dnaGdprOrganisation(), response)
        verify(exactly = 1) { organisaatioService.get(1) }
    }

    @Test
    fun `extractOrganisation with missing organisation id returns null`() {
        val yhteystieto =
            HankeYhteystietoFactory.create().copy(organisaatioId = 2, organisaatioNimi = "Org")
        every { organisaatioService.get(2) }.returns(null)

        val response = gdprJsonConverter.extractOrganisation(yhteystieto)

        assertNull(response)
        verify(exactly = 1) { organisaatioService.get(2) }
    }

    @Test
    fun `extractOrganisation with just organisation name uses name`() {
        val yhteystieto =
            HankeYhteystietoFactory.create()
                .copy(organisaatioId = null, organisaatioNimi = "Toinen")

        val response = gdprJsonConverter.extractOrganisation(yhteystieto)

        assertNotNull(response)
        assertNull(response?.id)
        assertNull(response?.registryKey)
        assertEquals("Toinen", response?.name)
        assertEquals("Osasto", response?.department)
        verify { organisaatioService wasNot Called }
    }

    @Test
    fun `extractOrganisation without organisation id and name returns null`() {
        val yhteystieto =
            HankeYhteystietoFactory.create().copy(organisaatioId = null, organisaatioNimi = null)

        val response = gdprJsonConverter.extractOrganisation(yhteystieto)

        assertNull(response)
        verify { organisaatioService wasNot Called }
    }

    @Test
    fun `extractOrganisation reads osasto from yhteystieto`() {
        val yhteystieto = HankeYhteystietoFactory.create().copy(osasto = "Aliosasto")
        every { organisaatioService.get(1) }.returns(dnaOrganisaatio())

        val response = gdprJsonConverter.extractOrganisation(yhteystieto)

        assertNotNull(response)
        assertEquals("Aliosasto", response?.department)
        verify(exactly = 1) { organisaatioService.get(1) }
    }

    private fun teppoGdprInfo(
        name: String? = "Teppo Testihenkilö",
        phone: String? = "04012345678",
        email: String? = "teppo@example.test",
        ipAddress: String? = null,
        organisation: GdprOrganisation? = null,
    ) = GdprInfo(name, phone, email, ipAddress, organisation)

    private fun dnaOrganisaatio(id: Int = 1) = Organisaatio(id, "3766028-0", "DNA")

    private fun dnaGdprOrganisation(
        id: Int? = 1,
        name: String? = "DNA",
        registryKey: String? = "3766028-0",
        department: String? = "Osasto",
    ) = GdprOrganisation(id, name, registryKey, department)
}
