package fi.hel.haitaton.hanke.permissions

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
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val USERNAME = "test7358"

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

    @Test
    fun `saveNewTokensFromApplication does nothing if application has no contacts`() {
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts = AlluDataFactory.createCompanyCustomer().withContacts(),
                contractorWithContacts = AlluDataFactory.createCompanyCustomer().withContacts()
            )

        hankeKayttajaService.saveNewTokensFromApplication(applicationData, 1)

        assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
        assertThat(hankeKayttajaRepository.findAll()).isEmpty()
    }

    @Test
    fun `saveNewTokensFromApplication with different contact emails creates tokens for them all`() {
        val hanke = hankeFactory.save()
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

        hankeKayttajaService.saveNewTokensFromApplication(applicationData, hanke.id!!)

        val tunnisteet = kayttajaTunnisteRepository.findAll()
        assertThat(tunnisteet).hasSize(4)
        assertThat(tunnisteet).each { tunniste ->
            tunniste.transform { it.role }.isEqualTo(Role.KATSELUOIKEUS)
            tunniste.transform { it.createdAt }.isRecent()
            tunniste.transform { it.sentAt }.isNull()
            tunniste.transform { it.tunniste }.matches(Regex("[a-zA-z0-9]{24}"))
            tunniste.transform { it.hankeKayttaja }.isNotNull()
        }
        val kayttajat = hankeKayttajaRepository.findAll()
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
        val hanke = hankeFactory.save()
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2"),
                            AlluDataFactory.createContact(email = "email2", name = "Other Name"),
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                        )
            )

        hankeKayttajaService.saveNewTokensFromApplication(applicationData, hanke.id!!)

        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(2)
        val kayttajat = hankeKayttajaRepository.findAll()
        assertThat(kayttajat).hasSize(2)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
            )
    }

    @Test
    fun `saveNewTokensFromApplication with pre-existing tokens creates only new ones`() {
        val hanke = hankeFactory.save()
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
        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(2)

        hankeKayttajaService.saveNewTokensFromApplication(applicationData, hanke.id!!)

        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(4)
        val kayttajat = hankeKayttajaRepository.findAll()
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
        val hanke = hankeFactory.save()
        saveUserAndPermission(hanke, "Existing User", "email1")
        saveUserAndPermission(hanke, "Other User", "email4")
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
        assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()

        hankeKayttajaService.saveNewTokensFromApplication(applicationData, hanke.id!!)

        val tunnisteet = kayttajaTunnisteRepository.findAll()
        assertThat(tunnisteet).hasSize(2)
        assertThat(tunnisteet).each { tunniste ->
            tunniste.transform { it.hankeKayttaja?.sahkoposti }.isIn("email2", "email3")
        }
        val kayttajat = hankeKayttajaRepository.findAll()
        assertThat(kayttajat).hasSize(4)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
                "email3",
                "email4",
            )
    }

    private fun saveUserAndToken(
        hanke: Hanke,
        nimi: String,
        sahkoposti: String
    ): HankeKayttajaEntity {
        val kayttajaTunnisteEntity =
            kayttajaTunnisteRepository.save(
                KayttajaTunnisteEntity(
                    id = UUID.randomUUID(),
                    tunniste = "existing",
                    createdAt = OffsetDateTime.parse("2023-03-31T15:41:21Z"),
                    sentAt = null,
                    role = Role.KATSELUOIKEUS,
                    hankeKayttaja = null,
                )
            )
        return hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                id = UUID.randomUUID(),
                hankeId = hanke.id!!,
                nimi = nimi,
                sahkoposti = sahkoposti,
                permission = null,
                kayttajaTunniste = kayttajaTunnisteEntity,
            )
        )
    }

    private fun saveUserAndPermission(
        hanke: Hanke,
        nimi: String,
        sahkoposti: String
    ): HankeKayttajaEntity {
        val permissionEntity =
            permissionRepository.save(
                PermissionEntity(
                    userId = "fake id",
                    hankeId = hanke.id!!,
                    role = roleRepository.findOneByRole(Role.KATSELUOIKEUS),
                )
            )

        return hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                id = UUID.randomUUID(),
                hankeId = hanke.id!!,
                nimi = nimi,
                sahkoposti = sahkoposti,
                permission = permissionEntity,
                kayttajaTunniste = null,
            )
        )
    }
}
