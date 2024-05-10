package fi.hel.haitaton.hanke.permissions

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsMatch
import assertk.assertions.each
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.DEFAULT_HANKE_PERUSTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_ASIANHOITAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_MUU
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_OMISTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_PERUSTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_RAKENNUTTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_SUORITTAJA
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_GIVEN_NAME
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_LAST_NAME
import fi.hel.haitaton.hanke.factory.identifier
import fi.hel.haitaton.hanke.logging.AuditLogEvent
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.profiili.VerifiedNameNotFound
import fi.hel.haitaton.hanke.test.Asserts.hasReceivers
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.auditEvent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toChangeLogJsonString
import fi.hel.haitaton.hanke.userId
import io.mockk.every
import io.mockk.mockk
import jakarta.mail.internet.MimeMessage
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContext

const val kayttajaTunnistePattern = "[a-zA-z0-9]{24}"

class HankeKayttajaServiceITest : IntegrationTest() {

    @Autowired private lateinit var hankeKayttajaService: HankeKayttajaService
    @Autowired private lateinit var permissionService: PermissionService

    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var kayttajaFactory: HankeKayttajaFactory

    @Autowired private lateinit var kayttajakutsuRepository: KayttajakutsuRepository
    @Autowired private lateinit var hankeKayttajaRepository: HankekayttajaRepository
    @Autowired private lateinit var permissionRepository: PermissionRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var applicationRepository: ApplicationRepository

    @Autowired private lateinit var profiiliClient: ProfiiliClient

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Nested
    inner class GetKayttaja {
        @Test
        fun `Throws exception when kayttaja not found`() {
            val id = UUID.fromString("a278f4f0-4ef2-4b25-b5ab-4cee66e73146")

            val failure = assertFailure { hankeKayttajaService.getKayttaja(id) }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(id.toString())
            }
        }

        @Test
        fun `Returns kayttaja when it exists`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val kayttajaEntity = kayttajaFactory.saveIdentifiedUser(hanke.id)

            val response = hankeKayttajaService.getKayttaja(kayttajaEntity.id)

            assertThat(response).all {
                prop(HankeKayttaja::id).isEqualTo(kayttajaEntity.id)
                prop(HankeKayttaja::hankeId).isEqualTo(hanke.id)
                prop(HankeKayttaja::etunimi).isEqualTo(HankeKayttajaFactory.KAKE)
                prop(HankeKayttaja::sukunimi).isEqualTo(HankeKayttajaFactory.KATSELIJA)
                prop(HankeKayttaja::sahkoposti).isEqualTo(HankeKayttajaFactory.KAKE_EMAIL)
                prop(HankeKayttaja::puhelinnumero).isEqualTo(HankeKayttajaFactory.KAKE_PUHELIN)
                prop(HankeKayttaja::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
                prop(HankeKayttaja::roolit).isEmpty()
                prop(HankeKayttaja::permissionId).isNotNull()
                prop(HankeKayttaja::kayttajaTunnisteId).isNull()
                prop(HankeKayttaja::kutsuttu).isNull()
            }
        }

        @Test
        fun `Returns unidentified kayttaja when it exists`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val kayttajaEntity = kayttajaFactory.saveUnidentifiedUser(hanke.id)

            val response = hankeKayttajaService.getKayttaja(kayttajaEntity.id)

            assertThat(response).all {
                prop(HankeKayttaja::id).isEqualTo(kayttajaEntity.id)
                prop(HankeKayttaja::permissionId).isNull()
            }
        }
    }

    @Nested
    inner class GetKayttajatByHankeId {
        @Test
        fun `Returns users from correct hanke only`() {
            val hankeToFind = hankeFactory.builder(USERNAME).saveEntity()
            assertThat(hankeKayttajaRepository.findAll()).hasSize(1)
            val founder = hankeKayttajaRepository.findAll().first().id
            val user1 = kayttajaFactory.saveUnidentifiedUser(hankeToFind.id).id
            val user2 =
                kayttajaFactory.saveUnidentifiedUser(hankeToFind.id, sahkoposti = "toinen").id
            val otherHanke = hankeFactory.builder(USERNAME).save().id
            kayttajaFactory.saveUnidentifiedUser(otherHanke)
            kayttajaFactory.saveUnidentifiedUser(otherHanke, sahkoposti = "toinen@sahko.posti")

            val result: List<HankeKayttajaDto> =
                hankeKayttajaService.getKayttajatByHankeId(hankeToFind.id)

            assertThat(result.map { it.id }).containsExactlyInAnyOrder(founder, user1, user2)
        }

        @Test
        fun `Returns data matching to the saved entity`() {
            val hanke =
                hankeFactory.builder(USERNAME).withPerustaja(KAYTTAJA_INPUT_PERUSTAJA).create()

            val result: List<HankeKayttajaDto> =
                hankeKayttajaService.getKayttajatByHankeId(hanke.id)

            val entity: HankekayttajaEntity =
                hankeKayttajaRepository.findAll().also { assertThat(it).hasSize(1) }.first()
            assertThat(result).single().all {
                prop(HankeKayttajaDto::id).isEqualTo(entity.id)
                prop(HankeKayttajaDto::etunimi).isEqualTo(entity.etunimi)
                prop(HankeKayttajaDto::sukunimi).isEqualTo(entity.sukunimi)
                prop(HankeKayttajaDto::puhelinnumero).isEqualTo(entity.puhelin)
                prop(HankeKayttajaDto::sahkoposti).isEqualTo(entity.sahkoposti)
                prop(HankeKayttajaDto::kayttooikeustaso)
                    .isEqualTo(entity.permission!!.kayttooikeustaso)
                prop(HankeKayttajaDto::tunnistautunut).isEqualTo(true)
                prop(HankeKayttajaDto::kutsuttu).isNull()
            }
        }

        @Test
        fun `Returns correct roles for the users`() {
            val hanke =
                hankeFactory
                    .builder(USERNAME)
                    .withPerustaja(KAYTTAJA_INPUT_PERUSTAJA)
                    .saveWithYhteystiedot {
                        omistaja()
                        rakennuttaja()
                        toteuttaja()
                        toteuttaja(kayttaja(KAYTTAJA_INPUT_RAKENNUTTAJA))
                        muuYhteystieto(kayttaja(KAYTTAJA_INPUT_ASIANHOITAJA))
                        muuYhteystieto(kayttaja(KAYTTAJA_INPUT_MUU))
                    }
            val expectedRoles =
                mapOf(
                    Pair(KAYTTAJA_INPUT_OMISTAJA.email, arrayOf(ContactType.OMISTAJA)),
                    Pair(
                        KAYTTAJA_INPUT_RAKENNUTTAJA.email,
                        arrayOf(ContactType.RAKENNUTTAJA, ContactType.TOTEUTTAJA)
                    ),
                    Pair(KAYTTAJA_INPUT_SUORITTAJA.email, arrayOf(ContactType.TOTEUTTAJA)),
                    Pair(KAYTTAJA_INPUT_ASIANHOITAJA.email, arrayOf(ContactType.MUU)),
                    Pair(KAYTTAJA_INPUT_MUU.email, arrayOf(ContactType.MUU))
                )

            val result: List<HankeKayttajaDto> =
                hankeKayttajaService.getKayttajatByHankeId(hanke.id)

            assertThat(result).hasSize(6) // one is the founder
            result.forEach {
                if (it.sahkoposti == KAYTTAJA_INPUT_PERUSTAJA.email) {
                    assertThat(it.roolit).isEmpty() // founder has no roles
                } else {
                    assertThat(it.roolit).containsExactlyInAnyOrder(*expectedRoles[it.sahkoposti]!!)
                }
            }
        }
    }

    @Nested
    inner class GetKayttajaByUserId {
        @Test
        fun `When user exists should return current hanke user`() {
            val hanke = hankeFactory.builder(USERNAME).create()

            val result: HankekayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)

            assertThat(result).isNotNull().all {
                prop(HankekayttajaEntity::id).isNotNull()
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(DEFAULT_HANKE_PERUSTAJA.sahkoposti)
                prop(HankekayttajaEntity::etunimi).isEqualTo(DEFAULT_GIVEN_NAME)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(DEFAULT_LAST_NAME)
                prop(HankekayttajaEntity::puhelin).isEqualTo(DEFAULT_HANKE_PERUSTAJA.puhelinnumero)
                prop(HankekayttajaEntity::permission)
                    .isNotNull()
                    .prop(PermissionEntity::kayttooikeustaso)
                    .isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                prop(HankekayttajaEntity::kayttajakutsu).isNull() // no token for creator
                prop(HankekayttajaEntity::kutsujaId).isNull() // no inviter for creator
                prop(HankekayttajaEntity::kutsuttuEtunimi).isNull() // no name in invitation
                prop(HankekayttajaEntity::kutsuttuSukunimi).isNull() // no name in invitation
            }
        }

        @Test
        fun `When no hanke should return null`() {
            val result: HankekayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(123, USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `When no related permission should return null`() {
            val hanke = hankeFactory.builder(USERNAME).create()
            hankeKayttajaRepository.deleteAll()
            permissionRepository.deleteAll()

            val result: HankekayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `When no kayttaja should return null`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val hankeId = hanke.id
            val createdKayttaja = hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)!!
            hankeKayttajaRepository.deleteById(createdKayttaja.id)

            val result: HankekayttajaEntity? =
                hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class AddHankeFounder {
        private val founder = DEFAULT_HANKE_PERUSTAJA
        private val securityContext = mockk<SecurityContext>()

        @BeforeEach
        fun setUp() {
            every { securityContext.userId() } returns USERNAME
            every { profiiliClient.getVerifiedName(any()) } returns ProfiiliFactory.DEFAULT_NAMES
        }

        @Test
        fun `Saves kayttaja with correct permission and other data`() {
            val hankeEntity = hankeFactory.saveMinimal()
            val savedHankeId = hankeEntity.id
            assertThat(hankeKayttajaRepository.findAll()).isEmpty()

            hankeKayttajaService.addHankeFounder(savedHankeId, founder, securityContext)

            assertThat(hankeKayttajaRepository.findAll()).single().all {
                prop(HankekayttajaEntity::id).isNotNull()
                prop(HankekayttajaEntity::hankeId).isEqualTo(savedHankeId)
                prop(HankekayttajaEntity::permission).isNotNull().all {
                    prop(PermissionEntity::kayttooikeustaso)
                        .isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    prop(PermissionEntity::hankeId).isEqualTo(savedHankeId)
                    prop(PermissionEntity::userId).isEqualTo(USERNAME)
                }
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(founder.sahkoposti)
                prop(HankekayttajaEntity::puhelin).isEqualTo(founder.puhelinnumero)
                prop(HankekayttajaEntity::etunimi)
                    .isEqualTo(ProfiiliFactory.DEFAULT_NAMES.givenName)
                prop(HankekayttajaEntity::sukunimi)
                    .isEqualTo(ProfiiliFactory.DEFAULT_NAMES.lastName)
            }
        }

        @Test
        fun `Writes user and token to audit log`() {
            val hankeEntity = hankeFactory.saveMinimal()
            val savedHankeId = hankeEntity.id
            auditLogRepository.deleteAll()

            hankeKayttajaService.addHankeFounder(savedHankeId, founder, securityContext)

            val (kayttajaEntries, tunnisteEntries) =
                auditLogRepository
                    .findAll()
                    .also { assertThat(it).hasSize(2) }
                    .partition { it.message.auditEvent.target.type == ObjectType.HANKE_KAYTTAJA }
            assertThat(kayttajaEntries).single().isSuccess(Operation.CREATE) {
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
                        prop(HankeKayttaja::etunimi)
                            .isEqualTo(ProfiiliFactory.DEFAULT_NAMES.givenName)
                        prop(HankeKayttaja::sukunimi)
                            .isEqualTo(ProfiiliFactory.DEFAULT_NAMES.lastName)
                        prop(HankeKayttaja::sahkoposti).isEqualTo(founder.sahkoposti)
                        prop(HankeKayttaja::puhelinnumero).isEqualTo(founder.puhelinnumero)
                    }
                }
            }
            assertThat(tunnisteEntries).single().isSuccess(Operation.CREATE) {
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
    inner class CreateNewUser {
        private val email = "joku@sahkoposti.test"
        private val request = NewUserRequest("Joku", "Jokunen", email, "0508889999")

        @Test
        fun `throws exception when there is a pre-existing user with the same email`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            kayttajaFactory.saveUnidentifiedUser(hanke.id, sahkoposti = email)

            val failure = assertFailure {
                hankeKayttajaService.createNewUser(request, hanke, USERNAME)
            }

            failure.all {
                hasClass(UserAlreadyExistsException::class)
                messageContains(hanke.id.toString())
                messageContains(hanke.hankeTunnus)
                messageContains(email)
            }
        }

        @Test
        fun `returns information about the created user`() {
            val hanke = hankeFactory.builder(USERNAME).save()

            val result = hankeKayttajaService.createNewUser(request, hanke, USERNAME)

            assertThat(result).all {
                prop(HankeKayttajaDto::id).isNotNull()
                prop(HankeKayttajaDto::sahkoposti).isEqualTo(email)
                prop(HankeKayttajaDto::etunimi).isEqualTo("Joku")
                prop(HankeKayttajaDto::sukunimi).isEqualTo("Jokunen")
                prop(HankeKayttajaDto::puhelinnumero).isEqualTo("0508889999")
                prop(HankeKayttajaDto::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
                prop(HankeKayttajaDto::tunnistautunut).isFalse()
                prop(HankeKayttajaDto::kutsuttu).isRecent()
            }
        }

        @Test
        fun `creates a new hankekayttaja`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val inviter = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val result = hankeKayttajaService.createNewUser(request, hanke, USERNAME)

            assertThat(hankeKayttajaRepository.getReferenceById(result.id)).all {
                prop(HankekayttajaEntity::hankeId).isEqualTo(hanke.id)
                prop(HankekayttajaEntity::etunimi).isEqualTo("Joku")
                prop(HankekayttajaEntity::sukunimi).isEqualTo("Jokunen")
                prop(HankekayttajaEntity::puhelin).isEqualTo("0508889999")
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(email)
                prop(HankekayttajaEntity::kutsuttuEtunimi).isEqualTo("Joku")
                prop(HankekayttajaEntity::kutsuttuSukunimi).isEqualTo("Jokunen")
                prop(HankekayttajaEntity::permission).isNull()
                prop(HankekayttajaEntity::kutsujaId).isEqualTo(inviter.id)
            }
        }

        @Test
        fun `creates a new invitation when caller has a hankekayttaja`() {
            val hanke = hankeFactory.builder(USERNAME).save()

            val result = hankeKayttajaService.createNewUser(request, hanke, USERNAME)

            assertThat(hankeKayttajaRepository.getReferenceById(result.id))
                .prop(HankekayttajaEntity::kayttajakutsu)
                .isNotNull()
                .all {
                    prop(KayttajakutsuEntity::tunniste).matches(Regex(kayttajaTunnistePattern))
                    prop(KayttajakutsuEntity::createdAt).isRecent()
                    prop(KayttajakutsuEntity::kayttooikeustaso)
                        .isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
                }
        }

        @Test
        fun `sends invitation email when caller has a hankekayttaja`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val inviter = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            hankeKayttajaService.createNewUser(request, hanke, USERNAME)

            val capturedEmails = greenMail.receivedMessages
            assertThat(capturedEmails).hasSize(1)
            assertThat(capturedEmails.first())
                .isValidHankeInvitation(
                    inviter.fullName(),
                    inviter.sahkoposti,
                    hanke.nimi,
                    hanke.hankeTunnus
                )
        }

        @Test
        fun `creates a new invitation even when current user doesn't have a hankekayttaja`() {
            val hanke = hankeFactory.saveMinimalHanke()
            assertThat(hankeKayttajaRepository.findAll()).isEmpty()

            val result = hankeKayttajaService.createNewUser(request, hanke, USERNAME)

            assertThat(hankeKayttajaRepository.getReferenceById(result.id)).all {
                prop(HankekayttajaEntity::hankeId).isEqualTo(hanke.id)
                prop(HankekayttajaEntity::permission).isNull()
                prop(HankekayttajaEntity::kutsujaId).isNull()
                prop(HankekayttajaEntity::kayttajakutsu).isNotNull().all {
                    prop(KayttajakutsuEntity::tunniste).matches(Regex(kayttajaTunnistePattern))
                    prop(KayttajakutsuEntity::createdAt).isRecent()
                    prop(KayttajakutsuEntity::kayttooikeustaso)
                        .isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
                }
            }
        }

        @Test
        fun `doesn't send invitation email when caller doesn't have a hankekayttaja`() {
            val hanke = hankeFactory.saveMinimalHanke()
            assertThat(hankeKayttajaRepository.findAll()).isEmpty()

            hankeKayttajaService.createNewUser(request, hanke, USERNAME)

            assertThat(greenMail.receivedMessages).isEmpty()
        }
    }

    @Nested
    inner class UpdatePermissions {
        @Test
        fun `Doesn't throw any exceptions with no updates`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val updates = mapOf<UUID, Kayttooikeustaso>()

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)
        }

        @Test
        fun `Updates kayttooikeustaso to permission if it exists`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja = kayttajaFactory.saveIdentifiedUser(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.kayttajakutsu).isNull()
            assertThat(updatedKayttaja.permission).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Writes permission update to audit log`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja = kayttajaFactory.saveIdentifiedUser(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)
            auditLogRepository.deleteAll()

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)

            val logs = auditLogRepository.findAll()
            assertThat(logs).single().isSuccess(Operation.UPDATE) {
                hasUserActor(USERNAME)
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.PERMISSION)
                    val permission = kayttaja.permission!!.toDomain()
                    hasId(permission.id)
                    prop(AuditLogTarget::objectBefore).isEqualTo(permission.toChangeLogJsonString())
                    prop(AuditLogTarget::objectAfter)
                        .isEqualTo(
                            permission
                                .copy(kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS)
                                .toChangeLogJsonString()
                        )
                }
            }
        }

        @Test
        fun `Updates kayttooikeustaso to tunniste if permission doesn't exist`() {
            val hankehankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja = kayttajaFactory.saveUnidentifiedUser(hankehankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankehankeIdentifier, updates, false, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNull()
            assertThat(updatedKayttaja.kayttajakutsu).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Writes tunniste update to audit log`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja = kayttajaFactory.saveUnidentifiedUser(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)
            auditLogRepository.deleteAll()

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)

            val logs = auditLogRepository.findAll()
            val expectedTunniste =
                kayttaja.kayttajakutsu!!.toDomain().copy(hankeKayttajaId = kayttaja.id)
            assertThat(logs).single().auditEvent().all {
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.KAYTTAJA_TUNNISTE)
                    hasId(kayttaja.kayttajakutsu!!.id)
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
            val hanke = hankeFactory.builder(USERNAME).save()
            val kayttaja =
                kayttajaFactory.saveIdentifiedUser(
                    hanke.id,
                    etunimi = "Kolmas",
                    sukunimi = "Kehveli",
                    sahkoposti = "kolmas@kehveli.test"
                )
            kayttajaFactory.addToken(kayttaja, "token for both", Kayttooikeustaso.KATSELUOIKEUS)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hanke.identifier(), updates, false, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
            assertThat(updatedKayttaja.kayttajakutsu).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.KATSELUOIKEUS
            }
        }

        @Test
        fun `Throws exception if changing the user's own permission`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja =
                kayttajaFactory.saveIdentifiedUser(
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
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
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
            val hanke1 = hankeFactory.builder(USERNAME).save().identifier()
            val hanke2 = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja = kayttajaFactory.saveIdentifiedUser(hanke2.id)
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
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
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
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja =
                kayttajaFactory.saveIdentifiedUser(
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
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja =
                kayttajaFactory.saveUnidentifiedUser(
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
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja =
                kayttajaFactory.saveIdentifiedUser(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.kayttajakutsu).isNull()
            assertThat(updatedKayttaja.permission).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Succeeds with with admin permission when the kayttaja to update has KAIKKI_OIKEUDET in tunniste`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja =
                kayttajaFactory.saveUnidentifiedUser(
                    hankeIdentifier.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNull()
            assertThat(updatedKayttaja.kayttajakutsu).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Throws exception without admin permission if updating to KAIKKI_OIKEUDET`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja = kayttajaFactory.saveUnidentifiedUser(hankeIdentifier.id)
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
        fun `Succeeds with admin permission if updating to KAIKKI_OIKEUDET`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            val kayttaja = kayttajaFactory.saveUnidentifiedUser(hankeIdentifier.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.KAIKKI_OIKEUDET)

            hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)

            val updatedKayttaja = hankeKayttajaRepository.getReferenceById(kayttaja.id)
            assertThat(updatedKayttaja.permission).isNull()
            assertThat(updatedKayttaja.kayttajakutsu).isNotNull().transform {
                it.kayttooikeustaso == Kayttooikeustaso.HANKEMUOKKAUS
            }
        }

        @Test
        fun `Throw exception if trying to demote the last KAIKKI_OIKEUDET kayttaja`() {
            val hanke = hankeFactory.saveMinimal()
            val kayttaja =
                kayttajaFactory.saveIdentifiedUser(
                    hanke.id,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET
                )
            kayttajaFactory.saveIdentifiedUser(
                hanke.id,
                kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS,
                sahkoposti = "hankemuokkaus",
            )
            kayttajaFactory.saveIdentifiedUser(
                hanke.id,
                kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
                sahkoposti = "hakemusasiointi",
            )
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure { hankeKayttajaService.updatePermissions(hanke, updates, true, USERNAME) }
                .all {
                    hasClass(NoAdminRemainingException::class)
                    messageContains(hanke.id.toString())
                    messageContains(hanke.hankeTunnus)
                }
        }

        @Test
        fun `Don't throw an exception if an anonymous user still has KAIKKI_OIKEUDET`() {
            val hankeIdentifier = hankeFactory.builder(USERNAME).save().identifier()
            hankeKayttajaRepository.deleteAll()
            permissionRepository.deleteAll()
            permissionService.create(hankeIdentifier.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)
            val kayttaja =
                kayttajaFactory.saveIdentifiedUser(
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

        @Test
        fun `Sends an email about the changed permission`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val updater = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            val kayttaja = kayttajaFactory.saveIdentifiedUser(hanke.id)
            val updates = mapOf(kayttaja.id to Kayttooikeustaso.HANKEMUOKKAUS)

            hankeKayttajaService.updatePermissions(hanke.identifier(), updates, false, USERNAME)

            val capturedEmails = greenMail.receivedMessages
            assertThat(capturedEmails).hasSize(1)
            assertThat(capturedEmails.first())
                .isValidPermissionUpdateNotification(
                    hanke.hankeTunnus,
                    hanke.nimi,
                    updater.fullName(),
                    updater.sahkoposti,
                    "Hankemuokkaus"
                )
        }
    }

    @Nested
    inner class CreatePermissionFromToken {
        private val tunniste = "Itf4UuErPqBHkhJF7CUAsu69"
        private val newUserId = "newUser"
        private val securityContext = mockk<SecurityContext>()

        @BeforeEach
        fun setUp() {
            every { securityContext.userId() } returns USERNAME
            every { profiiliClient.getVerifiedName(any()) } returns ProfiiliFactory.DEFAULT_NAMES
        }

        @Test
        fun `throws exception if tunniste doesn't exist`() {
            assertFailure {
                    hankeKayttajaService.createPermissionFromToken(
                        newUserId,
                        "fake",
                        securityContext
                    )
                }
                .all {
                    hasClass(TunnisteNotFoundException::class)
                    messageContains(newUserId)
                    messageContains("fake")
                }
        }

        @Test
        fun `throws exception if cannot retrieve verified name from Profiili`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            kayttajaFactory.saveUnidentifiedUser(hanke.id, tunniste = tunniste)
            every { profiiliClient.getVerifiedName(any()) } throws
                VerifiedNameNotFound("Verified name not found from profile.")

            assertFailure {
                    hankeKayttajaService.createPermissionFromToken(
                        newUserId,
                        tunniste,
                        securityContext
                    )
                }
                .all {
                    hasClass(VerifiedNameNotFound::class)
                    messageContains("Verified name not found from profile.")
                }
        }

        @Test
        fun `throws an exception if the user already has a permission for the hanke kayttaja`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val kayttaja =
                kayttajaFactory.saveIdentifiedUser(
                    hanke.id,
                    userId = newUserId,
                )
            kayttajaFactory.addToken(kayttaja)

            assertFailure {
                    hankeKayttajaService.createPermissionFromToken(
                        newUserId,
                        "existing",
                        securityContext
                    )
                }
                .all {
                    hasClass(UserAlreadyHasPermissionException::class)
                    messageContains(newUserId)
                    messageContains(kayttaja.id.toString())
                    messageContains(kayttaja.permission!!.id.toString())
                }
        }

        @Test
        fun `throws an exception if the user has a permission for the hanke from elsewhere`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val kayttaja = kayttajaFactory.saveUnidentifiedUser(hanke.id, tunniste = tunniste)
            val permission =
                permissionService.create(
                    userId = newUserId,
                    hankeId = hanke.id,
                    kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS
                )

            assertFailure {
                    hankeKayttajaService.createPermissionFromToken(
                        newUserId,
                        tunniste,
                        securityContext
                    )
                }
                .all {
                    hasClass(UserAlreadyHasPermissionException::class)
                    messageContains(newUserId)
                    messageContains(kayttaja.id.toString())
                    messageContains(permission.id.toString())
                }
        }

        @Test
        fun `throws an exception if another user already has a permission for the hanke kayttaja`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val kayttaja = kayttajaFactory.saveIdentifiedUser(hanke.id, userId = "Other user")
            kayttajaFactory.addToken(kayttaja)

            assertFailure {
                    hankeKayttajaService.createPermissionFromToken(
                        newUserId,
                        "existing",
                        securityContext
                    )
                }
                .all {
                    hasClass(PermissionAlreadyExistsException::class)
                    messageContains(newUserId)
                    messageContains("Other user")
                    messageContains(kayttaja.id.toString())
                    messageContains(kayttaja.permission!!.id.toString())
                }
        }

        @Test
        fun `Updates name from Profiili`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val originalHankekayttaja =
                kayttajaFactory.saveUnidentifiedUser(hanke.id, tunniste = tunniste)

            val kayttaja =
                hankeKayttajaService.createPermissionFromToken(newUserId, tunniste, securityContext)

            assertThat(kayttaja.etunimi).isEqualTo(DEFAULT_GIVEN_NAME)
            assertThat(kayttaja.sukunimi).isEqualTo(DEFAULT_LAST_NAME)
            val hankeKayttaja = hankeKayttajaRepository.findById(kayttaja.id).get()
            assertThat(hankeKayttaja).all {
                prop(HankekayttajaEntity::etunimi).isEqualTo(DEFAULT_GIVEN_NAME)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(DEFAULT_LAST_NAME)
                prop(HankekayttajaEntity::etunimi).isNotEqualTo(originalHankekayttaja.etunimi)
                prop(HankekayttajaEntity::sukunimi).isNotEqualTo(originalHankekayttaja.sukunimi)
                prop(HankekayttajaEntity::kutsuttuEtunimi)
                    .isEqualTo(originalHankekayttaja.kutsuttuEtunimi)
                prop(HankekayttajaEntity::kutsuttuSukunimi)
                    .isEqualTo(originalHankekayttaja.kutsuttuSukunimi)
            }
        }

        @Test
        fun `Creates a permission`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            kayttajaFactory.saveUnidentifiedUser(hanke.id, tunniste = tunniste)

            hankeKayttajaService.createPermissionFromToken(newUserId, tunniste, securityContext)

            val permission = permissionRepository.findOneByHankeIdAndUserId(hanke.id, newUserId)
            assertThat(permission)
                .isNotNull()
                .transform { it.kayttooikeustaso }
                .isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
        }

        @Test
        fun `Removes the user token`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            kayttajaFactory.saveUnidentifiedUser(hanke.id, tunniste = tunniste)

            hankeKayttajaService.createPermissionFromToken(newUserId, tunniste, securityContext)

            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
        }

        @Test
        fun `Writes the token removal to audit logs`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            kayttajaFactory.saveUnidentifiedUser(hanke.id, tunniste = tunniste)
            auditLogRepository.deleteAll()

            hankeKayttajaService.createPermissionFromToken(newUserId, tunniste, securityContext)

            val logs =
                auditLogRepository.findAll().filter {
                    it.message.auditEvent.target.type == ObjectType.KAYTTAJA_TUNNISTE
                }
            assertThat(logs).single().isSuccess(Operation.DELETE) {
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
        private val dummyEmail = "current@user"

        @Test
        fun `Throws exception if current user doesn't have a kayttaja`() {
            val hanke = hankeFactory.saveMinimal()
            val kayttaja = kayttajaFactory.saveUser(hanke.id)

            assertFailure { hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME) }
                .all {
                    hasClass(CurrentUserWithoutKayttajaException::class)
                    messageContains(USERNAME)
                }
        }

        @Test
        fun `Throws exception if kayttaja already has permission`() {
            val hanke = hankeFactory.saveMinimal()
            val kayttaja = kayttajaFactory.saveIdentifiedUser(hanke.id)

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
            kayttajaFactory.saveIdentifiedUser(
                hankeId = hanke.id,
                userId = USERNAME,
                sahkoposti = dummyEmail,
            )
            val kayttaja = kayttajaFactory.saveUser(hanke.id)
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()

            val result = hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME)

            assertThat(kayttajakutsuRepository.findAll()).single().hasKayttajaWithId(kayttaja.id)
            assertThat(result.kutsuttu).isNotNull().isRecent()
        }

        @Test
        fun `If kayttaja has a tunniste recreates it and deletes the old one `() {
            val hanke = hankeFactory.saveMinimal(nimi = "Hanke")
            kayttajaFactory.saveIdentifiedUser(
                hankeId = hanke.id,
                userId = USERNAME,
                sahkoposti = dummyEmail,
            )
            val kayttaja = kayttajaFactory.saveUnidentifiedUser(hanke.id)
            assertThat(kayttajakutsuRepository.findAll()).hasSize(1)
            val tunnisteId = kayttaja.kayttajakutsu!!.id
            val tunniste = kayttaja.kayttajakutsu!!.tunniste

            hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME)

            assertThat(kayttajakutsuRepository.findAll()).single().all {
                hasKayttajaWithId(kayttaja.id)
                prop(KayttajakutsuEntity::id).isNotEqualTo(tunnisteId)
                prop(KayttajakutsuEntity::tunniste).isNotEqualTo(tunniste)
            }
        }

        @Test
        fun `Returns the hankekayttaja with the updated invitation date`() {
            val hanke = hankeFactory.saveWithAlue()
            val pastDate = OffsetDateTime.parse("2024-01-23T08:41:14Z")
            val kayttaja = kayttajaFactory.saveUnidentifiedUser(hanke.id, kutsuttu = pastDate)

            val result = hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME)

            assertThat(result.kutsuttu).isNotNull().isRecent()
            assertThat(kayttajakutsuRepository.findAll())
                .single()
                .prop(KayttajakutsuEntity::createdAt)
                .isRecent()
        }

        @Test
        fun `Sends the invitation email`() {
            val hanke = hankeFactory.saveMinimal(nimi = "Hanke")
            kayttajaFactory.saveIdentifiedUser(
                hanke.id,
                userId = USERNAME,
                etunimi = "Current",
                sukunimi = "User",
                sahkoposti = dummyEmail,
            )
            val kayttaja = kayttajaFactory.saveUser(hanke.id)

            hankeKayttajaService.resendInvitation(kayttaja.id, USERNAME)

            val capturedEmails = greenMail.receivedMessages
            assertThat(capturedEmails).hasSize(1)
            assertThat(capturedEmails[0])
                .isValidHankeInvitation("Current User", dummyEmail, hanke.nimi, hanke.hankeTunnus)
            assertThat(capturedEmails).hasReceivers(kayttaja.sahkoposti)
        }
    }

    @Nested
    inner class UpdateOwnContactInfo {
        private val update = ContactUpdate("updated@email.test", "9991111")

        @Test
        fun `Throws exception if there's no hanke`() {
            val hanketunnus = "HAI-001"

            val failure = assertFailure {
                hankeKayttajaService.updateOwnContactInfo(hanketunnus, update, USERNAME)
            }

            failure.all {
                hasClass(HankeNotFoundException::class)
                messageContains(hanketunnus)
            }
        }

        @Test
        fun `Throws exception if user not in hanke`() {
            val hanke = hankeFactory.builder("Other user").save()

            val failure = assertFailure {
                hankeKayttajaService.updateOwnContactInfo(hanke.hankeTunnus, update, USERNAME)
            }

            failure.all {
                hasClass(HankeNotFoundException::class)
                messageContains(hanke.hankeTunnus)
            }
        }

        @Test
        fun `Updates the contact information of the user`() {
            val hanke = hankeFactory.builder(USERNAME).save()

            hankeKayttajaService.updateOwnContactInfo(hanke.hankeTunnus, update, USERNAME)

            val kayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)
            assertThat(kayttaja).isNotNull().all {
                prop(HankekayttajaEntity::etunimi).isEqualTo(DEFAULT_GIVEN_NAME)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(DEFAULT_LAST_NAME)
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankekayttajaEntity::puhelin).isEqualTo(update.puhelinnumero)
            }
        }
    }

    @Nested
    inner class UpdateKayttajaInfo {
        private val update = KayttajaUpdate("updated@email.test", "9991111")

        @Test
        fun `Throws exception if there's no hanke`() {
            val hanketunnus = "HAI-001"

            val failure = assertFailure {
                hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, UUID.randomUUID())
            }

            failure.all {
                hasClass(HankeNotFoundException::class)
                messageContains(hanketunnus)
            }
        }

        @Test
        fun `Throws exception if user not in hanke`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val otherHanke = hankeFactory.builder("Other user").save()
            val otherUser = kayttajaFactory.saveIdentifiedUser(otherHanke.id)

            val failure = assertFailure {
                hankeKayttajaService.updateKayttajaInfo(hanke.hankeTunnus, update, otherUser.id)
            }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(otherUser.id.toString())
            }
        }

        @Test
        fun `Throws exception when user is identified and try to change name`() {
            val update = KayttajaUpdate("updated@email.test", "9991111", "Uusi", "Nimi")
            val hanke = hankeFactory.builder(USERNAME).save()
            val identifiedUser = kayttajaFactory.saveIdentifiedUser(hanke.id)

            val failure = assertFailure {
                hankeKayttajaService.updateKayttajaInfo(
                    hanke.hankeTunnus,
                    update,
                    identifiedUser.id
                )
            }

            failure.all {
                hasClass(UserAlreadyHasPermissionException::class)
                messageContains("The user already has an active permission")
            }
        }

        @Test
        fun `Updates email and phone number of an identified user`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val identifiedUser = kayttajaFactory.saveIdentifiedUser(hanke.id)

            hankeKayttajaService.updateKayttajaInfo(hanke.hankeTunnus, update, identifiedUser.id)

            val kayttaja = hankeKayttajaService.getKayttajaForHanke(identifiedUser.id, hanke.id)
            assertThat(kayttaja).isNotNull().all {
                prop(HankekayttajaEntity::etunimi).isEqualTo(identifiedUser.etunimi)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(identifiedUser.sukunimi)
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankekayttajaEntity::puhelin).isEqualTo(update.puhelinnumero)
            }
        }

        @Test
        fun `Updates email and phone number of an unidentified user`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val unidentifiedUser = kayttajaFactory.saveUnidentifiedUser(hanke.id)

            hankeKayttajaService.updateKayttajaInfo(hanke.hankeTunnus, update, unidentifiedUser.id)

            val kayttaja = hankeKayttajaService.getKayttajaForHanke(unidentifiedUser.id, hanke.id)
            assertThat(kayttaja).isNotNull().all {
                prop(HankekayttajaEntity::etunimi).isEqualTo(unidentifiedUser.etunimi)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(unidentifiedUser.sukunimi)
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankekayttajaEntity::puhelin).isEqualTo(update.puhelinnumero)
            }
        }

        @Test
        fun `Updates email, phone and name of an unidentified user`() {
            val update = KayttajaUpdate("updated@email.test", "9991111", "Uusi", "Nimi")
            val hanke = hankeFactory.builder(USERNAME).save()
            val unidentifiedUser = kayttajaFactory.saveUnidentifiedUser(hanke.id)

            hankeKayttajaService.updateKayttajaInfo(hanke.hankeTunnus, update, unidentifiedUser.id)

            val kayttaja = hankeKayttajaService.getKayttajaForHanke(unidentifiedUser.id, hanke.id)
            assertThat(kayttaja).isNotNull().all {
                prop(HankekayttajaEntity::etunimi).isEqualTo(update.etunimi)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(update.sukunimi)
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankekayttajaEntity::puhelin).isEqualTo(update.puhelinnumero)
            }
        }

        @Test
        fun `Does not update name if it is empty or blank`() {
            val update = KayttajaUpdate("updated@email.test", "9991111", "", " ")
            val hanke = hankeFactory.builder(USERNAME).save()
            val unidentifiedUser = kayttajaFactory.saveUnidentifiedUser(hanke.id)

            hankeKayttajaService.updateKayttajaInfo(hanke.hankeTunnus, update, unidentifiedUser.id)

            val kayttaja = hankeKayttajaService.getKayttajaForHanke(unidentifiedUser.id, hanke.id)
            assertThat(kayttaja).isNotNull().all {
                prop(HankekayttajaEntity::etunimi).isEqualTo(unidentifiedUser.etunimi)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(unidentifiedUser.sukunimi)
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankekayttajaEntity::puhelin).isEqualTo(update.puhelinnumero)
            }
        }
    }

    private fun findKayttaja(hankeId: Int, email: String) =
        hankeKayttajaRepository.findByHankeIdAndSahkopostiIn(hankeId, listOf(email)).first()

    private fun Assert<KayttajakutsuEntity>.hasKayttajaWithId(kayttajaId: UUID) =
        prop(KayttajakutsuEntity::hankekayttaja)
            .isNotNull()
            .prop(HankekayttajaEntity::id)
            .isEqualTo(kayttajaId)

    private fun Assert<List<KayttajakutsuEntity>>.areValid() = each { t ->
        t.prop(KayttajakutsuEntity::id).isNotNull()
        t.prop(KayttajakutsuEntity::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
        t.prop(KayttajakutsuEntity::createdAt).isRecent()
        t.prop(KayttajakutsuEntity::tunniste).matches(Regex(kayttajaTunnistePattern))
        t.prop(KayttajakutsuEntity::hankekayttaja).isNotNull()
    }

    private fun Assert<Array<MimeMessage>>.areValidHankeInvitations(
        inviterName: String,
        inviterEmail: String,
        hanke: Hanke
    ) {
        each { it.isValidHankeInvitation(inviterName, inviterEmail, hanke.nimi, hanke.hankeTunnus) }
    }

    private fun Assert<MimeMessage>.isValidHankeInvitation(
        inviterName: String,
        inviterEmail: String,
        hankeNimi: String,
        hankeTunnus: String,
    ) {
        prop(MimeMessage::textBody).all {
            contains("$inviterName ($inviterEmail) lisäsi sinut")
            containsMatch("kutsu\\?id=$kayttajaTunnistePattern".toRegex())
            contains("hankkeelle $hankeNimi ($hankeTunnus)")
        }
    }

    private fun Assert<MimeMessage>.isValidPermissionUpdateNotification(
        hankeTunnus: String,
        hankeNimi: String,
        updatedByName: String,
        updatedByEmail: String,
        newAccessRights: String,
    ) {
        prop(MimeMessage::textBody).all {
            contains("$updatedByName ($updatedByEmail) on muuttanut käyttöoikeustasoasi")
            contains("hankkeella \"$hankeNimi\" ($hankeTunnus)")
            contains("Uusi käyttöoikeutesi on \"$newAccessRights\"")
        }
    }
}
