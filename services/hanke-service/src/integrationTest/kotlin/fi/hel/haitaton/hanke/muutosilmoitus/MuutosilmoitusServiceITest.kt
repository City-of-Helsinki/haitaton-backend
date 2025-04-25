package fi.hel.haitaton.hanke.muutosilmoitus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
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
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentRepository
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory.DEFAULT_HHS_PYORALIIKENNE
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory.Companion.withPaperDecisionReceiver
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusAttachmentFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory.Companion.toUpdateRequest
import fi.hel.haitaton.hanke.factory.PaperDecisionReceiverFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.WrongHakemusTypeException
import fi.hel.haitaton.hanke.logging.ALLU_AUDIT_LOG_USERID
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.pdf.getPdfAsText
import fi.hel.haitaton.hanke.pdf.withName
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.taydennys.NoChangesException
import fi.hel.haitaton.hanke.test.Asserts.hasSameGeometryAs
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
import fi.hel.haitaton.hanke.test.resetCustomerIds
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verifySequence
import java.util.UUID
import kotlin.collections.single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired

class MuutosilmoitusServiceITest(
    @Autowired private val muutosilmoitusService: MuutosilmoitusService,
    @Autowired private val attachmentContentService: ApplicationAttachmentContentService,
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val hankeService: HankeService,
    @Autowired private val attachmentFactory: MuutosilmoitusAttachmentFactory,
    @Autowired private val hakemusAttachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
    @Autowired private val attachmentRepository: MuutosilmoitusAttachmentRepository,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val hakemusAttachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val muutosilmoitusRepository: MuutosilmoitusRepository,
    @Autowired private val yhteystietoRepository: MuutosilmoituksenYhteystietoRepository,
    @Autowired private val yhteyshenkiloRepository: MuutosilmoituksenYhteyshenkiloRepository,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val fileClient: MockFileClient,
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
            val muutosilmoitus = muutosilmoitusFactory.builder().withSent().saveEntity()

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

        @Test
        fun `deletes the attachments when deleting a muutosilmoitus`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()
            attachmentFactory.save(muutosilmoitus = muutosilmoitus)
            attachmentFactory.save(muutosilmoitus = muutosilmoitus)
            assertThat(attachmentRepository.findByMuutosilmoitusId(muutosilmoitus.id)).hasSize(2)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(2)

            muutosilmoitusService.delete(muutosilmoitus.id, USERNAME)

            assertThat(muutosilmoitusRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
            assertThat(attachmentRepository.findByMuutosilmoitusId(muutosilmoitus.id)).isEmpty()
        }

        @Test
        fun `deletes all attachment metadata even when deleting attachment content fails`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()
            attachmentFactory.save(muutosilmoitus = muutosilmoitus)
            attachmentFactory.save(muutosilmoitus = muutosilmoitus)
            assertThat(attachmentRepository.findByMuutosilmoitusId(muutosilmoitus.id)).hasSize(2)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(2)
            fileClient.connected = false

            muutosilmoitusService.delete(muutosilmoitus.id, USERNAME)

            fileClient.connected = true
            assertThat(muutosilmoitusRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(2)
            assertThat(attachmentRepository.findByMuutosilmoitusId(muutosilmoitus.id)).isEmpty()
        }
    }

    @Nested
    inner class Send {
        private val startTime = DateFactory.getStartDatetime().minusDays(1)

        @Test
        fun `throws exception when muutosilmoitus is not found`() {
            val failure = assertFailure {
                muutosilmoitusService.send(
                    UUID.fromString("d754f2b7-2e96-4983-bbf8-1e1d34bc0c81"),
                    null,
                    USERNAME,
                )
            }

            failure.all {
                hasClass(MuutosilmoitusNotFoundException::class)
                messageContains("id=d754f2b7-2e96-4983-bbf8-1e1d34bc0c81")
                messageContains("Muutosilmoitus not found")
            }
        }

        @Test
        fun `throws exception when muutosilmoitus has already been sent`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().withSent().save()

            val failure = assertFailure {
                muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)
            }

            failure.all {
                hasClass(MuutosilmoitusAlreadySentException::class)
                messageContains("id=${muutosilmoitus.id}")
                messageContains("Muutosilmoitus is already sent to Allu")
            }
        }

        @Test
        fun `throws exception if the muutosilmoitus is identical to the hakemus`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()

            val failure = assertFailure {
                muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)
            }

            failure.all {
                hasClass(NoChangesException::class)
                messageContains("Not sending a muutosilmoitus without any changes")
                messageContains("id=${muutosilmoitus.id}")
            }
        }

        @Test
        fun `throws exception when the muutosilmoitus fails validation`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().withStartTime(null).save()

            val failure = assertFailure {
                muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)
            }

            failure.all {
                hasClass(InvalidHakemusDataException::class)
                messageContains("Application contains invalid data")
                messageContains("Errors at paths: applicationData.startTime")
            }
        }

        @Test
        fun `sends the data to Allu with list of changed fields`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(
                            Haittojenhallintatyyppi.PYORALIIKENNE to
                                "$DEFAULT_HHS_PYORALIIKENNE. Muutettu."
                        ),
                )
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withStartTime(startTime)
                    .withEndTime(DateFactory.getEndDatetime().plusDays(1))
                    .withConstructionWork(true)
                    .withMaintenanceWork(true)
                    .withWorkDescription("New description")
                    .withCustomerReference("New Reference")
                    .withAreas(listOf(area))
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hanke.hankeTunnus),
                    setOf(
                        InformationRequestFieldKey.START_TIME,
                        InformationRequestFieldKey.END_TIME,
                        InformationRequestFieldKey.OTHER,
                        InformationRequestFieldKey.INVOICING_CUSTOMER,
                        InformationRequestFieldKey.ATTACHMENT,
                    ),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends other as changed field if there is a change to kaivuilmoitus cable reports`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val hankealue = hankeService.loadHankeById(hanke.id)!!.alueet.single()
            val hakemus =
                hakemusFactory
                    .builder(hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields(hankealue)
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withCableReports(listOf("JS2500450", "JS2500451"))
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.OTHER),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends other as changed field if there is a change to kaivuilmoitus placement contracts`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val hankealue = hankeService.loadHankeById(hanke.id)!!.alueet.single()
            val hakemus =
                hakemusFactory
                    .builder(hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields(hankealue)
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withPlacementContracts(listOf("SL2500450", "SL2500451"))
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.OTHER),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends postal address as changed field if there is a change to kaivuilmoitus area street address`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val hankealue = hankeService.loadHankeById(hanke.id)!!.alueet.single()
            val hakemus =
                hakemusFactory
                    .builder(hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields(hankealue)
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hakemusalue = hakemus.hakemusEntityData.areas!!.single() as KaivuilmoitusAlue
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withAreas(listOf(hakemusalue.copy(katuosoite = "New address")))
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.POSTAL_ADDRESS),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends geometry as changed field if there is a change to kaivuilmoitus work area`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    tyoalueet =
                        listOf(
                            ApplicationFactory.createTyoalue(
                                geometry = GeometriaFactory.fourthPolygon()
                            )
                        ),
                )
            val muutosilmoitus =
                muutosilmoitusFactory.builder(hakemus).withAreas(listOf(area)).save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.GEOMETRY),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends attachment as changed field if there are changes only to kaivuilmoitus area properties other than address`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val hankealue = hankeService.loadHankeById(hanke.id)!!.alueet.single()
            val hakemus =
                hakemusFactory
                    .builder(hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields(hankealue)
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hakemusalue = hakemus.hakemusEntityData.areas!!.single() as KaivuilmoitusAlue
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withAreas(
                        listOf(
                            hakemusalue.copy(
                                tyonTarkoitukset = setOf(TyomaaTyyppi.LIIKENNEVALO),
                                meluhaitta = Meluhaitta.SATUNNAINEN_MELUHAITTA,
                                polyhaitta = Polyhaitta.JATKUVA_POLYHAITTA,
                                tarinahaitta = Tarinahaitta.EI_TARINAHAITTAA,
                                kaistahaitta =
                                    VaikutusAutoliikenteenKaistamaariin
                                        .YKSI_AJOSUUNTA_POISTUU_KAYTOSTA,
                                kaistahaittojenPituus =
                                    AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                                lisatiedot = "Uudet lisätiedot",
                            )
                        )
                    )
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends attachment as changed field if there is a change to kaivuilmoitus haittojenhallintasuunnitelma`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(
                            Haittojenhallintatyyppi.PYORALIIKENNE to
                                "$DEFAULT_HHS_PYORALIIKENNE. Täydennetty."
                        ),
                )
            val muutosilmoitus =
                muutosilmoitusFactory.builder(hakemus).withAreas(listOf(area)).save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends the attachments to Allu and adds it to change list`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(),
                )
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withWorkDescription("New description")
                    .withAreas(listOf(area))
                    .save()
            val muuAttachment =
                attachmentFactory
                    .save(
                        muutosilmoitus = muutosilmoitus,
                        attachmentType = ApplicationAttachmentType.MUU,
                    )
                    .toDomain()
            val liikennejarjestely =
                attachmentFactory
                    .save(
                        fileName = "liikenne.pdf",
                        muutosilmoitus = muutosilmoitus,
                        attachmentType = ApplicationAttachmentType.LIIKENNEJARJESTELY,
                    )
                    .toDomain()
            val valtakirja =
                attachmentFactory
                    .save(
                        fileName = "valtakirja.pdf",
                        attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                        muutosilmoitus = muutosilmoitus,
                    )
                    .toDomain()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.addAttachment(any(), eq(muuAttachment.toAlluAttachment(PDF_BYTES)))
                alluClient.addAttachment(any(), eq(liikennejarjestely.toAlluAttachment(PDF_BYTES)))
                alluClient.addAttachment(any(), eq(valtakirja.toAlluAttachment(PDF_BYTES)))
                alluClient.reportChange(
                    hakemus.alluid!!,
                    any(),
                    setOf(InformationRequestFieldKey.OTHER, InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sends other as changed field if there is a change to additional info`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val hankealue = hankeService.loadHankeById(hanke.id)!!.alueet.single()
            val hakemus =
                hakemusFactory
                    .builder(hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields(hankealue)
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withAdditionalInfo("New additional info")
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            verifySequence {
                alluClient.reportChange(
                    hakemus.alluid!!,
                    muutosilmoitus.hakemusData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.OTHER),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `adds the muutosilmoitus attachments to the form data PDF`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hakemusAttachment =
                hakemusAttachmentFactory
                    .save(fileName = "Hakemus-attachment.pdf", application = hakemus)
                    .withContent()
                    .value
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(),
                )
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withWorkDescription("New description")
                    .withAreas(listOf(area))
                    .save()
            val muutosilmoitusAttachment =
                attachmentFactory
                    .save(
                        fileName = "Muutosilmoitus-attachment.pdf",
                        muutosilmoitus = muutosilmoitus,
                    )
                    .toDomain()
            val sentAttachments = mutableListOf<Attachment>()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), capture(sentAttachments)) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            assertThat(getPdfAsText(sentAttachments[1].file)).all {
                contains(hakemusAttachment.fileName)
                contains(muutosilmoitusAttachment.fileName)
            }
            verifySequence {
                alluClient.addAttachment(any(), withName(muutosilmoitusAttachment.fileName))
                alluClient.reportChange(any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `sets the sent field`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(),
                )
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withWorkDescription("New description")
                    .withAreas(listOf(area))
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            val updatedEntity = muutosilmoitusRepository.getReferenceById(muutosilmoitus.id)
            assertThat(updatedEntity.sent).isNotNull().isRecent()
            verifySequence {
                alluClient.reportChange(any(), any(), setOf(InformationRequestFieldKey.OTHER))
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `returns the associated hakemus`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(),
                )
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withWorkDescription("New description")
                    .withAreas(listOf(area))
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }

            val response = muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            assertThat(response).isEqualTo(hakemus.toHakemus())
            verifySequence {
                alluClient.reportChange(any(), any(), setOf(InformationRequestFieldKey.OTHER))
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }

        @Test
        fun `saves audit logs when request decision on paper`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(),
                )
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withWorkDescription("New description")
                    .withAreas(listOf(area))
                    .save()
            val paperDecisionReceiver = PaperDecisionReceiverFactory.default
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.sendSystemComment(hakemus.alluid!!, any()) } returns 4
            auditLogRepository.deleteAll()

            muutosilmoitusService.send(muutosilmoitus.id, paperDecisionReceiver, USERNAME)

            val expectedDataAfter =
                muutosilmoitus.hakemusData.withPaperDecisionReceiver(paperDecisionReceiver)
            val createdLogs = auditLogRepository.findByType(ObjectType.MUUTOSILMOITUS)
            assertThat(createdLogs).single().isSuccess(Operation.UPDATE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasObjectBefore(muutosilmoitus)
                    hasObjectAfter(muutosilmoitus.copy(hakemusData = expectedDataAfter))
                }
            }
            verifySequence {
                alluClient.reportChange(any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.sendSystemComment(hakemus.alluid!!, PAPER_DECISION_MSG)
            }
        }

        @Test
        fun `clears paper decision when it's null`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .withPaperReceiver()
                    .saveEntity()
            val hanke = hankeService.loadHankeById(hakemus.hanke.id)!!
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!,
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(),
                )
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withWorkDescription("New description")
                    .withAreas(listOf(area))
                    .save()
            justRun { alluClient.reportChange(any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            auditLogRepository.deleteAll()

            muutosilmoitusService.send(muutosilmoitus.id, null, USERNAME)

            val expectedDataAfter = muutosilmoitus.hakemusData.withPaperDecisionReceiver(null)
            val createdLogs = auditLogRepository.findByType(ObjectType.MUUTOSILMOITUS)
            assertThat(createdLogs).single().isSuccess(Operation.UPDATE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasObjectBefore(muutosilmoitus)
                    hasObjectAfter(muutosilmoitus.copy(hakemusData = expectedDataAfter))
                }
            }
            val updatedMuutosilmoitus = muutosilmoitusRepository.findAll().single()
            assertThat(updatedMuutosilmoitus.hakemusData.paperDecisionReceiver).isNull()
            verifySequence {
                alluClient.reportChange(any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
            }
        }
    }

    @Nested
    inner class MergeMuutosilmoitusToHakemusIfItExists {
        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `doesn't change the hakemus when a muutosilmoitus doesn't exist`(
            applicationType: ApplicationType
        ) {
            val hakemus = hakemusFactory.builder(applicationType).withMandatoryFields().saveEntity()
            val originalHakemus = hakemus.toHakemus()

            muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(hakemus)

            val updatedHakemus = hakemusService.getById(hakemus.id)
            assertThat(updatedHakemus).isEqualTo(originalHakemus)
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `doesn't change the hakemus when the muutosilmoitus hasn't been sent`(
            applicationType: ApplicationType
        ) {
            val hakemus = hakemusFactory.builder(applicationType).withMandatoryFields().saveEntity()
            val originalHakemus = hakemus.toHakemus()
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withEndTime(originalHakemus.applicationData.endTime!!.plusDays(1))
                    .withSent(null)
                    .save()

            muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(hakemus)

            val updatedHakemus = hakemusService.getById(hakemus.id)
            assertThat(updatedHakemus).isEqualTo(originalHakemus)
            assertThat(muutosilmoitusService.find(hakemus.id)).isEqualTo(muutosilmoitus)
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `merges data from the muutosilmoitus when the muutosilmoitus has been sent`(
            applicationType: ApplicationType
        ) {
            val hakemus = hakemusFactory.builder(applicationType).withMandatoryFields().saveEntity()
            assertThat(hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]).isNull()
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withStartTime(hakemus.hakemusEntityData.startTime!!.minusDays(1))
                    .rakennuttaja()
                    .withSent()
                    .save()

            muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(hakemus)

            val updatedHakemus = hakemusService.getById(hakemus.id)
            assertThat(updatedHakemus.applicationData.startTime)
                .isEqualTo(muutosilmoitus.hakemusData.startTime)
            val hakemusRakennuttaja =
                updatedHakemus.applicationData.yhteystiedot().first {
                    it.rooli == ApplicationContactType.RAKENNUTTAJA
                }
            val muutosilmoitusRakennuttaja =
                muutosilmoitus.hakemusData.yhteystiedot().first {
                    it.rooli == ApplicationContactType.RAKENNUTTAJA
                }
            assertThat(hakemusRakennuttaja.nimi).isEqualTo(muutosilmoitusRakennuttaja.nimi)
            assertThat(hakemusRakennuttaja.yhteyshenkilot.single().hankekayttajaId)
                .isEqualTo(muutosilmoitusRakennuttaja.yhteyshenkilot.single().hankekayttajaId)
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `deletes the merged muutosilmoitus and logs the deletion`(
            applicationType: ApplicationType
        ) {
            val hakemus = hakemusFactory.builder(applicationType).withMandatoryFields().saveEntity()
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(hakemus)
                    .withStartTime(hakemus.hakemusEntityData.startTime!!.minusDays(1))
                    .withSent()
                    .save()
            auditLogRepository.deleteAll()

            muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(hakemus)

            assertThat(muutosilmoitusRepository.findAll()).isEmpty()
            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.DELETE) {
                hasServiceActor(ALLU_AUDIT_LOG_USERID)
                withTarget {
                    prop(AuditLogTarget::id).isEqualTo(muutosilmoitus.id.toString())
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.MUUTOSILMOITUS)
                    hasObjectBefore(muutosilmoitus)
                    hasNoObjectAfter()
                }
            }
        }

        @Test
        fun `transfers attachments from muutosilmoitus to hakemus`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).withSent().save()
            val attachment = attachmentFactory.save(muutosilmoitus = muutosilmoitus).toDomain()

            muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(hakemus)

            assertThat(attachmentRepository.findAll()).isEmpty()
            val hakemusAttachment =
                hakemusAttachmentRepository.findByApplicationId(hakemus.id).single().toDomain()
            assertThat(hakemusAttachment).all {
                prop(ApplicationAttachmentMetadata::fileName).isEqualTo(attachment.fileName)
                prop(ApplicationAttachmentMetadata::contentType).isEqualTo(attachment.contentType)
                prop(ApplicationAttachmentMetadata::size).isEqualTo(attachment.size)
                prop(ApplicationAttachmentMetadata::blobLocation).isEqualTo(attachment.blobLocation)
                prop(ApplicationAttachmentMetadata::createdByUserId)
                    .isEqualTo(attachment.createdByUserId)
                prop(ApplicationAttachmentMetadata::createdAt).isEqualTo(attachment.createdAt)
                prop(ApplicationAttachmentMetadata::attachmentType)
                    .isEqualTo(attachment.attachmentType)
            }
            assertThat(attachmentContentService.find(hakemusAttachment)).isEqualTo(PDF_BYTES)
        }
    }
}
