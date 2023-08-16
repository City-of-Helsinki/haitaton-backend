package fi.hel.haitaton.hanke.permissions

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.permissions.Role.KAIKKI_OIKEUDET
import fi.hel.haitaton.hanke.permissions.Role.KATSELUOIKEUS
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import java.time.OffsetDateTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val USERNAME = "test7358"
const val kayttajaTunnistePattern = "[a-zA-z0-9]{24}"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(USERNAME)
class HankeKayttajaServiceITest : DatabaseTest() {

    @Autowired private lateinit var hankeKayttajaService: HankeKayttajaService

    @Autowired private lateinit var hankeFactory: HankeFactory

    @Autowired private lateinit var kayttajaTunnisteRepository: KayttajaTunnisteRepository
    @Autowired private lateinit var hankeKayttajaRepository: HankeKayttajaRepository
    @Autowired private lateinit var permissionRepository: PermissionRepository
    @Autowired private lateinit var roleRepository: RoleRepository

    private val perustaja = HankeFactory.defaultPerustaja

    @Test
    fun `getKayttajatByHankeId should return users from correct hanke only`() {
        val hankeToFind = hankeFactory.save(HankeFactory.create().withYhteystiedot())
        hankeFactory.save(HankeFactory.create().withYhteystiedot())

        val result: List<HankeKayttajaDto> =
            hankeKayttajaService.getKayttajatByHankeId(hankeToFind.id!!)

        val yhteystiedotPlusPerustaja = 4 + 1
        assertThat(result).hasSize(yhteystiedotPlusPerustaja)
    }

    @Test
    fun `getKayttajatByHankeId should return data matching to the saved entity`() {
        val hanke = hankeFactory.save(HankeFactory.create())

        val result: List<HankeKayttajaDto> = hankeKayttajaService.getKayttajatByHankeId(hanke.id!!)

        val entity: HankeKayttajaEntity =
            hankeKayttajaRepository.findAll().also { assertThat(it).hasSize(1) }.first()
        val dto: HankeKayttajaDto = result.first().also { assertThat(result).hasSize(1) }
        with(dto) {
            assertThat(id).isEqualTo(entity.id)
            assertThat(nimi).isEqualTo(entity.nimi)
            assertThat(sahkoposti).isEqualTo(entity.sahkoposti)
            assertThat(rooli).isEqualTo(entity.kayttajaTunniste!!.role)
            assertThat(tunnistautunut).isEqualTo(true) // hanke perustaja
        }
    }

    @Test
    fun `createToken saves kayttaja and tunniste with correct permission and other data`() {
        val hankeEntity = hankeFactory.saveEntity(HankeFactory.createNewEntity(id = null))
        val savedHankeId = hankeEntity.id!!
        val savedPermission = savePermission(savedHankeId, USERNAME, KAIKKI_OIKEUDET)

        hankeKayttajaService.createToken(savedHankeId, perustaja.toUserContact(), savedPermission)

        val kayttajaEntity =
            hankeKayttajaRepository.findAll().also { assertThat(it).hasSize(1) }.first()
        val tunnisteEntity =
            kayttajaTunnisteRepository.findAll().also { assertThat(it).hasSize(1) }.first()
        with(kayttajaEntity) {
            assertThat(id).isNotNull()
            assertThat(hankeId).isEqualTo(savedHankeId)
            assertThat(permission!!).isOfEqualDataTo(savedPermission)
            assertThat(sahkoposti).isEqualTo(perustaja.email)
            assertThat(nimi).isEqualTo(perustaja.nimi)
        }
        with(tunnisteEntity) {
            assertThat(id).isNotNull()
            assertThat(role).isEqualTo(KAIKKI_OIKEUDET)
            assertThat(tunniste).matches(Regex(kayttajaTunnistePattern))
            assertThat(sentAt).isNull()
            assertThat(createdAt).isRecent()
        }
    }

    @Test
    fun `saveNewTokensFromApplication does nothing if application has no contacts`() {
        val hanke = hankeFactory.saveEntity()
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts = AlluDataFactory.createCompanyCustomer().withContacts(),
                contractorWithContacts = AlluDataFactory.createCompanyCustomer().withContacts()
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )
        val initialKayttajatSize = 1 // hanke perustaja

        hankeKayttajaService.saveNewTokensFromApplication(application, 1)

        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(initialKayttajatSize)
        assertThat(hankeKayttajaRepository.findAll()).hasSize(initialKayttajatSize)
    }

    @Test
    fun `saveNewTokensFromApplication with different contact emails creates tokens for them all`() {
        val hanke = hankeFactory.saveEntity()
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2")
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email3"),
                            AlluDataFactory.createContact(email = "email4")
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        val tunnisteet = nonPerustajaTunnisteet()
        assertThat(tunnisteet).hasSize(4)
        assertThat(tunnisteet).areValid()
        val kayttajat = nonPerustajaKayttajat()
        assertThat(kayttajat).hasSize(4)
        assertThat(kayttajat).each { kayttaja ->
            kayttaja.transform { it.nimi }.isEqualTo("Teppo Testihenkilö")
            kayttaja.transform { it.hankeId }.isEqualTo(hanke.id)
            kayttaja.transform { it.permission }.isNull()
            kayttaja.transform { it.kayttajaTunniste }.isNotNull()
        }
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
                "email3",
                "email4",
            )
    }

    @Test
    fun `saveNewTokensFromApplication with non-unique contact emails creates only the unique ones`() {
        val hanke = hankeFactory.saveEntity()
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2"),
                            AlluDataFactory.createContact(
                                email = "email2",
                                firstName = "Other",
                                lastName = "Name"
                            ),
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        assertThat(nonPerustajaTunnisteet()).hasSize(2)
        val kayttajat = nonPerustajaKayttajat()
        assertThat(kayttajat).hasSize(2)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
            )
    }

    @Test
    fun `saveNewTokensFromApplication with pre-existing tokens creates only new ones`() {
        val hanke = hankeFactory.saveEntity()
        saveUserAndToken(hanke, "Existing User", "email1")
        saveUserAndToken(hanke, "Other User", "email4")
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2")
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email3"),
                            AlluDataFactory.createContact(email = "email4")
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )
        assertThat(nonPerustajaTunnisteet()).hasSize(2)

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        assertThat(nonPerustajaTunnisteet()).hasSize(4)
        val kayttajat = nonPerustajaKayttajat()
        assertThat(kayttajat).hasSize(4)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
                "email3",
                "email4",
            )
    }

    @Test
    fun `saveNewTokensFromApplication with pre-existing permissions creates only new ones`() {
        val hanke = hankeFactory.saveEntity()
        saveUserAndPermission(hanke.id!!, "Existing User", "email1")
        saveUserAndPermission(hanke.id!!, "Other User", "email4")
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2")
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email3"),
                            AlluDataFactory.createContact(email = "email4")
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )
        assertThat(nonPerustajaTunnisteet()).isEmpty()

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        val tunnisteet = nonPerustajaTunnisteet()
        assertThat(tunnisteet).hasSize(2)
        assertThat(tunnisteet).each { tunniste ->
            tunniste.transform { it.hankeKayttaja?.sahkoposti }.isIn("email2", "email3")
        }
        val kayttajat = nonPerustajaKayttajat()
        assertThat(kayttajat).hasSize(4)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
                "email3",
                "email4",
            )
    }

    @Test
    fun `saveNewTokensFromHanke does nothing if hanke has no contacts`() {
        val hanke = hankeFactory.save(HankeFactory.create())

        hankeKayttajaService.saveNewTokensFromHanke(hanke)

        assertThat(nonPerustajaTunnisteet()).isEmpty()
        assertThat(nonPerustajaKayttajat()).isEmpty()
    }

    @Test
    fun `saveNewTokensFromHanke creates tokens for unique ones`() {
        val hanke =
            hankeFactory.save(
                HankeFactory.create()
                    .withYhteystiedot(
                        // each has a duplicate
                        omistajat = listOf(1, 1),
                        rakennuttajat = listOf(2, 2),
                        toteuttajat = listOf(3, 3),
                        muut = listOf(4, 4)
                    )
            )
        assertThat(hanke.extractYhteystiedot()).hasSize(8)

        hankeKayttajaService.saveNewTokensFromHanke(hanke)

        val tunnisteet: List<KayttajaTunnisteEntity> = nonPerustajaTunnisteet()
        val kayttajat: List<HankeKayttajaEntity> = nonPerustajaKayttajat()
        assertThat(tunnisteet).hasSize(4) // 4 yhteyshenkilo subcontacts.
        assertThat(kayttajat).hasSize(4)
        assertThat(tunnisteet).areValid()
        assertThat(kayttajat).areValid(hanke.id)
    }

    @Test
    fun `saveNewTokensFromHanke with pre-existing permissions does not create duplicate`() {
        val hankeEntity = hankeFactory.saveEntity()
        val hanke =
            HankeFactory.create(id = hankeEntity.id, hankeTunnus = hankeEntity.hankeTunnus).apply {
                omistajat.add(HankeYhteystietoFactory.create())
            }
        saveUserAndToken(hankeEntity, "Existing User One", "ali.kontakti@meili.com")

        hankeKayttajaService.saveNewTokensFromHanke(hanke)

        val tunnisteet = nonPerustajaTunnisteet()
        val kayttajat = nonPerustajaKayttajat()
        assertThat(tunnisteet).hasSize(1)
        assertThat(kayttajat).hasSize(1)
        val kayttaja = kayttajat.first()
        assertThat(kayttaja.nimi).isEqualTo("Existing User One")
        assertThat(kayttaja.sahkoposti).isEqualTo("ali.kontakti@meili.com")
    }

    private val expectedNames =
        arrayOf(
            "yhteys-etu1 yhteys-suku1",
            "yhteys-etu2 yhteys-suku2",
            "yhteys-etu3 yhteys-suku3",
            "yhteys-etu4 yhteys-suku4",
        )

    private val expectedEmails =
        arrayOf(
            "yhteys-email1",
            "yhteys-email2",
            "yhteys-email3",
            "yhteys-email4",
        )

    private fun Assert<List<KayttajaTunnisteEntity>>.areValid() = each { t ->
        t.transform { it.id }.isNotNull()
        t.transform { it.role }.isEqualTo(KATSELUOIKEUS)
        t.transform { it.createdAt }.isRecent()
        t.transform { it.sentAt }.isNull()
        t.transform { it.tunniste }.matches(Regex(kayttajaTunnistePattern))
        t.transform { it.hankeKayttaja }.isNotNull()
    }

    private fun Assert<List<HankeKayttajaEntity>>.areValid(hankeId: Int?) = each { k ->
        k.transform { it.id }.isNotNull()
        k.transform { it.nimi }.isIn(*expectedNames)
        k.transform { it.sahkoposti }.isIn(*expectedEmails)
        k.transform { it.hankeId }.isEqualTo(hankeId)
        k.transform { it.permission }.isNull()
        k.transform { it.kayttajaTunniste }.isNotNull()
    }

    private fun Assert<PermissionEntity>.isOfEqualDataTo(other: PermissionEntity) =
        given { actual ->
            assertThat(actual.id).isEqualTo(other.id)
            assertThat(actual.hankeId).isEqualTo(other.hankeId)
            assertThat(actual.userId).isEqualTo(other.userId)
            assertThat(actual.role).isOfEqualDataTo(other.role)
        }

    private fun Assert<RoleEntity>.isOfEqualDataTo(other: RoleEntity) = given { actual ->
        assertThat(actual.id).isEqualTo(other.id)
        assertThat(actual.role).isEqualTo(other.role)
        assertThat(actual.permissionCode).isEqualTo(other.permissionCode)
    }

    /**
     * Creating a Hanke through HankeService adds HankeKayttajaEntity for perustaja. This is a
     * helper function to filter out perustaja.
     */
    private fun nonPerustajaKayttajat() =
        hankeKayttajaRepository.findAll().filter { it.nimi != perustaja.nimi }

    /**
     * Creating a Hanke through HankeService adds KayttajaTunnisteEntity for perustaja. This is a
     * helper function to filter out perustaja.
     */
    private fun nonPerustajaTunnisteet() =
        kayttajaTunnisteRepository.findAll().filter { it.role != KAIKKI_OIKEUDET }

    private fun saveUserAndToken(
        hanke: HankeEntity,
        nimi: String,
        sahkoposti: String
    ): HankeKayttajaEntity {
        val kayttajaTunnisteEntity =
            kayttajaTunnisteRepository.save(
                KayttajaTunnisteEntity(
                    tunniste = "existing",
                    createdAt = OffsetDateTime.parse("2023-03-31T15:41:21Z"),
                    sentAt = null,
                    role = KATSELUOIKEUS,
                    hankeKayttaja = null,
                )
            )
        return hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                hankeId = hanke.id!!,
                nimi = nimi,
                sahkoposti = sahkoposti,
                permission = null,
                kayttajaTunniste = kayttajaTunnisteEntity,
            )
        )
    }

    private fun saveUserAndPermission(
        hankeId: Int,
        nimi: String,
        sahkoposti: String
    ): HankeKayttajaEntity {
        val permissionEntity = savePermission(hankeId, "fake id", KATSELUOIKEUS)

        return hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                hankeId = hankeId,
                nimi = nimi,
                sahkoposti = sahkoposti,
                permission = permissionEntity,
                kayttajaTunniste = null,
            )
        )
    }

    private fun savePermission(hankeId: Int, userId: String, role: Role) =
        permissionRepository.save(
            PermissionEntity(
                userId = userId,
                hankeId = hankeId,
                role = roleRepository.findOneByRole(role)
            )
        )
}
