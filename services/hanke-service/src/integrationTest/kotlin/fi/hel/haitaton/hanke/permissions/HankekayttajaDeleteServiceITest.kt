package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.permissions.HankekayttajaDeleteService.DeleteInfo
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.USERNAME
import jakarta.mail.internet.MimeMessage
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class HankekayttajaDeleteServiceITest(
    @Autowired val deleteService: HankekayttajaDeleteService,
    @Autowired val hankeKayttajaService: HankeKayttajaService,
    @Autowired val hankeService: HankeService,
    @Autowired val hakemusService: HakemusService,
    @Autowired val hankeFactory: HankeFactory,
    @Autowired val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired val hakemusFactory: HakemusFactory,
    @Autowired val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired val permissionRepository: PermissionRepository,
    @Autowired val auditLogRepository: AuditLogRepository,
    @Autowired val kayttajakutsuRepository: KayttajakutsuRepository,
) : IntegrationTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CheckForDelete {
        @Test
        fun `throws exception when hankekayttaja does not exist`() {
            val kayttajaId = UUID.fromString("750b4207-2c5f-49a0-9b80-4829c807abeb")

            val failure = assertFailure { deleteService.checkForDelete(kayttajaId) }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `returns true for onlyOmistajanYhteyshenkilo when the user is only contact for omistaja`() {
            val hanke = hankeFactory.builder().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeFactory.addYhteystiedotTo(hanke) { omistaja(founder) }

            val response: DeleteInfo = deleteService.checkForDelete(founder.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isTrue()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns true for onlyOmistajanYhteyshenkilo when the user is only contact for one omistaja`() {
            val hanke = hankeFactory.builder().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeFactory.addYhteystiedotTo(hanke) {
                omistaja(founder)
                omistaja(founder, kayttaja())
            }

            val response: DeleteInfo = deleteService.checkForDelete(founder.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isTrue()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns false for onlyOmistajanYhteyshenkilo when there are no contacts`() {
            val hanke = hankeFactory.builder().save()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val response: DeleteInfo = deleteService.checkForDelete(founder.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns false for onlyOmistajanYhteyshenkilo when someone else is the contact`() {
            val hanke = hankeFactory.builder().saveWithYhteystiedot { omistaja() }
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val response: DeleteInfo = deleteService.checkForDelete(founder.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns false for onlyOmistajanYhteyshenkilo when there is another contact as well`() {
            val hanke = hankeFactory.builder().saveWithYhteystiedot { omistaja() }
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val response: DeleteInfo = deleteService.checkForDelete(founder.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `divides active and draft applications correctly`() {
            val hanke = hankeFactory.saveWithAlue()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hakemusFactory.builder(hankeEntity = hanke).withName("Draft").hakija(founder).save()
            hakemusFactory
                .builder(hankeEntity = hanke)
                .withName("Pending")
                .withStatus(ApplicationStatus.PENDING, alluId = 1, identifier = "JS230001")
                .hakija(founder)
                .save()
            hakemusFactory
                .builder(hankeEntity = hanke)
                .withName("Decision")
                .withStatus(ApplicationStatus.DECISION, alluId = 2, identifier = "JS230002")
                .hakija(founder)
                .save()

            val response: DeleteInfo = deleteService.checkForDelete(founder.id)

            assertThat(response).prop(DeleteInfo::draftHakemukset).single().all {
                prop(DeleteInfo.HakemusDetails::nimi).isEqualTo("Draft")
                prop(DeleteInfo.HakemusDetails::alluStatus).isNull()
                prop(DeleteInfo.HakemusDetails::applicationIdentifier).isNull()
            }
            assertThat(response).prop(DeleteInfo::activeHakemukset).all {
                hasSize(2)
                extracting { it.nimi }.containsExactlyInAnyOrder("Pending", "Decision")
                extracting { it.alluStatus }
                    .containsExactlyInAnyOrder(
                        ApplicationStatus.PENDING,
                        ApplicationStatus.DECISION,
                    )
                extracting { it.applicationIdentifier }
                    .containsExactlyInAnyOrder("JS230001", "JS230002")
            }
            assertThat(response).prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
        }

        @Test
        fun `returns each hakemus only once when the user is a contact is different roles`() {
            val hanke = hankeFactory.saveWithAlue()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hakemusFactory
                .builder(hankeEntity = hanke)
                .withName("Draft")
                .withEachCustomer(founder)
                .save()
            hakemusFactory
                .builder(hankeEntity = hanke)
                .withName("Pending")
                .inHandling()
                .withEachCustomer(founder)
                .save()

            val response: DeleteInfo = deleteService.checkForDelete(founder.id)

            assertThat(response).prop(DeleteInfo::draftHakemukset).single()
            assertThat(response).prop(DeleteInfo::activeHakemukset).single()
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Delete {
        @Test
        fun `throws an exception when hankekayttaja does not exist`() {
            val kayttajaId = UUID.fromString("750b4207-2c5f-49a0-9b80-4829c807abeb")

            val failure = assertFailure { deleteService.delete(kayttajaId, USERNAME) }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `throws an exception if the user is the only user with Kaikki Oikeudet privileges`() {
            val hanke = hankeFactory.builder().save()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val failure = assertFailure { deleteService.delete(founder.id, USERNAME) }

            failure.all {
                hasClass(NoAdminRemainingException::class)
                messageContains(hanke.hankeTunnus)
            }
        }

        @Test
        fun `throws an exception if the user is the only identified user with Kaikki Oikeudet privileges`() {
            val hanke = hankeFactory.builder().save()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeKayttajaFactory.saveUnidentifiedUser(
                hanke.id, kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET)

            val failure = assertFailure { deleteService.delete(founder.id, USERNAME) }

            failure.all {
                hasClass(NoAdminRemainingException::class)
                messageContains(hanke.hankeTunnus)
            }
        }

        @Test
        fun `throws an exception if the user is the only yhteyshenkilo for an omistaja`() {
            val hanke = hankeFactory.builder().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            val builder = hankeFactory.yhteystietoBuilderFrom(hanke)
            builder.omistaja(founder, builder.kayttaja())
            val offendingOmistaja = builder.omistaja(founder)
            builder.toteuttaja(Kayttooikeustaso.KAIKKI_OIKEUDET)

            val failure = assertFailure { deleteService.delete(founder.id, USERNAME) }

            failure.all {
                hasClass(OnlyOmistajaContactException::class)
                messageContains(founder.id.toString())
                messageContains("yhteystietoIds=${offendingOmistaja.id}")
            }
        }

        @Test
        fun `throws an exception if the user is a contact in an active hakemus`() {
            val hanke =
                hankeFactory.builder().withHankealue().saveWithYhteystiedot {
                    toteuttaja(Kayttooikeustaso.KAIKKI_OIKEUDET)
                }
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            val pending =
                hakemusFactory
                    .builder(hankeEntity = hanke)
                    .withName("Pending")
                    .withStatus(ApplicationStatus.PENDING, alluId = 1, identifier = "JS230001")
                    .hakija(founder)
                    .save()
            val decision =
                hakemusFactory
                    .builder(hankeEntity = hanke)
                    .withName("Decision")
                    .withStatus(ApplicationStatus.DECISION, alluId = 2, identifier = "JS230002")
                    .tyonSuorittaja(founder)
                    .save()

            val failure = assertFailure { deleteService.delete(founder.id, USERNAME) }

            failure.all {
                hasClass(HasActiveApplicationsException::class)
                messageContains(founder.id.toString())
                messageContains(pending.id.toString())
                messageContains(decision.id.toString())
            }
        }

        @Test
        fun `delete the user when there are no violations`() {
            val hanke = hankeFactory.builder().withHankealue().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeFactory.addYhteystiedotTo(hanke) {
                omistaja(founder, kayttaja())
                toteuttaja(Kayttooikeustaso.KAIKKI_OIKEUDET)
            }
            val otherKayttaja =
                hankeKayttajaFactory.saveIdentifiedUser(hanke.id, sahkoposti = "Something else")
            val draftHakemus =
                hakemusFactory
                    .builder(hanke)
                    .withName("Draft")
                    .hakija(founder, otherKayttaja)
                    .save()

            deleteService.delete(founder.id, USERNAME)

            assertThat(hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)).isNull()
            assertThat(hankeService.loadHankeById(hanke.id))
                .isNotNull()
                .prop(Hanke::omistajat)
                .single()
                .prop(HankeYhteystieto::yhteyshenkilot)
                .single()
                .prop(Yhteyshenkilo::id)
                .isNotEqualTo(founder.id)
            assertThat(hakemusService.getById(draftHakemus.id))
                .isNotNull()
                .prop(Hakemus::applicationData)
                .prop(HakemusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .single()
                .prop(Hakemusyhteyshenkilo::hankekayttajaId)
                .isNotEqualTo(founder.id)
            assertThat(permissionRepository.findOneByHankeIdAndUserId(hanke.id, USERNAME)).isNull()
        }

        @Test
        fun `removes kutsujaId from kayttajat invited by the kayttaja to be deleted`() {
            val hanke = hankeFactory.builder().withHankealue().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            val invited1 =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hankeId = hanke.id,
                    kutsujaId = founder.id,
                    sahkoposti = "invited1",
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
                )
            val invited2 =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hankeId = hanke.id,
                    kutsujaId = founder.id,
                    sahkoposti = "invited2",
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
                )
            val invitedBySomeoneElse =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hankeId = hanke.id,
                    kutsujaId = invited1.id,
                    sahkoposti = "invitedBySomeoneElse",
                )
            hankeFactory.addYhteystiedotTo(hanke) {
                omistaja(founder, invited1)
                toteuttaja(invitedBySomeoneElse)
            }
            assertThat(hankekayttajaRepository.getReferenceById(invitedBySomeoneElse.id))
                .prop(HankekayttajaEntity::kutsujaId)
                .isEqualTo(invited1.id)

            deleteService.delete(founder.id, USERNAME)

            assertThat(hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)).isNull()
            assertThat(hankekayttajaRepository.getReferenceById(invited1.id))
                .prop(HankekayttajaEntity::kutsujaId)
                .isNull()
            assertThat(hankekayttajaRepository.getReferenceById(invited2.id))
                .prop(HankekayttajaEntity::kutsujaId)
                .isNull()
            assertThat(hankekayttajaRepository.getReferenceById(invitedBySomeoneElse.id))
                .prop(HankekayttajaEntity::kutsujaId)
                .isEqualTo(invited1.id)
        }

        @Test
        fun `removes kayttajakutsu when user is not identified`() {
            val hanke = hankeFactory.builder().withHankealue().saveEntity()
            val invited = hankeKayttajaFactory.saveUnidentifiedUser(hanke.id)

            deleteService.delete(invited.id, USERNAME)

            assertThat(hankekayttajaRepository.findByIdOrNull(invited.id)).isNull()
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
        }

        @Test
        fun `logs hankekayttaja delete to audit logs`() {
            val hanke = hankeFactory.builder().withHankealue().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeKayttajaFactory.saveIdentifiedUser(
                hanke.id,
                sahkoposti = "Something else",
                kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
            )
            val kayttaja = hankeKayttajaService.getKayttaja(founder.id)
            auditLogRepository.deleteAll()

            deleteService.delete(founder.id, USERNAME)

            val logs = auditLogRepository.findByType(ObjectType.HANKE_KAYTTAJA)
            assertThat(logs).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME)
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.HANKE_KAYTTAJA)
                    hasId(founder.id)
                    hasObjectBefore(kayttaja)
                    hasNoObjectAfter()
                }
            }
        }

        @Test
        fun `logs permission delete to audit logs`() {
            val hanke = hankeFactory.builder().withHankealue().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeKayttajaFactory.saveIdentifiedUser(
                hanke.id,
                sahkoposti = "Something else",
                kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
            )
            val permission = permissionRepository.findOneByHankeIdAndUserId(hanke.id, USERNAME)!!
            auditLogRepository.deleteAll()

            deleteService.delete(founder.id, USERNAME)

            val logs = auditLogRepository.findByType(ObjectType.PERMISSION)
            assertThat(logs).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME)
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.PERMISSION)
                    hasId(permission.id)
                    hasObjectBefore(permission.toDomain())
                    hasNoObjectAfter()
                }
            }
        }

        @Test
        fun `send an email notification when the user is deleted`() {
            val hanke = hankeFactory.builder().withHankealue().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeFactory.addYhteystiedotTo(hanke) {
                omistaja(founder, kayttaja())
                toteuttaja(Kayttooikeustaso.KAIKKI_OIKEUDET)
            }

            deleteService.delete(founder.id, USERNAME)

            val capturedEmails = greenMail.receivedMessages
            assertThat(capturedEmails).hasSize(1)
            assertThat(capturedEmails.first()).prop(MimeMessage::textBody).all {
                contains("${founder.fullName()} (${founder.sahkoposti}) on poistanut sinut")
                contains("hankkeelta \"${hanke.nimi}\" (${hanke.hankeTunnus})")
            }
        }
    }

    @Nested
    inner class GetHakemuksetForKayttaja {
        @Test
        fun `throws an exception when hankekayttaja does not exist`() {
            val kayttajaId = UUID.fromString("750b4207-2c5f-49a0-9b80-4829c807abeb")

            val failure = assertFailure { deleteService.getHakemuksetForKayttaja(kayttajaId) }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `returns an empty list when the kayttaja exists but there are no applications`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val result = deleteService.getHakemuksetForKayttaja(founder.id)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns applications when the kayttaja is an yhteyshenkilo`() {
            val hanke = hankeFactory.saveWithAlue(USERNAME)
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            val application1 =
                hakemusFactory.builder(hanke).hakija(founder).rakennuttaja().tyonSuorittaja().save()
            val application2 =
                hakemusFactory.builder(hanke).hakija().rakennuttaja().tyonSuorittaja(founder).save()

            val result = deleteService.getHakemuksetForKayttaja(founder.id)

            assertThat(result)
                .extracting { it.id }
                .containsExactlyInAnyOrder(application1.id, application2.id)
        }
    }
}
