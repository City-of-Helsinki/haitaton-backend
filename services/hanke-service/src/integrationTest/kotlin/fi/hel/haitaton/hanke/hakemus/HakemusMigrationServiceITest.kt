package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_PHONE
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TESTIHENKILO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContact
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajakutsuRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException

class HakemusMigrationServiceITest(
    @Autowired private val hakemusMigrationService: HakemusMigrationService,
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val hakemusRepository: ApplicationRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val hankeKayttajaService: HankeKayttajaService,
    @Autowired private val kayttajakutsuRepository: KayttajakutsuRepository,
    @Autowired private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    @Autowired private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val applicationFactory: ApplicationFactory,
) : IntegrationTest() {

    @Nested
    inner class MigrateOneHanke {

        private fun setup(): Pair<HankeEntity, PermissionEntity> {
            val hanke = hankeFactory.saveMinimal()
            val permission =
                permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer(name = "Hakija Oy")
                            .withContact(email = "hakija@email", orderer = true),
                    contractorWithContacts =
                        ApplicationFactory.createCompanyCustomer(name = "Suorittaja Oy")
                            .withContact(email = "tyonsuorittaja@email", orderer = false),
                    propertyDeveloperWithContacts =
                        ApplicationFactory.createCompanyCustomer(name = "Rakennuttaja Oy")
                            .withContact(email = "asianhoitaja@email", orderer = false),
                    representativeWithContacts =
                        ApplicationFactory.createCompanyCustomer(name = "Asianhoitaja Oy")
                            .withContacts(),
                )
            applicationFactory.saveApplicationEntity(USERNAME, hanke, applicationData = data)

            return Pair(hanke, permission)
        }

        @Test
        fun `throws exception when hanke doesn't exist`() {
            val failure = assertFailure { hakemusMigrationService.migrateOneHanke(1) }

            failure.hasClass(JpaObjectRetrievalFailureException::class)
        }

        @Test
        fun `doesn't panic when there's no hakemus`() {
            val hanke = hankeFactory.saveMinimal()

            hakemusMigrationService.migrateOneHanke(hanke.id)

            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
            assertThat(hakemusyhteystietoRepository.findAll()).isEmpty()
            assertThat(hakemusyhteyshenkiloRepository.findAll()).isEmpty()
        }

        @Test
        fun `creates a hankekayttaja for the founder when the application has an orderer`() {
            val (hanke, permission) = setup()

            hakemusMigrationService.migrateOneHanke(hanke.id)

            assertThat(hankekayttajaRepository.findByPermissionId(permission.id)).isNotNull()
        }

        @Test
        fun `creates hankekayttajat for other contacts`() {
            val (hanke, _) = setup()

            hakemusMigrationService.migrateOneHanke(hanke.id)

            val otherKayttajat =
                hankeKayttajaService.getKayttajatByHankeId(hanke.id).filter {
                    it.kayttooikeustaso != Kayttooikeustaso.KAIKKI_OIKEUDET
                }
            assertThat(otherKayttajat)
                .extracting { it.sahkoposti }
                .containsExactlyInAnyOrder("tyonsuorittaja@email", "asianhoitaja@email")
        }

        @Test
        fun `creates hakemusyhteystiedot for the customers`() {
            val (hanke, _) = setup()

            hakemusMigrationService.migrateOneHanke(hanke.id)

            assertThat(hakemusyhteystietoRepository.findAll())
                .extracting { it.nimi }
                .containsExactlyInAnyOrder(
                    "Hakija Oy",
                    "Suorittaja Oy",
                    "Rakennuttaja Oy",
                    "Asianhoitaja Oy"
                )
        }

        @Test
        fun `creates hakemusyhteyshenkilot for the contacts`() {
            val (hanke, _) = setup()

            hakemusMigrationService.migrateOneHanke(hanke.id)

            assertThat(hakemusyhteyshenkiloRepository.findAll()).hasSize(3)
            val applicationId = hakemusRepository.findAll().first().id!!
            val yhteyshenkilot =
                hakemusService.getById(applicationId).applicationData.yhteystiedot().flatMap {
                    it.yhteyshenkilot
                }
            assertThat(yhteyshenkilot)
                .extracting { it.sahkoposti }
                .containsExactlyInAnyOrder(
                    "hakija@email",
                    "tyonsuorittaja@email",
                    "asianhoitaja@email",
                )
        }

        @Test
        fun `removes customer and contacts from the application data`() {
            val (hanke, _) = setup()

            hakemusMigrationService.migrateOneHanke(hanke.id)

            val applicationData = hakemusRepository.findAll().single().applicationData
            assertThat(applicationData).isInstanceOf(CableReportApplicationData::class).all {
                prop(CableReportApplicationData::customerWithContacts).isNull()
                prop(CableReportApplicationData::contractorWithContacts).isNull()
                prop(CableReportApplicationData::propertyDeveloperWithContacts).isNull()
                prop(CableReportApplicationData::representativeWithContacts).isNull()
            }
        }
    }

    @Nested
    inner class CreateFounderKayttaja {
        @Test
        fun `returns null and saves nothing if there are no orderers`() {
            val hanke = hankeFactory.saveMinimal()
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts = ApplicationFactory.createCompanyCustomer().withContact()
                )

            val response = hakemusMigrationService.createFounderKayttaja(hanke.id, data)

            assertThat(response).isNull()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " "])
        @NullSource
        fun `returns null and saves nothing if the orderer doesn't have an email`(email: String?) {
            val hanke = hankeFactory.saveMinimal()
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContact(orderer = true, email = email)
                )

            val response = hakemusMigrationService.createFounderKayttaja(hanke.id, data)

            assertThat(response).isNull()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
        }

        @Test
        fun `saves and returns a hankekayttaja for the orderer`() {
            val hanke = hankeFactory.saveMinimal()
            val data = ApplicationFactory.createCableReportApplicationData()

            val response = hakemusMigrationService.createFounderKayttaja(hanke.id, data)

            fun Assert<HankekayttajaEntity>.hasCorrectFields() = all {
                prop(HankekayttajaEntity::etunimi).isEqualTo(TEPPO)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(TESTIHENKILO)
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(TEPPO_EMAIL)
                prop(HankekayttajaEntity::puhelin).isEqualTo(TEPPO_PHONE)
                prop(HankekayttajaEntity::hankeId).isEqualTo(hanke.id)
            }
            assertThat(response).isNotNull().hasCorrectFields()
            assertThat(hankekayttajaRepository.findAll()).single().hasCorrectFields()
        }

        @Test
        fun `uses '-' when the orderer has a missing values`() {
            val hanke = hankeFactory.saveMinimal()
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContact(
                                orderer = true,
                                firstName = null,
                                lastName = " ",
                                phone = ""
                            )
                )

            val response = hakemusMigrationService.createFounderKayttaja(hanke.id, data)

            fun Assert<HankekayttajaEntity>.hasCorrectFields() = all {
                prop(HankekayttajaEntity::etunimi).isEqualTo("-")
                prop(HankekayttajaEntity::sukunimi).isEqualTo("-")
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(TEPPO_EMAIL)
                prop(HankekayttajaEntity::puhelin).isEqualTo("-")
                prop(HankekayttajaEntity::hankeId).isEqualTo(hanke.id)
            }
            assertThat(response).isNotNull().hasCorrectFields()
            assertThat(hankekayttajaRepository.findAll()).single().hasCorrectFields()
        }
    }

    @Nested
    inner class CreateOtherKayttajat {
        @Test
        fun `doesn't save anything when there are no customers`() {
            val hanke = hankeFactory.saveMinimal()
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts = null,
                    contractorWithContacts = null,
                    propertyDeveloperWithContacts = null,
                    representativeWithContacts = null,
                )

            hakemusMigrationService.createOtherKayttajat(data, null, hanke.id)

            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
        }

        @Test
        fun `uses the most common names and phone numbers by email`() {
            val hanke = hankeFactory.saveMinimal()
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts = customerWithContacts("1"),
                    contractorWithContacts = customerWithContacts("2"),
                    propertyDeveloperWithContacts = customerWithContacts("1"),
                    representativeWithContacts = null,
                )

            hakemusMigrationService.createOtherKayttajat(data, null, hanke.id)

            val kayttajat = hankekayttajaRepository.findAll()
            assertThat(kayttajat).hasSize(2)
            assertThat(kayttajat).all {
                extracting { it.etunimi }.containsExactlyInAnyOrder("Pekka1", "Matti1")
                extracting { it.sukunimi }.containsExactlyInAnyOrder("Pelokas1", "Mallikas1")
                extracting { it.puhelin }.containsExactlyInAnyOrder("9871", "1231")
                extracting { it.sahkoposti }.containsExactlyInAnyOrder("pekka@pelko", "matti@malli")
                extracting { it.kutsuttuEtunimi }.containsExactlyInAnyOrder("Pekka1", "Matti1")
                extracting { it.kutsuttuSukunimi }
                    .containsExactlyInAnyOrder("Pelokas1", "Mallikas1")
                each {
                    it.prop(HankekayttajaEntity::hankeId).isEqualTo(hanke.id)
                    it.prop(HankekayttajaEntity::permission).isNull()
                }
            }
            assertThat(kayttajakutsuRepository.findAll()).hasSize(2)
        }

        @Test
        fun `skips contacts with the founder's email`() {
            val hanke = hankeFactory.saveMinimal()
            val founder = HankeKayttajaFactory.createEntity(sahkoposti = "pekka@pelko")
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts = customerWithContacts("1"),
                    contractorWithContacts = customerWithContacts("2"),
                    propertyDeveloperWithContacts = customerWithContacts("1"),
                    representativeWithContacts = null,
                )

            hakemusMigrationService.createOtherKayttajat(data, founder, hanke.id)

            assertThat(hankekayttajaRepository.findAll())
                .single()
                .prop(HankekayttajaEntity::sahkoposti)
                .isEqualTo("matti@malli")
            assertThat(kayttajakutsuRepository.findAll()).single()
        }

        @Test
        fun `replaces missing names and phone numbers with dashes`() {
            val hanke = hankeFactory.saveMinimal()
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContacts(
                                ApplicationFactory.createContact(email = "em1", firstName = ""),
                                ApplicationFactory.createContact(email = "em2", lastName = null),
                                ApplicationFactory.createContact(email = "em3", phone = " "),
                            ),
                    contractorWithContacts = null,
                    propertyDeveloperWithContacts = null,
                    representativeWithContacts = null,
                )

            hakemusMigrationService.createOtherKayttajat(data, null, hanke.id)

            val kayttajat = hankekayttajaRepository.findAll()
            assertThat(kayttajat).hasSize(3)
            assertThat(kayttajat).all {
                extracting { it.etunimi }.containsExactlyInAnyOrder(TEPPO, TEPPO, "-")
                extracting { it.sukunimi }
                    .containsExactlyInAnyOrder(TESTIHENKILO, TESTIHENKILO, "-")
                extracting { it.kutsuttuEtunimi }.containsExactlyInAnyOrder(TEPPO, TEPPO, "-")
                extracting { it.kutsuttuSukunimi }
                    .containsExactlyInAnyOrder(TESTIHENKILO, TESTIHENKILO, "-")
                extracting { it.puhelin }.containsExactlyInAnyOrder(TEPPO_PHONE, TEPPO_PHONE, "-")
                extracting { it.sahkoposti }.containsExactlyInAnyOrder("em1", "em2", "em3")
            }
            assertThat(kayttajakutsuRepository.findAll()).hasSize(3)
        }

        @Test
        fun `skips contacts without email`() {
            val hanke = hankeFactory.saveMinimal()
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContacts(
                                ApplicationFactory.createContact(email = null),
                                ApplicationFactory.createContact(email = ""),
                                ApplicationFactory.createContact(email = " ")
                            ),
                    contractorWithContacts = null,
                    propertyDeveloperWithContacts = null,
                    representativeWithContacts = null,
                )

            hakemusMigrationService.createOtherKayttajat(data, null, hanke.id)

            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
        }

        private fun customerWithContacts(suffix: String) =
            ApplicationFactory.createCompanyCustomer().withContacts(*contacts(suffix))

        private fun contacts(suffix: String) =
            arrayOf(
                ApplicationFactory.createContact(
                    firstName = "Pekka$suffix",
                    lastName = "Pelokas$suffix",
                    email = "pekka@pelko",
                    phone = "987$suffix",
                ),
                ApplicationFactory.createContact(
                    firstName = "Matti$suffix",
                    lastName = "Mallikas$suffix",
                    email = "matti@malli",
                    phone = "123$suffix",
                ),
            )
    }
}
