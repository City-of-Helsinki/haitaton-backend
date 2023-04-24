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
import assertk.assertions.matches
import assertk.assertions.prop
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeStatus
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
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.permissions.KayttajaTunnisteRepository
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import fi.hel.haitaton.hanke.permissions.kayttajaTunnistePattern
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.junit.jupiter.Testcontainers

private const val USERNAME = "test7358"

private val dataWithoutAreas = AlluDataFactory.createCableReportApplicationData(areas = listOf())

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(USERNAME)
class ApplicationServiceITest : DatabaseTest() {
    @MockkBean private lateinit var cableReportServiceAllu: CableReportService
    @Autowired private lateinit var applicationService: ApplicationService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService

    @Autowired private lateinit var applicationRepository: ApplicationRepository
    @Autowired private lateinit var hankeRepository: HankeRepository
    @Autowired private lateinit var alluStatusRepository: AlluStatusRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var kayttajaTunnisteRepository: KayttajaTunnisteRepository
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
                AlluDataFactory.createApplication(
                    id = null,
                    hankeTunnus = hanke.hankeTunnus!!,
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
    fun `updateApplicationData creates an audit log entry for updated application`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHanke()
        val application =
            applicationService.create(
                AlluDataFactory.createApplication(
                    id = null,
                    hankeTunnus = hanke.hankeTunnus!!,
                    applicationData = dataWithoutAreas
                ),
                USERNAME
            )
        auditLogRepository.deleteAll()
        assertThat(auditLogRepository.findAll()).isEmpty()
        every { cableReportServiceAllu.create(any()) }.returns(2)

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
    fun `updateApplicationData doesn't create an audit log entry if the application hasn't changed`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = null }
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
    fun `updateApplicationData doesn't send application to Allu if it hasn't changed`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = 2 }
        assertThat(auditLogRepository.findAll()).isEmpty()

        applicationService.updateApplicationData(
            application.id!!,
            application.applicationData,
            USERNAME
        )

        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `getAllApplicationsForUser with no applications returns empty list`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        val response = applicationService.getAllApplicationsForUser(USERNAME)

        assertEquals(listOf<Application>(), response)
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `getAllApplicationsForUser returns applications for the correct user`() {
        assertThat(applicationRepository.findAll()).isEmpty()
        val otherUser = "otherUser"
        val hanke = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1234"))
        val hanke2 = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1235"))
        permissionService.setPermission(hanke.id!!, USERNAME, Role.HAKEMUSASIOINTI)
        permissionService.setPermission(hanke2.id!!, "otherUser", Role.HAKEMUSASIOINTI)

        alluDataFactory.saveApplicationEntities(3, USERNAME, hanke = hanke) { _, application ->
            application.userId = USERNAME
            application.applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    name = "Application data for $USERNAME"
                )
        }
        alluDataFactory.saveApplicationEntities(3, "otherUser", hanke = hanke2)

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
    fun `getAllApplicationsForUser returns applications for user hankkeet`() {
        assertThat(applicationRepository.findAll()).isEmpty()
        val hanke = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1234"))
        val hanke2 = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1235"))
        val hanke3 = hankeRepository.save(HankeEntity(hankeTunnus = "HAI-1236"))
        permissionService.setPermission(hanke.id!!, USERNAME, Role.HAKEMUSASIOINTI)
        permissionService.setPermission(hanke2.id!!, USERNAME, Role.HAKEMUSASIOINTI)
        val application1 = alluDataFactory.saveApplicationEntity(username = USERNAME, hanke = hanke)
        val application2 =
            alluDataFactory.saveApplicationEntity(username = "secondUser", hanke = hanke2)
        alluDataFactory.saveApplicationEntity(username = "thirdUser", hanke = hanke3)

        val response = applicationService.getAllApplicationsForUser(USERNAME).map { it.id }

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
        val applications = alluDataFactory.saveApplicationEntities(3, USERNAME, hanke = hanke)
        val selectedId = applications[1].id!!
        assertThat(applicationRepository.findAll()).hasSize(3)

        val response = applicationService.getApplicationById(selectedId)

        assertEquals(selectedId, response.id)
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `create saves new application with correct IDs`() {
        val givenId: Long = 123456789
        val hanke = initializedHanke()
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                pendingOnClient = true,
                areas = listOf(aleksanterinpatsas)
            )
        val newApplication =
            AlluDataFactory.createApplication(
                id = givenId,
                applicationData = cableReportApplicationData,
                hankeTunnus = hanke.hankeTunnus!!,
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
    fun `create sets pendingOnClient to true`() {
        val givenId: Long = 123456789
        val hanke = initializedHanke()
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                pendingOnClient = false,
                areas = listOf(aleksanterinpatsas)
            )
        val newApplication =
            AlluDataFactory.createApplication(
                id = givenId,
                applicationData = cableReportApplicationData,
                hankeTunnus = hanke.hankeTunnus!!,
            )

        val response = applicationService.create(newApplication, USERNAME)

        assertTrue(response.applicationData.pendingOnClient)
        val savedApplication = applicationRepository.findById(response.id!!).get()
        assertTrue(savedApplication.applicationData.pendingOnClient)
        verify { cableReportServiceAllu wasNot Called }
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
    fun `create throws exception when application area is outside hankealue`() {
        val hanke = hankeService.createHanke(HankeFactory.create().withHankealue())
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(areas = listOf(havisAmanda))
        val newApplication =
            AlluDataFactory.createApplication(
                id = null,
                hankeTunnus = hanke.hankeTunnus!!,
                applicationData = cableReportApplicationData
            )

        assertThrows<ApplicationGeometryNotInsideHankeException> {
            applicationService.create(newApplication, USERNAME)
        }
    }

    @Test
    fun `create when hanke was generated skips verify areas inside hankealue succeeds`() {
        val applicationInput = AlluDataFactory.cableReportWithoutHanke()

        val result = hankeService.generateHankeWithApplication(applicationInput, USERNAME)

        with(result) {
            val application = applications.first()
            assertEquals(application.applicationData.name, hanke.nimi)
            assertEquals(true, hanke.generated)
            assertEquals(HankeStatus.DRAFT, hanke.status)
        }
    }

    @Test
    fun `updateApplicationData with unknown ID throws exception`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> {
            applicationService.updateApplicationData(
                1234,
                AlluDataFactory.createCableReportApplicationData(),
                USERNAME
            )
        }
    }

    @Test
    fun `updateApplicationData when hanke was generated should skip area inside hanke check`() {
        val initial =
            hankeService.generateHankeWithApplication(
                AlluDataFactory.cableReportWithoutHanke(),
                USERNAME
            )
        val initialApplication = initial.applications.first()
        assertFalse(initialApplication.applicationData.areas.isNullOrEmpty())
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                pendingOnClient = true,
                name = "Uudistettu johtoselvitys",
                areas = initialApplication.applicationData.areas
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
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `updateApplicationData saves new application data to database`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            )
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Uudistettu johtoselvitys",
                areas = application.applicationData.areas
            )

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, USERNAME)

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
    fun `updateApplicationData with application that's already saved to Allu is updated in Allu`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) { it.alluid = 21 }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Uudistettu johtoselvitys",
                areas = application.applicationData.areas
            )
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        justRun { cableReportServiceAllu.update(21, newApplicationData.toAlluData()) }
        justRun { cableReportServiceAllu.addAttachment(21, any()) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, USERNAME)

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
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `updateApplicationData doesn't save to database if Allu update fails`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) { it.alluid = 21 }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Uudistettu johtoselvitys",
                areas = application.applicationData.areas
            )
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        every { cableReportServiceAllu.update(21, newApplicationData.toAlluData()) } throws
            RuntimeException("Allu call failed")

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
        assertEquals(AlluDataFactory.defaultApplicationName, savedApplication.applicationData.name)
        verifyOrder {
            cableReportServiceAllu.getApplicationInformation(21)
            cableReportServiceAllu.update(21, newApplicationData.toAlluData())
        }
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `updateApplicationData with application that's pending on Allu is updated in Allu`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Uudistettu johtoselvitys",
                pendingOnClient = false,
                areas = application.applicationData.areas
            )
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        justRun { cableReportServiceAllu.update(21, newApplicationData.toAlluData()) }
        justRun { cableReportServiceAllu.addAttachment(21, any()) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, USERNAME)

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
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `updateApplicationData with application that's pending on Allu is not updated on Allu if new data is invalid`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) {
                it.apply {
                    alluid = 21
                    applicationData = applicationData.copy(pendingOnClient = false)
                }
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                startTime = null,
                pendingOnClient = false,
                areas = application.applicationData.areas
            )
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)

        val exception =
            assertThrows<AlluDataException> {
                applicationService.updateApplicationData(
                    application.id!!,
                    newApplicationData,
                    USERNAME
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
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `updateApplicationData doesn't update pendingOnClient`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Päivitetty hakemus",
                pendingOnClient = true,
                areas = application.applicationData.areas
            )
        val expectedApplicationData = newApplicationData.copy(pendingOnClient = false).toAlluData()
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21)
        justRun { cableReportServiceAllu.update(21, expectedApplicationData) }
        justRun { cableReportServiceAllu.addAttachment(21, any()) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, USERNAME)

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
    fun `updateApplicationData with application that's already beyond pending in Allu is not updated`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) { it.alluid = 21 }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Uudistettu johtoselvitys",
                areas = application.applicationData.areas
            )
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21, ApplicationStatus.HANDLING)

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.updateApplicationData(application.id!!, newApplicationData, USERNAME)
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
    fun `updateApplicationData throws exception with invalid geometry in areas`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = 21 }
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
    fun `updateApplicationData throws exception when application area is outside hankealue`() {
        val hanke = hankeService.createHanke(HankeFactory.create().withHankealue())
        val hankeEntity = hankeRepository.getOne(hanke.id!!)
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hankeEntity) { it.alluid = 21 }
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(areas = listOf(havisAmanda))

        assertThrows<ApplicationGeometryNotInsideHankeException> {
            applicationService.updateApplicationData(
                application.id!!,
                cableReportApplicationData,
                USERNAME
            )
        }
    }

    @Test
    fun `sendApplication with unknown ID throws exception`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> {
            applicationService.sendApplication(1234, USERNAME)
        }
    }

    @Test
    fun `sendApplication when generated hanke should skip area inside hanke check`() {
        val initial =
            hankeService.generateHankeWithApplication(
                AlluDataFactory.cableReportWithoutHanke(),
                USERNAME
            )
        val initialApplication = initial.applications.first()
        assertFalse(initialApplication.applicationData.areas.isNullOrEmpty())
        val alluIdMock = 123
        every { cableReportServiceAllu.create(any()) } returns alluIdMock
        every { cableReportServiceAllu.getApplicationInformation(alluIdMock) } returns
            AlluDataFactory.createAlluApplicationResponse(alluIdMock)
        justRun { cableReportServiceAllu.addAttachment(any(), any()) }

        applicationService.sendApplication(initialApplication.id!!, USERNAME)

        verify { cableReportServiceAllu.create(any()) }
        verify { cableReportServiceAllu.addAttachment(any(), any()) }
        verify { cableReportServiceAllu.getApplicationInformation(any()) }
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `sendApplication sets pendingOnClient to false`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application =
                    mockApplicationWithArea(
                        AlluDataFactory.createCableReportApplicationData(
                            pendingOnClient = true,
                            areas = listOf(aleksanterinpatsas)
                        )
                    )
            ) {
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

        val response = applicationService.sendApplication(application.id!!, USERNAME)

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
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `sendApplication creates new application to Allu and saves ID and status to database`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) { it.alluid = null }
        val applicationData = application.applicationData as CableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.create(pendingApplicationData.toAlluData()) } returns 26
        justRun { cableReportServiceAllu.addAttachment(26, any()) }
        every { cableReportServiceAllu.getApplicationInformation(26) } returns
            AlluDataFactory.createAlluApplicationResponse(26)

        val response = applicationService.sendApplication(application.id!!, USERNAME)

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
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `sendApplication saves user tokens from application contacts`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) { it.alluid = null }
        val applicationData = application.applicationData as CableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.create(pendingApplicationData.toAlluData()) } returns 26
        justRun { cableReportServiceAllu.addAttachment(26, any()) }
        every { cableReportServiceAllu.getApplicationInformation(26) } returns
            AlluDataFactory.createAlluApplicationResponse(26)

        applicationService.sendApplication(application.id!!, USERNAME)

        val tunnisteet = kayttajaTunnisteRepository.findAll()
        assertThat(tunnisteet).hasSize(1)
        assertThat(tunnisteet[0].role).isEqualTo(Role.KATSELUOIKEUS)
        assertThat(tunnisteet[0].createdAt).isRecent()
        assertThat(tunnisteet[0].sentAt).isNull()
        assertThat(tunnisteet[0].tunniste).matches(Regex(kayttajaTunnistePattern))
        assertThat(tunnisteet[0].hankeKayttaja).isNotNull()
        val kayttaja = tunnisteet[0].hankeKayttaja!!
        assertThat(kayttaja.nimi).isEqualTo("Teppo Testihenkilö")
        assertThat(kayttaja.sahkoposti).isEqualTo("teppo@example.test")
        assertThat(kayttaja.hankeId).isEqualTo(application.hanke.id)
        assertThat(kayttaja.permission).isNull()
        assertThat(kayttaja.kayttajaTunniste).isNotNull()
        verifyOrder {
            cableReportServiceAllu.create(pendingApplicationData.toAlluData())
            cableReportServiceAllu.addAttachment(26, any())
            cableReportServiceAllu.getApplicationInformation(26)
        }
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `sendApplication with application that's been sent before is not sent again`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21, ApplicationStatus.PENDING)

        applicationService.sendApplication(application.id!!, USERNAME)

        verify { cableReportServiceAllu.getApplicationInformation(21) }
        verify(exactly = 0) { cableReportServiceAllu.update(any(), any()) }
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `sendApplication with application that's already beyond pending in Allu is not sent`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) {
                it.alluid = 21
                it.applicationData = it.applicationData.copy(pendingOnClient = false)
            }
        every { cableReportServiceAllu.getApplicationInformation(21) } returns
            AlluDataFactory.createAlluApplicationResponse(21, ApplicationStatus.DECISIONMAKING)

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.sendApplication(application.id!!, USERNAME)
        }

        verify { cableReportServiceAllu.getApplicationInformation(21) }
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `sendApplication sends application and saves alluid even if status query fails`() {
        val application =
            alluDataFactory.saveApplicationEntity(
                USERNAME,
                hanke = initializedHanke(),
                application = mockApplicationWithArea()
            ) { it.alluid = null }
        val applicationData = application.applicationData as CableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.create(pendingApplicationData.toAlluData()) } returns 26
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
            cableReportServiceAllu.create(pendingApplicationData.toAlluData())
            cableReportServiceAllu.addAttachment(26, any())
            cableReportServiceAllu.getApplicationInformation(26)
        }
    }

    @Test
    fun `sendApplication throws exception when application area is outside hankealue`() {
        val hanke = hankeService.createHanke(HankeFactory.create().withHankealue())
        val hankeEntity = hankeRepository.getOne(hanke.id!!)
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hankeEntity) {
                it.applicationData =
                    AlluDataFactory.createCableReportApplicationData(areas = listOf(havisAmanda))
            }

        assertThrows<ApplicationGeometryNotInsideHankeException> {
            applicationService.sendApplication(application.id!!, USERNAME)
        }
    }

    @Test
    fun `delete with unknown ID throws exception`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> { applicationService.delete(1234, USERNAME) }
    }

    @Test
    fun `delete with an application not yet in Allu just deletes application`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = null }
        assertThat(applicationRepository.findAll()).hasSize(1)

        applicationService.delete(application.id!!, USERNAME)

        assertThat(applicationRepository.findAll()).isEmpty()
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `delete creates an audit log entry for delete application`() {
        TestUtils.addMockedRequestIp()
        val hanke = createHanke()
        val application =
            applicationService.create(
                AlluDataFactory.createApplication(
                    id = null,
                    hankeTunnus = hanke.hankeTunnus!!,
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
    fun `delete with a pending application in Allu cancels application before deleting it`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = 73 }
        assertThat(applicationRepository.findAll()).hasSize(1)
        every { cableReportServiceAllu.getApplicationInformation(73) } returns
            AlluDataFactory.createAlluApplicationResponse(73, ApplicationStatus.PENDING)
        justRun { cableReportServiceAllu.cancel(73) }

        applicationService.delete(application.id!!, USERNAME)

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
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = 73 }
        assertThat(applicationRepository.findAll()).hasSize(1)
        every { cableReportServiceAllu.getApplicationInformation(73) } returns
            AlluDataFactory.createAlluApplicationResponse(73, ApplicationStatus.APPROVED)

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.delete(application.id!!, USERNAME)
        }

        assertThat(applicationRepository.findAll()).hasSize(1)
        verifyOrder { cableReportServiceAllu.getApplicationInformation(73) }
    }

    @Test
    fun `downloadDecision with unknown ID throws exception`() {
        assertThrows<ApplicationNotFoundException> {
            applicationService.downloadDecision(1234, USERNAME)
        }
    }

    @Test
    fun `downloadDecision without alluid throws exception`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = null }

        assertThrows<ApplicationDecisionNotFoundException> {
            applicationService.downloadDecision(application.id!!, USERNAME)
        }

        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `downloadDecision without decision in Allu throws exception`() {
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = 134 }
        every { cableReportServiceAllu.getDecisionPdf(134) }
            .throws(ApplicationDecisionNotFoundException(""))

        assertThrows<ApplicationDecisionNotFoundException> {
            applicationService.downloadDecision(application.id!!, USERNAME)
        }

        verify { cableReportServiceAllu.getDecisionPdf(134) }
    }

    @Test
    fun `downloadDecision returns application identifier with the PDF bytes`() {
        val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) {
                it.alluid = 134
                it.applicationIdentifier = "JS230001"
            }
        every { cableReportServiceAllu.getDecisionPdf(134) }.returns(pdfBytes)

        val (filename, bytes) = applicationService.downloadDecision(application.id!!, USERNAME)

        assertThat(filename).isNotNull().isEqualTo("JS230001")
        assertThat(bytes).isEqualTo(pdfBytes)
        verify { cableReportServiceAllu.getDecisionPdf(134) }
    }

    @Test
    fun `downloadDecision returns default filename when application has no identifier`() {
        val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
        val hanke = createHankeEntity()
        val application =
            alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) {
                it.alluid = 134
                it.applicationIdentifier = null
            }
        every { cableReportServiceAllu.getDecisionPdf(134) }.returns(pdfBytes)

        val (filename, bytes) = applicationService.downloadDecision(application.id!!, USERNAME)

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
        alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = alluid }
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
        alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = alluid }
        alluDataFactory.saveApplicationEntity(USERNAME, hanke = hanke) { it.alluid = alluid + 2 }
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
                USERNAME
            )
        }
    }

    @ParameterizedTest(name = "{displayName} ({arguments})")
    @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
    fun `isStillPending when status is pending and allu confirms should return true`(
        status: ApplicationStatus
    ) {
        val alluId = 123
        val application = AlluDataFactory.createApplication(alluid = alluId, alluStatus = status)
        every { cableReportServiceAllu.getApplicationInformation(alluId) } returns
            AlluDataFactory.createAlluApplicationResponse(status = status)

        assertTrue(applicationService.isStillPending(application))

        verify { cableReportServiceAllu.getApplicationInformation(alluId) }
    }

    @ParameterizedTest(name = "{displayName} ({arguments})")
    @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
    fun `isStillPending when status is pending but status in allu in handling should return false`(
        status: ApplicationStatus
    ) {
        val alluId = 123
        val application = AlluDataFactory.createApplication(alluid = alluId, alluStatus = status)
        every { cableReportServiceAllu.getApplicationInformation(alluId) } returns
            AlluDataFactory.createAlluApplicationResponse(status = ApplicationStatus.HANDLING)

        assertFalse(applicationService.isStillPending(application))

        verify { cableReportServiceAllu.getApplicationInformation(alluId) }
    }

    @ParameterizedTest(name = "{displayName} ({arguments})")
    @EnumSource(
        value = ApplicationStatus::class,
        mode = EnumSource.Mode.EXCLUDE,
        names = ["PENDING", "PENDING_CLIENT"]
    )
    fun `isStillPending when status is not pending should return false`(status: ApplicationStatus) {
        val alluId = 123
        val application = AlluDataFactory.createApplication(alluid = alluId, alluStatus = status)

        assertFalse(applicationService.isStillPending(application))

        verify { cableReportServiceAllu wasNot Called }
    }

    private fun initializedHanke(): HankeEntity =
        hankeRepository.findByHankeTunnus("HAI23-5") ?: throw NoSuchElementException()

    private val aleksanterinpatsas: ApplicationArea =
        AlluDataFactory.createApplicationArea(
            geometry = "/fi/hel/haitaton/hanke/geometria/aleksanterin-patsas.json".asJsonResource()
        )

    private val havisAmanda: ApplicationArea =
        AlluDataFactory.createApplicationArea(
            geometry = "/fi/hel/haitaton/hanke/geometria/havis-amanda.json".asJsonResource()
        )

    private fun mockApplicationWithArea(
        applicationData: ApplicationData =
            AlluDataFactory.createCableReportApplicationData(areas = listOf(aleksanterinpatsas))
    ): Application = AlluDataFactory.createApplication(applicationData = applicationData)

    val customerWithContactsJson =
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
                "areas": [],
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
