package fi.hel.haitaton.hanke.permissions

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.exactly
import assertk.assertions.first
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import assertk.assertions.messageContains
import assertk.assertions.prop
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.email.HankeInvitationData
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianHoitajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createApplicationEntity
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createCableReportApplicationData
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createCompanyCustomer
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.defaultApplicationIdentifier
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.defaultApplicationName
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.expectedRecipients
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.defaultNimi
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.factory.identifier
import fi.hel.haitaton.hanke.logging.AuditLogEvent
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.auditEvent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasTargetType
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.toChangeLogJsonString
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifySequence
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeKayttajaServiceITest : DatabaseTest() {

    @Autowired private lateinit var hankeKayttajaService: HankeKayttajaService
    @Autowired private lateinit var permissionService: PermissionService

    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var kayttajaFactory: HankeKayttajaFactory

    @Autowired private lateinit var kayttajaTunnisteRepository: KayttajaTunnisteRepository
    @Autowired private lateinit var hankeKayttajaRepository: HankeKayttajaRepository
    @Autowired private lateinit var permissionRepository: PermissionRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var applicationRepository: ApplicationRepository

    @MockkBean private lateinit var emailSenderService: EmailSenderService

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @AfterEach
    fun tearDown() {
        checkUnnecessaryStub()
        confirmVerified(emailSenderService)
    }

    @Nested
    inner class GetKayttajatByHankeId {

        @Test
        fun `Returns users from correct hanke only`() {
            val hankeToFind = hankeFactory.createRequest().withYhteystiedot().save()
            hankeFactory.createRequest().withYhteystiedot().save()

            val result: List<HankeKayttajaDto> =
                hankeKayttajaService.getKayttajatByHankeId(hankeToFind.id!!)

            assertThat(result).hasSize(4)
        }

        @Test
        fun `Returns data matching to the saved entity`() {
            val hanke = hankeFactory.createRequest().withGeneratedOmistaja(1).save()

            val result: List<HankeKayttajaDto> =
                hankeKayttajaService.getKayttajatByHankeId(hanke.id!!)

            val entity: HankeKayttajaEntity =
                hankeKayttajaRepository.findAll().also { assertThat(it).hasSize(1) }.first()
            val dto: HankeKayttajaDto = result.first().also { assertThat(result).hasSize(1) }
            with(dto) {
                assertThat(id).isEqualTo(entity.id)
                assertThat(nimi).isEqualTo(entity.nimi)
                assertThat(sahkoposti).isEqualTo(entity.sahkoposti)
                assertThat(kayttooikeustaso).isEqualTo(entity.kayttajaTunniste!!.kayttooikeustaso)
                assertThat(tunnistautunut).isEqualTo(false)
            }
        }
    }

    @Nested
    inner class GetKayttajaByUserId {

        @Test
        fun `When user exists should return current hanke user`() {
            val hanke = hankeFactory.saveGenerated(userId = USERNAME)

            val result: HankeKayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(hanke.id!!, USERNAME)

            assertThat(result).isNotNull()
            with(result!!) {
                assertThat(id).isNotNull()
                assertThat(sahkoposti).isEqualTo(teppoEmail)
                assertThat(nimi).isEqualTo(TEPPO_TESTI)
                assertThat(permission?.kayttooikeustaso).isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                assertThat(permission).isNotNull()
            }
        }

        @Test
        fun `When no hanke should return null`() {
            val result: HankeKayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(123, USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `When no related permission should return null`() {
            val hanke = hankeFactory.save()
            permissionRepository.deleteAll()

            val result: HankeKayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(hanke.id!!, USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `When no kayttaja should return null`() {
            val hanke = hankeFactory.saveGenerated(userId = USERNAME)
            val hankeId = hanke.id!!
            val createdKayttaja = hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)!!
            hankeKayttajaRepository.deleteById(createdKayttaja.id)

            val result: HankeKayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class AddHankeFounder {
        private val perustaja = HankeFactory.defaultPerustaja

        @Test
        fun `Saves kayttaja with correct permission and other data`() {
            val hankeEntity = hankeFactory.saveEntity()
            val savedHankeId = hankeEntity.id!!
            assertThat(hankeKayttajaRepository.findAll()).isEmpty()

            hankeKayttajaService.addHankeFounder(savedHankeId, perustaja, USERNAME)

            val kayttajaEntity =
                hankeKayttajaRepository.findAll().also { assertThat(it).hasSize(1) }.first()
            with(kayttajaEntity) {
                assertThat(id).isNotNull()
                assertThat(hankeId).isEqualTo(savedHankeId)
                assertThat(permission).isNotNull().all {
                    prop(PermissionEntity::kayttooikeustaso)
                        .isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    prop(PermissionEntity::hankeId).isEqualTo(savedHankeId)
                    prop(PermissionEntity::userId).isEqualTo(USERNAME)
                }
                assertThat(sahkoposti).isEqualTo(perustaja.email)
                assertThat(nimi).isEqualTo(perustaja.nimi)
            }
        }

        @Test
        fun `Writes user and token to audit log`() {
            val hankeEntity = hankeFactory.saveEntity()
            val savedHankeId = hankeEntity.id!!
            auditLogRepository.deleteAll()

            hankeKayttajaService.addHankeFounder(savedHankeId, perustaja, USERNAME)

            val (kayttajaEntries, tunnisteEntries) =
                auditLogRepository
                    .findAll()
                    .also { assertThat(it).hasSize(2) }
                    .partition { it.message.auditEvent.target.type == ObjectType.HANKE_KAYTTAJA }
            assertThat(kayttajaEntries).hasSize(1)
            assertThat(kayttajaEntries).first().isSuccess(Operation.CREATE) {
                hasUserActor(USERNAME)
                withTarget {
                    prop(AuditLogTarget::id).isNotNull()
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.HANKE_KAYTTAJA)
                    prop(AuditLogTarget::objectBefore).isNull()
                    hasObjectAfter {
                        prop(HankeKayttaja::id).isNotNull()
                        prop(HankeKayttaja::hankeId).isEqualTo(savedHankeId)
                        prop(HankeKayttaja::kayttajaTunnisteId).isNull()
                        prop(HankeKayttaja::permissionId).isNotNull()
                        prop(HankeKayttaja::nimi).isEqualTo(perustaja.nimi)
                        prop(HankeKayttaja::sahkoposti).isEqualTo(perustaja.email)
                    }
                }
            }
            assertThat(tunnisteEntries).hasSize(1)
            assertThat(tunnisteEntries).first().isSuccess(Operation.CREATE) {
                hasUserActor(USERNAME)
                withTarget {
                    prop(AuditLogTarget::id).isNotNull()
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.PERMISSION)
                    prop(AuditLogTarget::objectBefore).isNull()
                    hasObjectAfter {
                        prop(Permission::kayttooikeustaso)
                            .isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                        prop(Permission::hankeId).isEqualTo(savedHankeId)
                        prop(Permission::userId).isEqualTo(USERNAME)
                    }
                }
            }
        }
    }

    @Nested
    inner class SaveNewTokensFromApplication {

        @Test
        fun `Does nothing if application has no contacts`() {
            val hanke = hankeFactory.saveEntity()
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = createCompanyCustomer().withContacts(),
                    contractorWithContacts = createCompanyCustomer().withContacts()
                )
            val application =
                createApplicationEntity(
                    applicationIdentifier = defaultApplicationIdentifier,
                    applicationData = applicationData,
                    hanke = hanke
                )

            hankeKayttajaService.saveNewTokensFromApplication(
                application,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME,
                HankeKayttajaFactory.createEntity()
            )

            assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
            assertThat(hankeKayttajaRepository.findAll()).isEmpty()
        }

        @Test
        fun `With different contact emails creates tokens for them all`() {
            val hanke = hankeFactory.saveEntity()
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email1"),
                                createContact(email = "email2")
                            ),
                    contractorWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email3"),
                                createContact(email = "email4")
                            )
                )
            val application =
                createApplicationEntity(
                    applicationIdentifier = defaultApplicationIdentifier,
                    applicationData = applicationData,
                    hanke = hanke
                )

            hankeKayttajaService.saveNewTokensFromApplication(
                application,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME
            )

            val tunnisteet = kayttajaTunnisteRepository.findAll()
            assertThat(tunnisteet).hasSize(4)
            assertThat(tunnisteet).areValid()
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
        fun `When no sender info should skip invitations`() {
            val hanke = hankeFactory.saveMinimal(nimi = defaultNimi)
            val application =
                createApplicationEntity(
                    hanke = hanke,
                    applicationIdentifier = defaultApplicationIdentifier,
                    userId = USERNAME
                )

            hankeKayttajaService.saveNewTokensFromApplication(
                application,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME,
                currentKayttaja = null
            )

            verify { emailSenderService wasNot Called }
        }

        @Test
        fun `With different contact emails sends invitations`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact
                )
            val cableReportWithoutHanke =
                CableReportWithoutHanke(ApplicationType.CABLE_REPORT, applicationData)
            val hanke = hankeFactory.saveGenerated(cableReportWithoutHanke, USERNAME)
            val applicationEntity =
                applicationRepository.findAll().first().apply {
                    applicationIdentifier = defaultApplicationIdentifier
                }
            val capturedEmails = mutableListOf<HankeInvitationData>()
            justRun { emailSenderService.sendHankeInvitationEmail(capture(capturedEmails)) }
            val inviter =
                with(hakijaApplicationContact) {
                    HankeKayttajaFactory.createEntity(nimi = name, sahkoposti = email)
                }

            hankeKayttajaService.saveNewTokensFromApplication(
                applicationEntity,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME,
                inviter
            )

            assertThat(capturedEmails).each { inv ->
                inv.transform { it.inviterEmail }.isEqualTo(hakijaApplicationContact.email)
                inv.transform { it.inviterName }.isEqualTo(hakijaApplicationContact.name)
                inv.transform { it.invitationToken }.isNotEmpty()
                inv.transform { it.recipientEmail }.isIn(*expectedRecipients)
                inv.transform { it.hankeTunnus }.isEqualTo(hanke.hankeTunnus)
                inv.transform { it.hankeNimi }.isEqualTo(hanke.nimi)
            }
            // 4 contacts but one is the sender
            verify(exactly = 3) { emailSenderService.sendHankeInvitationEmail(any()) }
        }

        @Test
        fun `With non-unique contact emails creates only the unique ones`() {
            val hanke = hankeFactory.saveEntity()
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email1"),
                                createContact(email = "email2"),
                                createContact(
                                    email = "email2",
                                    firstName = "Other",
                                    lastName = "Name"
                                ),
                            ),
                    contractorWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email1"),
                            )
                )
            val application =
                createApplicationEntity(
                    applicationIdentifier = defaultApplicationIdentifier,
                    applicationData = applicationData,
                    hanke = hanke
                )

            hankeKayttajaService.saveNewTokensFromApplication(
                application,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME
            )

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
        fun `With pre-existing tokens creates only new ones`() {
            val hanke = hankeFactory.saveEntity()
            kayttajaFactory.saveUserAndToken(hanke.id!!, "Existing User", "email1")
            kayttajaFactory.saveUserAndToken(hanke.id!!, "Other User", "email4")
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email1"),
                                createContact(email = "email2")
                            ),
                    contractorWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email3"),
                                createContact(email = "email4")
                            )
                )
            val application =
                createApplicationEntity(
                    applicationIdentifier = defaultApplicationIdentifier,
                    applicationData = applicationData,
                    hanke = hanke
                )
            assertThat(kayttajaTunnisteRepository.findAll()).hasSize(2)

            hankeKayttajaService.saveNewTokensFromApplication(
                application,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME
            )

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
        fun `With pre-existing permissions creates only new ones`() {
            val hanke = hankeFactory.saveEntity()
            kayttajaFactory.saveUserAndPermission(hanke.id!!, "Existing User", "email1")
            kayttajaFactory.saveUserAndPermission(hanke.id!!, "Other User", "email4")
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email1"),
                                createContact(email = "email2")
                            ),
                    contractorWithContacts =
                        createCompanyCustomer()
                            .withContacts(
                                createContact(email = "email3"),
                                createContact(email = "email4")
                            )
                )
            val application =
                createApplicationEntity(
                    applicationIdentifier = defaultApplicationIdentifier,
                    applicationData = applicationData,
                    hanke = hanke
                )
            assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()

            hankeKayttajaService.saveNewTokensFromApplication(
                application,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME
            )

            val tunnisteet = kayttajaTunnisteRepository.findAll()
            assertThat(tunnisteet).hasSize(2)
            assertThat(tunnisteet).each { tunniste ->
                tunniste.transform { it.hankeKayttaja.sahkoposti }.isIn("email2", "email3")
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

        @Test
        fun `Writes new users and tokens to audit log`() {
            val hanke = hankeFactory.saveEntity()
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = createCompanyCustomer().withContact(email = "email1"),
                    contractorWithContacts = createCompanyCustomer().withContact(email = "email3"),
                )
            val application =
                createApplicationEntity(
                    applicationIdentifier = defaultApplicationIdentifier,
                    applicationData = applicationData,
                    hanke = hanke
                )
            auditLogRepository.deleteAll()

            hankeKayttajaService.saveNewTokensFromApplication(
                application,
                hanke.id!!,
                hanke.hankeTunnus!!,
                hanke.nimi,
                USERNAME
            )

            assertThat(auditLogRepository.findAll()).all {
                hasSize(4)
                each { it.auditEvent().hasUserActor(USERNAME) }
                exactly(2) { it.hasTargetType(ObjectType.HANKE_KAYTTAJA) }
                exactly(2) { it.hasTargetType(ObjectType.KAYTTAJA_TUNNISTE) }
            }
        }
    }

    @Nested
    inner class SaveNewTokensFromHanke {

        @Test
        fun `Does nothing if hanke has no contacts`() {
            hankeKayttajaService.saveNewTokensFromHanke(HankeFactory.create(), USERNAME)

            assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
            assertThat(hankeKayttajaRepository.findAll()).isEmpty()
        }

        @Test
        fun `Creates tokens for unique ones`() {
            val hankeEntity = hankeFactory.saveMinimal()
            val hanke =
                HankeFactory.create(id = hankeEntity.id, hankeTunnus = hankeEntity.hankeTunnus)
                    .withYhteystiedot(
                        // each has a duplicate
                        omistajat = listOf(1, 1),
                        rakennuttajat = listOf(2, 2),
                        toteuttajat = listOf(3, 3),
                        muut = listOf(4, 4)
                    )
            assertThat(hanke.extractYhteystiedot()).hasSize(8)

            hankeKayttajaService.saveNewTokensFromHanke(hanke, USERNAME)

            val tunnisteet: List<KayttajaTunnisteEntity> = kayttajaTunnisteRepository.findAll()
            val kayttajat: List<HankeKayttajaEntity> = hankeKayttajaRepository.findAll()
            assertThat(tunnisteet).hasSize(4) // 4 yhteyshenkilo subcontacts.
            assertThat(kayttajat).hasSize(4)
            assertThat(tunnisteet).areValid()
            assertThat(kayttajat).areValid(hanke.id)
        }

        @Test
        fun `Writes new users and tokens to audit log`() {
            val hankeEntity = hankeFactory.saveMinimal()
            val hanke =
                HankeFactory.create(id = hankeEntity.id, hankeTunnus = hankeEntity.hankeTunnus)
                    .withYhteystiedot(
                        // each has a duplicate
                        omistajat = listOf(1, 1),
                        rakennuttajat = listOf(2, 2),
                        toteuttajat = listOf(3, 3),
                        muut = listOf(4, 4)
                    )
            auditLogRepository.deleteAll()

            hankeKayttajaService.saveNewTokensFromHanke(hanke, USERNAME)

            assertThat(auditLogRepository.findAll()).all {
                hasSize(8)
                each { it.auditEvent().hasUserActor(USERNAME) }
                exactly(4) { it.hasTargetType(ObjectType.HANKE_KAYTTAJA) }
                exactly(4) { it.hasTargetType(ObjectType.KAYTTAJA_TUNNISTE) }
            }
        }

        @Test
        fun `With pre-existing permissions does not create duplicate`() {
            val hanke = hankeFactory.save()
            val yhteystieto = HankeYhteystietoFactory.create()
            val contact = yhteystieto.alikontaktit[0]
            kayttajaFactory.saveUserAndPermission(hanke.id!!, contact.fullName(), contact.email)
            auditLogRepository.deleteAll()

            hankeKayttajaService.saveNewTokensFromHanke(
                hanke.apply { this.omistajat.add(yhteystieto) },
                USERNAME
            )

            val tunnisteet = kayttajaTunnisteRepository.findAll()
            assertThat(tunnisteet).isEmpty()
            assertThat(tunnisteet).areValid()
            val kayttajat = hankeKayttajaRepository.findAll()
            assertThat(kayttajat).hasSize(1)
            assertThat(kayttajat.map { it.sahkoposti }).containsExactly(contact.email)
            assertThat(auditLogRepository.findAll()).isEmpty()
        }

        @Test
        fun `Sends emails for new hanke users`() {
            val hanke = hankeFactory.saveGenerated(userId = USERNAME)
            val hankeWithYhteystiedot = hanke.withYhteystiedot() // 4 sub contacts
            val capturedEmails = mutableListOf<HankeInvitationData>()
            justRun { emailSenderService.sendHankeInvitationEmail(capture(capturedEmails)) }

            hankeKayttajaService.saveNewTokensFromHanke(hankeWithYhteystiedot, USERNAME)

            verify(exactly = 4) { emailSenderService.sendHankeInvitationEmail(any()) }
            assertThat(capturedEmails).each { inv ->
                inv.transform { it.inviterName }.isEqualTo(TEPPO_TESTI)
                inv.transform { it.inviterEmail }.isEqualTo(teppoEmail)
                inv.transform { it.recipientEmail }
                    .isIn("yhteys-email1", "yhteys-email2", "yhteys-email3", "yhteys-email4")
                inv.transform { it.hankeTunnus }.isEqualTo(hanke.hankeTunnus!!)
                inv.transform { it.hankeNimi }.isEqualTo(defaultApplicationName)
                inv.transform { it.invitationToken }.isNotEmpty()
            }
        }
    }

    @Nested
    inner class UpdatePermissions {

        @Test
        fun `Doesn't throw any exceptions with no updates`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val updates = mapOf<UUID, Kayttooikeustaso>()

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)
        }

        @Test
        fun `Updates kayttooikeustaso to permission if it exists`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUserAndPermission(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.kayttajaTunniste).isNull()
            assertThat(updatedKayttaja.permission).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Writes permission update to audit log`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUserAndPermission(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)
            auditLogRepository.deleteAll()

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)

            val logs = auditLogRepository.findAll()
            assertThat(logs).hasSize(1)
            assertThat(logs)
                .first()
                .transform { it.message.auditEvent }
                .all {
                    transform { it.target.type }.isEqualTo(ObjectType.PERMISSION)
                    transform { it.target.id }.isEqualTo(kayttaja.permission?.id.toString())
                    transform { it.operation }.isEqualTo(Operation.UPDATE)
                    transform { it.actor.role }.isEqualTo(UserRole.USER)
                    transform { it.actor.userId }.isEqualTo(USERNAME)
                    val permission = kayttaja.permission!!.toDomain()
                    transform { it.target.objectBefore }
                        .isEqualTo(permission.toChangeLogJsonString())
                    transform { it.target.objectAfter }
                        .isEqualTo(
                            permission
                                .copy(kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS)
                                .toChangeLogJsonString()
                        )
                }
        }

        @Test
        fun `Updates kayttooikeustaso to tunniste if permission doesn't exist`() {
            val hankehankeIdentifier = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUserAndToken(hankehankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankehankeIdentifier, updates, false, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNull()
            assertThat(updatedKayttaja.kayttajaTunniste).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Writes tunniste update to audit log`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUserAndToken(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)
            auditLogRepository.deleteAll()

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)

            val logs = auditLogRepository.findAll()
            val expectedTunniste =
                kayttaja.kayttajaTunniste!!.toDomain().copy(hankeKayttajaId = kayttaja.id)
            assertThat(logs).hasSize(1)
            assertThat(logs).first().auditEvent().all {
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.KAYTTAJA_TUNNISTE)
                    hasId(kayttaja.kayttajaTunniste!!.id)
                    prop(AuditLogTarget::objectBefore)
                        .isEqualTo(expectedTunniste.toChangeLogJsonString())
                    prop(AuditLogTarget::objectAfter)
                        .isEqualTo(
                            expectedTunniste
                                .copy(kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS)
                                .toChangeLogJsonString()
                        )
                }
                prop(AuditLogEvent::operation).isEqualTo(Operation.UPDATE)
                hasUserActor(USERNAME)
            }
        }

        @Test
        fun `Updates kayttooikeustaso to only permission if both permission and tunniste exist`() {
            val hanke = hankeFactory.save()
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hanke.id!!,
                    "Kolmas Kehveli",
                    "kolmas@kehveli.test"
                )
            kayttajaFactory.addToken(kayttaja, "token for both", Kayttooikeustaso.KATSELUOIKEUS)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hanke.identifier(), updates, false, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
            assertThat(updatedKayttaja.kayttajaTunniste).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.KATSELUOIKEUS
            }
        }

        @Test
        fun `Throws exception if changing the user's own permission`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKIEN_MUOKKAUS,
                    userId = USERNAME
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    hankeKayttajaService.updatePermissions(
                        hankeIdentifier,
                        updates,
                        false,
                        USERNAME
                    )
                }
                .all {
                    hasClass(ChangingOwnPermissionException::class)
                    messageContains(USERNAME)
                }
        }

        @Test
        fun `Throws exception if given a non-existing Kayttaja Id`() {
            val missingId = UUID.fromString("b4f4872d-ac5a-43e0-b0bc-79d7d56d238e")
            val hankeIdentifier = hankeFactory.save().identifier()
            val updates = mapOf(missingId to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    hankeKayttajaService.updatePermissions(
                        hankeIdentifier,
                        updates,
                        false,
                        USERNAME
                    )
                }
                .all {
                    hasClass(HankeKayttajatNotFoundException::class)
                    messageContains(missingId.toString())
                    messageContains(hankeIdentifier.id.toString())
                }
        }

        @Test
        fun `Throws exception if given Kayttaja from another Hanke`() {
            val hanke1 = hankeFactory.save().identifier()
            val hanke2 = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUserAndPermission(hanke2.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    hankeKayttajaService.updatePermissions(hanke1, updates, false, USERNAME)
                }
                .all {
                    hasClass(HankeKayttajatNotFoundException::class)
                    messageContains(kayttaja.id.toString())
                    messageContains(hanke1.id.toString())
                }
        }

        @Test
        fun `Throws exception if the kayttaja to update has neither permission nor tunniste`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUser(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    hankeKayttajaService.updatePermissions(
                        hankeIdentifier,
                        updates,
                        false,
                        USERNAME
                    )
                }
                .all {
                    hasClass(UsersWithoutKayttooikeustasoException::class)
                    messageContains(kayttaja.id.toString())
                }
        }

        @Test
        fun `Throws exception without admin permission if the kayttaja to update has KAIKKI_OIKEUDET in permission`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    hankeKayttajaService.updatePermissions(
                        hankeIdentifier,
                        updates,
                        false,
                        USERNAME
                    )
                }
                .all {
                    hasClass(MissingAdminPermissionException::class)
                    messageContains(USERNAME)
                }
        }

        @Test
        fun `Throws exception without admin permission if the kayttaja to update has KAIKKI_OIKEUDET in tunniste`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja =
                kayttajaFactory.saveUserAndToken(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    hankeKayttajaService.updatePermissions(
                        hankeIdentifier,
                        updates,
                        false,
                        USERNAME
                    )
                }
                .all {
                    hasClass(MissingAdminPermissionException::class)
                    messageContains(USERNAME)
                }
        }

        @Test
        fun `Succeeds with admin permission when the kayttaja to update has KAIKKI_OIKEUDET in permission`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.kayttajaTunniste).isNull()
            assertThat(updatedKayttaja.permission).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Succeeds with with admin permission when the kayttaja to update has KAIKKI_OIKEUDET in tunniste`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja =
                kayttajaFactory.saveUserAndToken(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNull()
            assertThat(updatedKayttaja.kayttajaTunniste).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Throws exception without admin permission if updating to KAIKKI_OIKEUDET`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUserAndToken(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.KAIKKI_OIKEUDET)

            assertFailure {
                    hankeKayttajaService.updatePermissions(
                        hankeIdentifier,
                        updates,
                        false,
                        USERNAME
                    )
                }
                .all {
                    hasClass(MissingAdminPermissionException::class)
                    messageContains(USERNAME)
                }
        }

        @Test
        fun `Succeeds with with admin permission if updating to KAIKKI_OIKEUDET`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            val kayttaja = kayttajaFactory.saveUserAndToken(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.KAIKKI_OIKEUDET)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNull()
            assertThat(updatedKayttaja.kayttajaTunniste).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Throw exception if trying to demote the last KAIKKI_OIKEUDET kayttaja`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            permissionRepository.deleteAll()
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            kayttajaFactory.saveUserAndPermission(
                hankeIdentifier.id,
                kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS,
                sahkoposti = "hankemuokkaus"
            )
            kayttajaFactory.saveUserAndPermission(
                hankeIdentifier.id,
                kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
                sahkoposti = "hakemusasiointi"
            )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)
                }
                .all {
                    hasClass(NoAdminRemainingException::class)
                    messageContains(hankeIdentifier.id.toString())
                    messageContains(hankeIdentifier.hankeTunnus)
                }
        }

        @Test
        fun `Don't throw an exception if an anonymous user still has KAIKKI_OIKEUDET`() {
            val hankeIdentifier = hankeFactory.save().identifier()
            hankeKayttajaRepository.deleteAll()
            permissionRepository.deleteAll()
            permissionService.create(hankeIdentifier.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }
    }

    @Nested
    inner class CreatePermissionFromToken {
        private val tunniste = "Itf4UuErPqBHkhJF7CUAsu69"
        private val newUserId = "newUser"

        @Test
        fun `throws exception if tunniste doesn't exist`() {
            assertFailure { hankeKayttajaService.createPermissionFromToken(newUserId, "fake") }
                .all {
                    hasClass(TunnisteNotFoundException::class)
                    messageContains(newUserId)
                    messageContains("fake")
                }
        }

        @Test
        fun `throws an exception if the user already has a permission for the hanke kayttaja`() {
            val hanke = hankeFactory.save()
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hanke.id!!,
                    userId = newUserId,
                )
            kayttajaFactory.addToken(kayttaja)

            assertFailure { hankeKayttajaService.createPermissionFromToken(newUserId, "existing") }
                .all {
                    hasClass(UserAlreadyHasPermissionException::class)
                    messageContains(newUserId)
                    messageContains(kayttaja.id.toString())
                    messageContains(kayttaja.permission!!.id.toString())
                }
        }

        @Test
        fun `throws an exception if the user has a permission for the hanke from elsewhere`() {
            val hanke = hankeFactory.save()
            val kayttaja = kayttajaFactory.saveUserAndToken(hanke.id!!, tunniste = tunniste)
            val permission =
                permissionService.create(
                    userId = newUserId,
                    hankeId = hanke.id!!,
                    kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS
                )

            assertFailure { hankeKayttajaService.createPermissionFromToken(newUserId, tunniste) }
                .all {
                    hasClass(UserAlreadyHasPermissionException::class)
                    messageContains(newUserId)
                    messageContains(kayttaja.id.toString())
                    messageContains(permission.id.toString())
                }
        }

        @Test
        fun `throws an exception if another user already has a permission for the hanke kayttaja`() {
            val hanke = hankeFactory.save()
            val kayttaja =
                kayttajaFactory.saveUserAndPermission(
                    hanke.id!!,
                    userId = "Other user",
                )
            kayttajaFactory.addToken(kayttaja)

            assertFailure { hankeKayttajaService.createPermissionFromToken(newUserId, "existing") }
                .all {
                    hasClass(PermissionAlreadyExistsException::class)
                    messageContains(newUserId)
                    messageContains("Other user")
                    messageContains(kayttaja.id.toString())
                    messageContains(kayttaja.permission!!.id.toString())
                }
        }

        @Test
        fun `Creates a permission`() {
            val hanke = hankeFactory.save()
            kayttajaFactory.saveUserAndToken(hanke.id!!, tunniste = tunniste)

            hankeKayttajaService.createPermissionFromToken(newUserId, tunniste)

            val permission = permissionRepository.findOneByHankeIdAndUserId(hanke.id!!, newUserId)
            assertThat(permission)
                .isNotNull()
                .transform { it.kayttooikeustaso }
                .isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
        }

        @Test
        fun `Removes the user token`() {
            val hanke = hankeFactory.save()
            kayttajaFactory.saveUserAndToken(hanke.id!!, tunniste = tunniste)

            hankeKayttajaService.createPermissionFromToken(newUserId, tunniste)

            assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
        }

        @Test
        fun `Writes the token removal to audit logs`() {
            val hanke = hankeFactory.save()
            kayttajaFactory.saveUserAndToken(hanke.id!!, tunniste = tunniste)
            auditLogRepository.deleteAll()

            hankeKayttajaService.createPermissionFromToken(newUserId, tunniste)

            val logs =
                auditLogRepository.findAll().filter {
                    it.message.auditEvent.target.type == ObjectType.KAYTTAJA_TUNNISTE
                }
            assertThat(logs).hasSize(1)
            assertThat(logs).first().isSuccess(Operation.DELETE) {
                hasUserActor(newUserId)
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.KAYTTAJA_TUNNISTE)
                    prop(AuditLogTarget::objectAfter).isNull()
                }
            }
        }
    }

    @Nested
    inner class ResendInvitation {
        @Test
        fun `Throws exception if current user doesn't have a kayttaja`() {
            val hanke = hankeFactory.saveMinimal()
            val kayttaja = kayttajaFactory.saveUser(hanke.id!!)

            assertFailure { hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME) }
                .all {
                    hasClass(CurrentUserWithoutKayttajaException::class)
                    messageContains(USERNAME)
                }
        }

        @Test
        fun `Throws exception if kayttaja already has permission`() {
            val hanke = hankeFactory.saveMinimal()
            val kayttaja = kayttajaFactory.saveUserAndPermission(hanke.id!!)

            assertFailure { hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME) }
                .all {
                    hasClass(UserAlreadyHasPermissionException::class)
                    messageContains(USERNAME)
                    messageContains(kayttaja.id.toString())
                    messageContains(kayttaja.permission!!.id.toString())
                }
        }

        @Test
        fun `Creates a new tunniste if kayttaja doesn't have one`() {
            val hanke = hankeFactory.saveMinimal(nimi = "Hanke")
            kayttajaFactory.saveUserAndPermission(
                hanke.id!!,
                userId = USERNAME,
                sahkoposti = "current@user"
            )
            val kayttaja = kayttajaFactory.saveUser(hanke.id!!)
            assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
            justRun { emailSenderService.sendHankeInvitationEmail(any()) }

            hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME)

            val tunnisteet = kayttajaTunnisteRepository.findAll()
            assertThat(tunnisteet).hasSize(1)
            assertThat(tunnisteet).first().hasKayttajaWithId(kayttaja.id)
            verifySequence { emailSenderService.sendHankeInvitationEmail(any()) }
        }

        @Test
        fun `If kayttaja has a tunniste recreates it and deletes the old one `() {
            val hanke = hankeFactory.saveMinimal(nimi = "Hanke")
            kayttajaFactory.saveUserAndPermission(
                hanke.id!!,
                userId = USERNAME,
                sahkoposti = "current@user"
            )
            val kayttaja = kayttajaFactory.saveUserAndToken(hanke.id!!)
            assertThat(kayttajaTunnisteRepository.findAll()).hasSize(1)
            val tunnisteId = kayttaja.kayttajaTunniste!!.id
            val tunniste = kayttaja.kayttajaTunniste!!.tunniste
            justRun { emailSenderService.sendHankeInvitationEmail(any()) }

            hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME)

            val tunnisteet = kayttajaTunnisteRepository.findAll()
            assertThat(tunnisteet).hasSize(1)
            assertThat(tunnisteet).first().all {
                hasKayttajaWithId(kayttaja.id)
                prop(KayttajaTunnisteEntity::id).isNotEqualTo(tunnisteId)
                prop(KayttajaTunnisteEntity::tunniste).isNotEqualTo(tunniste)
            }
            verifySequence { emailSenderService.sendHankeInvitationEmail(any()) }
        }

        @Test
        fun `Sends the invitation email`() {
            val hanke = hankeFactory.saveMinimal(nimi = "Hanke")
            kayttajaFactory.saveUserAndPermission(
                hanke.id!!,
                userId = USERNAME,
                sahkoposti = "current@user",
                nimi = "Current User"
            )
            val kayttaja = kayttajaFactory.saveUser(hanke.id!!)
            val capturedEmails = mutableListOf<HankeInvitationData>()
            justRun { emailSenderService.sendHankeInvitationEmail(capture(capturedEmails)) }

            hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME)

            assertThat(capturedEmails).hasSize(1)
            assertThat(capturedEmails).first().all {
                prop(HankeInvitationData::inviterEmail).isEqualTo("current@user")
                prop(HankeInvitationData::inviterName).isEqualTo("Current User")
                prop(HankeInvitationData::recipientEmail).isEqualTo(kayttaja.sahkoposti)
                prop(HankeInvitationData::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(HankeInvitationData::hankeNimi).isEqualTo(hanke.nimi)
                prop(HankeInvitationData::invitationToken).isNotNull()
            }
            verifySequence { emailSenderService.sendHankeInvitationEmail(any()) }
        }
    }

    private fun Assert<KayttajaTunnisteEntity>.hasKayttajaWithId(kayttajaId: UUID) =
        prop(KayttajaTunnisteEntity::hankeKayttaja)
            .isNotNull()
            .prop(HankeKayttajaEntity::id)
            .isEqualTo(kayttajaId)

    private fun Assert<List<KayttajaTunnisteEntity>>.areValid() = each { t ->
        t.prop(KayttajaTunnisteEntity::id).isNotNull()
        t.prop(KayttajaTunnisteEntity::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
        t.prop(KayttajaTunnisteEntity::createdAt).isRecent()
        t.prop(KayttajaTunnisteEntity::tunniste).matches(Regex(kayttajaTunnistePattern))
        t.prop(KayttajaTunnisteEntity::hankeKayttaja).isNotNull()
    }

    private fun Assert<List<HankeKayttajaEntity>>.areValid(hankeId: Int?) = each { k ->
        k.prop(HankeKayttajaEntity::id).isNotNull()
        k.prop(HankeKayttajaEntity::nimi).isIn(*expectedNames)
        k.prop(HankeKayttajaEntity::sahkoposti).isIn(*expectedEmails)
        k.prop(HankeKayttajaEntity::hankeId).isEqualTo(hankeId)
        k.prop(HankeKayttajaEntity::permission).isNull()
        k.prop(HankeKayttajaEntity::kayttajaTunniste).isNotNull()
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
}
