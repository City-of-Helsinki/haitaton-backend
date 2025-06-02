package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory.DEFAULT_HHS_PYORALIIKENNE
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory.Companion.toUpdateRequest
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.Hakemusalue
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.ALLU_AUDIT_LOG_USERID
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.pdf.withName
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.test.AlluException
import fi.hel.haitaton.hanke.test.Asserts.hasNullNode
import fi.hel.haitaton.hanke.test.Asserts.hasTextNode
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBeforeJson
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
import io.mockk.slot
import io.mockk.verifySequence
import java.util.UUID
import org.geojson.Polygon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TaydennysServiceITest(
    @Autowired private val taydennysService: TaydennysService,
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val hankeService: HankeService,
    @Autowired private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    @Autowired private val taydennysRepository: TaydennysRepository,
    @Autowired private val taydennysyhteystietoRepository: TaydennysyhteystietoRepository,
    @Autowired private val taydennysyhteyshenkiloRepository: TaydennysyhteyshenkiloRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val attachmentRepository: TaydennysAttachmentRepository,
    @Autowired private val hakemusAttachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val attachmentContentService: ApplicationAttachmentContentService,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val taydennyspyyntoFactory: TaydennyspyyntoFactory,
    @Autowired private val taydennysFactory: TaydennysFactory,
    @Autowired private val attachmentFactory: TaydennysAttachmentFactory,
    @Autowired private val auditLogRepository: AuditLogRepository,
) : IntegrationTest() {
    private val alluId = 3464

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
    inner class FindTaydennyspyynto {

        @Test
        fun `returns null when taydennyspyynto doesn't exist`() {
            val result = taydennysService.findTaydennyspyynto(1L)

            assertThat(result).isNull()
        }

        @Test
        fun `returns taydennyspyynto when it exists`() {
            val hakemus = hakemusFactory.builder().withStatus().save()
            val taydennyspyynto = taydennyspyyntoFactory.save(hakemus.id)

            val result = taydennysService.findTaydennyspyynto(hakemus.id)

            assertThat(result).isEqualTo(taydennyspyynto)
        }
    }

    @Nested
    inner class FindTaydennys {
        @Test
        fun `returns null when taydennys doesn't exist`() {
            val result = taydennysService.findTaydennys(1L)

            assertThat(result).isNull()
        }

        @Test
        fun `returns taydennys when it exists`() {
            val hakemus = hakemusFactory.builder().withStatus().save()
            val taydennys = taydennysFactory.save(applicationId = hakemus.id)

            val result = taydennysService.findTaydennys(hakemus.id)

            assertThat(result).isEqualTo(taydennys)
        }
    }

    @Nested
    inner class SaveTaydennyspyyntoFromAllu {

        @Test
        fun `saves taydennyspyynto when Allu has one for the application`() {
            val hakemus = hakemusFactory.builder().withStatus(alluId = alluId).save()
            val kentat =
                listOf(
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.CUSTOMER,
                        "Customer is missing",
                    ),
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.ATTACHMENT,
                        "Needs a letter of attorney",
                    ),
                )
            every { alluClient.getInformationRequest(hakemus.alluid!!) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId, fields = kentat)

            taydennysService.saveTaydennyspyyntoFromAllu(hakemus)

            assertThat(taydennyspyyntoRepository.findAll()).single().all {
                prop(TaydennyspyyntoEntity::alluId)
                    .isEqualTo(AlluFactory.DEFAULT_INFORMATION_REQUEST_ID)
                prop(TaydennyspyyntoEntity::applicationId).isEqualTo(hakemus.id)
                prop(TaydennyspyyntoEntity::kentat)
                    .containsOnly(
                        InformationRequestFieldKey.CUSTOMER to "Customer is missing",
                        InformationRequestFieldKey.ATTACHMENT to "Needs a letter of attorney",
                    )
            }
            verifySequence { alluClient.getInformationRequest(hakemus.alluid!!) }
        }

        @Test
        fun `returns taydennyspyynto when Allu has one for the application`() {
            val hakemus = hakemusFactory.builder().withStatus(alluId = alluId).save()
            val kentat =
                listOf(
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.GEOMETRY,
                        "Check the areas",
                    ),
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.START_TIME,
                        "Too far in history",
                    ),
                )
            every { alluClient.getInformationRequest(hakemus.alluid!!) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId, fields = kentat)

            val response = taydennysService.saveTaydennyspyyntoFromAllu(hakemus)

            assertThat(response)
                .isNotNull()
                .prop(Taydennyspyynto::kentat)
                .containsOnly(
                    InformationRequestFieldKey.GEOMETRY to "Check the areas",
                    InformationRequestFieldKey.START_TIME to "Too far in history",
                )
            verifySequence { alluClient.getInformationRequest(hakemus.alluid!!) }
        }

        @Test
        fun `returns null when Allu doesn't have the taydennyspyynto`() {
            val hakemus = hakemusFactory.builder().withStatus(alluId = alluId).save()
            every { alluClient.getInformationRequest(hakemus.alluid!!) } returns null

            val response = taydennysService.saveTaydennyspyyntoFromAllu(hakemus)

            assertThat(response).isNull()
            verifySequence { alluClient.getInformationRequest(hakemus.alluid!!) }
        }
    }

    @Nested
    inner class Create {
        private fun builder() =
            hakemusFactory
                .builder()
                .withMandatoryFields()
                .withStatus(ApplicationStatus.WAITING_INFORMATION)

        @Test
        fun `throws exception when there's no hakemus`() {
            val failure = assertFailure { taydennysService.create(414, USERNAME) }

            failure.all {
                hasClass(NoTaydennyspyyntoException::class)
                messageContains("Hakemus doesn't have an open täydennyspyyntö")
                messageContains("hakemusId=414")
            }
        }

        @Test
        fun `throws exception when there's no taydennyspyynto on the hakemus`() {
            val hakemus = builder().save()

            val failure = assertFailure { taydennysService.create(hakemus.id, USERNAME) }

            failure.all {
                hasClass(NoTaydennyspyyntoException::class)
                messageContains("Hakemus doesn't have an open täydennyspyyntö")
                messageContains("hakemusId=${hakemus.id}")
            }
        }

        @Test
        fun `throws exception when the hakemus has the wrong status`() {
            val hakemus = builder().withStatus(ApplicationStatus.HANDLING).save()
            taydennyspyyntoFactory.save(hakemus.id)

            val failure = assertFailure { taydennysService.create(hakemus.id, USERNAME) }

            failure.all {
                hasClass(HakemusInWrongStatusException::class)
                messageContains("Hakemus is in the wrong status for this operation")
                messageContains("allowed statuses=WAITING_INFORMATION")
                messageContains("id=${hakemus.id}")
            }
        }

        @Test
        fun `creates the taydennys in the database`() {
            val hakemus = builder().save()
            val taydennyspyynto = taydennyspyyntoFactory.save(hakemus.id)

            taydennysService.create(hakemus.id, USERNAME)

            val taydennys = taydennysService.findTaydennys(hakemus.id)
            val taydennysData = taydennys?.hakemusData?.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(taydennys)
                .isNotNull()
                .prop(Taydennys::taydennyspyyntoId)
                .isEqualTo(taydennyspyynto.id)
            assertThat(taydennysData).isEqualTo(hakemusData)
        }

        @Test
        fun `returns the created taydennys`() {
            val hakemus = builder().save()
            val taydennyspyynto = taydennyspyyntoFactory.save(hakemus.id)

            val response = taydennysService.create(hakemus.id, USERNAME)

            val taydennysData = response.hakemusData.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(response).prop(Taydennys::taydennyspyyntoId).isEqualTo(taydennyspyynto.id)
            assertThat(taydennysData).isEqualTo(hakemusData)
        }

        @Test
        fun `writes the created taydennys to the audit log`() {
            val hakemus = builder().save()
            taydennyspyyntoFactory.save(hakemus.id)
            auditLogRepository.deleteAll()

            val result = taydennysService.create(hakemus.id, USERNAME)

            assertThat(auditLogRepository.findByType(ObjectType.TAYDENNYS)).single().isSuccess(
                Operation.CREATE
            ) {
                hasUserActor(USERNAME)
                withTarget {
                    hasTargetType(ObjectType.TAYDENNYS)
                    hasId(result.id)
                    hasNoObjectBefore()
                    hasObjectAfter(result)
                }
            }
        }
    }

    @Nested
    inner class RemoveTaydennyspyyntoIfItExists {
        @Test
        fun `logs removing the taydennyspyynto and taydennys to audit logs`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, 42)
                    .saveEntity()
            val taydennys = taydennysFactory.save(applicationId = hakemus.id)
            val taydennyspyynto = taydennyspyyntoRepository.findByApplicationId(hakemus.id)!!
            auditLogRepository.deleteAll()

            taydennysService.removeTaydennyspyyntoIfItExists(hakemus)

            val taydennysLogs = auditLogRepository.findByType(ObjectType.TAYDENNYS)
            assertThat(taydennysLogs).single().isSuccess(Operation.DELETE) {
                hasServiceActor("Allu")
                withTarget {
                    hasNoObjectAfter()
                    hasObjectBefore<Taydennys> {
                        prop(Taydennys::id).isEqualTo(taydennys.id)
                        prop(Taydennys::hakemusId).isEqualTo(hakemus.id)
                    }
                }
            }
            val taydennyspyyntoLogs = auditLogRepository.findByType(ObjectType.TAYDENNYSPYYNTO)
            assertThat(taydennyspyyntoLogs).single().isSuccess(Operation.DELETE) {
                hasServiceActor("Allu")
                withTarget {
                    hasNoObjectAfter()
                    hasObjectBefore<Taydennyspyynto> {
                        prop(Taydennyspyynto::id).isEqualTo(taydennyspyynto.id)
                        prop(Taydennyspyynto::hakemusId).isEqualTo(taydennyspyynto.applicationId)
                    }
                }
            }
        }

        @Test
        fun `removes and logs the taydennyspyynto when there is no taydennys`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, 42)
                    .saveEntity()
            val taydennyspyynto = taydennyspyyntoFactory.save(hakemus.id)
            auditLogRepository.deleteAll()

            taydennysService.removeTaydennyspyyntoIfItExists(hakemus)

            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            val taydennyspyyntoLogs = auditLogRepository.findAll()
            assertThat(taydennyspyyntoLogs).single().isSuccess(Operation.DELETE) {
                hasServiceActor("Allu")
                withTarget {
                    hasTargetType(ObjectType.TAYDENNYSPYYNTO)
                    hasNoObjectAfter()
                    hasObjectBefore(taydennyspyynto)
                }
            }
        }

        @Test
        fun `removes taydennys attachments from Blob storage when the new status is HANDLING`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, 42)
                    .saveEntity()
            val taydennys = taydennysFactory.save(applicationId = hakemus.id)
            attachmentFactory.save(taydennys = taydennys).withContent()
            attachmentFactory.save(taydennys = taydennys).withContent()
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(2)

            taydennysService.removeTaydennyspyyntoIfItExists(hakemus)

            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
            assertThat(attachmentRepository.findAll()).isEmpty()
            assertThat(taydennysRepository.findAll()).isEmpty()
            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
        }

        @Test
        fun `resets the hankealueet when the hanke is a generated one`() {
            val hakemusEntity =
                hakemusFactory
                    .builderWithGeneratedHanke()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, 42)
                    .withMandatoryFields()
                    .saveEntity()
            val hakemus = hakemusService.getById(hakemusEntity.id)
            val taydennys = taydennysFactory.builder(hakemus.id, hakemus.hankeId).save()
            val newAreas =
                listOf(
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.polygon()
                    ),
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.thirdPolygon()
                    ),
                )
            val update = taydennys.toUpdateRequest() as JohtoselvityshakemusUpdateRequest
            taydennysService.updateTaydennys(taydennys.id, update.copy(areas = newAreas), USERNAME)
            assertHankeHasSameGeometryAsHakemus(hakemus.hankeTunnus, newAreas)

            taydennysService.removeTaydennyspyyntoIfItExists(hakemusEntity)

            assertHankeHasSameGeometryAsHakemus(
                hakemus.hankeTunnus,
                (hakemus.applicationData as JohtoselvityshakemusData).areas!!,
            )
        }
    }

    @Nested
    inner class SendTaydennys {
        private val startTime = DateFactory.getStartDatetime().minusDays(1)

        @Test
        fun `throws exception when taydennys is not found`() {
            val failure = assertFailure {
                taydennysService.sendTaydennys(
                    UUID.fromString("d754f2b7-2e96-4983-bbf8-1e1d34bc0c81"),
                    USERNAME,
                )
            }

            failure.all {
                hasClass(TaydennysNotFoundException::class)
                messageContains("id=d754f2b7-2e96-4983-bbf8-1e1d34bc0c81")
                messageContains("Täydennys not found")
            }
        }

        @Test
        fun `throws exception if the taydennys is identical to the hakemus`() {
            val taydennys = taydennysFactory.builder().save()

            val failure = assertFailure { taydennysService.sendTaydennys(taydennys.id, USERNAME) }

            failure.all {
                hasClass(NoChangesException::class)
                messageContains("Not sending a täydennys without any changes")
                messageContains("id=${taydennys.id}")
            }
        }

        @Test
        fun `throws exception if the hakemus is not in WAITING_INFORMATION`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.INFORMATION_RECEIVED)
                    .saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).withStartTime(startTime).save()

            val failure = assertFailure { taydennysService.sendTaydennys(taydennys.id, USERNAME) }

            failure.all {
                hasClass(HakemusInWrongStatusException::class)
                messageContains("Hakemus is in the wrong status for this operation")
                messageContains("status=INFORMATION_RECEIVED")
                messageContains("allowed statuses=WAITING_INFORMATION")
            }
        }

        @Test
        fun `throws exception when the taydennys fails validation`() {
            val taydennys = taydennysFactory.builder().withStartTime(null).save()

            val failure = assertFailure { taydennysService.sendTaydennys(taydennys.id, USERNAME) }

            failure.all {
                hasClass(InvalidHakemusDataException::class)
                messageContains("Application contains invalid data")
                messageContains("Errors at paths: applicationData.startTime")
            }
        }

        @Test
        fun `sends the data to Allu with list of changed fields`() {
            val taydennys =
                taydennysFactory
                    .builder()
                    .withStartTime(startTime)
                    .withEndTime(DateFactory.getEndDatetime().plusDays(1))
                    .withConstructionWork(true)
                    .withMaintenanceWork(true)
                    .withWorkDescription("New description")
                    .withStreetAddress("New street")
                    .withAreas(
                        listOf(
                            ApplicationFactory.createCableReportApplicationArea(),
                            ApplicationFactory.createCableReportApplicationArea(),
                        )
                    )
                    .save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val hakemus = hakemusService.getById(taydennyspyynto.applicationId)
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hankeTunnus),
                    setOf(
                        InformationRequestFieldKey.START_TIME,
                        InformationRequestFieldKey.END_TIME,
                        InformationRequestFieldKey.OTHER,
                        InformationRequestFieldKey.POSTAL_ADDRESS,
                        InformationRequestFieldKey.GEOMETRY,
                    ),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
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
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .saveEntity()
            val taydennys =
                taydennysFactory
                    .builder(hakemus, alluId)
                    .withCableReports(listOf("JS2500001"))
                    .save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.OTHER),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
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
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .saveEntity()
            val taydennys =
                taydennysFactory
                    .builder(hakemus)
                    .withPlacementContracts(listOf("SL2500450", "SL2500451"))
                    .save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.OTHER),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
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
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .saveEntity()
            val hakemusalue =
                hakemusService.getById(hakemus.id).applicationData.areas!!.single()
                    as KaivuilmoitusAlue
            val taydennys =
                taydennysFactory
                    .builder(hakemus)
                    .withAreas(listOf(hakemusalue.copy(katuosoite = "New address")))
                    .save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.POSTAL_ADDRESS),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `sends geometry as changed field if there is a change to kaivuilmoitus work area`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
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
            val taydennys = taydennysFactory.builder(hakemus, alluId).withAreas(listOf(area)).save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.GEOMETRY),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
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
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .saveEntity()
            val hakemusalue =
                hakemusService.getById(hakemus.id).applicationData.areas!!.single()
                    as KaivuilmoitusAlue
            val taydennys =
                taydennysFactory
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
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `sends attachment as changed field if there is a change to kaivuilmoitus haittojenhallintasuunnitelma`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
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
            val taydennys = taydennysFactory.builder(hakemus, alluId).withAreas(listOf(area)).save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
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
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .saveEntity()
            val taydennys =
                taydennysFactory.builder(hakemus, alluId).withAdditionalInfo("Lisätietoja").save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hanke.hankeTunnus),
                    setOf(InformationRequestFieldKey.OTHER),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(any(), withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `sends the attachments to Allu`() {
            val taydennys = taydennysFactory.builder().save()
            val attachment =
                attachmentFactory.save(taydennys = taydennys).withContent().value.toDomain()
            val liikennejarjestely =
                attachmentFactory
                    .save(
                        fileName = "liikennejärjestely.pdf",
                        taydennys = taydennys,
                        attachmentType = ApplicationAttachmentType.LIIKENNEJARJESTELY,
                    )
                    .withContent()
                    .value
                    .toDomain()
            val valtakirja =
                attachmentFactory
                    .save(
                        fileName = "valtakirja.pdf",
                        taydennys = taydennys,
                        attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                    )
                    .withContent()
                    .value
                    .toDomain()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val hakemus = hakemusService.getById(taydennyspyynto.applicationId)
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.addAttachment(any(), eq(attachment.toAlluAttachment(PDF_BYTES)))
                alluClient.addAttachment(any(), eq(liikennejarjestely.toAlluAttachment(PDF_BYTES)))
                alluClient.addAttachment(any(), eq(valtakirja.toAlluAttachment(PDF_BYTES)))
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hankeTunnus),
                    setOf(InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `adds attachment to changed fields when taydennys has attachments`() {
            val taydennys = taydennysFactory.builder().withWorkDescription("New description").save()
            val attachment =
                attachmentFactory.save(taydennys = taydennys).withContent().value.toDomain()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val hakemus = hakemusService.getById(taydennyspyynto.applicationId)
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.addAttachment(any(), eq(attachment.toAlluAttachment(PDF_BYTES)))
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    taydennys.hakemusData.toAlluData(hakemus.hankeTunnus),
                    setOf(InformationRequestFieldKey.OTHER, InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `transfers attachments from taydennys to hakemus`() {
            val taydennys = taydennysFactory.builder().save()
            val attachment =
                attachmentFactory.save(taydennys = taydennys).withContent().value.toDomain()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val hakemus = hakemusService.getById(taydennyspyynto.applicationId)
            val updatedTaydennysData = taydennysService.findTaydennys(hakemus.id)!!.hakemusData
            assertThat(attachmentRepository.existsById(attachment.id)).isTrue()
            assertThat(hakemusAttachmentRepository.findAll()).isEmpty()
            assertThat(attachmentContentService.find(attachment.blobLocation, attachment.id))
                .isEqualTo(PDF_BYTES)
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.addAttachment(any(), withName(FILE_NAME_PDF))
                alluClient.respondToInformationRequest(
                    hakemus.alluid!!,
                    taydennyspyynto.alluId,
                    updatedTaydennysData.toAlluData(hakemus.hankeTunnus),
                    setOf(InformationRequestFieldKey.ATTACHMENT),
                )
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
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
                prop(ApplicationAttachmentMetadata::applicationId).isEqualTo(hakemus.id)
                prop(ApplicationAttachmentMetadata::attachmentType)
                    .isEqualTo(attachment.attachmentType)
            }
            assertThat(attachmentContentService.find(hakemusAttachment)).isEqualTo(PDF_BYTES)
        }

        @Test
        fun `sends the updated form data as a PDF`() {
            val taydennys = taydennysFactory.builder().withStartTime(startTime).save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val hakemus = hakemusService.getById(taydennyspyynto.applicationId)
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            val attachmentCapturingSlot = slot<Attachment>()
            justRun { alluClient.addAttachment(hakemus.alluid!!, capture(attachmentCapturingSlot)) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            val attachment = attachmentCapturingSlot.captured
            assertThat(attachment.metadata).all {
                prop(AttachmentMetadata::name).isEqualTo("haitaton-form-data-taydennys.pdf")
                prop(AttachmentMetadata::description)
                    .isNotNull()
                    .contains("Taydennys form data from Haitaton, dated ")
            }
            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(hakemus.alluid!!, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `logs personal info sent to Allu to disclosure logs`() {
            val hakija = HakemusyhteystietoFactory.createPerson()
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION)
                    .hakija(hakija)
                    .saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).withStartTime(startTime).save()
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)
            auditLogRepository.deleteAll()

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            val customerLogs = auditLogRepository.findByType(ObjectType.ALLU_CUSTOMER)
            assertThat(customerLogs).single().isSuccess(Operation.READ) {
                hasServiceActor(ALLU_AUDIT_LOG_USERID)
                withTarget {
                    hasNoObjectAfter()
                    hasObjectBeforeJson {
                        hasTextNode("role").isEqualTo("HAKIJA")
                        hasTextNode("type").isEqualTo("PERSON")
                        hasTextNode("name").isEqualTo(HakemusyhteystietoFactory.DEFAULT_PERSON_NIMI)
                        hasTextNode("email")
                            .isEqualTo(HakemusyhteystietoFactory.DEFAULT_PERSON_SAHKOPOSTI)
                        hasTextNode("phone")
                            .isEqualTo(HakemusyhteystietoFactory.DEFAULT_PERSON_PUHELINNUMERO)
                        hasNullNode("registryKey")
                        hasNullNode("ovt")
                        hasNullNode("invoicingOperator")
                        hasTextNode("country").isEqualTo("FI")
                        hasNullNode("sapCustomerNumber")
                    }
                }
            }

            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(any())
            }
        }

        @Test
        fun `resets hanke nimi when hanke is generated`() {
            val hakemus =
                hakemusFactory
                    .builderWithGeneratedHanke()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION)
                    .saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).withName("New name").save()
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            val hanke = hankeRepository.findAll().single()
            assertThat(hanke.nimi).isEqualTo(taydennys.hakemusData.name)
            verifySequence {
                alluClient.respondToInformationRequest(hakemus.alluid!!, any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `merges the taydennys data with the hakemus`() {
            val taydennys = taydennysFactory.builder().withStartTime(startTime).save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val hakemus = hakemusService.getById(taydennyspyynto.applicationId)
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            val updatedHakemus = hakemusService.getById(hakemus.id)
            assertThat(updatedHakemus.applicationData.startTime).isEqualTo(startTime)
            verifySequence {
                alluClient.respondToInformationRequest(hakemus.alluid!!, any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `removes the taydennyspyynto and taydennys`() {
            val taydennys = taydennysFactory.builder().withStartTime(startTime).save()
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(any())
            }
            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            assertThat(taydennysRepository.findAll()).isEmpty()
        }

        @Test
        fun `logs the removed taydennys and taydennyspyynto`() {
            val taydennys = taydennysFactory.builder().withStartTime(startTime).save()
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)
            justRun { alluClient.addAttachment(any(), any()) }
            auditLogRepository.deleteAll()

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            val taydennysEntries = auditLogRepository.findByType(ObjectType.TAYDENNYS)
            assertThat(taydennysEntries).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasNoObjectAfter()
                    hasObjectBefore(taydennys)
                }
            }
            val taydennyspyyntoEntries = auditLogRepository.findByType(ObjectType.TAYDENNYSPYYNTO)
            assertThat(taydennyspyyntoEntries).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasNoObjectAfter()
                    hasObjectBefore {
                        prop(Taydennyspyynto::id).isEqualTo(taydennys.taydennyspyyntoId)
                        prop(Taydennyspyynto::kentat)
                            .isEqualTo(TaydennyspyyntoFactory.DEFAULT_KENTAT)
                    }
                }
            }
            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(any(), withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(any())
            }
        }

        @Test
        fun `updates the Allu status even when sending the form data attachment fails`() {
            val taydennys = taydennysFactory.builder().withStartTime(startTime).save()
            val taydennyspyynto = taydennyspyyntoRepository.findAll().single()
            val hakemus = hakemusService.getById(taydennyspyynto.applicationId)
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(
                    hakemus.alluid!!,
                    status = ApplicationStatus.HANDLING,
                )
            every { alluClient.addAttachment(hakemus.alluid!!, any()) } throws AlluException()

            val response = taydennysService.sendTaydennys(taydennys.id, USERNAME)

            assertThat(response.alluStatus).isEqualTo(ApplicationStatus.HANDLING)
            val updatedHakemus = hakemusService.getById(hakemus.id)
            assertThat(updatedHakemus.alluStatus).isEqualTo(ApplicationStatus.HANDLING)
            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(hakemus.alluid!!, withName(FORM_DATA_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `sends a new haittojenhallintasuunnitelma to Allu when the hakemus is a kaivuilmoitus`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.getReferenceById(hanke.id)
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields(hankealue = hanke.alueet.single())
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).withStartTime(startTime).save()
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(
                    hakemus.alluid!!,
                    status = ApplicationStatus.HANDLING,
                )
            val attachments = mutableListOf<Attachment>()
            justRun { alluClient.addAttachment(hakemus.alluid!!, capture(attachments)) }

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            val attachmentMetadata =
                attachments
                    .map { it.metadata }
                    .first { it.name == "haitaton-haittojenhallintasuunnitelma-taydennys.pdf" }
            assertThat(attachmentMetadata)
                .prop(AttachmentMetadata::description)
                .isNotNull()
                .contains("Haittojenhallintasuunnitelma from Haitaton taydennys, dated ")
            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(hakemus.alluid!!, withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(hakemus.alluid!!, withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `updates the Allu status even when sending the haittojenhallintasuunnitelma attachment fails`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.getReferenceById(hanke.id)
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields(hankealue = hanke.alueet.single())
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).withStartTime(startTime).save()
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(
                    hakemus.alluid!!,
                    status = ApplicationStatus.HANDLING,
                )
            every { alluClient.addAttachment(hakemus.alluid!!, any()) } throws AlluException()

            val response = taydennysService.sendTaydennys(taydennys.id, USERNAME)

            assertThat(response.alluStatus).isEqualTo(ApplicationStatus.HANDLING)
            val updatedHakemus = hakemusService.getById(hakemus.id)
            assertThat(updatedHakemus.alluStatus).isEqualTo(ApplicationStatus.HANDLING)
            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(hakemus.alluid!!, withName(FORM_DATA_PDF_FILENAME))
                alluClient.addAttachment(hakemus.alluid!!, withName(HHS_PDF_FILENAME))
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `deletes the attachments when deleting a taydennys`() {
            val taydennys = taydennysFactory.builder().save()
            attachmentFactory.save(taydennys = taydennys).withContent()
            attachmentFactory.save(taydennys = taydennys).withContent()
            assertThat(attachmentRepository.findByTaydennysId(taydennys.id)).hasSize(2)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(2)

            taydennysService.delete(taydennys.id, USERNAME)

            assertThat(taydennysRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
            assertThat(attachmentRepository.findByTaydennysId(taydennys.id)).isEmpty()
        }

        @Test
        fun `deletes all attachment metadata even when deleting attachment content fails`() {
            val taydennys = taydennysFactory.builder().save()
            attachmentFactory.save(taydennys = taydennys).withContent()
            attachmentFactory.save(taydennys = taydennys).withContent()
            assertThat(attachmentRepository.findByTaydennysId(taydennys.id)).hasSize(2)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(2)
            fileClient.connected = false

            taydennysService.delete(taydennys.id, USERNAME)

            fileClient.connected = true
            assertThat(taydennysRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(2)
            assertThat(attachmentRepository.findByTaydennysId(taydennys.id)).isEmpty()
        }

        @Test
        fun `writes audit log for the deleted taydennys`() {
            TestUtils.addMockedRequestIp()
            val taydennys = taydennysFactory.builder().save()
            auditLogRepository.deleteAll()

            taydennysService.delete(taydennys.id, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME, TestUtils.MOCKED_IP)
                withTarget {
                    prop(AuditLogTarget::id).isEqualTo(taydennys.id.toString())
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.TAYDENNYS)
                    hasObjectBefore(taydennys)
                    hasNoObjectAfter()
                }
            }
        }

        @Test
        fun `deletes yhteystiedot and yhteyshenkilot but no hankekayttaja`() {
            val taydennys =
                taydennysFactory
                    .builder()
                    .hakija()
                    .rakennuttaja()
                    .tyonSuorittaja()
                    .asianhoitaja()
                    .saveEntity()
            assertThat(taydennysRepository.findAll()).hasSize(1)
            assertThat(taydennysyhteystietoRepository.findAll()).hasSize(4)
            assertThat(taydennysyhteyshenkiloRepository.findAll()).hasSize(4)
            assertThat(hankekayttajaRepository.count())
                .isEqualTo(5) // Hanke founder + one kayttaja for each role

            taydennysService.delete(taydennys.id, USERNAME)

            assertThat(taydennysRepository.findAll()).isEmpty()
            assertThat(taydennysyhteystietoRepository.findAll()).isEmpty()
            assertThat(taydennysyhteyshenkiloRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.count())
                .isEqualTo(5) // Hanke founder + one kayttaja for each role
        }

        @Test
        fun `deletes taydennys but not taydennyspyynto`() {
            val taydennys = taydennysFactory.builder().saveEntity()
            assertThat(taydennysRepository.findAll()).hasSize(1)
            assertThat(taydennyspyyntoRepository.findAll()).hasSize(1)

            taydennysService.delete(taydennys.id, USERNAME)

            assertThat(taydennysRepository.findAll()).isEmpty()
            assertThat(taydennyspyyntoRepository.findAll()).hasSize(1)
        }

        @Test
        fun `resets the hankealueet when the hanke is a generated one`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().withMandatoryFields().save()
            val taydennys = taydennysFactory.builder(hakemus.id, hakemus.hankeId).save()
            val newAreas =
                listOf(
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.polygon()
                    ),
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.thirdPolygon()
                    ),
                )
            val update = taydennys.toUpdateRequest() as JohtoselvityshakemusUpdateRequest
            taydennysService.updateTaydennys(taydennys.id, update.copy(areas = newAreas), USERNAME)
            assertHankeHasSameGeometryAsHakemus(hakemus.hankeTunnus, newAreas)

            taydennysService.delete(taydennys.id, USERNAME)

            assertHankeHasSameGeometryAsHakemus(
                hakemus.hankeTunnus,
                (hakemus.applicationData as JohtoselvityshakemusData).areas!!,
            )
        }
    }

    private fun assertHankeHasSameGeometryAsHakemus(
        hanketunnus: String,
        hakemusalueet: List<Hakemusalue>,
    ) {
        val hanke = hankeService.loadHanke(hanketunnus)!!
        val hankeCoordinates =
            hanke.alueet
                .flatMap { it.geometriat!!.featureCollection!!.features }
                .map { it.geometry as Polygon }
                .map { it.coordinates }
        assertThat(hankeCoordinates)
            .hasSameElementsAs(hakemusalueet.map { it.geometries().flatMap { g -> g.coordinates } })
    }
}
