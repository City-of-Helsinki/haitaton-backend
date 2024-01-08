package fi.hel.haitaton.hanke.application

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.first
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isIn
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isPresent
import assertk.assertions.matches
import assertk.assertions.prop
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluException
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus.APPROVED
import fi.hel.haitaton.hanke.allu.ApplicationStatus.DECISION
import fi.hel.haitaton.hanke.allu.ApplicationStatus.DECISIONMAKING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.HANDLING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING_CLIENT
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.asUtc
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.prefix
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.email.EmailSenderService.Companion.translations
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.AlluFactory.createAlluApplicationResponse
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.asianHoitajaCustomerContact
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createApplication
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createApplicationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createApplicationEntity
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createCableReportApplicationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createCompanyCustomerWithOrderer
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.expectedRecipients
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.hakijaCustomerContact
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.rakennuttajaCustomerContact
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.suorittajaCustomerContact
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withCustomer
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_HAKIJA
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajakutsuEntity
import fi.hel.haitaton.hanke.permissions.KayttajakutsuRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso.HAKEMUSASIOINTI
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso.KATSELUOIKEUS
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.kayttajaTunnistePattern
import fi.hel.haitaton.hanke.test.Asserts.hasReceivers
import fi.hel.haitaton.hanke.test.Asserts.hasSingleGeometryWithCoordinates
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.TestUtils.nextYear
import fi.hel.haitaton.hanke.validation.InvalidApplicationDataException
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
import jakarta.mail.internet.MimeMessage
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql

private const val USERNAME = "test7358"
private const val HANKE_TUNNUS = "HAI23-5"

private val dataWithoutAreas = createCableReportApplicationData(areas = listOf())

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
class ApplicationServiceITest : DatabaseTest() {

    @Autowired private lateinit var applicationService: ApplicationService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var cableReportServiceAllu: CableReportService

    @Autowired private lateinit var applicationRepository: ApplicationRepository
    @Autowired private lateinit var applicationAttachmentRepository: ApplicationAttachmentRepository
    @Autowired private lateinit var hankeRepository: HankeRepository
    @Autowired private lateinit var alluStatusRepository: AlluStatusRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var kayttajakutsuRepository: KayttajakutsuRepository
    @Autowired private lateinit var hankeKayttajaRepository: HankekayttajaRepository
    @Autowired private lateinit var geometriatDao: GeometriatDao

    @Autowired private lateinit var applicationFactory: ApplicationFactory
    @Autowired private lateinit var attachmentFactory: ApplicationAttachmentFactory
    @Autowired private lateinit var hankeFactory: HankeFactory

    @Autowired private lateinit var fileClient: MockFileClient

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(cableReportServiceAllu)
    }

    @Nested
    inner class CreateApplication {
        @Test
        fun `when valid input should create and audit log entry`() {
            TestUtils.addMockedRequestIp()
            val hanke = hankeFactory.builder(USERNAME).save()

            val application =
                applicationService.create(
                    createApplication(
                        id = null,
                        hankeTunnus = hanke.hankeTunnus,
                        applicationData = dataWithoutAreas
                    ),
                    USERNAME
                )

            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).hasSize(1)
            val logEntry = applicationLogs[0]
            assertThat(logEntry::isSent).isFalse()
            assertThat(logEntry::createdAt).isRecent()
            val event = logEntry.message.auditEvent
            assertThat(event::dateTime).isRecent()
            assertThat(event::operation).isEqualTo(Operation.CREATE)
            assertThat(event::status).isEqualTo(Status.SUCCESS)
            assertThat(event::failureDescription).isNull()
            assertThat(event::appVersion).isEqualTo("1")
            assertThat(event.actor::userId).isEqualTo(USERNAME)
            assertThat(event.actor::role).isEqualTo(UserRole.USER)
            assertThat(event.actor::ipAddress).isEqualTo(TestUtils.mockedIp)
            assertThat(event.target::id).isEqualTo(application.id?.toString())
            assertThat(event.target::type).isEqualTo(ObjectType.APPLICATION)
            assertThat(event.target::objectBefore).isNull()
            val expectedObject =
                expectedLogObject(application.id, null, hankeTunnus = hanke.hankeTunnus)
            JSONAssert.assertEquals(
                expectedObject,
                event.target.objectAfter,
                JSONCompareMode.NON_EXTENSIBLE
            )
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when valid input should save application with correct ids`() {
            val givenId: Long = 123456789
            val hanke = initializedHanke()
            val cableReportApplicationData =
                createCableReportApplicationData(
                    pendingOnClient = true,
                    areas = listOf(aleksanterinpatsas)
                )
            val newApplication =
                createApplication(
                    id = givenId,
                    applicationData = cableReportApplicationData,
                    hankeTunnus = hanke.hankeTunnus,
                )
            assertTrue(cableReportApplicationData.pendingOnClient)

            val response = applicationService.create(newApplication, USERNAME)

            assertNotEquals(givenId, response.id)
            assertEquals(null, response.alluid)
            assertEquals(newApplication.applicationType, response.applicationType)
            assertEquals(newApplication.applicationData, response.applicationData)
            assertTrue(response.applicationData.pendingOnClient)
            assertTrue(cableReportApplicationData.pendingOnClient)
            val savedApplications = applicationRepository.findAll()
            assertThat(savedApplications).hasSize(1)
            val savedApplication = savedApplications[0]
            assertEquals(response.id, savedApplication.id)
            assertEquals(null, savedApplication.alluid)
            assertEquals(newApplication.applicationType, savedApplication.applicationType)
            assertEquals(newApplication.applicationData, savedApplication.applicationData)
            assertTrue(savedApplication.applicationData.pendingOnClient)
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when created should set pendingOnClient true`() {
            val givenId: Long = 123456789
            val hanke = initializedHanke()
            val cableReportApplicationData =
                createCableReportApplicationData(
                    pendingOnClient = false,
                    areas = listOf(aleksanterinpatsas)
                )
            val newApplication =
                createApplication(
                    id = givenId,
                    applicationData = cableReportApplicationData,
                    hankeTunnus = hanke.hankeTunnus,
                )

            val response = applicationService.create(newApplication, USERNAME)

            assertTrue(response.applicationData.pendingOnClient)
            val savedApplication = applicationRepository.findById(response.id!!).get()
            assertTrue(savedApplication.applicationData.pendingOnClient)
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when invalid geometry in areas should throw`() {
            val cableReportData = createCableReportApplicationData(areas = listOf(intersectingArea))
            val newApplication = createApplication(id = null, applicationData = cableReportData)

            val exception =
                assertThrows<ApplicationGeometryException> {
                    applicationService.create(newApplication, USERNAME)
                }

            assertEquals(
                """Invalid geometry received when creating a new application
                | for user $USERNAME, reason = Self-intersection,
                | location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
                    .trimMargin()
                    .replace("\n", ""),
                exception.message
            )
        }

        @Test
        fun `when application area is outside hankealue should throw`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val cableReportApplicationData =
                createCableReportApplicationData(areas = listOf(havisAmanda))
            val newApplication =
                createApplication(
                    id = null,
                    hankeTunnus = hanke.hankeTunnus,
                    applicationData = cableReportApplicationData
                )

            assertThrows<ApplicationGeometryNotInsideHankeException> {
                applicationService.create(newApplication, USERNAME)
            }
        }

        @Test
        fun `when application without hankeTunnus should throw`() {
            assertThrows<HankeNotFoundException> {
                applicationService.create(createApplication(id = null, hankeTunnus = ""), USERNAME)
            }
        }
    }

    @Nested
    inner class UpdateApplicationData {
        @Test
        fun `when valid input should audit log`() {
            TestUtils.addMockedRequestIp()
            val hanke = hankeFactory.builder(USERNAME).save()
            val application =
                applicationService.create(
                    createApplication(
                        id = null,
                        hankeTunnus = hanke.hankeTunnus,
                        applicationData = dataWithoutAreas
                    ),
                    USERNAME
                )
            auditLogRepository.deleteAll()
            assertThat(auditLogRepository.findAll()).isEmpty()

            applicationService.updateApplicationData(
                application.id!!,
                dataWithoutAreas.copy(name = "Modified application"),
                USERNAME
            )

            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).hasSize(1)
            val logEntry = applicationLogs[0]
            assertThat(logEntry::isSent).isFalse()
            assertThat(logEntry::createdAt).isRecent()
            val event = logEntry.message.auditEvent
            assertThat(event::dateTime).isRecent()
            assertThat(event::operation).isEqualTo(Operation.UPDATE)
            assertThat(event::status).isEqualTo(Status.SUCCESS)
            assertThat(event::failureDescription).isNull()
            assertThat(event::appVersion).isEqualTo("1")
            assertThat(event.actor::userId).isEqualTo(USERNAME)
            assertThat(event.actor::role).isEqualTo(UserRole.USER)
            assertThat(event.actor::ipAddress).isEqualTo(TestUtils.mockedIp)
            assertThat(event.target::id).isEqualTo(application.id?.toString())
            assertThat(event.target::type).isEqualTo(ObjectType.APPLICATION)
            val expectedObjectBefore =
                expectedLogObject(application.id, null, hankeTunnus = hanke.hankeTunnus)
            JSONAssert.assertEquals(
                expectedObjectBefore,
                event.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE
            )
            val expectedObjectAfter =
                expectedLogObject(
                    application.id,
                    null,
                    "Modified application",
                    hankeTunnus = hanke.hankeTunnus
                )
            JSONAssert.assertEquals(
                expectedObjectAfter,
                event.target.objectAfter,
                JSONCompareMode.NON_EXTENSIBLE
            )
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when application in Allu and pending sends update to Allu`() {
            val alluId = 21
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = alluId,
                    applicationData = mockApplicationDataWithArea()
                )
            val newApplicationData =
                createCableReportApplicationData(
                    name = "Uudistettu johtoselvitys",
                    areas = application.applicationData.areas
                )
            every { cableReportServiceAllu.getApplicationInformation(alluId) } returns
                createAlluApplicationResponse(alluId)
            justRun { cableReportServiceAllu.update(alluId, any()) }
            justRun { cableReportServiceAllu.addAttachment(alluId, any()) }

            applicationService.updateApplicationData(application.id!!, newApplicationData, USERNAME)

            verifyOrder {
                cableReportServiceAllu.getApplicationInformation(alluId)
                cableReportServiceAllu.update(alluId, any())
                cableReportServiceAllu.addAttachment(alluId, any())
            }
        }

        @Test
        fun `when application has not changed should not create an audit log entry`() {
            TestUtils.addMockedRequestIp()
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = null)
            assertThat(auditLogRepository.findAll()).isEmpty()

            applicationService.updateApplicationData(
                application.id!!,
                application.applicationData,
                USERNAME
            )

            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).isEmpty()
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when missing data should throw on send`() {
            TestUtils.addMockedRequestIp()
            val alluId = 21
            val hanke = hankeFactory.saveMinimal()
            val applicationData: CableReportApplicationData = dataWithoutAreas
            val savedApplication =
                applicationFactory.saveApplicationEntity(
                    username = USERNAME,
                    hanke = hanke,
                    alluId = alluId,
                    applicationData = applicationData,
                )
            every { cableReportServiceAllu.getApplicationInformation(alluId) } returns
                createAlluApplicationResponse(alluId)

            val exception =
                assertThrows<InvalidApplicationDataException> {
                    applicationService.updateApplicationData(
                        savedApplication.id!!,
                        applicationData.copy(startTime = null),
                        USERNAME
                    )
                }

            assertThat(exception.message)
                .isEqualTo(
                    "Application contains invalid data. Errors at paths: applicationData.startTime"
                )
            verify { cableReportServiceAllu.getApplicationInformation(alluId) }
            verify(exactly = 0) { cableReportServiceAllu.update(any(), any()) }
        }

        @Test
        fun `when application has not changed should not send application to Allu`() {
            TestUtils.addMockedRequestIp()
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = 2)
            assertThat(auditLogRepository.findAll()).isEmpty()

            applicationService.updateApplicationData(
                application.id!!,
                application.applicationData,
                USERNAME
            )

            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when no application should throw`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            assertThrows<ApplicationNotFoundException> {
                applicationService.updateApplicationData(
                    1234,
                    createCableReportApplicationData(),
                    USERNAME
                )
            }
        }

        @Test
        fun `when hanke was generated should skip area inside hanke check`() {
            val (initialApplication, hanke) =
                hankeFactory.builder(USERNAME).saveAsGenerated(createCableReportApplicationData())
            assertThat(initialApplication.applicationData.areas).isNotNull().hasSize(1)
            val newGeometry: Polygon = GeometriaFactory.thirdPolygon
            assertThat(geometriatDao.isInsideHankeAlueet(hanke.id, newGeometry)).isFalse()
            val newApplicationData =
                createCableReportApplicationData(
                    pendingOnClient = true,
                    name = "Uudistettu johtoselvitys",
                    areas = listOf(createApplicationArea(geometry = newGeometry))
                )

            val response =
                applicationService.updateApplicationData(
                    initialApplication.id!!,
                    newApplicationData,
                    USERNAME
                )

            assertEquals(newApplicationData, response.applicationData)
        }

        @Test
        fun `when the hanke is generated it should recreate the hankealueet`() {
            val initialApplicationData =
                createCableReportApplicationData(areas = null)
                    .withArea("First area", GeometriaFactory.polygon)
                    .withArea("Second area", GeometriaFactory.secondPolygon)
            val (initialApplication, _) =
                hankeFactory.builder(USERNAME).saveAsGenerated(initialApplicationData)
            val newApplicationData =
                createCableReportApplicationData(areas = null)
                    .withArea("New area", GeometriaFactory.thirdPolygon)

            val response =
                applicationService.updateApplicationData(
                    initialApplication.id!!,
                    newApplicationData,
                    USERNAME
                )

            val updatedHanke = hankeService.loadHanke(response.hankeTunnus)!!
            assertThat(updatedHanke.alueet).hasSize(1)
            assertThat(updatedHanke.alueet).first().all {
                prop(Hankealue::nimi).isEqualTo("Hankealue 1")
                hasSingleGeometryWithCoordinates(GeometriaFactory.thirdPolygon)
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when valid input should save data`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    applicationData = mockApplicationDataWithArea()
                )
            val newApplicationData =
                createCableReportApplicationData(
                    name = "Uudistettu johtoselvitys",
                    areas = application.applicationData.areas
                )

            val response =
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    USERNAME
                )

            assertEquals(null, response.alluid)
            assertEquals(application.applicationType, response.applicationType)
            assertEquals(newApplicationData, response.applicationData)
            val savedApplications = applicationRepository.findAll()
            assertThat(savedApplications).hasSize(1)
            val savedApplication = savedApplications[0]
            assertEquals(response.id, savedApplication.id)
            assertEquals(null, savedApplication.alluid)
            assertEquals(application.applicationType, savedApplication.applicationType)
            assertEquals(newApplicationData, savedApplication.applicationData)
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when application already in Allu should update in Allu`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea(),
                )
            val newApplicationData =
                createCableReportApplicationData(
                    name = "Uudistettu johtoselvitys",
                    areas = application.applicationData.areas
                )
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21)
            val expectedAlluRequest = newApplicationData.toAlluData(HANKE_TUNNUS)
            justRun { cableReportServiceAllu.update(21, expectedAlluRequest) }
            justRun { cableReportServiceAllu.addAttachment(21, any()) }

            val response =
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    USERNAME
                )

            assertEquals(newApplicationData, response.applicationData)
            val savedApplication = applicationRepository.findById(application.id!!).orElseThrow()
            assertEquals(response.id, savedApplication.id)
            assertEquals(21, savedApplication.alluid)
            assertEquals(application.applicationType, savedApplication.applicationType)
            assertEquals(newApplicationData, savedApplication.applicationData)
            verifyOrder {
                cableReportServiceAllu.getApplicationInformation(21)
                cableReportServiceAllu.update(21, expectedAlluRequest)
                cableReportServiceAllu.addAttachment(21, any())
            }
        }

        @Test
        fun `when application is in Allu should provide access for new contacts`() {
            val cableReport =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                )
            val (application, hanke) =
                hankeFactory
                    .builder(USERNAME)
                    .withPerustaja(KAYTTAJA_INPUT_HAKIJA)
                    .saveAsGenerated(cableReport)
            setAlluFields(applicationRepository.findById(application.id!!).orElseThrow())
            justRun { cableReportServiceAllu.update(21, any()) }
            every { cableReportServiceAllu.getApplicationInformation(any()) } returns
                createAlluApplicationResponse(21)
            justRun { cableReportServiceAllu.addAttachment(21, any()) }
            val updatedApplication =
                cableReport.copy(
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact,
                    contractorWithContacts =
                        suorittajaCustomerContact.changeContactEmails("new.mail@foo.fi")
                )

            applicationService.updateApplicationData(application.id!!, updatedApplication, USERNAME)

            val capturedNotifications = getApplicationNotifications()
            assertThat(capturedNotifications)
                .areValid(application.applicationType, hanke.hankeTunnus)
            assertThat(capturedNotifications.toTypedArray())
                .hasReceivers(
                    "new.mail@foo.fi",
                    asianHoitajaCustomerContact.contacts[0].email,
                    rakennuttajaCustomerContact.contacts[0].email
                )
            verifySequence {
                cableReportServiceAllu.getApplicationInformation(any())
                cableReportServiceAllu.update(21, any())
                cableReportServiceAllu.addAttachment(any(), any())
            }
        }

        @Test
        fun `when no sender hanke user found should not send any emails`() {
            val initialApplicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                )
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = hankeFactory.saveMinimal(hankeTunnus = HANKE_TUNNUS, generated = true),
                    alluId = 21,
                    alluStatus = PENDING,
                    applicationIdentifier = ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER,
                    applicationData = initialApplicationData,
                )
            justRun { cableReportServiceAllu.update(21, any()) }
            justRun { cableReportServiceAllu.addAttachment(21, any()) }
            every { cableReportServiceAllu.getApplicationInformation(any()) } returns
                createAlluApplicationResponse(21)

            val updatedApplication =
                initialApplicationData.copy(
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact,
                    contractorWithContacts =
                        suorittajaCustomerContact.changeContactEmails("new.mail@foo.fi")
                )

            applicationService.updateApplicationData(application.id!!, updatedApplication, USERNAME)

            verifySequence {
                cableReportServiceAllu.getApplicationInformation(any())
                cableReportServiceAllu.update(21, any())
                cableReportServiceAllu.addAttachment(any(), any())
            }
            assertThat(greenMail.receivedMessages).isEmpty()
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when Allu update fails should roll back transaction`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea()
                )
            val newApplicationData =
                createCableReportApplicationData(
                    name = "Uudistettu johtoselvitys",
                    areas = application.applicationData.areas
                )
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21)
            every {
                cableReportServiceAllu.update(21, newApplicationData.toAlluData(HANKE_TUNNUS))
            } throws RuntimeException("Allu call failed")

            val exception =
                assertThrows<RuntimeException> {
                    applicationService.updateApplicationData(
                        application.id!!,
                        newApplicationData,
                        USERNAME
                    )
                }

            assertEquals("Allu call failed", exception.message)
            val savedApplication = applicationRepository.findById(application.id!!).orElseThrow()
            assertEquals(
                ApplicationFactory.DEFAULT_APPLICATION_NAME,
                savedApplication.applicationData.name
            )
            verifyOrder {
                cableReportServiceAllu.getApplicationInformation(21)
                cableReportServiceAllu.update(21, newApplicationData.toAlluData(HANKE_TUNNUS))
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when application still pending should update in Allu`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea().copy(pendingOnClient = false),
                )
            val newApplicationData =
                createCableReportApplicationData(
                    name = "Uudistettu johtoselvitys",
                    pendingOnClient = false,
                    areas = application.applicationData.areas
                )
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21)
            justRun {
                cableReportServiceAllu.update(21, newApplicationData.toAlluData(HANKE_TUNNUS))
            }
            justRun { cableReportServiceAllu.addAttachment(21, any()) }

            val response =
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    USERNAME
                )

            assertEquals(21, response.alluid)
            assertEquals(newApplicationData, response.applicationData)
            val savedApplications = applicationRepository.findAll()
            assertThat(savedApplications).hasSize(1)
            val savedApplication = savedApplications[0]
            assertEquals(21, savedApplication.alluid)
            assertEquals(newApplicationData, savedApplication.applicationData)
            verifyOrder {
                cableReportServiceAllu.getApplicationInformation(21)
                cableReportServiceAllu.update(21, newApplicationData.toAlluData(HANKE_TUNNUS))
                cableReportServiceAllu.addAttachment(21, any())
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when update data invalid should not update Allu`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea().copy(pendingOnClient = false),
                )
            val newApplicationData =
                createCableReportApplicationData(
                    startTime = null,
                    pendingOnClient = false,
                    areas = application.applicationData.areas
                )
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21)

            val exception =
                assertThrows<InvalidApplicationDataException> {
                    applicationService.updateApplicationData(
                        application.id!!,
                        newApplicationData,
                        USERNAME
                    )
                }

            assertEquals(
                "Application contains invalid data. Errors at paths: applicationData.startTime",
                exception.message
            )
            val savedApplications = applicationRepository.findAll()
            assertThat(savedApplications).hasSize(1)
            val savedApplication = savedApplications[0]
            assertEquals(21, savedApplication.alluid)
            assertEquals(application.applicationData, savedApplication.applicationData)
            verify { cableReportServiceAllu.getApplicationInformation(21) }
            verify(exactly = 0) { cableReportServiceAllu.update(any(), any()) }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when updated should not update pendingOnClient`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea().copy(pendingOnClient = false)
                )
            val newApplicationData =
                createCableReportApplicationData(
                    name = "Päivitetty hakemus",
                    pendingOnClient = true,
                    areas = application.applicationData.areas
                )
            val expectedApplicationData =
                newApplicationData.copy(pendingOnClient = false).toAlluData(HANKE_TUNNUS)
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21)
            justRun { cableReportServiceAllu.update(21, expectedApplicationData) }
            justRun { cableReportServiceAllu.addAttachment(21, any()) }

            val response =
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    USERNAME
                )

            assertFalse(response.applicationData.pendingOnClient)
            val savedApplication = applicationRepository.findById(application.id!!).get()
            assertFalse(savedApplication.applicationData.pendingOnClient)
            verifyOrder {
                cableReportServiceAllu.getApplicationInformation(21)
                cableReportServiceAllu.update(21, expectedApplicationData)
                cableReportServiceAllu.addAttachment(21, any())
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `when application beyond pending in Allu should not update`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea(),
                )
            val newApplicationData =
                createCableReportApplicationData(
                    name = "Uudistettu johtoselvitys",
                    areas = application.applicationData.areas
                )
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21, HANDLING)

            assertThrows<ApplicationAlreadyProcessingException> {
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    USERNAME
                )
            }

            val savedApplications = applicationRepository.findAll()
            assertThat(savedApplications).hasSize(1)
            val savedApplication = savedApplications[0]
            assertEquals(
                ApplicationFactory.DEFAULT_APPLICATION_NAME,
                (savedApplication.applicationData as CableReportApplicationData).name
            )
            verify { cableReportServiceAllu.getApplicationInformation(21) }
            verify(exactly = 0) { cableReportServiceAllu.update(any(), any()) }
        }

        @Test
        fun `when invalid geometry in areas should throw`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = 21)
            val cableReportApplicationData =
                createCableReportApplicationData(areas = listOf(intersectingArea))

            val exception =
                assertThrows<ApplicationGeometryException> {
                    applicationService.updateApplicationData(
                        application.id!!,
                        cableReportApplicationData,
                        USERNAME
                    )
                }

            assertEquals(
                """Invalid geometry received when updating application for
                | user $USERNAME, id=${application.id}, alluid=${application.alluid},
                | reason = Self-intersection,
                | location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
                    .trimMargin()
                    .replace("\n", ""),
                exception.message
            )
        }

        @Test
        fun `when application area outside hankealue should throw`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.getReferenceById(hanke.id)
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hankeEntity, alluId = 21)
            val cableReportApplicationData =
                createCableReportApplicationData(areas = listOf(havisAmanda))
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21)

            assertThrows<ApplicationGeometryNotInsideHankeException> {
                applicationService.updateApplicationData(
                    application.id!!,
                    cableReportApplicationData,
                    USERNAME
                )
            }

            verifySequence { cableReportServiceAllu.getApplicationInformation(21) }
        }
    }

    @Nested
    inner class GetAllApplicationsForUser {
        @Test
        fun `when no applications should return empty list`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            val response = applicationService.getAllApplicationsForUser(USERNAME)

            assertEquals(listOf<Application>(), response)
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when applications exist should return users applications`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            val otherUser = "otherUser"
            val hanke = hankeFactory.saveMinimal(hankeTunnus = HANKE_TUNNUS)
            val hanke2 = hankeFactory.saveMinimal(hankeTunnus = "HAI-1236")
            permissionService.create(hanke.id, USERNAME, HAKEMUSASIOINTI)
            permissionService.create(hanke2.id, "otherUser", HAKEMUSASIOINTI)
            applicationFactory.saveApplicationEntities(3, USERNAME, hanke = hanke) { _, application
                ->
                application.userId = USERNAME
                application.applicationData =
                    createCableReportApplicationData(name = "Application data for $USERNAME")
            }
            applicationFactory.saveApplicationEntities(3, "otherUser", hanke = hanke2)
            assertThat(applicationRepository.findAll()).hasSize(6)
            assertThat(applicationRepository.getAllByUserId(USERNAME)).hasSize(3)
            assertThat(applicationRepository.getAllByUserId(otherUser)).hasSize(3)

            val response = applicationService.getAllApplicationsForUser(USERNAME)

            assertThat(response).hasSize(3)
            assertThat(response)
                .extracting { a -> a.applicationData.name }
                .each { name -> name.isEqualTo("Application data for $USERNAME") }
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when applications in different projects should returns applications`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            val hanke = hankeFactory.saveMinimal(hankeTunnus = HANKE_TUNNUS)
            val hanke2 = hankeFactory.saveMinimal(hankeTunnus = "HAI-1236")
            val hanke3 = hankeFactory.saveMinimal(hankeTunnus = "HAI-1237")
            permissionService.create(hanke.id, USERNAME, HAKEMUSASIOINTI)
            permissionService.create(hanke2.id, USERNAME, HAKEMUSASIOINTI)
            val application1 =
                applicationFactory.saveApplicationEntity(username = USERNAME, hanke = hanke)
            val application2 =
                applicationFactory.saveApplicationEntity(username = "secondUser", hanke = hanke2)
            applicationFactory.saveApplicationEntity(username = "thirdUser", hanke = hanke3)

            val response = applicationService.getAllApplicationsForUser(USERNAME).map { it.id }

            assertThat(applicationRepository.findAll()).hasSize(3)
            assertThat(response).containsExactlyInAnyOrder(application1.id, application2.id)
        }
    }

    @Nested
    inner class GetApplicationById {
        @Test
        fun `when application does not exist should throw`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            assertThrows<ApplicationNotFoundException> {
                applicationService.getApplicationById(1234)
            }

            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when application exists should return it`() {
            val hanke = hankeFactory.saveMinimal()
            val applications =
                applicationFactory.saveApplicationEntities(3, USERNAME, hanke = hanke)
            val selectedId = applications[1].id!!
            assertThat(applicationRepository.findAll()).hasSize(3)

            val response = applicationService.getApplicationById(selectedId)

            assertEquals(selectedId, response.id)
            verify { cableReportServiceAllu wasNot Called }
        }
    }

    @Nested
    inner class SendApplication {
        @Test
        fun `Throws exception with unknown ID`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            assertThrows<ApplicationNotFoundException> {
                applicationService.sendApplication(1234, USERNAME)
            }
        }

        @Test
        fun `Skips the area inside hanke check with a generated hanke`() {
            val (initialApplication, _) = hankeFactory.builder(USERNAME).saveAsGenerated()
            assertFalse(initialApplication.applicationData.areas.isNullOrEmpty())
            val alluIdMock = 123
            every { cableReportServiceAllu.create(any()) } returns alluIdMock
            every { cableReportServiceAllu.getApplicationInformation(alluIdMock) } returns
                createAlluApplicationResponse(alluIdMock)
            justRun { cableReportServiceAllu.addAttachment(any(), any()) }

            applicationService.sendApplication(initialApplication.id!!, USERNAME)

            verify { cableReportServiceAllu.create(any()) }
            verify { cableReportServiceAllu.addAttachment(any(), any()) }
            verify { cableReportServiceAllu.getApplicationInformation(any()) }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `Sets pendingOnClient to false`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData =
                        createCableReportApplicationData(
                            pendingOnClient = true,
                            areas = listOf(aleksanterinpatsas)
                        )
                )
            val applicationData =
                application.applicationData.toAlluData(HANKE_TUNNUS)
                    as AlluCableReportApplicationData
            val pendingApplicationData = applicationData.copy(pendingOnClient = false)
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21)
            justRun { cableReportServiceAllu.update(21, pendingApplicationData) }
            justRun { cableReportServiceAllu.addAttachment(21, any()) }
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21, PENDING)

            val response = applicationService.sendApplication(application.id!!, USERNAME)

            val responseApplicationData = response.applicationData as CableReportApplicationData
            assertFalse(responseApplicationData.pendingOnClient)
            val savedApplication = applicationRepository.findById(application.id!!).get()
            val savedApplicationData =
                savedApplication.applicationData as CableReportApplicationData
            assertFalse(savedApplicationData.pendingOnClient)
            verifyOrder {
                cableReportServiceAllu.getApplicationInformation(21)
                cableReportServiceAllu.update(21, pendingApplicationData)
                cableReportServiceAllu.addAttachment(21, any())
                cableReportServiceAllu.getApplicationInformation(21)
            }
        }

        @Test
        fun `Saves user tokens from application contacts`() {
            val data =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                )
            val (application, hanke) = hankeFactory.builder(USERNAME).saveAsGenerated(data)
            val pending = data.copy(pendingOnClient = false).toAlluData(hanke.hankeTunnus)
            every { cableReportServiceAllu.create(pending) } returns 26
            justRun { cableReportServiceAllu.addAttachment(26, any()) }
            every { cableReportServiceAllu.getApplicationInformation(26) } returns
                createAlluApplicationResponse(26)

            applicationService.sendApplication(application.id!!, USERNAME)

            val kutsut = kayttajakutsuRepository.findAll()
            assertThat(kutsut).hasSize(1)
            assertThat(kutsut.first()).all {
                prop(KayttajakutsuEntity::kayttooikeustaso).isEqualTo(KATSELUOIKEUS)
                prop(KayttajakutsuEntity::createdAt).isRecent()
                prop(KayttajakutsuEntity::tunniste).matches(Regex(kayttajaTunnistePattern))
                prop(KayttajakutsuEntity::hankekayttaja).isNotNull()
            }
            val inviter = findKayttaja(hanke.id, KAYTTAJA_INPUT_HAKIJA.email)
            assertThat(kutsut.first().hankekayttaja).all {
                prop(HankekayttajaEntity::fullName).isEqualTo(TEPPO_TESTI)
                prop(HankekayttajaEntity::puhelin).isEqualTo("04012345678")
                prop(HankekayttajaEntity::kutsuttuEtunimi).isEqualTo("Teppo")
                prop(HankekayttajaEntity::kutsuttuSukunimi).isEqualTo("Testihenkilö")
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(TEPPO_EMAIL)
                prop(HankekayttajaEntity::hankeId).isEqualTo(hanke.id)
                prop(HankekayttajaEntity::permission).isNull() // user not logged in yet
                prop(HankekayttajaEntity::kayttajakutsu).isNotNull()
                prop(HankekayttajaEntity::kutsujaId).isEqualTo(inviter.id)
            }
            verifyOrder {
                cableReportServiceAllu.create(pending)
                cableReportServiceAllu.addAttachment(26, any())
                cableReportServiceAllu.getApplicationInformation(26)
            }
        }

        @Test
        fun `Sends application notifications to contacts`() {
            val cableReportData =
                createCableReportApplicationData(
                    areas = listOf(aleksanterinpatsas),
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact
                )
            val (application, hanke) =
                hankeFactory
                    .builder(USERNAME)
                    .withPerustaja(KAYTTAJA_INPUT_HAKIJA)
                    .saveAsGenerated(cableReportData)
            every { cableReportServiceAllu.create(any()) } returns 26
            every { cableReportServiceAllu.getApplicationInformation(any()) } returns
                createAlluApplicationResponse(26)
            justRun { cableReportServiceAllu.addAttachment(26, any()) }

            applicationService.sendApplication(application.id!!, USERNAME)

            val capturedEmails = getApplicationNotifications()
            assertThat(capturedEmails).hasSize(3) // 4 contacts, but one is the sender
            assertThat(capturedEmails).areValid(application.applicationType, hanke.hankeTunnus)
            verifySequence {
                cableReportServiceAllu.create(any())
                cableReportServiceAllu.addAttachment(any(), any())
                cableReportServiceAllu.getApplicationInformation(any())
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `Doesn't resend application that's been sent before`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea().copy(pendingOnClient = false),
                )
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21, PENDING)

            applicationService.sendApplication(application.id!!, USERNAME)

            verify { cableReportServiceAllu.getApplicationInformation(21) }
            verify(exactly = 0) { cableReportServiceAllu.update(any(), any()) }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `Doesn't send an application that's already beyond pending in Allu`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = 21,
                    applicationData = mockApplicationDataWithArea().copy(pendingOnClient = false),
                )
            every { cableReportServiceAllu.getApplicationInformation(21) } returns
                createAlluApplicationResponse(21, DECISIONMAKING)

            assertThrows<ApplicationAlreadyProcessingException> {
                applicationService.sendApplication(application.id!!, USERNAME)
            }

            verify { cableReportServiceAllu.getApplicationInformation(21) }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `Sends application and saves alluid even if status query fails`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    alluId = null,
                    applicationData = mockApplicationDataWithArea()
                )
            val applicationData = application.applicationData as CableReportApplicationData
            val pendingApplicationData = applicationData.copy(pendingOnClient = false)
            every {
                cableReportServiceAllu.create(pendingApplicationData.toAlluData(HANKE_TUNNUS))
            } returns 26
            justRun { cableReportServiceAllu.addAttachment(26, any()) }
            every { cableReportServiceAllu.getApplicationInformation(26) } throws
                AlluException(listOf())

            val response = applicationService.sendApplication(application.id!!, USERNAME)

            assertEquals(26, response.alluid)
            assertEquals(pendingApplicationData, response.applicationData)
            assertNull(response.applicationIdentifier)
            assertNull(response.alluStatus)
            val savedApplication = applicationRepository.findById(application.id!!).get()
            assertEquals(26, savedApplication.alluid)
            assertEquals(pendingApplicationData, savedApplication.applicationData)
            assertNull(savedApplication.applicationIdentifier)
            assertNull(savedApplication.alluStatus)

            verifyOrder {
                cableReportServiceAllu.create(pendingApplicationData.toAlluData(HANKE_TUNNUS))
                cableReportServiceAllu.addAttachment(26, any())
                cableReportServiceAllu.getApplicationInformation(26)
            }
        }

        @Test
        fun `Throws an exception when application area is outside hankealue`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.getReferenceById(hanke.id)
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = hankeEntity,
                    applicationData = createCableReportApplicationData(areas = listOf(havisAmanda))
                )

            assertThrows<ApplicationGeometryNotInsideHankeException> {
                applicationService.sendApplication(application.id!!, USERNAME)
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `Cancels the sent application before throwing if uploading initial attachments fails`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    applicationData = mockApplicationDataWithArea()
                )
            attachmentFactory.save(application = application).withDbContent()
            val applicationData = application.applicationData as CableReportApplicationData
            val pendingApplicationData = applicationData.copy(pendingOnClient = false)
            val expectedAlluRequest = pendingApplicationData.toAlluData(HANKE_TUNNUS)
            val alluId = 236
            every { cableReportServiceAllu.create(expectedAlluRequest) } returns alluId
            justRun { cableReportServiceAllu.addAttachment(alluId, any()) }
            every { cableReportServiceAllu.addAttachments(alluId, any(), any()) } throws
                AlluException(listOf())
            justRun { cableReportServiceAllu.cancel(alluId) }
            every { cableReportServiceAllu.sendSystemComment(alluId, any()) } returns 4141

            assertThrows<AlluException> {
                applicationService.sendApplication(application.id!!, USERNAME)
            }

            verifyOrder {
                cableReportServiceAllu.create(expectedAlluRequest)
                cableReportServiceAllu.addAttachment(alluId, any())
                cableReportServiceAllu.addAttachments(alluId, any(), any())
                cableReportServiceAllu.cancel(alluId)
                cableReportServiceAllu.sendSystemComment(
                    alluId,
                    ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG
                )
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `Sends application and saves alluid, status even if form data attachment sending fails`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    applicationData = mockApplicationDataWithArea(),
                )
            val applicationData = application.applicationData as CableReportApplicationData
            val pendingApplicationData = applicationData.copy(pendingOnClient = false)
            val expectedAlluRequest = pendingApplicationData.toAlluData(HANKE_TUNNUS)
            val alluId = 467
            every { cableReportServiceAllu.create(expectedAlluRequest) } returns alluId
            every { cableReportServiceAllu.addAttachment(alluId, any()) } throws
                AlluException(listOf())
            every { cableReportServiceAllu.getApplicationInformation(alluId) } returns
                createAlluApplicationResponse(alluId)

            val response = applicationService.sendApplication(application.id!!, USERNAME)

            assertThat(response.alluid).isEqualTo(alluId)
            assertThat(response.alluStatus).isEqualTo(PENDING)
            val savedApplication = applicationRepository.findById(application.id!!).get()
            assertThat(savedApplication.alluid).isEqualTo(alluId)
            assertThat(savedApplication.alluStatus).isEqualTo(PENDING)
            verifyOrder {
                cableReportServiceAllu.create(expectedAlluRequest)
                cableReportServiceAllu.addAttachment(alluId, any())
                cableReportServiceAllu.getApplicationInformation(alluId)
            }
        }

        @Test
        @Sql("/sql/senaatintorin-hanke.sql")
        fun `Creates new application to Allu and saves ID and status to database`() {
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = initializedHanke(),
                    applicationData = mockApplicationDataWithArea()
                )
            attachmentFactory.save(application = application).withDbContent()
            val applicationData = application.applicationData as CableReportApplicationData
            val pendingApplicationData = applicationData.copy(pendingOnClient = false)
            val expectedAlluRequest = pendingApplicationData.toAlluData(HANKE_TUNNUS)
            every { cableReportServiceAllu.create(expectedAlluRequest) } returns 26
            justRun { cableReportServiceAllu.addAttachment(26, any()) }
            justRun { cableReportServiceAllu.addAttachments(26, any(), any()) }
            every { cableReportServiceAllu.getApplicationInformation(26) } returns
                createAlluApplicationResponse(26)

            val response = applicationService.sendApplication(application.id!!, USERNAME)

            assertEquals(26, response.alluid)
            assertEquals(pendingApplicationData, response.applicationData)
            assertEquals(
                ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER,
                response.applicationIdentifier
            )
            assertEquals(PENDING, response.alluStatus)
            val savedApplication = applicationRepository.findById(application.id!!).get()
            assertEquals(26, savedApplication.alluid)
            assertEquals(pendingApplicationData, savedApplication.applicationData)
            assertEquals(
                ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER,
                savedApplication.applicationIdentifier
            )
            assertEquals(PENDING, savedApplication.alluStatus)
            verifyOrder {
                cableReportServiceAllu.create(expectedAlluRequest)
                cableReportServiceAllu.addAttachment(26, any())
                cableReportServiceAllu.addAttachments(26, any(), any())
                cableReportServiceAllu.getApplicationInformation(26)
            }
        }

        private fun findKayttaja(hankeId: Int, email: String) =
            hankeKayttajaRepository.findByHankeIdAndSahkopostiIn(hankeId, listOf(email)).first()
    }

    @Nested
    inner class DeleteApplication {
        @Test
        fun `when no application should throw`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            assertThrows<ApplicationNotFoundException> { applicationService.delete(1234, USERNAME) }
        }

        @Test
        fun `when application not in Allu should delete application and all its attachments`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = null)
            assertThat(applicationRepository.findAll()).hasSize(1)
            attachmentFactory.save(application = application).withCloudContent()
            attachmentFactory.save(application = application).withCloudContent()
            assertThat(fileClient.list(Container.HAKEMUS_LIITTEET, prefix(application.id!!)))
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id!!))
                .hasSize(2)

            applicationService.delete(application.id!!, USERNAME)

            assertThat(applicationRepository.findAll()).isEmpty()
            verify { cableReportServiceAllu wasNot Called }
            assertThat(fileClient.list(Container.HAKEMUS_LIITTEET, prefix(application.id!!)))
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id!!))
                .isEmpty()
        }

        @Test
        fun `when deleted should audit log for deleted application`() {
            TestUtils.addMockedRequestIp()
            val hanke = hankeFactory.builder(USERNAME).save()
            val application =
                applicationService.create(
                    createApplication(
                        id = null,
                        hankeTunnus = hanke.hankeTunnus,
                        applicationData = dataWithoutAreas
                    ),
                    USERNAME
                )
            auditLogRepository.deleteAll()
            assertThat(auditLogRepository.findAll()).isEmpty()

            applicationService.delete(application.id!!, USERNAME)

            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).hasSize(1)
            val logEntry = applicationLogs[0]
            assertThat(logEntry::isSent).isFalse()
            assertThat(logEntry::createdAt).isRecent()
            val event = logEntry.message.auditEvent
            assertThat(event::dateTime).isRecent()
            assertThat(event::operation).isEqualTo(Operation.DELETE)
            assertThat(event::status).isEqualTo(Status.SUCCESS)
            assertThat(event::failureDescription).isNull()
            assertThat(event::appVersion).isEqualTo("1")
            assertThat(event.actor::userId).isEqualTo(USERNAME)
            assertThat(event.actor::role).isEqualTo(UserRole.USER)
            assertThat(event.actor::ipAddress).isEqualTo(TestUtils.mockedIp)
            assertThat(event.target::id).isEqualTo(application.id?.toString())
            assertThat(event.target::type).isEqualTo(ObjectType.APPLICATION)
            val expectedObject =
                expectedLogObject(application.id, null, hankeTunnus = hanke.hankeTunnus)
            JSONAssert.assertEquals(
                expectedObject,
                event.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE
            )
            assertThat(event.target::objectAfter).isNull()
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when pending application in Allu should cancel before delete`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = 73)
            assertThat(applicationRepository.findAll()).hasSize(1)
            every { cableReportServiceAllu.getApplicationInformation(73) } returns
                createAlluApplicationResponse(73, PENDING)
            justRun { cableReportServiceAllu.cancel(73) }
            every { cableReportServiceAllu.sendSystemComment(73, any()) } returns 1324

            applicationService.delete(application.id!!, USERNAME)

            assertThat(applicationRepository.findAll()).hasSize(0)
            verifyOrder {
                cableReportServiceAllu.getApplicationInformation(73)
                cableReportServiceAllu.cancel(73)
                cableReportServiceAllu.sendSystemComment(73, ALLU_USER_CANCELLATION_MSG)
            }
        }

        @Test
        fun `when past pending application in Allu should throw`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = 73)
            assertThat(applicationRepository.findAll()).hasSize(1)
            every { cableReportServiceAllu.getApplicationInformation(73) } returns
                createAlluApplicationResponse(73, APPROVED)

            assertThrows<ApplicationAlreadyProcessingException> {
                applicationService.delete(application.id!!, USERNAME)
            }

            assertThat(applicationRepository.findAll()).hasSize(1)
            verifyOrder { cableReportServiceAllu.getApplicationInformation(73) }
        }
    }

    @Nested
    inner class DeleteWithOrphanGeneratedHankeRemoval {
        @Test
        fun `when deleted application is the only one of generated hanke should delete hanke also`() {
            val hanke = hankeFactory.saveMinimal(generated = true)
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = null)
            assertThat(applicationRepository.findAll()).hasSize(1)

            val result =
                applicationService.deleteWithOrphanGeneratedHankeRemoval(application.id!!, USERNAME)

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = true))
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            verify { cableReportServiceAllu wasNot Called }
            val auditLogEntry = auditLogRepository.findByType(ObjectType.HANKE).first()
            assertThat(auditLogEntry).isSuccess(Operation.DELETE) { hasUserActor(USERNAME) }
        }

        @Test
        fun `when deleting orphaned application all its attachments are also deleted`() {
            val hanke = hankeFactory.saveMinimal(generated = true)
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = null)
            assertThat(applicationRepository.findAll()).hasSize(1)
            attachmentFactory.save(application = application).withCloudContent()
            attachmentFactory.save(application = application).withCloudContent()
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id!!))
                .hasSize(2)
            assertThat(fileClient.list(Container.HAKEMUS_LIITTEET, prefix(application.id!!)))
                .hasSize(2)

            val result =
                applicationService.deleteWithOrphanGeneratedHankeRemoval(application.id!!, USERNAME)

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = true))
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(fileClient.list(Container.HAKEMUS_LIITTEET, prefix(application.id!!)))
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id!!))
                .isEmpty()
        }

        @Test
        fun `when hanke not generated should not delete hanke`() {
            val hanke = hankeFactory.saveMinimal(generated = false)
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = null)
            assertThat(applicationRepository.findAll()).hasSize(1)

            val result =
                applicationService.deleteWithOrphanGeneratedHankeRemoval(application.id!!, USERNAME)

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = false))
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findByHankeTunnus(hanke.hankeTunnus)).isNotNull()
            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when generated hanke has other applications should not delete hanke`() {
            val hanke = hankeFactory.saveMinimal(generated = true)
            val application1 =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = null)
            val application2 = applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke)
            assertThat(applicationRepository.findAll()).hasSize(2)

            val result =
                applicationService.deleteWithOrphanGeneratedHankeRemoval(
                    application1.id!!,
                    USERNAME
                )

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = false))
            assertThat(applicationRepository.findAll()).hasSize(1)
            assertThat(applicationRepository.findById(application2.id!!)).isPresent()
            assertThat(hankeRepository.findByHankeTunnus(hanke.hankeTunnus)).isNotNull()
            verify { cableReportServiceAllu wasNot Called }
        }
    }

    @Nested
    inner class DownloadDecision {
        private val alluId = 134
        private val decisionPdf: ByteArray =
            "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()

        @Test
        fun `when unknown ID should throw`() {
            assertThrows<ApplicationNotFoundException> {
                applicationService.downloadDecision(1234, USERNAME)
            }
        }

        @Test
        fun `when no alluid should throw`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = null)

            assertThrows<ApplicationDecisionNotFoundException> {
                applicationService.downloadDecision(application.id!!, USERNAME)
            }

            verify { cableReportServiceAllu wasNot Called }
        }

        @Test
        fun `when no decision in Allu should throw`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = alluId)
            every { cableReportServiceAllu.getDecisionPdf(alluId) }
                .throws(ApplicationDecisionNotFoundException(""))

            assertThrows<ApplicationDecisionNotFoundException> {
                applicationService.downloadDecision(application.id!!, USERNAME)
            }

            verify { cableReportServiceAllu.getDecisionPdf(alluId) }
        }

        @Test
        fun `when decision exists should return it`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = hanke,
                    alluId = alluId,
                    applicationIdentifier = "JS230001"
                )
            every { cableReportServiceAllu.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = applicationService.downloadDecision(application.id!!, USERNAME)

            assertThat(filename).isNotNull().isEqualTo("JS230001")
            assertThat(bytes).isEqualTo(decisionPdf)
            verify { cableReportServiceAllu.getDecisionPdf(alluId) }
        }

        @Test
        fun `when application has no identifier should use default file name`() {
            val hanke = hankeFactory.saveMinimal()
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = hanke,
                    alluId = alluId,
                    applicationIdentifier = null
                )
            every { cableReportServiceAllu.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = applicationService.downloadDecision(application.id!!, USERNAME)

            assertThat(filename).isNotNull().isEqualTo("paatos")
            assertThat(bytes).isEqualTo(decisionPdf)
            verify { cableReportServiceAllu.getDecisionPdf(alluId) }
        }
    }

    @Nested
    inner class HandleApplicationUpdates {

        /** The timestamp used in the initial DB migration. */
        private val placeholderUpdateTime = OffsetDateTime.parse("2017-01-01T00:00:00Z")
        private val updateTime = OffsetDateTime.parse("2022-10-09T06:36:51Z")
        private val alluid = 42
        private val identifier = ApplicationHistoryFactory.defaultApplicationIdentifier

        @Test
        fun `updates the last updated time with empty histories`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            assertEquals(placeholderUpdateTime, alluStatusRepository.getLastUpdateTime().asUtc())

            applicationService.handleApplicationUpdates(listOf(), updateTime)

            assertEquals(updateTime, alluStatusRepository.getLastUpdateTime().asUtc())
        }

        @Test
        fun `updates the application statuses in the correct order`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            assertEquals(placeholderUpdateTime, alluStatusRepository.getLastUpdateTime().asUtc())
            val hanke = hankeFactory.saveMinimal()
            applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = alluid)
            val firstEventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z")
            val history =
                ApplicationHistoryFactory.create(
                    alluid,
                    ApplicationHistoryFactory.createEvent(firstEventTime.plusDays(5), PENDING),
                    ApplicationHistoryFactory.createEvent(firstEventTime.plusDays(10), HANDLING),
                    ApplicationHistoryFactory.createEvent(firstEventTime, PENDING),
                )

            applicationService.handleApplicationUpdates(listOf(history), updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
            val application = applicationRepository.getOneByAlluid(alluid)
            assertThat(application)
                .isNotNull()
                .prop("alluStatus", ApplicationEntity::alluStatus)
                .isEqualTo(HANDLING)
            assertThat(application!!.applicationIdentifier).isEqualTo(identifier)
        }

        @Test
        fun `ignores missing application`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            assertEquals(placeholderUpdateTime, alluStatusRepository.getLastUpdateTime().asUtc())
            val hanke = hankeFactory.saveMinimal()
            applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = alluid)
            applicationFactory.saveApplicationEntity(USERNAME, hanke = hanke, alluId = alluid + 2)
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(alluid, "JS2300082"),
                    ApplicationHistoryFactory.create(alluid + 1, "JS2300083"),
                    ApplicationHistoryFactory.create(alluid + 2, "JS2300084"),
                )

            applicationService.handleApplicationUpdates(histories, updateTime)

            assertEquals(updateTime, alluStatusRepository.getLastUpdateTime().asUtc())
            val applications = applicationRepository.findAll()
            assertThat(applications).hasSize(2)
            assertThat(applications.map { it.alluid }).containsExactlyInAnyOrder(alluid, alluid + 2)
            assertThat(applications.map { it.alluStatus })
                .containsExactlyInAnyOrder(PENDING_CLIENT, PENDING_CLIENT)
            assertThat(applications.map { it.applicationIdentifier })
                .containsExactlyInAnyOrder("JS2300082", "JS2300084")
        }

        @Test
        fun `sends email to the orderer when application gets a decision`() {
            val hanke = hankeFactory.saveMinimal()
            applicationRepository.save(
                createApplicationEntity(
                        alluid = alluid,
                        applicationIdentifier = identifier,
                        userId = "user",
                        hanke = hanke,
                    )
                    .withCustomer(createCompanyCustomerWithOrderer())
            )
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluid,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = DECISION
                        )
                    ),
                )

            applicationService.handleApplicationUpdates(histories, updateTime)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEPPO_EMAIL)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Johtoselvitys $identifier / Ledningsutredning $identifier / Cable report $identifier"
                )
        }
    }

    @Nested
    inner class GetAllApplicationsCreatedByUser {
        @Test
        fun `Returns empty list without applications`() {
            val result = applicationService.getAllApplicationsCreatedByUser(USERNAME)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns applications created by the user`() {
            val hanke = hankeFactory.saveMinimal()
            applicationFactory.saveApplicationEntities(6, USERNAME, hanke) { i, application ->
                if (i % 2 == 0) application.userId = "Other User"
            }

            val result = applicationService.getAllApplicationsCreatedByUser(USERNAME)

            assertThat(result).hasSize(3)
            val userids =
                result.map { applicationRepository.getReferenceById(it.id!!) }.map { it.userId }
            assertThat(userids).containsExactly(USERNAME, USERNAME, USERNAME)
        }
    }

    @Nested
    inner class IsApplicationStillPending {
        private val alluId = 123

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
        fun `when status is pending and allu confirms should return true`(
            status: ApplicationStatus
        ) {
            val application = createApplication(alluid = alluId, alluStatus = status)
            every { cableReportServiceAllu.getApplicationInformation(alluId) } returns
                createAlluApplicationResponse(status = status)

            assertTrue(
                applicationService.isStillPending(application.alluid, application.alluStatus)
            )

            verify { cableReportServiceAllu.getApplicationInformation(alluId) }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
        fun `when status is pending but status in Allu in handling should return false`(
            status: ApplicationStatus
        ) {
            val application = createApplication(alluid = alluId, alluStatus = status)
            every { cableReportServiceAllu.getApplicationInformation(alluId) } returns
                createAlluApplicationResponse(status = HANDLING)

            assertFalse(
                applicationService.isStillPending(application.alluid, application.alluStatus)
            )

            verify { cableReportServiceAllu.getApplicationInformation(alluId) }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(
            value = ApplicationStatus::class,
            mode = EnumSource.Mode.EXCLUDE,
            names = ["PENDING", "PENDING_CLIENT"]
        )
        fun `when status is not pending should return false`(status: ApplicationStatus) {
            val application = createApplication(alluid = alluId, alluStatus = status)

            assertFalse(
                applicationService.isStillPending(application.alluid, application.alluStatus)
            )

            verify { cableReportServiceAllu wasNot Called }
        }
    }

    private fun getApplicationNotifications() =
        greenMail.receivedMessages.filter {
            it.subject.startsWith("Haitaton: Sinut on lisätty hakemukselle")
        }

    private fun Assert<List<MimeMessage>>.areValid(type: ApplicationType, hankeTunnus: String?) {
        each { it.isValid(type, hankeTunnus) }
    }

    private fun Assert<MimeMessage>.isValid(type: ApplicationType, hankeTunnus: String?) {
        val name = KAYTTAJA_INPUT_HAKIJA.fullName()
        val email = KAYTTAJA_INPUT_HAKIJA.email
        prop(MimeMessage::textBody).all {
            contains("$name ($email) on tehnyt")
            contains("hakemukselle ${ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER}")
            contains("on tehnyt ${type.translations().fi}")
            contains("hankkeella $hankeTunnus")
        }

        transform { it.allRecipients.first().toString() }.isIn(*expectedRecipients)
    }

    private fun initializedHanke(): HankeEntity =
        hankeRepository.findByHankeTunnus("HAI23-5") ?: throw NoSuchElementException()

    private val aleksanterinpatsas: ApplicationArea =
        createApplicationArea(
            geometry = "/fi/hel/haitaton/hanke/geometria/aleksanterin-patsas.json".asJsonResource()
        )

    private val havisAmanda: ApplicationArea =
        createApplicationArea(
            geometry = "/fi/hel/haitaton/hanke/geometria/havis-amanda.json".asJsonResource()
        )

    private val intersectingArea =
        createApplicationArea(
            name = "area",
            geometry = "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json".asJsonResource()
        )

    private fun mockApplicationDataWithArea(): ApplicationData =
        createCableReportApplicationData(areas = listOf(aleksanterinpatsas))

    private fun setAlluFields(applicationEntity: ApplicationEntity) {
        applicationRepository.save(
            applicationEntity.copy(
                alluid = 21,
                alluStatus = PENDING,
                applicationIdentifier = ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER,
            )
        )
    }

    private fun customerWithContactsJson(orderer: Boolean) =
        """
           "customer": {
             "type": "COMPANY",
             "name": "DNA",
             "country": "FI",
             "email": "info@dna.test",
             "phone": "+3581012345678",
             "registryKey": "3766028-0",
             "ovt": null,
             "invoicingOperator": null,
             "sapCustomerNumber": null
           },
           "contacts": [
             {
               "firstName": "Teppo",
               "lastName": "Testihenkilö",
               "email": "teppo@example.test",
               "phone": "04012345678",
               "orderer": $orderer
             }
           ]
        """

    private fun expectedLogObject(
        id: Long?,
        alluId: Int?,
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        hankeTunnus: String?,
    ) =
        """
            {
              "id": $id,
              "alluid": $alluId,
              "hankeTunnus": $hankeTunnus,
              "alluStatus": null,
              "applicationIdentifier": null,
              "applicationType": "CABLE_REPORT",
              "applicationData": {
                "name": "$name",
                "customerWithContacts": {
                  ${customerWithContactsJson(orderer = true)}
                },
                "areas": [],
                "startTime": "${nextYear()}-02-20T23:45:56Z",
                "endTime": "${nextYear()}-02-21T00:12:34Z",
                "pendingOnClient": true,
                "workDescription": "Work description.",
                "rockExcavation": false,
                "contractorWithContacts": {
                  ${customerWithContactsJson(orderer = false)}
                },
                "postalAddress": null,
                "representativeWithContacts": null,
                "invoicingCustomer": null,
                "customerReference": null,
                "area": null,
                "propertyDeveloperWithContacts": null,
                "constructionWork": false,
                "maintenanceWork": false,
                "emergencyWork": false,
                "propertyConnectivity": false
              }
            }
        """

    private fun CustomerWithContacts.changeContactEmails(newEmail: String?) =
        copy(contacts = contacts.map { it.copy(email = newEmail) })
}
