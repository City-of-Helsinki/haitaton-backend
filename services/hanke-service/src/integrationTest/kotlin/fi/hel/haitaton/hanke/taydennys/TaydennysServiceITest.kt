package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import fi.hel.haitaton.hanke.logging.ALLU_AUDIT_LOG_USERID
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
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
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verifySequence
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TaydennysServiceITest(
    @Autowired private val taydennysService: TaydennysService,
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    @Autowired private val taydennysRepository: TaydennysRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val taydennyspyyntoFactory: TaydennyspyyntoFactory,
    @Autowired private val taydennysFactory: TaydennysFactory,
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
        fun `returns null when Allu doesn't have the täydennyspyyntö`() {
            val hakemus = hakemusFactory.builder().withStatus(alluId = alluId).save()
            every { alluClient.getInformationRequest(hakemus.alluid!!) } returns null

            val response = taydennysService.saveTaydennyspyyntoFromAllu(hakemus)

            assertThat(response).isNull()
            verifySequence { alluClient.getInformationRequest(hakemus.alluid!!) }
        }
    }

    @Nested
    inner class Create {
        private val fixedUUID = UUID.fromString("789b38cf-5345-4889-a5b8-2711c47559c8")

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
            assertThat(response)
                .isNotNull()
                .prop(Taydennys::taydennyspyyntoId)
                .isEqualTo(taydennyspyynto.id)
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

        /**
         * The created entities for customers and contacts are in a different table in the database,
         * so the IDs cannot be the same for the taydennys and hakemus.
         *
         * Otherwise, the data should be identical, so the easiest way to compare them is to reset
         * all the IDs in both of them to a known fixed value and them see if they're equal.
         */
        private fun HakemusData.resetCustomerIds(): HakemusData {
            val customer = customerWithContacts?.resetIds()
            return when (this) {
                is JohtoselvityshakemusData -> {
                    val contractor = contractorWithContacts?.resetIds()
                    val representative = representativeWithContacts?.resetIds()
                    val developer = propertyDeveloperWithContacts?.resetIds()
                    copy(
                        customerWithContacts = customer,
                        contractorWithContacts = contractor,
                        representativeWithContacts = representative,
                        propertyDeveloperWithContacts = developer,
                    )
                }
                is KaivuilmoitusData -> {
                    val contractor = contractorWithContacts?.resetIds()
                    val representative = representativeWithContacts?.resetIds()
                    val developer = propertyDeveloperWithContacts?.resetIds()
                    copy(
                        customerWithContacts = customer,
                        contractorWithContacts = contractor,
                        representativeWithContacts = representative,
                        propertyDeveloperWithContacts = developer,
                    )
                }
            }
        }

        private fun Hakemusyhteystieto.resetIds() =
            copy(id = fixedUUID, yhteyshenkilot = yhteyshenkilot.map { it.copy(id = fixedUUID) })
    }

    @Nested
    inner class SendTaydennys {
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
            val taydennys = taydennysFactory.saveWithHakemus()

            val failure = assertFailure { taydennysService.sendTaydennys(taydennys.id, USERNAME) }

            failure.all {
                hasClass(NoChangesException::class)
                messageContains("Not sending a täydennys without any changes")
                messageContains("id=${taydennys.id}")
            }
        }

        @Test
        fun `throws exception if the hakemus is not in WAITING_INFORMATION`() {
            val taydennys =
                taydennysFactory.saveWithHakemus {
                    it.withMandatoryFields()
                        .withStatus(status = ApplicationStatus.INFORMATION_RECEIVED)
                }
            taydennysFactory.updateJohtoselvitysTaydennys(taydennys) {
                copy(startTime = DateFactory.getStartDatetime().minusDays(1))
            }

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
            val taydennys = taydennysFactory.saveWithHakemus { it.withMandatoryFields() }
            taydennysFactory.updateJohtoselvitysTaydennys(taydennys) { copy(startTime = null) }

            val failure = assertFailure { taydennysService.sendTaydennys(taydennys.id, USERNAME) }

            failure.all {
                hasClass(InvalidHakemusDataException::class)
                messageContains("Application contains invalid data")
                messageContains("Errors at paths: applicationData.startTime")
            }
        }

        @Test
        fun `sends the data to Allu with list of changed fields`() {
            val taydennys = taydennysFactory.saveWithHakemus { it.withMandatoryFields() }
            taydennysFactory.updateJohtoselvitysTaydennys(taydennys) {
                copy(
                    startTime = DateFactory.getStartDatetime().minusDays(1),
                    endTime = DateFactory.getEndDatetime().plusDays(1),
                    constructionWork = true,
                    maintenanceWork = true,
                    workDescription = "New description",
                    postalAddress = ApplicationFactory.createPostalAddress("New street"),
                    areas =
                        listOf(
                            ApplicationFactory.createCableReportApplicationArea(),
                            ApplicationFactory.createCableReportApplicationArea(),
                        ),
                )
            }
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
                alluClient.addAttachment(any(), any())
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `sends the updated form data as a PDF`() {
            val taydennys = taydennysFactory.saveWithHakemus { it.withMandatoryFields() }
            taydennysFactory.updateJohtoselvitysTaydennys(taydennys) {
                copy(startTime = DateFactory.getStartDatetime().minusDays(1))
            }
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
                alluClient.addAttachment(hakemus.alluid!!, any())
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `logs personal info sent to Allu to disclosure logs`() {
            val hakija = HakemusyhteystietoFactory.createPerson()
            val taydennys =
                taydennysFactory.saveWithHakemus { it.withMandatoryFields().hakija(hakija) }
            taydennysFactory.updateJohtoselvitysTaydennys(taydennys) {
                copy(startTime = DateFactory.getStartDatetime().minusDays(1))
            }
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
                alluClient.addAttachment(any(), any())
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
            val taydennys =
                taydennysFactory.saveForHakemus(hakemus) {
                    this as JohtoselvityshakemusEntityData
                    copy(name = "New name")
                }
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(hakemus.alluid!!) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            val hanke = hankeRepository.findAll().single()
            assertThat(hanke.nimi).isEqualTo(taydennys.hakemusData.name)
            verifySequence {
                alluClient.respondToInformationRequest(hakemus.alluid!!, any(), any(), any())
                alluClient.addAttachment(any(), any())
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `merges the taydennys data with the hakemus`() {
            val taydennys = taydennysFactory.saveWithHakemus { it.withMandatoryFields() }
            val startTime = DateFactory.getStartDatetime().minusDays(1)
            taydennysFactory.updateJohtoselvitysTaydennys(taydennys) { copy(startTime = startTime) }
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
                alluClient.addAttachment(any(), any())
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }

        @Test
        fun `removes the taydennyspyynto and taydennys`() {
            val taydennys = taydennysFactory.saveWithHakemus { it.withMandatoryFields() }
            taydennysFactory.updateJohtoselvitysTaydennys(taydennys) {
                copy(startTime = DateFactory.getStartDatetime().minusDays(1))
            }
            justRun { alluClient.respondToInformationRequest(any(), any(), any(), any()) }
            justRun { alluClient.addAttachment(any(), any()) }
            every { alluClient.getApplicationInformation(any()) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            taydennysService.sendTaydennys(taydennys.id, USERNAME)

            verifySequence {
                alluClient.respondToInformationRequest(any(), any(), any(), any())
                alluClient.addAttachment(any(), any())
                alluClient.getApplicationInformation(any())
            }
            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            assertThat(taydennysRepository.findAll()).isEmpty()
        }

        @Test
        fun `logs the removed taydennys and taydennyspyynto`() {
            val unchangedTaydennys = taydennysFactory.saveWithHakemus { it.withMandatoryFields() }
            val taydennys =
                taydennysFactory.updateJohtoselvitysTaydennys(unchangedTaydennys) {
                    copy(startTime = DateFactory.getStartDatetime().minusDays(1))
                }
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
                alluClient.addAttachment(any(), any())
                alluClient.getApplicationInformation(any())
            }
        }

        @Test
        fun `updates the Allu status even when sending the form data attachment fails`() {
            val unchangedTaydennys = taydennysFactory.saveWithHakemus { it.withMandatoryFields() }
            val taydennys =
                taydennysFactory.updateJohtoselvitysTaydennys(unchangedTaydennys) {
                    copy(startTime = DateFactory.getStartDatetime().minusDays(1))
                }
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
                alluClient.addAttachment(hakemus.alluid!!, any())
                alluClient.getApplicationInformation(hakemus.alluid!!)
            }
        }
    }
}
