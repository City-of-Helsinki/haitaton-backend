package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluExcavationNotificationData
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER
import fi.hel.haitaton.hanke.factory.CreateHakemusRequestFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_ASIANHOITAJA
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.factory.PaperDecisionReceiverFactory
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory.Companion.addKentta
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory.Companion.clearKentat
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.ASIANHOITAJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluCableReportData
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluExcavationNotificationData
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.AlluContactWithRole
import fi.hel.haitaton.hanke.logging.AlluCustomerWithRole
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.paatos.PaatosTila
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi
import fi.hel.haitaton.hanke.pdf.withName
import fi.hel.haitaton.hanke.pdf.withNames
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.taydennys.TaydennysWithExtras
import fi.hel.haitaton.hanke.taydennys.Taydennyspyynto
import fi.hel.haitaton.hanke.test.AlluException
import fi.hel.haitaton.hanke.test.Asserts.hasStreetName
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasServiceActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasTargetType
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.valmistumisilmoitus.Valmistumisilmoitus
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifySequence
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.springframework.beans.factory.annotation.Autowired

class HakemusServiceITest(
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    @Autowired private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val applicationAttachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val geometriatDao: GeometriatDao,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val paatosFactory: PaatosFactory,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
    @Autowired private val taydennyspyyntoFactory: TaydennyspyyntoFactory,
    @Autowired private val taydennysFactory: TaydennysFactory,
    @Autowired private val taydennysAttachmentFactory: TaydennysAttachmentFactory,
    @Autowired private val hankeService: HankeService,
) : IntegrationTest() {

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(alluClient)
    }

    @Nested
    inner class GetById {
        @Test
        fun `throws an exception when the application does not exist`() {
            val failure = assertFailure { hakemusService.getById(1234) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `returns yhteystiedot and yhteyshenkilot if they're present`() {
            val application =
                hakemusFactory
                    .builder(USERNAME)
                    .hakija(Kayttooikeustaso.KAIKKI_OIKEUDET, tilaaja = true)
                    .rakennuttaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                    .asianhoitaja()
                    .tyonSuorittaja(Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                    .save()

            val response = hakemusService.getById(application.id)

            assertThat(response.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::yhteystiedot)
                .all {
                    extracting { it.rooli to it.yhteyshenkilot.single().tilaaja }
                        .containsExactlyInAnyOrder(
                            HAKIJA to true,
                            TYON_SUORITTAJA to false,
                            RAKENNUTTAJA to false,
                            ASIANHOITAJA to false,
                        )
                    each { it.prop(Hakemusyhteystieto::tyyppi).isEqualTo(CustomerType.COMPANY) }
                }
        }
    }

    @Nested
    inner class GetWithExtras {
        @Test
        fun `throws an exception when the hakemus does not exist`() {
            val failure = assertFailure { hakemusService.getWithExtras(1234) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `returns hakemus`() {
            val hakemus = hakemusFactory.builder(USERNAME).withMandatoryFields().save()

            val response = hakemusService.getWithExtras(hakemus.id)

            assertThat(response.hakemus).all {
                prop(Hakemus::id).isEqualTo(hakemus.id)
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::hankeId).isEqualTo(hakemus.hankeId)
                prop(Hakemus::id).isEqualTo(hakemus.id)
                prop(Hakemus::applicationData).isInstanceOf(JohtoselvityshakemusData::class).all {
                    prop(JohtoselvityshakemusData::name).isEqualTo(hakemus.applicationData.name)
                    prop(JohtoselvityshakemusData::areas).isNotNull().single().isNotNull()
                }
            }
            assertThat(response.paatokset).isEmpty()
            assertThat(response.taydennyspyynto).isNull()
            assertThat(response.taydennys).isNull()
        }

        @Test
        fun `returns paatokset when they exist`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .save()
            val paatos1 = paatosFactory.save(hakemus, tila = PaatosTila.KORVATTU)
            val paatos2 = paatosFactory.save(hakemus, hakemus.applicationIdentifier + "-1")

            val response = hakemusService.getWithExtras(hakemus.id)

            assertThat(response.paatokset).containsExactlyInAnyOrder(paatos1, paatos2)
        }

        @Test
        fun `returns taydennyspyynto when it exists`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION)
                    .save()
            val taydennyspyynto =
                taydennyspyyntoFactory.save(hakemus.id) {
                    clearKentat()
                    addKentta(InformationRequestFieldKey.START_TIME, "Too soon")
                    addKentta(InformationRequestFieldKey.GEOMETRY, "Not enough")
                }

            val response = hakemusService.getWithExtras(hakemus.id)

            assertThat(response.taydennyspyynto).isNotNull().all {
                prop(Taydennyspyynto::id).isEqualTo(taydennyspyynto.id)
                prop(Taydennyspyynto::kentat)
                    .containsOnly(
                        InformationRequestFieldKey.START_TIME to "Too soon",
                        InformationRequestFieldKey.GEOMETRY to "Not enough",
                    )
            }
        }

        @Test
        fun `returns taydennys when it exists`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION)
                    .saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).save()

            val response = hakemusService.getWithExtras(hakemus.id)

            assertThat(response.taydennys).isNotNull().all {
                prop(TaydennysWithExtras::id).isEqualTo(taydennys.id)
                prop(TaydennysWithExtras::hakemusData).all {
                    prop(HakemusData::name).isEqualTo(ApplicationFactory.DEFAULT_APPLICATION_NAME)
                    prop(HakemusData::startTime).isEqualTo(DateFactory.getStartDatetime())
                }
                prop(TaydennysWithExtras::muutokset).isEmpty()
            }
        }

        @Test
        fun `returns muutokset with taydennys when it differs from hakemus`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION)
                    .saveEntity()
            taydennysFactory
                .builder(hakemus)
                .withEmergencyWork(true)
                .withStreetAddress("Tie 3")
                .saveEntity()

            val response = hakemusService.getWithExtras(hakemus.id)

            assertThat(response.taydennys).isNotNull().all {
                prop(TaydennysWithExtras::hakemusData)
                    .isInstanceOf(JohtoselvityshakemusData::class)
                    .all {
                        prop(JohtoselvityshakemusData::emergencyWork).isTrue()
                        prop(JohtoselvityshakemusData::postalAddress).hasStreetName("Tie 3")
                    }
                prop(TaydennysWithExtras::muutokset)
                    .containsExactlyInAnyOrder("emergencyWork", "postalAddress")
            }
        }

        @Test
        fun `returns liitteet with taydennys`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION)
                    .saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).save()
            taydennysAttachmentFactory.save(fileName = "First", taydennys = taydennys)
            taydennysAttachmentFactory.save(fileName = "Second", taydennys = taydennys)

            val response = hakemusService.getWithExtras(hakemus.id)

            assertThat(response.taydennys).isNotNull().prop(TaydennysWithExtras::liitteet).all {
                hasSize(2)
                extracting { it.fileName }.containsExactlyInAnyOrder("First", "Second")
            }
        }

        @Test
        fun `returns muutosilmoitus when it exists`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()

            val response = hakemusService.getWithExtras(hakemus.id)

            assertThat(response.muutosilmoitus).isNotNull().all {
                prop(Muutosilmoitus::id).isEqualTo(muutosilmoitus.id)
                prop(Muutosilmoitus::hakemusData).isInstanceOf(KaivuilmoitusData::class).all {
                    prop(HakemusData::name).isEqualTo(ApplicationFactory.DEFAULT_APPLICATION_NAME)
                    prop(HakemusData::startTime).isEqualTo(DateFactory.getStartDatetime())
                    prop(HakemusData::yhteystiedot).hasSize(2)
                }
            }
        }
    }

    @Nested
    inner class HankkeenHakemukset {

        @Test
        fun `throws not found when hanke does not exist`() {
            val hankeTunnus = "HAI-1234"

            assertFailure { hakemusService.hankkeenHakemukset(hankeTunnus) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `returns an empty result when there are no applications`() {
            val hankeInitial = hankeFactory.builder(USERNAME).save()

            val result = hakemusService.hankkeenHakemukset(hankeInitial.hankeTunnus)

            assertThat(result).isEmpty()
        }

        @Nested
        inner class WithJohtoselvityshakemus {
            @Test
            fun `returns applications`() {
                val hakemus = hakemusFactory.builderWithGeneratedHanke().save()

                val result = hakemusService.hankkeenHakemukset(hakemus.hankeTunnus)

                val expectedHakemus = hakemusService.getById(hakemus.id)
                assertThat(result).containsExactly(expectedHakemus)
            }
        }

        @Nested
        inner class WithKaivuilmoitus {
            @Test
            fun `returns applications`() {
                val hakemus = hakemusFactory.builder(ApplicationType.EXCAVATION_NOTIFICATION).save()

                val result = hakemusService.hankkeenHakemukset(hakemus.hankeTunnus)

                val expectedHakemus = hakemusService.getById(hakemus.id)
                assertThat(result).containsExactly(expectedHakemus)
            }
        }
    }

    @Nested
    inner class HankkeenHakemuksetWithMuutosilmoitukset {

        @Test
        fun `throws not found when hanke does not exist`() {
            val hankeTunnus = "HAI-1234"

            assertFailure { hakemusService.hankkeenHakemuksetResponses(hankeTunnus, false) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `returns an empty result when there are no applications`() {
            val hankeInitial = hankeFactory.builder(USERNAME).save()

            val result = hakemusService.hankkeenHakemuksetResponses(hankeInitial.hankeTunnus, false)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns correct hakemukset`() {
            val targetHanke = hankeFactory.saveMinimal()
            val targetHakemus1 = hakemusFactory.builder(targetHanke).saveEntity()
            val targetHakemus2 =
                hakemusFactory
                    .builder(targetHanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .saveEntity()
            val targetHakemus3 = hakemusFactory.builder(targetHanke).saveEntity()
            val muutosilmoitus1 = muutosilmoitusFactory.builder(targetHakemus1).save()
            val muutosilmoitus2 = muutosilmoitusFactory.builder(targetHakemus2).save()
            val otherHanke = hankeFactory.saveMinimal()
            val otherHakemus1 = hakemusFactory.builder(otherHanke).saveEntity()
            val otherHakemus2 =
                hakemusFactory
                    .builder(otherHanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .saveEntity()
            hakemusFactory.builder(otherHanke).saveEntity()
            muutosilmoitusFactory.builder(otherHakemus1).save()
            muutosilmoitusFactory.builder(otherHakemus2).save()

            val result = hakemusService.hankkeenHakemuksetResponses(targetHanke.hankeTunnus, false)

            assertThat(result)
                .extracting { it.id }
                .containsExactlyInAnyOrder(targetHakemus1.id, targetHakemus2.id, targetHakemus3.id)
            assertThat(result)
                .extracting { it.muutosilmoitus?.id }
                .containsExactlyInAnyOrder(muutosilmoitus1.id, muutosilmoitus2.id, null)
        }

        @Nested
        inner class WithJohtoselvityshakemus {
            @Test
            fun `returns applications`() {
                val hakemus = hakemusFactory.builderWithGeneratedHanke().save()

                val result = hakemusService.hankkeenHakemuksetResponses(hakemus.hankeTunnus, false)

                val expectedHakemus = hakemusRepository.getReferenceById(hakemus.id)
                val expectedResponse =
                    HankkeenHakemusResponse(expectedHakemus, null, listOf(), false)
                assertThat(result).containsExactly(expectedResponse)
            }

            @Test
            fun `returns muutosilmoitus when hakemus has one`() {
                val hakemusEntity = hakemusFactory.builderWithGeneratedHanke().saveEntity()
                val hakemus = hakemusService.getById(hakemusEntity.id)
                val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).saveEntity()

                val result = hakemusService.hankkeenHakemuksetResponses(hakemus.hankeTunnus, false)

                val expectedMuutosilmoitus =
                    HankkeenHakemusMuutosilmoitusResponse(
                        muutosilmoitus.id,
                        muutosilmoitus.sent,
                        HankkeenHakemusDataResponse(
                            muutosilmoitus.hakemusData as JohtoselvityshakemusEntityData,
                            false,
                        ),
                    )
                assertThat(result)
                    .single()
                    .prop(HankkeenHakemusResponse::muutosilmoitus)
                    .isEqualTo(expectedMuutosilmoitus)
            }

            @Test
            fun `returns paatokset when hakemus has them`() {
                val hakemustunnus = "JS2500014"
                val hakemusEntity =
                    hakemusFactory
                        .builderWithGeneratedHanke()
                        .withStatus(identifier = hakemustunnus)
                        .withMandatoryFields()
                        .saveEntity()
                val hakemus = hakemusService.getById(hakemusEntity.id)
                val paatos = paatosFactory.save(hakemus, tyyppi = PaatosTyyppi.PAATOS).toResponse()

                val result = hakemusService.hankkeenHakemuksetResponses(hakemus.hankeTunnus, false)

                assertThat(result)
                    .single()
                    .prop(HankkeenHakemusResponse::paatokset)
                    .containsOnly(hakemustunnus to listOf(paatos))
            }
        }

        @Nested
        inner class WithKaivuilmoitus {
            @Test
            fun `returns applications`() {
                val hakemus = hakemusFactory.builder(ApplicationType.EXCAVATION_NOTIFICATION).save()

                val result = hakemusService.hankkeenHakemuksetResponses(hakemus.hankeTunnus, false)

                val expectedHakemus = hakemusRepository.getReferenceById(hakemus.id)
                val expectedResponse =
                    HankkeenHakemusResponse(expectedHakemus, null, listOf(), false)
                assertThat(result).containsExactly(expectedResponse)
            }

            @Test
            fun `returns muutosilmoitus when hakemus has one`() {
                val hakemusEntity =
                    hakemusFactory.builder(ApplicationType.EXCAVATION_NOTIFICATION).saveEntity()
                val hakemus = hakemusService.getById(hakemusEntity.id)
                val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).saveEntity()

                val result = hakemusService.hankkeenHakemuksetResponses(hakemus.hankeTunnus, false)

                val expectedMuutosilmoitus =
                    HankkeenHakemusMuutosilmoitusResponse(
                        muutosilmoitus.id,
                        muutosilmoitus.sent,
                        HankkeenHakemusDataResponse(
                            muutosilmoitus.hakemusData as KaivuilmoitusEntityData,
                            false,
                        ),
                    )
                assertThat(result)
                    .single()
                    .prop(HankkeenHakemusResponse::muutosilmoitus)
                    .isEqualTo(expectedMuutosilmoitus)
            }

            @Test
            fun `returns paatokset when hakemus has them`() {
                val hakemustunnus = "KP2500141-2"
                val hakemusEntity =
                    hakemusFactory
                        .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                        .withStatus(identifier = hakemustunnus)
                        .withMandatoryFields()
                        .saveEntity()
                val hakemus = hakemusService.getById(hakemusEntity.id)
                val paatos = paatosFactory.save(hakemus, tyyppi = PaatosTyyppi.PAATOS).toResponse()
                val toiminnallinenKunto =
                    paatosFactory
                        .save(hakemus, tyyppi = PaatosTyyppi.TOIMINNALLINEN_KUNTO)
                        .toResponse()
                val tyoValmis =
                    paatosFactory.save(hakemus, tyyppi = PaatosTyyppi.TYO_VALMIS).toResponse()
                val oldHakemustunnus = "KP2500141"
                val oldPaatos =
                    paatosFactory
                        .save(
                            hakemus,
                            hakemustunnus = oldHakemustunnus,
                            tyyppi = PaatosTyyppi.PAATOS,
                        )
                        .toResponse()
                val oldToiminnallinenKunto =
                    paatosFactory
                        .save(
                            hakemus,
                            hakemustunnus = oldHakemustunnus,
                            tyyppi = PaatosTyyppi.TOIMINNALLINEN_KUNTO,
                        )
                        .toResponse()

                val result = hakemusService.hankkeenHakemuksetResponses(hakemus.hankeTunnus, false)

                assertThat(result)
                    .single()
                    .prop(HankkeenHakemusResponse::paatokset)
                    .containsOnly(
                        hakemustunnus to listOf(paatos, toiminnallinenKunto, tyoValmis),
                        oldHakemustunnus to listOf(oldPaatos, oldToiminnallinenKunto),
                    )
            }
        }
    }

    @Nested
    inner class Create {
        abstract inner class CreateTest {
            abstract val request: CreateHakemusRequest

            abstract fun Assert<Hakemus>.matchesRequest(hankeTunnus: String)

            private fun CreateHakemusRequest.withHanketunnus(hanketunnus: String) =
                when (this) {
                    is CreateJohtoselvityshakemusRequest -> copy(hankeTunnus = hanketunnus)
                    is CreateKaivuilmoitusRequest -> copy(hankeTunnus = hanketunnus)
                }

            @Test
            fun `throws exception when hanke does not exist`() {
                val failure = assertFailure { hakemusService.create(request, USERNAME) }

                failure.all {
                    hasClass(HankeNotFoundException::class)
                    messageContains("hankeTunnus ${request.hankeTunnus}")
                }
            }

            @Test
            fun `creates the hakemus`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)

                val result = hakemusService.create(request, USERNAME)

                val hakemus = hakemusService.getById(result.id)
                assertThat(hakemus).matchesRequest(hanke.hankeTunnus)
            }

            @Test
            fun `returns the created hakemus`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)

                val result = hakemusService.create(request, USERNAME)

                assertThat(result).matchesRequest(hanke.hankeTunnus)
            }

            @Test
            fun `writes the created hakemus to the audit log`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)
                auditLogRepository.deleteAll()

                val result = hakemusService.create(request, USERNAME)

                val createdLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
                assertThat(createdLogs).single().isSuccess(Operation.CREATE) {
                    hasUserActor(USERNAME)
                    withTarget {
                        hasId(result.id)
                        prop(AuditLogTarget::type).isEqualTo(ObjectType.HAKEMUS)
                        hasNoObjectBefore()
                        hasObjectAfter(result)
                    }
                }
            }

            @Test
            fun `updates the hanke phase to RAKENTAMINEN`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)

                hakemusService.create(request, USERNAME)

                val updatedHanke = hankeRepository.findByHankeTunnus(hanke.hankeTunnus)!!
                assertThat(updatedHanke.vaihe).isEqualTo(Hankevaihe.RAKENTAMINEN)
            }

            @Test
            fun `writes the updated hanke to the audit log if the phase has changed`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
                val request = request.withHanketunnus(hanke.hankeTunnus)
                auditLogRepository.deleteAll()

                hakemusService.create(request, USERNAME)

                val createdLogs = auditLogRepository.findByType(ObjectType.HANKE)
                assertThat(createdLogs).single().isSuccess(Operation.UPDATE) {
                    hasUserActor(USERNAME)
                    withTarget {
                        hasId(hanke.id)
                        prop(AuditLogTarget::type).isEqualTo(ObjectType.HANKE)
                        prop(AuditLogTarget::objectBefore)
                            .isNotNull()
                            .contains("\"vaihe\":\"OHJELMOINTI\"")
                        prop(AuditLogTarget::objectAfter)
                            .isNotNull()
                            .contains("\"vaihe\":\"RAKENTAMINEN\"")
                    }
                }
            }

            @Test
            fun `does not write hanke to the audit log if the phase has not changed`() {
                val hanke =
                    hankeFactory
                        .builder(USERNAME)
                        .withHankealue()
                        .withVaihe(Hankevaihe.RAKENTAMINEN)
                        .save()
                val request = request.withHanketunnus(hanke.hankeTunnus)
                auditLogRepository.deleteAll()

                hakemusService.create(request, USERNAME)

                assertThat(auditLogRepository.findByType(ObjectType.HANKE)).isEmpty()
            }
        }

        @Nested
        inner class WithJohtoselvityshakemus : CreateTest() {
            override val request: CreateJohtoselvityshakemusRequest =
                CreateHakemusRequestFactory.johtoselvitysRequest()

            override fun Assert<Hakemus>.matchesRequest(hankeTunnus: String) = all {
                prop(Hakemus::id).isGreaterThan(0)
                prop(Hakemus::hankeTunnus).isEqualTo(hankeTunnus)
                prop(Hakemus::alluid).isNull()
                prop(Hakemus::alluStatus).isNull()
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::applicationType).isEqualTo(ApplicationType.CABLE_REPORT)
                prop(Hakemus::applicationData)
                    .isInstanceOf(JohtoselvityshakemusData::class)
                    .matchesRequest()
            }

            private fun Assert<JohtoselvityshakemusData>.matchesRequest() = all {
                prop(JohtoselvityshakemusData::name).isEqualTo(request.name)
                prop(JohtoselvityshakemusData::postalAddress)
                    .hasStreetName(request.postalAddress!!.streetAddress.streetName!!)
                prop(JohtoselvityshakemusData::constructionWork).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::maintenanceWork).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::propertyConnectivity).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::emergencyWork).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::rockExcavation).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::workDescription).isEqualTo(request.workDescription)
                prop(JohtoselvityshakemusData::startTime).isNull()
                prop(JohtoselvityshakemusData::endTime).isNull()
                prop(JohtoselvityshakemusData::areas).isNull()
                prop(JohtoselvityshakemusData::yhteystiedot).isEmpty()
            }
        }

        @Nested
        inner class WithKaivuilmoitus : CreateTest() {
            override val request: CreateKaivuilmoitusRequest =
                CreateHakemusRequestFactory.kaivuilmoitusRequest()

            override fun Assert<Hakemus>.matchesRequest(hankeTunnus: String) = all {
                prop(Hakemus::id).isGreaterThan(0)
                prop(Hakemus::hankeTunnus).isEqualTo(hankeTunnus)
                prop(Hakemus::alluid).isNull()
                prop(Hakemus::alluStatus).isNull()
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::applicationType).isEqualTo(ApplicationType.EXCAVATION_NOTIFICATION)
                prop(Hakemus::applicationData)
                    .isInstanceOf(KaivuilmoitusData::class)
                    .matchesRequest()
            }

            private fun Assert<KaivuilmoitusData>.matchesRequest() = all {
                prop(KaivuilmoitusData::name).isEqualTo(request.name)
                prop(KaivuilmoitusData::workDescription).isEqualTo(request.workDescription)
                prop(KaivuilmoitusData::constructionWork).isEqualTo(request.constructionWork)
                prop(KaivuilmoitusData::maintenanceWork).isEqualTo(request.maintenanceWork)
                prop(KaivuilmoitusData::emergencyWork).isEqualTo(request.emergencyWork)
                prop(KaivuilmoitusData::cableReportDone).isEqualTo(request.cableReportDone)
                prop(KaivuilmoitusData::rockExcavation).isEqualTo(request.rockExcavation)
                prop(KaivuilmoitusData::cableReports).isEqualTo(request.cableReports)
                prop(KaivuilmoitusData::placementContracts).isEqualTo(request.placementContracts)
                prop(KaivuilmoitusData::requiredCompetence).isEqualTo(request.requiredCompetence)
                prop(KaivuilmoitusData::startTime).isNull()
                prop(KaivuilmoitusData::endTime).isNull()
                prop(KaivuilmoitusData::areas).isNull()
                prop(KaivuilmoitusData::yhteystiedot).isEmpty()
                prop(KaivuilmoitusData::invoicingCustomer).isNull()
                prop(KaivuilmoitusData::additionalInfo).isNull()
            }
        }
    }

    @Nested
    inner class CreateJohtoselvitys {
        private val hakemusNimi = "Johtoselvitys for a private property"

        @Test
        fun `saves the new hakemus`() {
            val hanke = hankeFactory.saveMinimal(nimi = hakemusNimi)

            hakemusService.createJohtoselvitys(hanke, USERNAME)

            assertThat(hakemusRepository.findAll()).single().all {
                prop(HakemusEntity::id).isNotNull()
                prop(HakemusEntity::alluid).isNull()
                prop(HakemusEntity::alluStatus).isNull()
                prop(HakemusEntity::applicationIdentifier).isNull()
                prop(HakemusEntity::userId).isEqualTo(USERNAME)
                prop(HakemusEntity::applicationType).isEqualTo(ApplicationType.CABLE_REPORT)
                prop(HakemusEntity::hakemusEntityData)
                    .isInstanceOf(JohtoselvityshakemusEntityData::class)
                    .all {
                        prop(HakemusEntityData::name).isEqualTo(hakemusNimi)
                        prop(HakemusEntityData::applicationType)
                            .isEqualTo(ApplicationType.CABLE_REPORT)
                        prop(HakemusEntityData::areas).isNull()
                        prop(JohtoselvityshakemusEntityData::startTime).isNull()
                        prop(JohtoselvityshakemusEntityData::endTime).isNull()
                    }
            }
        }

        @Test
        fun `returns the created hakemus`() {
            val hanke = hankeFactory.saveMinimal(nimi = hakemusNimi)

            val hakemus = hakemusService.createJohtoselvitys(hanke, USERNAME)

            assertThat(hakemus).all {
                prop(Hakemus::id).isNotNull()
                prop(Hakemus::alluid).isNull()
                prop(Hakemus::alluStatus).isNull()
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::applicationType).isEqualTo(ApplicationType.CABLE_REPORT)
                prop(Hakemus::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(Hakemus::applicationData).isInstanceOf(JohtoselvityshakemusData::class).all {
                    prop(HakemusData::name).isEqualTo(hakemusNimi)
                    prop(JohtoselvityshakemusData::postalAddress).isNull()
                    prop(HakemusData::startTime).isNull()
                    prop(HakemusData::endTime).isNull()
                    prop(HakemusData::areas).isNull()
                    prop(HakemusData::yhteystiedot).isEmpty()
                }
            }
        }

        @Test
        fun `writes to audit logs`() {
            val hanke = hankeFactory.saveMinimal(nimi = hakemusNimi)
            auditLogRepository.deleteAll()

            val hakemus = hakemusService.createJohtoselvitys(hanke, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.CREATE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasNoObjectBefore()
                    hasObjectAfter<Hakemus> { prop(Hakemus::id).isEqualTo(hakemus.id) }
                }
            }
        }
    }

    @Nested
    inner class SendHakemus {
        private val alluId = 35124

        private val areaOutsideDefaultHanke: JohtoselvitysHakemusalue =
            ApplicationFactory.createCableReportApplicationArea(
                geometry = GeometriaFactory.thirdPolygon()
            )

        @Test
        fun `throws exception when the application doesn't exist`() {
            val failure = assertFailure { hakemusService.sendHakemus(1234, null, USERNAME) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `throws exception when the application has been sent before`() {
            val application =
                hakemusFactory.builder().withMandatoryFields().withStatus(alluId = alluId).save()

            val failure = assertFailure {
                hakemusService.sendHakemus(application.id, null, USERNAME)
            }

            failure.all {
                hasClass(HakemusAlreadySentException::class)
                messageContains("id=${application.id}")
                messageContains("alluId=$alluId")
                messageContains("status=PENDING")
            }
        }

        @Test
        fun `throws exception when the application fails validation`() {
            val application =
                hakemusFactory.builder().withMandatoryFields().withRockExcavation(null).save()

            val failure = assertFailure {
                hakemusService.sendHakemus(application.id, null, USERNAME)
            }

            failure
                .isInstanceOf(InvalidHakemusDataException::class)
                .messageContains("applicationData.rockExcavation")
        }

        @Test
        fun `skips the check for hakemusalueet inside hankealueet when the hanke is generated`() {
            val application =
                hakemusFactory.builderWithGeneratedHanke().withMandatoryFields().save()
            val areas = application.applicationData.areas!!
            val hanke = hankeRepository.findAll().single()
            hankeRepository.save(hanke.apply { alueet = mutableListOf() })
            assertThat(
                    geometriatDao.matchingHankealueet(
                        hanke.id,
                        areas.single().geometries().single(),
                    )
                )
                .isEmpty()
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            hakemusService.sendHakemus(application.id, null, USERNAME)

            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(any())
            }
        }

        @Test
        fun `sets pendingOnClient to false`() {
            val hanke = hankeFactory.saveWithAlue()
            val hakemus = hakemusFactory.builder(hankeEntity = hanke).withMandatoryFields().save()
            val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
            val applicationData =
                (hakemus.applicationData as JohtoselvityshakemusData)
                    .setOrdererForContractor(founder.id)
                    .toAlluCableReportData(hanke.hankeTunnus)
                    .copy(pendingOnClient = false)
            every { alluClient.create(applicationData) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(id = alluId)

            hakemusService.sendHakemus(hakemus.id, null, USERNAME)

            verifySequence {
                alluClient.create(applicationData)
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws an exception when the application is already beyond pending in Allu`() {
            val hanke = hankeFactory.saveWithAlue()
            val application =
                hakemusFactory
                    .builder(hankeEntity = hanke)
                    .withMandatoryFields()
                    .inHandling(alluId = alluId)
                    .saveEntity()

            val failure = assertFailure {
                hakemusService.sendHakemus(application.id, null, USERNAME)
            }

            failure.all {
                hasClass(HakemusAlreadySentException::class)
                messageContains("id=${application.id}")
                messageContains("alluId=$alluId")
                messageContains("status=HANDLING")
            }
        }

        @Test
        fun `sends application and saves alluid even when the status query fails`() {
            val hanke = hankeFactory.saveWithAlue()
            val application =
                hakemusFactory.builder(hankeEntity = hanke).withMandatoryFields().saveEntity()
            val applicationData = application.hakemusEntityData as JohtoselvityshakemusEntityData
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } throws AlluException()

            val response = hakemusService.sendHakemus(application.id, null, USERNAME)

            assertThat(response).all {
                prop(Hakemus::alluid).isEqualTo(alluId)
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::alluStatus).isNull()
            }
            assertThat(hakemusRepository.getReferenceById(application.id)).all {
                prop(HakemusEntity::alluid).isEqualTo(alluId)
                prop(HakemusEntity::hakemusEntityData).isEqualTo(applicationData)
                prop(HakemusEntity::applicationIdentifier).isNull()
                prop(HakemusEntity::alluStatus).isNull()
            }

            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws an exception when application area is outside hankealue`() {
            val hanke = hankeFactory.saveWithAlue()
            val hakemus =
                hakemusFactory
                    .builder(hanke)
                    .withMandatoryFields()
                    .withArea(areaOutsideDefaultHanke)
                    .save()

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, null, USERNAME) }

            failure.all {
                hasClass(HakemusGeometryNotInsideHankeException::class)
                messageContains("Hakemus geometry doesn't match any hankealue")
                messageContains(
                    "hakemus geometry=${areaOutsideDefaultHanke.geometry.toJsonString()}"
                )
            }
        }

        @Test
        fun `cancels the sent application before throwing when uploading initial attachments fails`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
            attachmentFactory.save(application = applicationEntity).withContent()
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.addAttachments(alluId, any(), any()) } throws AlluException()
            justRun { alluClient.cancel(alluId) }
            every { alluClient.sendSystemComment(alluId, any()) } returns 4141

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, null, USERNAME) }

            failure.hasClass(AlluException::class)
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachments(alluId, withNames(FILE_NAME_PDF), any())
                alluClient.cancel(alluId)
                alluClient.sendSystemComment(alluId, ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG)
            }
        }

        @Test
        fun `saves the Allu ID and status even when sending the form data attachment fails`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            every { alluClient.create(any()) } returns alluId
            every { alluClient.addAttachment(alluId, any()) } throws AlluException()
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val response = hakemusService.sendHakemus(hakemus.id, null, USERNAME)

            assertThat(response.alluid).isEqualTo(alluId)
            assertThat(response.alluStatus).isEqualTo(ApplicationStatus.PENDING)
            val savedHakemus = hakemusRepository.getReferenceById(hakemus.id)
            assertThat(savedHakemus.alluid).isEqualTo(alluId)
            assertThat(savedHakemus.alluStatus).isEqualTo(ApplicationStatus.PENDING)
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `creates a new application to Allu and saves the ID and status to database`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
            attachmentFactory.save(application = applicationEntity).withContent()
            val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
            val hakemusData = hakemus.applicationData as JohtoselvityshakemusData
            val expectedDataAfterSend = hakemusData.setOrdererForContractor(founder.id)
            val expectedAlluRequest =
                expectedDataAfterSend.toAlluCableReportData(hakemus.hankeTunnus)
            every { alluClient.create(expectedAlluRequest) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            justRun { alluClient.addAttachments(alluId, any(), any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val response = hakemusService.sendHakemus(hakemus.id, null, USERNAME)

            assertThat(response).all {
                prop(Hakemus::alluid).isEqualTo(alluId)
                prop(Hakemus::applicationIdentifier)
                    .isEqualTo(DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER)
                prop(Hakemus::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                prop(Hakemus::applicationData).isEqualTo(expectedDataAfterSend)
            }
            assertThat(hakemusRepository.getReferenceById(hakemus.id)).all {
                prop(HakemusEntity::alluid).isEqualTo(alluId)
                prop(HakemusEntity::applicationIdentifier)
                    .isEqualTo(DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER)
                prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING)
            }
            verifySequence {
                alluClient.create(expectedAlluRequest)
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachments(alluId, withNames(FILE_NAME_PDF), any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `sets the orderer on the correct contact`() {
            val hanke = hankeFactory.saveWithAlue()
            val founder = hankeKayttajaFactory.getFounderFromHanke(hanke)
            val otherKayttaja1 = hankeKayttajaFactory.saveUser(hanke.id)
            val otherKayttaja2 = hankeKayttajaFactory.saveUser(hanke.id, sahkoposti = "other@email")
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .hakija(otherKayttaja1, founder)
                    .tyonSuorittaja(founder, otherKayttaja2)
                    .rakennuttaja(otherKayttaja1, founder)
                    .asianhoitaja(founder, otherKayttaja2)
                    .save()
            val hakemusData = hakemus.applicationData as JohtoselvityshakemusData
            val expectedDataAfterSend = hakemusData.setOrdererForCustomer(founder.id)
            val expectedAlluRequest =
                expectedDataAfterSend.toAlluCableReportData(hakemus.hankeTunnus)
            every { alluClient.create(expectedAlluRequest) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val response = hakemusService.sendHakemus(hakemus.id, null, USERNAME)

            assertThat(response).prop(Hakemus::applicationData).isEqualTo(expectedDataAfterSend)
            assertThat(hakemusService.getById(hakemus.id))
                .prop(Hakemus::applicationData)
                .prop(HakemusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { Pair(it.hankekayttajaId, it.tilaaja) }
                .contains(Pair(founder.id, true))
            verifySequence {
                alluClient.create(expectedAlluRequest)
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws exception when sender is not a contact`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").withMandatoryFields().save()

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, null, USERNAME) }

            failure.all {
                hasClass(UserNotInContactsException::class)
                messageContains("id=${hakemus.id}")
                messageContains("alluId=null")
                messageContains("identifier=null")
            }
        }

        @Test
        fun `saves disclosure logs for the sent personal info`() {
            val hanke = hankeFactory.saveWithAlue()
            val founder = hankeKayttajaFactory.getFounderFromHanke(hanke)
            val hakijaYhteystieto =
                HakemusyhteystietoFactory.create(
                    tyyppi = CustomerType.PERSON,
                    nimi = "Ylevi Yhteyshenkilö",
                    sahkoposti = "ylevi@hakemus.info",
                    puhelinnumero = "111222333",
                    registryKey = null,
                )
            val asianhoitajaYhteystieto =
                hakijaYhteystieto.copy(
                    nimi = "Tytti Työläinen",
                    sahkoposti = "tytti@hakeus.info",
                    puhelinnumero = "999888777",
                )
            val hakemus =
                hakemusFactory
                    .builder(userId = "Other user")
                    .withMandatoryFields()
                    .hakija(hakijaYhteystieto, founder)
                    .tyonSuorittaja(asianhoitajaYhteystieto, founder)
                    .asianhoitaja()
                    .save()
            auditLogRepository.deleteAll()
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            hakemusService.sendHakemus(hakemus.id, null, USERNAME)

            val yhteystietoEntries = auditLogRepository.findAll()
            assertThat(yhteystietoEntries).hasSize(5)
            assertThat(auditLogRepository.findByType(ObjectType.ALLU_CUSTOMER)).hasSize(2)
            assertThat(auditLogRepository.findByType(ObjectType.ALLU_CONTACT)).hasSize(3)
            assertThat(yhteystietoEntries).each {
                it.isSuccess(Operation.READ) {
                    hasServiceActor("Allu")
                    withTarget {
                        hasId(hakemus.id)
                        prop(AuditLogTarget::objectBefore).isNotNull()
                        hasNoObjectAfter()
                    }
                }
            }
            val expectedHakijaCustomer =
                Customer(
                    type = CustomerType.PERSON,
                    name = "Ylevi Yhteyshenkilö",
                    postalAddress = null,
                    email = "ylevi@hakemus.info",
                    phone = "111222333",
                    registryKey = null,
                    ovt = null,
                    invoicingOperator = null,
                    country = "FI",
                    sapCustomerNumber = null,
                )
            val expectedAsianhoitajaCustomer =
                expectedHakijaCustomer.copy(
                    name = "Tytti Työläinen",
                    email = "tytti@hakeus.info",
                    phone = "999888777",
                )
            val expectedPerustajaContact =
                Contact(
                    name = founder.fullName(),
                    email = founder.sahkoposti,
                    phone = founder.puhelin,
                    orderer = true,
                )
            val expectedAsianhoitajaContact =
                Contact(
                    name =
                        "${KAYTTAJA_INPUT_ASIANHOITAJA.etunimi} ${KAYTTAJA_INPUT_ASIANHOITAJA.sukunimi}",
                    email = KAYTTAJA_INPUT_ASIANHOITAJA.email,
                    phone = KAYTTAJA_INPUT_ASIANHOITAJA.puhelin,
                    orderer = false,
                )
            val expectedObjects =
                listOf(
                    AlluCustomerWithRole(HAKIJA, expectedHakijaCustomer),
                    AlluCustomerWithRole(TYON_SUORITTAJA, expectedAsianhoitajaCustomer),
                    AlluContactWithRole(HAKIJA, expectedPerustajaContact),
                    AlluContactWithRole(
                        TYON_SUORITTAJA,
                        expectedPerustajaContact.copy(orderer = false),
                    ),
                    AlluContactWithRole(ASIANHOITAJA, expectedAsianhoitajaContact),
                )
            assertThat(yhteystietoEntries.map { it.message.auditEvent.target.objectBefore })
                .hasSameElementsAs(expectedObjects.map { it.toJsonString() })
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `saves audit logs when request decision on paper`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val paperDecisionReceiver = PaperDecisionReceiverFactory.default
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.sendSystemComment(alluId, any()) } returns 4
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)
            auditLogRepository.deleteAll()

            hakemusService.sendHakemus(hakemus.id, paperDecisionReceiver, USERNAME)

            val expectedDataAfter =
                (hakemus.applicationData as JohtoselvityshakemusData).copy(
                    paperDecisionReceiver = paperDecisionReceiver
                )
            val createdLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
            assertThat(createdLogs).single().isSuccess(Operation.UPDATE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasObjectBefore(hakemus)
                    hasObjectAfter(hakemus.copy(applicationData = expectedDataAfter))
                }
            }
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.sendSystemComment(alluId, any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `saves the paper decision receiver for the hakemus`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val paperDecisionReceiver = PaperDecisionReceiverFactory.default
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.sendSystemComment(alluId, any()) } returns 4
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val result = hakemusService.sendHakemus(hakemus.id, paperDecisionReceiver, USERNAME)

            assertThat(result)
                .prop(Hakemus::applicationData)
                .prop(HakemusData::paperDecisionReceiver)
                .isEqualTo(paperDecisionReceiver)
            assertThat(hakemusRepository.getReferenceById(hakemus.id))
                .prop(HakemusEntity::hakemusEntityData)
                .prop(HakemusEntityData::paperDecisionReceiver)
                .isEqualTo(paperDecisionReceiver)
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.sendSystemComment(alluId, any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `sends a system comment to Allu when there is a paper decision receiver`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val paperDecisionReceiver = PaperDecisionReceiverFactory.default
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.sendSystemComment(alluId, any()) } returns 4
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            hakemusService.sendHakemus(hakemus.id, paperDecisionReceiver, USERNAME)

            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                alluClient.sendSystemComment(
                    alluId,
                    "Asiakas haluaa päätöksen myös paperisena. Liitteessä " +
                        "haitaton-form-data.pdf on päätöksen toimitukseen liittyvät osoitetiedot.",
                )
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Nested
        inner class Kaivuilmoitus {

            @Nested
            inner class ExistingJohtoselvitys {

                @Test
                fun `creates a new application to Allu and saves the ID and status to database`() {
                    val hanke = hankeFactory.builder().withHankealue().saveEntity()
                    val hankeAlueet = hankeService.loadHanke(hanke.hankeTunnus)!!.alueet
                    val hakemus: Hakemus =
                        hakemusFactory
                            .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                            .withMandatoryFields(hankeAlueet[0])
                            .save()
                    val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
                    attachmentFactory.save(application = applicationEntity).withContent()
                    val hakemusData = hakemus.applicationData as KaivuilmoitusData
                    val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
                    val expectedDataAfterSend = hakemusData.setOrdererForContractor(founder.id)
                    val expectedAlluRequest =
                        expectedDataAfterSend.toAlluExcavationNotificationData(hakemus.hankeTunnus)
                    every { alluClient.create(expectedAlluRequest) } returns alluId
                    justRun { alluClient.addAttachment(alluId, any()) }
                    justRun { alluClient.addAttachments(alluId, any(), any()) }
                    every { alluClient.getApplicationInformation(alluId) } returns
                        AlluFactory.createAlluApplicationResponse(
                            alluId,
                            applicationId = DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER,
                        )

                    val response = hakemusService.sendHakemus(hakemus.id, null, USERNAME)

                    assertThat(response).all {
                        prop(Hakemus::alluid).isEqualTo(alluId)
                        prop(Hakemus::applicationIdentifier)
                            .isEqualTo(DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)
                        prop(Hakemus::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                        prop(Hakemus::applicationData).isEqualTo(expectedDataAfterSend)
                    }
                    assertThat(hakemusRepository.getReferenceById(hakemus.id)).all {
                        prop(HakemusEntity::alluid).isEqualTo(alluId)
                        prop(HakemusEntity::applicationIdentifier)
                            .isEqualTo(DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)
                        prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                    }
                    verifySequence {
                        alluClient.create(expectedAlluRequest)
                        alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                        alluClient.addAttachment(alluId, withName(HHS_PDF_FILENAME))
                        alluClient.addAttachments(alluId, withNames(FILE_NAME_PDF), any())
                        alluClient.getApplicationInformation(alluId)
                    }
                }
            }

            @Nested
            inner class AccompanyingJohtoselvitys {

                private var kaivuilmoitusHakemusId: Long = 0
                private val johtoselvitysId: Int = alluId
                private val kaivuilmoitusId: Int = alluId + 1
                private lateinit var expectedCableReportAlluRequest: AlluCableReportApplicationData
                private lateinit var expectedExcavationNotificationAlluRequest:
                    AlluExcavationNotificationData
                private lateinit var expectedJohtoselvitysHakemusEntityData:
                    JohtoselvityshakemusEntityData
                private lateinit var expectedKaivuilmoitusDataAfterSend: KaivuilmoitusData

                /**
                 * Set up the test environment before each test. All the necessary mocks and data
                 * are set up here even though the mocked calls are not verified in all tests in
                 * order to keep the tests clean and readable.
                 */
                @BeforeEach
                fun setUp() {
                    val hanke = hankeFactory.builder("Other user").withHankealue().saveEntity()
                    val hankeAlueet = hankeService.loadHanke(hanke.hankeTunnus)!!.alueet
                    val currentUser =
                        hankeKayttajaFactory.saveIdentifiedUser(
                            hanke.id,
                            userId = USERNAME,
                            kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
                        )
                    val hakemus =
                        hakemusFactory
                            .builder("Other user", hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                            .withMandatoryFields(hankeAlueet[0])
                            .withoutCableReports()
                            .withRockExcavation(false)
                            .asianhoitaja(currentUser)
                            .save()
                    kaivuilmoitusHakemusId = hakemus.id
                    val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
                    attachmentFactory.save(application = applicationEntity).withContent()
                    val hakemusData = hakemus.applicationData as KaivuilmoitusData
                    expectedJohtoselvitysHakemusEntityData =
                        (applicationEntity.hakemusEntityData as KaivuilmoitusEntityData)
                            .createAccompanyingJohtoselvityshakemusData()
                    val expectedJohtoselvityshakemusDataAfterSend =
                        expectedJohtoselvitysHakemusEntityData
                            .toHakemusData(hakemusData.yhteystiedotMap())
                            .setOrdererForRepresentative(currentUser.id)
                    expectedKaivuilmoitusDataAfterSend =
                        hakemusData
                            .copy(
                                cableReports = listOf(DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER),
                                cableReportDone = true,
                            )
                            .setOrdererForRepresentative(currentUser.id)
                    expectedCableReportAlluRequest =
                        expectedJohtoselvityshakemusDataAfterSend.toAlluCableReportData(
                            hakemus.hankeTunnus
                        )
                    expectedExcavationNotificationAlluRequest =
                        expectedKaivuilmoitusDataAfterSend.toAlluExcavationNotificationData(
                            hakemus.hankeTunnus
                        )
                    every { alluClient.create(expectedCableReportAlluRequest) } returns
                        johtoselvitysId
                    every { alluClient.create(expectedExcavationNotificationAlluRequest) } returns
                        kaivuilmoitusId
                    justRun { alluClient.addAttachment(johtoselvitysId, any()) }
                    justRun { alluClient.addAttachment(kaivuilmoitusId, any()) }
                    justRun { alluClient.addAttachments(johtoselvitysId, any(), any()) }
                    justRun { alluClient.addAttachments(kaivuilmoitusId, any(), any()) }
                    every { alluClient.getApplicationInformation(johtoselvitysId) } returns
                        AlluFactory.createAlluApplicationResponse(johtoselvitysId)
                    every { alluClient.getApplicationInformation(kaivuilmoitusId) } returns
                        AlluFactory.createAlluApplicationResponse(
                            kaivuilmoitusId,
                            applicationId = DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER,
                        )
                }

                /**
                 * Clear all mocks after each test. This is mandatory to do here in order to
                 * override the upper level @AfterEach method [checkMocks] which checks that all
                 * mocked calls were made and verifies their order. These checks cannot be done here
                 * because the calls described in [setUp] are not verified in all tests. Clearing
                 * the mocks has to be done nevertheless.
                 */
                @AfterEach
                fun clearMocks() {
                    clearAllMocks()
                }

                @Test
                fun `saves johtoselvitys to DB`() {
                    hakemusService.sendHakemus(kaivuilmoitusHakemusId, null, USERNAME)

                    assertThat(hakemusRepository.getOneByAlluid(johtoselvitysId)).isNotNull().all {
                        prop(HakemusEntity::alluid).isEqualTo(johtoselvitysId)
                        prop(HakemusEntity::applicationIdentifier)
                            .isEqualTo(DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER)
                        prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                        prop(HakemusEntity::hakemusEntityData)
                            .isEqualTo(expectedJohtoselvitysHakemusEntityData)
                        prop(HakemusEntity::userId).isEqualTo(USERNAME)
                    }
                }

                @Test
                fun `erases henkilotunnukset from the accompanying johtoselvitys`() {
                    val hakija = hakemusyhteystietoRepository.findAll().first { it.rooli == HAKIJA }
                    hakija.tyyppi = CustomerType.PERSON
                    hakija.registryKey = "060623F6387"
                    hakemusyhteystietoRepository.save(hakija)
                    every {
                        alluClient.create(match { it is AlluCableReportApplicationData })
                    } returns johtoselvitysId
                    every {
                        alluClient.create(match { it is AlluExcavationNotificationData })
                    } returns kaivuilmoitusId

                    hakemusService.sendHakemus(kaivuilmoitusHakemusId, null, USERNAME)

                    val entity = hakemusRepository.getOneByAlluid(johtoselvitysId)!!
                    val johtoselvitys = hakemusService.getById(entity.id)
                    assertThat(johtoselvitys.applicationData.customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::registryKey)
                        .isNull()
                    verify {
                        alluClient.create(
                            match {
                                it is AlluCableReportApplicationData &&
                                    it.customerWithContacts.customer.registryKey == null
                            }
                        )
                    }
                }

                @Test
                fun `sends johtoselvitys and kaivuilmoitus to Allu`() {
                    hakemusService.sendHakemus(kaivuilmoitusHakemusId, null, USERNAME)

                    verifySequence {
                        // first the cable report is sent
                        alluClient.create(expectedCableReportAlluRequest)
                        alluClient.addAttachment(johtoselvitysId, withName(FORM_DATA_PDF_FILENAME))
                        alluClient.addAttachments(johtoselvitysId, withNames(FILE_NAME_PDF), any())
                        alluClient.getApplicationInformation(johtoselvitysId)
                        // then the excavation notification is sent
                        alluClient.create(expectedExcavationNotificationAlluRequest)
                        alluClient.addAttachment(kaivuilmoitusId, withName(FORM_DATA_PDF_FILENAME))
                        alluClient.addAttachment(kaivuilmoitusId, withName(HHS_PDF_FILENAME))
                        alluClient.addAttachments(kaivuilmoitusId, withNames(FILE_NAME_PDF), any())
                        alluClient.getApplicationInformation(kaivuilmoitusId)
                    }
                }

                @Test
                fun `returns the created kaivuilmoitus`() {
                    val response =
                        hakemusService.sendHakemus(kaivuilmoitusHakemusId, null, USERNAME)

                    assertThat(response).all {
                        prop(Hakemus::alluid).isEqualTo(kaivuilmoitusId)
                        prop(Hakemus::applicationIdentifier)
                            .isEqualTo(DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)
                        prop(Hakemus::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                        prop(Hakemus::applicationData).isEqualTo(expectedKaivuilmoitusDataAfterSend)
                    }
                }

                @Test
                fun `writes the created hakemus to audit logs`() {
                    auditLogRepository.deleteAll()

                    hakemusService.sendHakemus(kaivuilmoitusHakemusId, null, USERNAME)

                    val logs = auditLogRepository.findByType(ObjectType.HAKEMUS)
                    assertThat(logs).single().isSuccess(Operation.CREATE) {
                        hasUserActor(USERNAME)
                        withTarget {
                            hasTargetType(ObjectType.HAKEMUS)
                            hasNoObjectBefore()
                            hasObjectAfter<Hakemus> {
                                prop(Hakemus::applicationType)
                                    .isEqualTo(ApplicationType.CABLE_REPORT)
                            }
                        }
                    }
                }

                private fun HakemusData.yhteystiedotMap():
                    Map<ApplicationContactType, Hakemusyhteystieto> =
                    this.yhteystiedot().associateBy { it.rooli }
            }
        }
    }

    @Nested
    inner class DeleteWithOrphanGeneratedHankeRemoval {
        @Test
        fun `deletes hanke when deleted application is the only one on a generated hanke`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().withNoAlluFields().save()
            auditLogRepository.deleteAll()

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = true))
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            val auditLogEntry = auditLogRepository.findByType(ObjectType.HANKE)
            assertThat(auditLogEntry).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME)
            }
        }

        @Test
        fun `deletes all attachments when deleting an application`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = hakemus).withContent()
            attachmentFactory.save(application = hakemus).withContent()
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).hasSize(2)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id),
                    )
                )
                .hasSize(2)

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = true))
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id),
                    )
                )
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).isEmpty()
        }

        @Test
        fun `deletes all attachment metadata even when deleting attachment content fails`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = hakemus).withContent()
            attachmentFactory.save(application = hakemus).withContent()
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).hasSize(2)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id),
                    )
                )
                .hasSize(2)
            fileClient.connected = false

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            fileClient.connected = true
            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = true))
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id),
                    )
                )
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).isEmpty()
        }

        @Test
        fun `doesn't delete hanke when hanke is not generated`() {
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = false))
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findByHankeTunnus(hakemus.hankeTunnus)).isNotNull()
        }

        @Test
        fun `doesn't delete hanke when a generated hanke has other applications`() {
            val hanke = hankeFactory.saveMinimal(generated = true)
            val hakemus1 = hakemusFactory.builder(hanke).withNoAlluFields().saveEntity()
            val hakemus2 = hakemusFactory.builder(hanke).withNoAlluFields().saveEntity()
            assertThat(hakemusRepository.findAll()).hasSize(2)

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus1.id, USERNAME)

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = false))
            assertThat(hakemusRepository.findAll())
                .single()
                .prop(HakemusEntity::id)
                .isEqualTo(hakemus2.id)
            assertThat(hankeRepository.findByHankeTunnus(hanke.hankeTunnus)).isNotNull()
        }

        @Test
        fun `deletes hankekayttajat when deleting the generated hanke`() {
            val application =
                hakemusFactory
                    .builderWithGeneratedHanke()
                    .withNoAlluFields()
                    .hakija()
                    .tyonSuorittaja()
                    .rakennuttaja()
                    .asianhoitaja()
                    .saveEntity()

            hakemusService.deleteWithOrphanGeneratedHankeRemoval(application.id, USERNAME)

            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(hakemusyhteystietoRepository.findAll()).isEmpty()
            assertThat(hakemusyhteyshenkiloRepository.findAll()).isEmpty()
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ReportCompletionDate {
        private val date: LocalDate = LocalDate.parse("2024-08-13")
        private val startDate: ZonedDateTime = ZonedDateTime.parse("2024-08-08T00:00Z")
        private val endDate: ZonedDateTime = ZonedDateTime.parse("2024-08-12T00:00Z")

        @ParameterizedTest
        @EnumSource(ValmistumisilmoitusType::class)
        fun `throws exception when hakemus is not found`(type: ValmistumisilmoitusType) {
            val failure = assertFailure { hakemusService.reportCompletionDate(type, 414L, date) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("with id 414")
            }
        }

        @ParameterizedTest
        @EnumSource(ValmistumisilmoitusType::class)
        fun `throws exception when hakemus is not in Allu`(type: ValmistumisilmoitusType) {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withNoAlluFields()
                    .save()

            val failure = assertFailure {
                hakemusService.reportCompletionDate(type, hakemus.id, date)
            }

            failure.all {
                hasClass(HakemusNotYetInAlluException::class)
                messageContains("Hakemus is not yet in Allu")
                messageContains("id=${hakemus.id}")
            }
        }

        private fun unallowedApplicationTypeParameters() =
            ApplicationType.entries.minus(ApplicationType.EXCAVATION_NOTIFICATION).flatMap {
                applicationType ->
                ValmistumisilmoitusType.entries.map { Arguments.of(applicationType, it) }
            }

        @ParameterizedTest
        @MethodSource("unallowedApplicationTypeParameters")
        fun `throws exception when hakemus is of wrong type`(
            applicationType: ApplicationType,
            ilmoitusType: ValmistumisilmoitusType,
        ) {
            val hakemus = hakemusFactory.builder(applicationType).withStatus().save()

            val failure = assertFailure {
                hakemusService.reportCompletionDate(ilmoitusType, hakemus.id, date)
            }

            failure.all {
                hasClass(WrongHakemusTypeException::class)
                messageContains("Wrong application type for this action")
                messageContains("type=$applicationType")
                messageContains("allowed types=EXCAVATION_NOTIFICATION")
                messageContains("id=${hakemus.id}")
            }
        }

        @ParameterizedTest
        @EnumSource(ValmistumisilmoitusType::class)
        fun `sends the date to Allu when the hakemus passes all checks`(
            ilmoitusType: ValmistumisilmoitusType
        ) {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.PENDING)
                    .withStartTime(startDate)
                    .withEndTime(endDate)
                    .save()
            justRun { alluClient.reportCompletionDate(ilmoitusType, hakemus.alluid!!, date) }

            hakemusService.reportCompletionDate(ilmoitusType, hakemus.id, date)

            verifySequence { alluClient.reportCompletionDate(ilmoitusType, hakemus.alluid!!, date) }
        }

        @ParameterizedTest
        @EnumSource(ValmistumisilmoitusType::class)
        fun `saves the report event to database`(ilmoitusType: ValmistumisilmoitusType) {

            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.PENDING)
                    .withStartTime(startDate)
                    .withEndTime(endDate)
                    .save()
            justRun { alluClient.reportCompletionDate(ilmoitusType, hakemus.alluid!!, date) }

            hakemusService.reportCompletionDate(ilmoitusType, hakemus.id, date)

            val savedHakemus: Hakemus = hakemusService.getById(hakemus.id)
            assertThat(savedHakemus.valmistumisilmoitukset).key(ilmoitusType).single().all {
                prop(Valmistumisilmoitus::type).isEqualTo(ilmoitusType)
                prop(Valmistumisilmoitus::dateReported).isEqualTo(date)
                prop(Valmistumisilmoitus::createdAt).isRecent()
                prop(Valmistumisilmoitus::hakemustunnus).isEqualTo(hakemus.applicationIdentifier)
            }
            verifySequence { alluClient.reportCompletionDate(ilmoitusType, hakemus.alluid!!, date) }
        }
    }

    @Nested
    inner class DownloadDecision {
        private val alluId = 134
        private val decisionPdf: ByteArray =
            "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()

        @Test
        fun `when unknown ID should throw`() {
            val failure = assertFailure { hakemusService.downloadDecision(1234) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `when no alluid should throw`() {
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()

            val failure = assertFailure { hakemusService.downloadDecision(hakemus.id) }

            failure.all {
                hasClass(HakemusDecisionNotFoundException::class)
                messageContains("id=${hakemus.id}")
            }
            verify { alluClient wasNot Called }
        }

        @Test
        fun `when no decision in Allu should throw`() {
            val hakemus = hakemusFactory.builder().inHandling(alluId = alluId).save()
            every { alluClient.getDecisionPdf(alluId) }.throws(HakemusDecisionNotFoundException(""))

            val failure = assertFailure { hakemusService.downloadDecision(hakemus.id) }

            failure.hasClass(HakemusDecisionNotFoundException::class)
            verify { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `when decision exists should return it`() {
            val hakemus = hakemusFactory.builder().inHandling(alluId = alluId).save()
            every { alluClient.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = hakemusService.downloadDecision(hakemus.id)

            assertThat(filename).isNotNull().isEqualTo(hakemus.applicationIdentifier)
            assertThat(bytes).isEqualTo(decisionPdf)
            verify { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `when application has no identifier should use default file name`() {
            val hakemus =
                hakemusFactory.builder().withStatus(alluId = alluId, identifier = null).save()
            every { alluClient.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = hakemusService.downloadDecision(hakemus.id)

            assertThat(filename).isNotNull().isEqualTo("paatos")
            assertThat(bytes).isEqualTo(decisionPdf)
            verify { alluClient.getDecisionPdf(alluId) }
        }
    }

    @Nested
    inner class CancelAndDelete {
        private val alluId = 73

        @Test
        fun `deletes application and all its attachments when application not in Allu`() {
            val application = hakemusFactory.builder().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = application).withContent()
            attachmentFactory.save(application = application).withContent()
            val hakemus = hakemusService.getById(application.id)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id),
                    )
                )
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .hasSize(2)

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id),
                    )
                )
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .isEmpty()
        }

        @Test
        fun `deletes application attachment metadata even when attachment content deletion fails`() {
            val application = hakemusFactory.builder().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = application).withContent()
            attachmentFactory.save(application = application).withContent()
            val hakemus = hakemusService.getById(application.id)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id),
                    )
                )
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .hasSize(2)
            fileClient.connected = false

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            fileClient.connected = true
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id),
                    )
                )
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .isEmpty()
        }

        @Test
        fun `writes audit log for the deleted application`() {
            TestUtils.addMockedRequestIp()
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()
            auditLogRepository.deleteAll()

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME, TestUtils.mockedIp)
                withTarget {
                    prop(AuditLogTarget::id).isEqualTo(hakemus.id.toString())
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.HAKEMUS)
                    hasObjectBefore(hakemus)
                    hasNoObjectAfter()
                }
            }
        }

        @Test
        fun `cancels the application in Allu before deleting when the application is pending in Allu`() {
            val hakemus =
                hakemusFactory.builder().withStatus(ApplicationStatus.PENDING, alluId).save()
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId, ApplicationStatus.PENDING)
            justRun { alluClient.cancel(alluId) }
            every { alluClient.sendSystemComment(alluId, any()) } returns 1324

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(hakemusRepository.findAll()).isEmpty()
            verifySequence {
                alluClient.getApplicationInformation(alluId)
                alluClient.cancel(alluId)
                alluClient.sendSystemComment(alluId, ALLU_USER_CANCELLATION_MSG)
            }
        }

        @Test
        fun `throws an exception when the application is past pending in Allu`() {
            val hakemus =
                hakemusFactory.builder().withStatus(ApplicationStatus.PENDING, alluId).save()
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId, ApplicationStatus.APPROVED)

            val failure = assertFailure { hakemusService.cancelAndDelete(hakemus, USERNAME) }

            failure.all {
                hasClass(HakemusAlreadyProcessingException::class)
                messageContains("id=${hakemus.id}")
                messageContains("alluId=$alluId")
            }
            assertThat(hakemusRepository.findAll()).hasSize(1)
            verifySequence { alluClient.getApplicationInformation(alluId) }
        }

        @Test
        fun `deletes yhteystiedot and yhteyshenkilot but no hankekayttaja`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withNoAlluFields()
                    .hakija()
                    .tyonSuorittaja()
                    .rakennuttaja()
                    .asianhoitaja()
                    .save()

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hakemusyhteystietoRepository.findAll()).isEmpty()
            assertThat(hakemusyhteyshenkiloRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.count())
                .isEqualTo(5) // Hanke founder + one kayttaja for each role
        }

        @Test
        fun `deletes application when application is already cancelled`() {
            val application =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.CANCELLED, alluId)
                    .saveEntity()
            val hakemus = hakemusService.getById(application.id)

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(hakemusRepository.findAll()).isEmpty()
        }
    }

    @Nested
    inner class IsStillPending {
        private val alluId = 123

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class)
        @NullSource
        fun `returns true when alluId is null`(status: ApplicationStatus?) {
            assertThat(hakemusService.isStillPending(null, status)).isTrue()

            verify { alluClient wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
        @NullSource
        fun `returns true when status is pending and allu confirms it`(status: ApplicationStatus?) {
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(
                    status = status ?: ApplicationStatus.PENDING
                )

            assertThat(hakemusService.isStillPending(alluId, status)).isTrue()

            verify { alluClient.getApplicationInformation(alluId) }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
        @NullSource
        fun `returns false when status is pending but status in Allu is handling`(
            status: ApplicationStatus?
        ) {
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(status = ApplicationStatus.HANDLING)

            assertThat(hakemusService.isStillPending(alluId, status)).isFalse()

            verify { alluClient.getApplicationInformation(alluId) }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(
            value = ApplicationStatus::class,
            mode = EnumSource.Mode.EXCLUDE,
            names = ["PENDING", "PENDING_CLIENT"],
        )
        fun `returns false when status is not pending`(status: ApplicationStatus) {
            assertThat(hakemusService.isStillPending(alluId, status)).isFalse()

            verify { alluClient wasNot Called }
        }
    }
}

private fun JohtoselvityshakemusData.setOrdererForCustomer(
    kayttajaId: UUID
): JohtoselvityshakemusData =
    this.copy(customerWithContacts = customerWithContacts!!.setOrderer(kayttajaId))

private fun JohtoselvityshakemusData.setOrdererForContractor(
    kayttajaId: UUID
): JohtoselvityshakemusData =
    this.copy(contractorWithContacts = contractorWithContacts!!.setOrderer(kayttajaId))

private fun JohtoselvityshakemusData.setOrdererForRepresentative(
    kayttajaId: UUID
): JohtoselvityshakemusData =
    this.copy(representativeWithContacts = representativeWithContacts!!.setOrderer(kayttajaId))

private fun KaivuilmoitusData.setOrdererForContractor(kayttajaId: UUID): KaivuilmoitusData =
    this.copy(contractorWithContacts = contractorWithContacts!!.setOrderer(kayttajaId))

private fun KaivuilmoitusData.setOrdererForRepresentative(kayttajaId: UUID): KaivuilmoitusData =
    this.copy(representativeWithContacts = representativeWithContacts!!.setOrderer(kayttajaId))

private fun Hakemusyhteystieto.setOrderer(kayttajaId: UUID): Hakemusyhteystieto {
    val yhteyshenkilot =
        yhteyshenkilot.map { if (it.hankekayttajaId == kayttajaId) it.copy(tilaaja = true) else it }

    return copy(yhteyshenkilot = yhteyshenkilot)
}
