package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.TestUtils
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    @MockkBean private lateinit var cableReportServiceAllu: CableReportServiceAllu
    @Autowired private lateinit var applicationService: ApplicationService

    @Autowired private lateinit var applicationRepository: ApplicationRepository
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

    @Test
    fun `create creates an audit log entry for created application`() {
        TestUtils.addMockedRequestIp()
        every { cableReportServiceAllu.create(any()) }.returns(2)

        val application =
            applicationService.create(AlluDataFactory.createApplication(id = null), username)

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
        val expectedObject = expectedLogObject(application.id, 2)
        JSONAssert.assertEquals(
            expectedObject,
            event.target.objectAfter,
            JSONCompareMode.NON_EXTENSIBLE
        )
        verify { cableReportServiceAllu.create(any()) }
    }

    @Test
    fun `updateHanke creates an audit log entry for updated application`() {
        TestUtils.addMockedRequestIp()
        every { cableReportServiceAllu.create(any()) }.returns(2)
        every { cableReportServiceAllu.getCurrentStatus(any()) }.returns(null)
        justRun { cableReportServiceAllu.update(any(), any()) }
        val application =
            applicationService.create(AlluDataFactory.createApplication(id = null), username)
        auditLogRepository.deleteAll()
        assertThat(auditLogRepository.findAll()).isEmpty()

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
        val expectedObjectBefore = expectedLogObject(application.id, 2)
        JSONAssert.assertEquals(
            expectedObjectBefore,
            event.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
        val expectedObjectAfter = expectedLogObject(application.id, 2, "Modified application")
        JSONAssert.assertEquals(
            expectedObjectAfter,
            event.target.objectAfter,
            JSONCompareMode.NON_EXTENSIBLE
        )
        verify { cableReportServiceAllu.create(any()) }
        verify { cableReportServiceAllu.getCurrentStatus(2) }
        verify { cableReportServiceAllu.update(2, any()) }
    }

    @Test
    fun `updateHanke doesn't create an audit log entry if the application hasn't changed`() {
        TestUtils.addMockedRequestIp()
        every { cableReportServiceAllu.create(any()) }.returns(2)
        every { cableReportServiceAllu.getCurrentStatus(any()) }.returns(null)
        justRun { cableReportServiceAllu.update(any(), any()) }
        val application =
            applicationService.create(AlluDataFactory.createApplication(id = null), username)
        auditLogRepository.deleteAll()
        assertThat(auditLogRepository.findAll()).isEmpty()

        applicationService.updateApplicationData(
            application.id!!,
            application.applicationData,
            username
        )

        val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
        assertThat(applicationLogs).isEmpty()
        verify { cableReportServiceAllu.create(any()) }
        verify { cableReportServiceAllu.getCurrentStatus(2) }
        verify { cableReportServiceAllu.update(2, any()) }
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
        alluDataFactory.saveApplicationEntities(6, "otherUser") { i, application ->
            if (i % 2 == 0) {
                application.apply {
                    this.userId = username
                    this.applicationData =
                        AlluDataFactory.createCableReportApplicationData(
                            name = "Application data for $username"
                        )
                }
            } else {
                application
            }
        }
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
    fun `getApplicationById with unknown ID throws error`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> {
            applicationService.getApplicationById(1234, username)
        }

        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `getApplicationById returns correct application`() {
        val applications = alluDataFactory.saveApplicationEntities(3, username)
        val selectedId = applications[1].id!!
        assertThat(applicationRepository.findAll()).hasSize(3)

        val response = applicationService.getApplicationById(selectedId, username)

        assertEquals(selectedId, response.id)
        verify { cableReportServiceAllu wasNot Called }
    }

    @Test
    fun `create saves new application with correct IDs`() {
        val givenId: Long = 123456789
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(pendingOnClient = true)
        val newApplication =
            AlluDataFactory.createApplication(
                id = givenId,
                applicationData = cableReportApplicationData
            )
        every { cableReportServiceAllu.create(cableReportApplicationData) } returns 21
        assertTrue(cableReportApplicationData.pendingOnClient)

        val response = applicationService.create(newApplication, username)

        assertNotEquals(givenId, response.id)
        assertEquals(21, response.alluid)
        assertEquals(newApplication.applicationType, response.applicationType)
        assertEquals(newApplication.applicationData, response.applicationData)
        assertTrue(response.applicationData.pendingOnClient)
        assertTrue(cableReportApplicationData.pendingOnClient)

        val savedApplications = applicationRepository.findAll()
        assertThat(savedApplications).hasSize(1)
        val savedApplication = savedApplications[0]
        assertEquals(response.id, savedApplication.id)
        assertEquals(21, savedApplication.alluid)
        assertEquals(newApplication.applicationType, savedApplication.applicationType)
        assertEquals(newApplication.applicationData, savedApplication.applicationData)
        assertTrue(savedApplication.applicationData.pendingOnClient)

        verify { cableReportServiceAllu.create(cableReportApplicationData) }
    }

    @Test
    fun `create sets pendingOnClient to true`() {
        val givenId: Long = 123456789
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(pendingOnClient = false)
        val newApplication =
            AlluDataFactory.createApplication(
                id = givenId,
                applicationData = cableReportApplicationData
            )
        val expectedApplicationData = cableReportApplicationData.copy(pendingOnClient = true)
        every { cableReportServiceAllu.create(expectedApplicationData) } returns 21

        val response = applicationService.create(newApplication, username)

        assertTrue(response.applicationData.pendingOnClient)
        val savedApplication = applicationRepository.findById(response.id!!).get()
        assertTrue(savedApplication.applicationData.pendingOnClient)
        verify { cableReportServiceAllu.create(expectedApplicationData) }
    }

    @Test
    fun `create saves new application without Allu ID if sending to Allu fails`() {
        val cableReportApplicationData =
            AlluDataFactory.createCableReportApplicationData(pendingOnClient = true)
        val newApplication =
            AlluDataFactory.createApplication(
                id = null,
                applicationData = cableReportApplicationData
            )
        every { cableReportServiceAllu.create(cableReportApplicationData) } throws
            RuntimeException()

        val response = applicationService.create(newApplication, username)

        assertNotNull(response.id)
        assertNull(response.alluid)

        val savedApplications = applicationRepository.findAll()
        assertThat(savedApplications).hasSize(1)
        val savedApplication = savedApplications[0]
        assertNotNull(savedApplication.id)
        assertNull(savedApplication.alluid)

        verify { cableReportServiceAllu.create(cableReportApplicationData) }
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
        val application = alluDataFactory.saveApplicationEntity(username)
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")
        every { cableReportServiceAllu.create(newApplicationData) } returns 21

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

        assertEquals(21, response.alluid)
        assertEquals(application.applicationType, response.applicationType)
        assertEquals(newApplicationData, response.applicationData)

        val savedApplications = applicationRepository.findAll()
        assertThat(savedApplications).hasSize(1)
        val savedApplication = savedApplications[0]
        assertEquals(response.id, savedApplication.id)
        assertEquals(21, savedApplication.alluid)
        assertEquals(application.applicationType, savedApplication.applicationType)
        assertEquals(newApplicationData, savedApplication.applicationData)

        verify { cableReportServiceAllu.create(newApplicationData) }
    }

    @Test
    fun `updateApplicationData with application that's already saved to Allu is updated in Allu`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) { it.apply { alluid = 21 } }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")
        every { cableReportServiceAllu.getCurrentStatus(21) } returns null
        justRun { cableReportServiceAllu.update(21, newApplicationData) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

        assertEquals(newApplicationData, response.applicationData)

        val savedApplication = applicationRepository.findById(application.id!!).orElseThrow()
        assertEquals(response.id, savedApplication.id)
        assertEquals(21, savedApplication.alluid)
        assertEquals(application.applicationType, savedApplication.applicationType)
        assertEquals(newApplicationData, savedApplication.applicationData)

        verify {
            cableReportServiceAllu.getCurrentStatus(21)
            cableReportServiceAllu.update(21, newApplicationData)
        }
    }

    @Test
    fun `updateApplicationData updates local database even if Allu update fails`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) { it.apply { alluid = 21 } }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")
        every { cableReportServiceAllu.getCurrentStatus(21) } returns null
        every { cableReportServiceAllu.update(21, newApplicationData) } throws RuntimeException()

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

        assertEquals(21, response.alluid)
        assertEquals(application.applicationType, response.applicationType)
        assertEquals(newApplicationData, response.applicationData)

        val savedApplication = applicationRepository.findById(application.id!!).orElseThrow()
        assertEquals(response.id, savedApplication.id)
        assertEquals(21, savedApplication.alluid)
        assertEquals(application.applicationType, savedApplication.applicationType)
        assertEquals(newApplicationData, savedApplication.applicationData)

        verify {
            cableReportServiceAllu.getCurrentStatus(21)
            cableReportServiceAllu.update(21, newApplicationData)
        }
    }

    @Test
    fun `updateApplicationData with application that's pending on Allu is updated in Allu`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) {
                it.apply {
                    alluid = 21
                    applicationData = applicationData.copy(pendingOnClient = false)
                }
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Uudistettu johtoselvitys",
                pendingOnClient = false
            )
        every { cableReportServiceAllu.getCurrentStatus(21) } returns ApplicationStatus.PENDING
        justRun { cableReportServiceAllu.update(21, newApplicationData) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

        assertEquals(21, response.alluid)
        assertEquals(newApplicationData, response.applicationData)

        val savedApplications = applicationRepository.findAll()
        assertThat(savedApplications).hasSize(1)
        val savedApplication = savedApplications[0]
        assertEquals(21, savedApplication.alluid)
        assertEquals(newApplicationData, savedApplication.applicationData)

        verify {
            cableReportServiceAllu.getCurrentStatus(21)
            cableReportServiceAllu.update(21, newApplicationData)
        }
    }

    @Test
    fun `updateApplicationData doesn't update pendingOnClient`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) {
                it.apply {
                    alluid = 21
                    applicationData = applicationData.copy(pendingOnClient = false)
                }
            }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(
                name = "Päivitetty hakemus",
                pendingOnClient = true,
            )
        val expectedApplicationData = newApplicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.getCurrentStatus(21) } returns ApplicationStatus.PENDING
        justRun { cableReportServiceAllu.update(21, expectedApplicationData) }

        val response =
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)

        assertFalse(response.applicationData.pendingOnClient)
        val savedApplication = applicationRepository.findById(application.id!!).get()
        assertFalse(savedApplication.applicationData.pendingOnClient)
        verify {
            cableReportServiceAllu.getCurrentStatus(21)
            cableReportServiceAllu.update(21, expectedApplicationData)
        }
    }

    @Test
    fun `updateApplicationData with application that's already beyond pending in Allu is not updated`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) { it.apply { alluid = 21 } }
        val newApplicationData =
            AlluDataFactory.createCableReportApplicationData(name = "Uudistettu johtoselvitys")
        every { cableReportServiceAllu.getCurrentStatus(21) } returns ApplicationStatus.HANDLING

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.updateApplicationData(application.id!!, newApplicationData, username)
        }

        val savedApplications = applicationRepository.findAll()
        assertThat(savedApplications).hasSize(1)
        val savedApplication = savedApplications[0]
        assertEquals(
            "Johtoselvitys",
            (savedApplication.applicationData as CableReportApplicationData).name
        )

        verify { cableReportServiceAllu.getCurrentStatus(21) }
    }

    @Test
    fun `sendApplication with unknown ID throws exception`() {
        assertThat(applicationRepository.findAll()).isEmpty()

        assertThrows<ApplicationNotFoundException> {
            applicationService.sendApplication(1234, username)
        }
    }

    @Test
    fun `sendApplication sends pending application to Allu`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) {
                it.apply {
                    alluid = 21
                    applicationData = applicationData.copy(pendingOnClient = false)
                }
            }
        val applicationData = application.applicationData as CableReportApplicationData
        every { cableReportServiceAllu.getCurrentStatus(21) } returns ApplicationStatus.PENDING
        justRun { cableReportServiceAllu.update(21, applicationData) }

        applicationService.sendApplication(application.id!!, username)

        verify {
            cableReportServiceAllu.getCurrentStatus(21)
            cableReportServiceAllu.update(21, applicationData)
        }
    }

    @Test
    fun `sendApplication sets pendingOnClient to false`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) {
                it.apply {
                    alluid = 21
                    applicationData = applicationData.copy(pendingOnClient = false)
                }
            }
        val applicationData = application.applicationData as CableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.getCurrentStatus(21) } returns ApplicationStatus.PENDING
        justRun { cableReportServiceAllu.update(21, pendingApplicationData) }

        val response = applicationService.sendApplication(application.id!!, username)

        val responseApplicationData = response.applicationData as CableReportApplicationData
        assertFalse(responseApplicationData.pendingOnClient)

        val savedApplication = applicationRepository.findById(application.id!!).get()
        val savedApplicationData = savedApplication.applicationData as CableReportApplicationData
        assertFalse(savedApplicationData.pendingOnClient)

        verify {
            cableReportServiceAllu.getCurrentStatus(21)
            cableReportServiceAllu.update(21, pendingApplicationData)
        }
    }

    @Test
    fun `sendApplication creates new application to Allu and saves ID to database`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) { it.apply { alluid = null } }
        val applicationData = application.applicationData as CableReportApplicationData
        val pendingApplicationData = applicationData.copy(pendingOnClient = false)
        every { cableReportServiceAllu.create(pendingApplicationData) } returns 26

        val response = applicationService.sendApplication(application.id!!, username)

        assertEquals(26, response.alluid)
        assertEquals(pendingApplicationData, response.applicationData)

        val savedApplication = applicationRepository.findById(application.id!!).get()
        assertEquals(26, savedApplication.alluid)
        assertEquals(pendingApplicationData, savedApplication.applicationData)

        verify { cableReportServiceAllu.create(pendingApplicationData) }
    }

    @Test
    fun `sendApplication with application that's already beyond pending in Allu is not sent`() {
        val application =
            alluDataFactory.saveApplicationEntity(username) {
                it.apply {
                    alluid = 21
                    applicationData = applicationData.copy(pendingOnClient = false)
                }
            }
        every { cableReportServiceAllu.getCurrentStatus(21) } throws
            ApplicationAlreadyProcessingException(application.id, 21)

        assertThrows<ApplicationAlreadyProcessingException> {
            applicationService.sendApplication(application.id!!, username)
        }

        verify { cableReportServiceAllu.getCurrentStatus(21) }
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
        """.trimIndent()

    private fun expectedLogObject(
        id: Long?,
        alluId: Int?,
        name: String = AlluDataFactory.defaultApplicationName,
    ) =
        """
            {
              "id": $id,
              "alluid": $alluId,
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
                "startTime": "2023-02-20T23:45:56Z",
                "endTime": "2023-02-21T00:12:34Z",
                "pendingOnClient": true,
                "identificationNumber": "identification",
                "clientApplicationKind": "applicationKind",
                "workDescription": "Work description.",
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
        """.trimIndent()
}
