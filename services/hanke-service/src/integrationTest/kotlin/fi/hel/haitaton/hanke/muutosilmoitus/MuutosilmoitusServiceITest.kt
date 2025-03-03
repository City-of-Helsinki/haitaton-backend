package fi.hel.haitaton.hanke.muutosilmoitus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory.Companion.toUpdateRequest
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.WrongHakemusTypeException
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.test.Asserts.hasSameGeometryAs
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasTargetType
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.test.resetCustomerIds
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.jpa.domain.AbstractPersistable_.id

class MuutosilmoitusServiceITest(
    @Autowired private val muutosilmoitusService: MuutosilmoitusService,
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val hankeService: HankeService,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val muutosilmoitusRepository: MuutosilmoitusRepository,
    @Autowired private val yhteystietoRepository: MuutosilmoituksenYhteystietoRepository,
    @Autowired private val yhteyshenkiloRepository: MuutosilmoituksenYhteyshenkiloRepository,
    @Autowired private val alluClient: AlluClient,
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
    inner class Find {
        @Test
        fun `returns null when hakemus not found`() {
            val result = muutosilmoitusService.find(411)

            assertThat(result).isNull()
        }

        @Test
        fun `returns null when muutosilmoitus not found`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .save()

            val result = muutosilmoitusService.find(hakemus.id)

            assertThat(result).isNull()
        }

        @Test
        fun `returns muutosilmoitus when it exists`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .save()
            val muutosilmoitus = muutosilmoitusService.create(hakemus.id, USERNAME)

            val result = muutosilmoitusService.find(hakemus.id)

            assertThat(result).isNotNull().isEqualTo(muutosilmoitus)
        }
    }

    @Nested
    inner class Create {
        private fun builder() =
            hakemusFactory
                .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                .withMandatoryFields()
                .withStatus(ApplicationStatus.DECISION)

        @Test
        fun `throws exception when there's no hakemus`() {
            val failure = assertFailure { muutosilmoitusService.create(414, USERNAME) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("Hakemus not found with id 414")
            }
        }

        @Test
        fun `throws exception when the hakemus has the wrong status`() {
            val hakemus = builder().withStatus(ApplicationStatus.HANDLING).save()

            val failure = assertFailure { muutosilmoitusService.create(hakemus.id, USERNAME) }

            failure.all {
                hasClass(HakemusInWrongStatusException::class)
                messageContains("Hakemus is in the wrong status for this operation")
                messageContains("status=HANDLING")
                messageContains("allowed statuses=DECISION, OPERATIONAL_CONDITION")
                messageContains("id=${hakemus.id}")
            }
        }

        @ParameterizedTest
        @EnumSource(
            value = ApplicationType::class,
            names = ["EXCAVATION_NOTIFICATION"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `throws exception when the hakemus not a kaivuilmoitus`(
            applicationType: ApplicationType
        ) {
            val hakemus =
                hakemusFactory
                    .builder(applicationType)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .save()

            val failure = assertFailure { muutosilmoitusService.create(hakemus.id, USERNAME) }

            failure.all {
                hasClass(WrongHakemusTypeException::class)
                messageContains("Wrong application type for this action")
                messageContains("type=CABLE_REPORT")
                messageContains("allowed types=EXCAVATION_NOTIFICATION")
                messageContains("id=${hakemus.id}")
            }
        }

        @Test
        fun `creates the muutosilmoitus in the database`() {
            val hakemus = builder().withMandatoryFields().save()

            muutosilmoitusService.create(hakemus.id, USERNAME)

            val muutosilmoitus = muutosilmoitusService.find(hakemus.id)
            val muutosilmoitusData = muutosilmoitus?.hakemusData?.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(muutosilmoitus)
                .isNotNull()
                .prop(Muutosilmoitus::hakemusId)
                .isEqualTo(hakemus.id)
            assertThat(muutosilmoitusData).isEqualTo(hakemusData)
        }

        @Test
        fun `returns the created muutosilmoitus`() {
            val hakemus = builder().save()

            val result = muutosilmoitusService.create(hakemus.id, USERNAME)

            val muutosilmoitusData = result.hakemusData.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(result).prop(Muutosilmoitus::hakemusId).isEqualTo(hakemus.id)
            assertThat(muutosilmoitusData).isEqualTo(hakemusData)
        }

        @Test
        fun `writes the created muutosilmoitus to the audit log`() {
            val hakemus = builder().save()
            auditLogRepository.deleteAll()

            val result = muutosilmoitusService.create(hakemus.id, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.CREATE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasTargetType(ObjectType.MUUTOSILMOITUS)
                    hasId(result.id)
                    hasNoObjectBefore()
                    hasObjectAfter(result)
                }
            }
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `throws exception when muutosilmoitus is not found`() {
            val id = UUID.fromString("51329a46-60a5-429d-a86e-b81ecbe26e60")

            val failure = assertFailure { muutosilmoitusService.delete(id, USERNAME) }

            failure.all {
                hasClass(MuutosilmoitusNotFoundException::class)
                messageContains("Muutosilmoitus not found")
                messageContains("id=$id")
            }
        }

        @Test
        fun `throws exception when muutosilmoitus has already been sent`() {
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder()
                    .withSent(DateFactory.getStartDatetime().toOffsetDateTime())
                    .saveEntity()

            val failure = assertFailure {
                muutosilmoitusService.delete(muutosilmoitus.id, USERNAME)
            }

            failure.all {
                hasClass(MuutosilmoitusAlreadySentException::class)
                messageContains("Muutosilmoitus is already sent to Allu")
                messageContains("id=${muutosilmoitus.id}")
            }
        }

        @Test
        fun `deletes muutosilmoitus`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().saveEntity()
            assertThat(muutosilmoitusRepository.findAll()).hasSize(1)

            muutosilmoitusService.delete(muutosilmoitus.id, USERNAME)

            assertThat(muutosilmoitusRepository.findAll()).isEmpty()
        }

        @Test
        fun `writes the deleted muutosilmoitus to audit log`() {
            TestUtils.addMockedRequestIp()
            val muutosilmoitus = muutosilmoitusFactory.builder().save()
            auditLogRepository.deleteAll()

            muutosilmoitusService.delete(muutosilmoitus.id, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME, TestUtils.mockedIp)
                withTarget {
                    prop(AuditLogTarget::id).isEqualTo(muutosilmoitus.id.toString())
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.MUUTOSILMOITUS)
                    hasObjectBefore(muutosilmoitus)
                    hasNoObjectAfter()
                }
            }
        }

        @Test
        fun `deletes yhteystiedot and yhteyshenkilot but no hankekayttaja`() {
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder()
                    .hakija()
                    .rakennuttaja()
                    .tyonSuorittaja()
                    .asianhoitaja()
                    .saveEntity()
            assertThat(muutosilmoitusRepository.findAll()).hasSize(1)
            assertThat(yhteystietoRepository.findAll()).hasSize(4)
            assertThat(yhteyshenkiloRepository.findAll()).hasSize(4)
            assertThat(hankekayttajaRepository.count())
                .isEqualTo(5) // Hanke founder + one kayttaja for each role

            muutosilmoitusService.delete(muutosilmoitus.id, USERNAME)

            assertThat(muutosilmoitusRepository.findAll()).isEmpty()
            assertThat(yhteystietoRepository.findAll()).isEmpty()
            assertThat(yhteyshenkiloRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.count())
                .isEqualTo(5) // Hanke founder + one kayttaja for each role
        }

        @Test
        fun `resets the hankealueet when the hanke is a generated one`() {
            val hakemusEntity =
                hakemusFactory.builderWithGeneratedHanke().withMandatoryFields().saveEntity()
            val hakemus = hakemusService.getById(hakemusEntity.id)
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val newAreas =
                listOf(
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.polygon()
                    ),
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.thirdPolygon()
                    ),
                )
            val update = muutosilmoitus.toUpdateRequest() as JohtoselvityshakemusUpdateRequest
            muutosilmoitusService.update(muutosilmoitus.id, update.copy(areas = newAreas), USERNAME)
            assertThat(hankeService.loadHanke(hakemus.hankeTunnus)!!).hasSameGeometryAs(newAreas)

            muutosilmoitusService.delete(muutosilmoitus.id, USERNAME)

            assertThat(hankeService.loadHanke(hakemus.hankeTunnus)!!)
                .hasSameGeometryAs((hakemus.applicationData as JohtoselvityshakemusData).areas!!)
        }
    }
}
