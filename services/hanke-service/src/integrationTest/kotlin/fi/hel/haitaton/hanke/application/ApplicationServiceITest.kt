package fi.hel.haitaton.hanke.application

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluException
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.asUtc
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.TestUtils.nextYear
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val username = "test7358"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(username)
class ApplicationServiceITest : DatabaseTest() {
    @MockkBean private lateinit var cableReportServiceAllu: CableReportService
    @Autowired private lateinit var applicationService: ApplicationService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService

    @Autowired private lateinit var applicationRepository: ApplicationRepository
    @Autowired private lateinit var hankeRepository: HankeRepository
    @Autowired private lateinit var alluStatusRepository: AlluStatusRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var alluDataFactory: AlluDataFactory

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(cableReportServiceAllu)
    }

    fun createHanke(): Hanke {
        return hankeService.createHanke(HankeFactory.create())
    }

    fun createHankeEntity(): HankeEntity {
        return hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1234"))
    }

    @Test
    fun `create creates an audit log entry for created application`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHanke()

        val application =
            applicationService.create(
                AlluDataFactory.createApplication(id = null, hankeTunnus = hanke.hankeTunnus!!),
                username
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
        assertThat(event.actor::userId).isEqualTo(username)
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
    fun `updateApplicationData creates an audit log entry for updated application`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHanke()
        val application =
            applicationService.create(
                AlluDataFactory.createApplication(id = null, hankeTunnus = hanke.hankeTunnus!!),
                username
            )
        auditLogRepository.deleteAll()
        assertThat(auditLogRepository.findAll()).isEmpty()
        every { cableReportServiceAllu.create(any()) }.returns(2)

        applicationService.updateApplicationData(
            application.id!!,
            AlluDataFactory.createCableReportApplicationData(name = "Modified application"),
            username
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
        assertThat(event.actor::userId).isEqualTo(username)
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
    fun `updateApplicationData doesn't create an audit log entry if the application hasn't changed`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = null }
        assertThat(auditLogRepository.findAll()).isEmpty()

        applicationService.updateApplicationData(
            application.id!!,
            application.applicationData,
            username
        )

        val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
        assertThat(applicationLogs).isEmpty()
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `updateApplicationData doesn't send application to Allu if it hasn't changed`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 2 }
        assertThat(auditLogRepository.findAll()).isEmpty()

        applicationService.updateApplicationData(
            application.id!!,
            application.applicationData,
            username
        )

        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `getAllApplicationsForUser with no applications returns empty list`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        val response = applicationService.getAllApplicationsForUser(username)

        assertEquals(listOf<Application>(), response)
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `getAllApplicationsForUser returns applications for the correct user`() {
        assertThat(applicationRepository.findAll()).isEmpty()
        val otherUser = "otherUser"
        val hanke = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1234"))
        val hanke2 = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1235"))
        permissionService.setPermission(hanke.id!!, username, Role.HAKEMUSASIOINTI)
        permissionService.setPermission(hanke2.id!!, "otherUser", Role.HAKEMUSASIOINTI)

        alluDataFactory.saveApplicationEntities(3, username, hanke = hanke) { i, application ->
            application.userId = username
            application.applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    name = "Application data for $username"
                )
        }
        alluDataFactory.saveApplicationEntities(3, "otherUser", hanke = hanke2)

        assertThat(applicationRepository.findAll()).hasSize(6)
        assertThat(applicationRepository.getAllByUserId(username)).hasSize(3)
        assertThat(applicationRepository.getAllByUserId(otherUser)).hasSize(3)

        val response = applicationService.getAllApplicationsForUser(username)

        assertThat(response).hasSize(3)
        assertThat(response)
            .extracting { a -> a.applicationData.name }
            .each { name -> name.isEqualTo("Application data for $username") }
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `getAllApplicationsForUser returns applications for user hankkeet`() {
        assertThat(applicationRepository.findAll()).isEmpty()
        val hanke = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1234"))
        val hanke2 = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1235"))
        val hanke3 = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1236"))
        permissionService.setPermission(hanke.id!!, username, Role.HAKEMUSASIOINTI)
        permissionService.setPermission(hanke2.id!!, username, Role.HAKEMUSASIOINTI)
        val application1 = alluDataFactory.saveApplicationEntity(username = username, hanke = hanke)
        val application2 =
            alluDataFactory.saveApplicationEntity(username = "secondUser", hanke = hanke2)
        alluDataFactory.saveApplicationEntity(username = "thirdUser", hanke = hanke3)

        val response = applicationService.getAllApplicationsForUser(username).map { it.id }

        assertThat(applicationRepository.findAll()).hasSize(3)
        assertThat(response).containsExactlyInAnyOrder(application1.id, application2.id)
    }

    @Test
    fun `getApplicationById with unknown ID throws error`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> { applicationService.getApplicationById(1234) }

        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `getApplicationById returns correct application`() {
        val hanke = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1234"))
        val applications = alluDataFactory.saveApplicationEntities(3, username, hanke = hanke)
        val selectedId = applications[1].id!!
        assertThat(applicationRepository.findAll()).hasSize(3)

        val response = applicationService.getApplicationById(selectedId)

        assertEquals(selectedId, response.id)
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `create saves new application with correct IDs`() {
        val givenId: Long = 123456789
        val hanke = createHanke()
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(pendingOnClient = true)
        val newApplication =
            AlluDataFactory.createApplication(
                id = givenId,
                applicationData = cableReportApplicationData,
                hankeTunnus = hanke.hankeTunnus!!,
            )
        assertTrue(cableReportApplicationData.pendingOnClient)

        val response = applicationService.create(newApplication, username)

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
    fun `create sets pendingOnClient to true`() {
        val givenId: Long = 123456789
        val hanke = createHanke()
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(pendingOnClient = false)
        val newApplication =
            AlluDataFactory.createApplication(
                id = givenId,
                applicationData = cableReportApplicationData,
                hankeTunnus = hanke.hankeTunnus!!,
            )

        val response = applicationService.create(newApplication, username)

        assertTrue(response.applicationData.pendingOnClient)
        val savedApplication = applicationRepository.findById(response.id!!).get()
        assertTrue(savedApplication.applicationData.pendingOnClient)
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `create throws exception with invalid geometry`() {
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                geometry =
                    "/fi/hel/haitaton/hanke/geometria/invalid-geometry-collection.json".asJsonResource()
            )
        val hanke = createHanke()
        val newApplication =
            AlluDataFactory.createApplication(
                id = null,
                applicationData = cableReportApplicationData,
                hankeTunnus = hanke.hankeTunnus!!,
            )

        val exception =
            assertThrows<ApplicationGeometryException> {
                applicationService.create(newApplication, username)
            }

        assertEquals(
            """Invalid geometry received when creating a new application for user $username, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}""",
            exception.message
        )
    }

    @Test
    fun `create throws exception with invalid geometry in areas`() {
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                areas =
                    listOf(
                        ApplicationArea(
                            "area",
                            "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json".asJsonResource()
                        )
                    )
            )
        val newApplication =
            AlluDataFactory.createApplication(
                id = null,
                applicationData = cableReportApplicationData
            )

        val exception =
            assertThrows<ApplicationGeometryException> {
                applicationService.create(newApplication, username)
            }

        assertEquals(
            """Invalid geometry received when creating a new application for user $username, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}""",
            exception.message
        )
    }

    @Test
    fun `updateApplicationData with unknown ID throws exception`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> {
            applicationService.updateApplicationData(
                1234,
                AlluDataFactory.createCableReportApplicationData(),
                username
            )
        }
    }

    @Test
    fun `updateApplicationData saves new application data to database`() {
        val hanke = createHankeEntity()
        val application = alluDataFactory.saveApplicationEntity(username, hanke = hanke)
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

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
    fun `updateApplicationData with application that's already saved to Allu is updated in Allu`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 21 }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        justRun { cableReportServiceAllu.update(21, newApplicationData.toAlluData()) }
        justRun { cableReportServiceAllu.addAttachment(21, any()) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

        assertEquals(newApplicationData, response.applicationData)
        val savedApplication = applicationRepository.findById(application.id!!).orElseThrow()
        assertEquals(response.id, savedApplication.id)
        assertEquals(21, savedApplication.alluid)
        assertEquals(application.applicationType, savedApplication.applicationType)
        assertEquals(newApplicationData, savedApplication.applicationData)

        verifyOrder {
            cableReportServiceAllu.getApplicationInformation(21)
            cableReportServiceAllu.update(21, newApplicationData.toAlluData())
            cableReportServiceAllu.addAttachment(21, any())
        }
    }

    @Test
    fun `updateApplicationData doesn't save to database if Allu update fails`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 21 }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        every { cableReportServiceAllu.update(21, newApplicationData.toAlluData()) } throws
            RuntimeException("Allu call failed")

        val exception =
            assertThrows<RuntimeException> {
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    username
                )
            }

        assertEquals("Allu call failed", exception.message)
        val savedApplication = applicationRepository.findById(application.id!!).orElseThrow()
        assertEquals(AlluDataFactory.defaultApplicationName, savedApplication.applicationData.name)
        verifyOrder {
            cableReportServiceAllu.getApplicationInformation(21)
            cableReportServiceAllu.update(21, newApplicationData.toAlluData())
        }
    }

    @Test
    fun `updateApplicationData with application that's pending on Allu is updated in Allu`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Uudistettu johtoselvitys",
                pendingOnClient = false
            )
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        justRun { cableReportServiceAllu.update(21, newApplicationData.toAlluData()) }
        justRun { cableReportServiceAllu.addAttachment(21, any()) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

        assertEquals(21, response.alluid)
        assertEquals(newApplicationData, response.applicationData)

        val savedApplications = applicationRepository.findAll()
        assertThat(savedApplications).hasSize(1)
        val savedApplication = savedApplications[0]
        assertEquals(21, savedApplication.alluid)
        assertEquals(newApplicationData, savedApplication.applicationData)

        verifyOrder {
            cableReportServiceAllu.getApplicationInformation(21)
            cableReportServiceAllu.update(21, newApplicationData.toAlluData())
            cableReportServiceAllu.addAttachment(21, any())
        }
    }

    @Test
    fun `updateApplicationData with application that's pending on Allu is not updated on Allu if new data is invalid`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.apply {
                    alluid = 21
                    applicationData = applicationData.copy(pendingOnClient = false)
                }
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                startTime = null,
                pendingOnClient = false
            )
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)

        val exception =
            assertThrows<AlluDataException> {
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    username
                )
            }

        assertEquals(
            "Application data failed validation at applicationData.startTime: Can't be null",
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
    fun `updateApplicationData doesn't update pendingOnClient`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Päivitetty hakemus",
                pendingOnClient = true,
            )
        val expectedApplicationData = newApplicationData.copy(pendingOnClient = false).toAlluData()
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        justRun { cableReportServiceAllu.update(21, expectedApplicationData) }
        justRun { cableReportServiceAllu.addAttachment(21, any()) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

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
    fun `updateApplicationData with application that's already beyond pending in Allu is not updated`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 21 }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21, ApplicationStatus.HANDLING)

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)
        }

        val savedApplications = applicationRepository.findAll()
        assertThat(savedApplications).hasSize(1)
        val savedApplication = savedApplications[0]
        assertEquals(
            AlluDataFactory.defaultApplicationName,
            (savedApplication.applicationData as CableReportApplicationData).name
        )

        verify { cableReportServiceAllu.getApplicationInformation(21) }
        verify(exactly = 0) { cableReportServiceAllu.update(any(), any()) }
    }

    @Test
    fun `updateApplicationData throws exception with invalid geometry`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 21 }
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                geometry =
                    "/fi/hel/haitaton/hanke/geometria/invalid-geometry-collection.json".asJsonResource()
            )

        val exception =
            assertThrows<ApplicationGeometryException> {
                applicationService.updateApplicationData(
                    application.id!!,
                    cableReportApplicationData,
                    username
                )
            }

        assertEquals(
            """Invalid geometry received when updating application for user $username, id=${application.id}, alluid=${application.alluid}, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}""",
            exception.message
        )
    }

    @Test
    fun `updateApplicationData throws exception with invalid geometry in areas`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 21 }
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                areas =
                    listOf(
                        ApplicationArea(
                            "area",
                            "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json".asJsonResource()
                        )
                    )
            )

        val exception =
            assertThrows<ApplicationGeometryException> {
                applicationService.updateApplicationData(
                    application.id!!,
                    cableReportApplicationData,
                    username
                )
            }

        assertEquals(
            """Invalid geometry received when updating application for user $username, id=${application.id}, alluid=${application.alluid}, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}""",
            exception.message
        )
    }

    @Test
    fun `sendApplication with unknown ID throws exception`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> {
            applicationService.sendApplication(1234, username)
        }
    }

    @Test
    fun `sendApplication sets pendingOnClient to false`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = true)
            }
        val applicationData =
            application.applicationData.toAlluData() as AlluCableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        justRun { cableReportServiceAllu.update(21, pendingApplicationData) }
        justRun { cableReportServiceAllu.addAttachment(21, any()) }
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21, ApplicationStatus.PENDING)

        val response = applicationService.sendApplication(application.id!!, username)

        val responseApplicationData = response.applicationData as CableReportApplicationData
        assertFalse(responseApplicationData.pendingOnClient)
        val savedApplication = applicationRepository.findById(application.id!!).get()
        val savedApplicationData = savedApplication.applicationData as CableReportApplicationData
        assertFalse(savedApplicationData.pendingOnClient)

        verifyOrder {
            cableReportServiceAllu.getApplicationInformation(21)
            cableReportServiceAllu.update(21, pendingApplicationData)
            cableReportServiceAllu.addAttachment(21, any())
            cableReportServiceAllu.getApplicationInformation(21)
        }
    }

    @Test
    fun `sendApplication creates new application to Allu and saves ID and status to database`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = null }
        val applicationData = application.applicationData as CableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.create(pendingApplicationData.toAlluData()) } returns 26
        justRun { cableReportServiceAllu.addAttachment(26, any()) }
        every { cableReportServiceAllu.getApplicationInformation(26) } returns
            AlluDataFactory.createAlluApplicationResponse(26)

        val response = applicationService.sendApplication(application.id!!, username)

        assertEquals(26, response.alluid)
        assertEquals(pendingApplicationData, response.applicationData)
        assertEquals(AlluDataFactory.defaultApplicationIdentifier, response.applicationIdentifier)
        assertEquals(ApplicationStatus.PENDING, response.alluStatus)
        val savedApplication = applicationRepository.findById(application.id!!).get()
        assertEquals(26, savedApplication.alluid)
        assertEquals(pendingApplicationData, savedApplication.applicationData)
        assertEquals(
            AlluDataFactory.defaultApplicationIdentifier,
            savedApplication.applicationIdentifier
        )
        assertEquals(ApplicationStatus.PENDING, savedApplication.alluStatus)

        verifyOrder {
            cableReportServiceAllu.create(pendingApplicationData.toAlluData())
            cableReportServiceAllu.addAttachment(26, any())
            cableReportServiceAllu.getApplicationInformation(26)
        }
    }

    @Test
    fun `sendApplication with application that's been sent before is not sent again`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21, ApplicationStatus.PENDING)

        applicationService.sendApplication(application.id!!, username)

        verify { cableReportServiceAllu.getApplicationInformation(21) }
        verify(exactly = 0) { cableReportServiceAllu.update(any(), any()) }
    }

    @Test
    fun `sendApplication with application that's already beyond pending in Allu is not sent`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21, ApplicationStatus.DECISIONMAKING)

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.sendApplication(application.id!!, username)
        }

        verify { cableReportServiceAllu.getApplicationInformation(21) }
    }

    @Test
    fun `sendApplication sends application and saves alluid even if status query fails`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = null }
        val applicationData = application.applicationData as CableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.create(pendingApplicationData.toAlluData()) } returns 26
        justRun { cableReportServiceAllu.addAttachment(26, any()) }
        every { cableReportServiceAllu.getApplicationInformation(26) } throws
            AlluException(listOf())

        val response = applicationService.sendApplication(application.id!!, username)

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
            cableReportServiceAllu.create(pendingApplicationData.toAlluData())
            cableReportServiceAllu.addAttachment(26, any())
            cableReportServiceAllu.getApplicationInformation(26)
        }
    }

    @Test
    fun `delete with unknown ID throws exception`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> { applicationService.delete(1234, username) }
    }

    @Test
    fun `delete with an application not yet in Allu just deletes application`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = null }
        assertThat(applicationRepository.findAll()).hasSize(1)

        applicationService.delete(application.id!!, username)

        assertThat(applicationRepository.findAll()).isEmpty()
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `delete creates an audit log entry for delete application`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHanke()
        val application =
            applicationService.create(
                AlluDataFactory.createApplication(id = null, hankeTunnus = hanke.hankeTunnus!!),
                username
            )
        auditLogRepository.deleteAll()
        assertThat(auditLogRepository.findAll()).isEmpty()

        applicationService.delete(application.id!!, username)

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
        assertThat(event.actor::userId).isEqualTo(username)
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
    fun `delete with a pending application in Allu cancels application before deleting it`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 73 }
        assertThat(applicationRepository.findAll()).hasSize(1)
        every { cableReportServiceAllu.getApplicationInformation(73) } returns
            AlluDataFactory.createAlluApplicationResponse(73, ApplicationStatus.PENDING)
        justRun { cableReportServiceAllu.cancel(73) }

        applicationService.delete(application.id!!, username)

        assertThat(applicationRepository.findAll()).hasSize(0)
        verifyOrder {
            cableReportServiceAllu.getApplicationInformation(73)
            cableReportServiceAllu.cancel(73)
        }
    }

    @Test
    fun `delete with a non-pending application in Allu throws exception`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 73 }
        assertThat(applicationRepository.findAll()).hasSize(1)
        every { cableReportServiceAllu.getApplicationInformation(73) } returns
            AlluDataFactory.createAlluApplicationResponse(73, ApplicationStatus.APPROVED)

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.delete(application.id!!, username)
        }

        assertThat(applicationRepository.findAll()).hasSize(1)
        verifyOrder { cableReportServiceAllu.getApplicationInformation(73) }
    }

    @Test
    fun `downloadDecision with unknown ID throws exception`() {
        assertThrows<ApplicationNotFoundException> {
            applicationService.downloadDecision(1234, username)
        }
    }

    @Test
    fun `downloadDecision without alluid throws exception`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = null }

        assertThrows<ApplicationDecisionNotFoundException> {
            applicationService.downloadDecision(application.id!!, username)
        }

        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `downloadDecision without decision in Allu throws exception`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = 134 }
        every { cableReportServiceAllu.getDecisionPdf(134) }
            .throws(ApplicationDecisionNotFoundException(""))

        assertThrows<ApplicationDecisionNotFoundException> {
            applicationService.downloadDecision(application.id!!, username)
        }

        verify { cableReportServiceAllu.getDecisionPdf(134) }
    }

    @Test
    fun `downloadDecision returns application identifier with the PDF bytes`() {
        val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.alluid = 134
                it.applicationIdentifier = "JS230001"
            }
        every { cableReportServiceAllu.getDecisionPdf(134) }.returns(pdfBytes)

        val (filename, bytes) = applicationService.downloadDecision(application.id!!, username)

        assertThat(filename).isNotNull().isEqualTo("JS230001")
        assertThat(bytes).isEqualTo(pdfBytes)
        verify { cableReportServiceAllu.getDecisionPdf(134) }
    }

    @Test
    fun `downloadDecision returns default filename when application has no identifier`() {
        val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(username, hanke = hanke) {
                it.alluid = 134
                it.applicationIdentifier = null
            }
        every { cableReportServiceAllu.getDecisionPdf(134) }.returns(pdfBytes)

        val (filename, bytes) = applicationService.downloadDecision(application.id!!, username)

        assertThat(filename).isNotNull().isEqualTo("paatos")
        assertThat(bytes).isEqualTo(pdfBytes)
        verify { cableReportServiceAllu.getDecisionPdf(134) }
    }

    // TODO: Needs Spring 5.3, which comes with Spring Boot 2.4.
    //  Inner test classes won't inherit properties from the enclosing class until then.
    // @Nested class HandleApplicationUpdates {

    /** The timestamp used in the initial DB migration. */
    private val placeholderUpdateTime = OffsetDateTime.parse("2017-01-01T00:00:00Z")
    private val updateTime = OffsetDateTime.parse("2022-10-09T06:36:51Z")
    private val alluid = 42

    @Test
    fun `handleApplicationUpdates with empty histories updates the last updated time`() {
        assertThat(applicationRepository.findAll()).isEmpty()
        assertEquals(placeholderUpdateTime, alluStatusRepository.getLastUpdateTime().asUtc())

        applicationService.handleApplicationUpdates(listOf(), updateTime)

        assertEquals(updateTime, alluStatusRepository.getLastUpdateTime().asUtc())
    }

    @Test
    fun `handleApplicationUpdates updates the application statuses in the correct order`() {
        assertThat(applicationRepository.findAll()).isEmpty()
        assertEquals(placeholderUpdateTime, alluStatusRepository.getLastUpdateTime().asUtc())
        val hanke = createHankeEntity()
        alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = alluid }
        val firstEventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z")
        val history =
            ApplicationHistoryFactory.create(applicationId = alluid)
                .copy(
                    events =
                        listOf(
                            ApplicationHistoryFactory.createEvent(
                                firstEventTime.plusDays(5),
                                ApplicationStatus.PENDING
                            ),
                            ApplicationHistoryFactory.createEvent(
                                firstEventTime.plusDays(10),
                                ApplicationStatus.HANDLING
                            ),
                            ApplicationHistoryFactory.createEvent(
                                firstEventTime,
                                ApplicationStatus.PENDING
                            ),
                        )
                )

        applicationService.handleApplicationUpdates(listOf(history), updateTime)

        assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
        val application = applicationRepository.getOneByAlluid(alluid)
        assertThat(application)
            .isNotNull()
            .prop("alluStatus", ApplicationEntity::alluStatus)
            .isEqualTo(ApplicationStatus.HANDLING)
        assertThat(application!!.applicationIdentifier)
            .isEqualTo(ApplicationHistoryFactory.defaultApplicationIdentifier)
    }

    @Test
    fun `handleApplicationUpdates ignores missing application`() {
        assertThat(applicationRepository.findAll()).isEmpty()
        assertEquals(placeholderUpdateTime, alluStatusRepository.getLastUpdateTime().asUtc())
        val hanke = createHankeEntity()
        alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = alluid }
        alluDataFactory.saveApplicationEntity(username, hanke = hanke) { it.alluid = alluid + 2 }
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
            .containsExactlyInAnyOrder(
                ApplicationStatus.PENDING_CLIENT,
                ApplicationStatus.PENDING_CLIENT
            )
        assertThat(applications.map { it.applicationIdentifier })
            .containsExactlyInAnyOrder("JS2300082", "JS2300084")
    }

    @Test
    fun `Creating an application without hankeTunnus fails`() {
        assertThrows<HankeNotFoundException> {
            applicationService.create(
                AlluDataFactory.createApplication(id = null, hankeTunnus = ""),
                username
            )
        }
    }

    val customerWithContactsJson =
        """
           "customer": {
             "type": "COMPANY",
             "name": "DNA",
             "country": "FI",
             "postalAddress": {
               "streetAddress": {
                 "streetName": "Katu 1"
               },
               "postalCode": "00100",
               "city": "Helsinki"
             },
             "email": "info@dna.test",
             "phone": "+3581012345678",
             "registryKey": "3766028-0",
             "ovt": null,
             "invoicingOperator": null,
             "sapCustomerNumber": null
           },
           "contacts": [
             {
               "name": "Teppo Testihenkilö",
               "postalAddress": {
                 "streetAddress": {
                   "streetName": "Katu 1"
                 },
                 "postalCode": "00100",
                 "city": "Helsinki"
               },
               "email": "teppo@example.test",
               "phone": "04012345678",
               "orderer": false
             }
           ]
        """

    private fun expectedLogObject(
        id: Long?,
        alluId: Int?,
        name: String = AlluDataFactory.defaultApplicationName,
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
                  $customerWithContactsJson
                },
                "geometry": {
                  "type": "GeometryCollection",
                  "geometries": []
                },
                "areas": null,
                "startTime": "${nextYear()}-02-20T23:45:56Z",
                "endTime": "${nextYear()}-02-21T00:12:34Z",
                "pendingOnClient": true,
                "identificationNumber": "identification",
                "clientApplicationKind": "applicationKind",
                "workDescription": "Work description.",
                "rockExcavation": false,
                "contractorWithContacts": {
                  $customerWithContactsJson
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
}
