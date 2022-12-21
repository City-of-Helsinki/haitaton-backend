package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.fasterxml.jackson.databind.node.ObjectNode
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
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val userId = "test7358"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(username = userId)
class ApplicationServiceITest : DatabaseTest() {
    @MockkBean private lateinit var cableReportServiceAllu: CableReportServiceAllu
    @Autowired private lateinit var applicationService: ApplicationService

    @Autowired private lateinit var auditLogRepository: AuditLogRepository

    @AfterEach
    fun cleanUp() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(cableReportServiceAllu)
        clearAllMocks()
    }

    @Test
    fun `create creates an audit log entry for created application`() {
        TestUtils.addMockedRequestIp()
        every { cableReportServiceAllu.create(any()) }.returns(2)

        val application =
            applicationService.create(AlluDataFactory.createApplicationDto(id = null), userId)

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
        assertThat(event.actor::userId).isEqualTo(userId)
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
            applicationService.create(AlluDataFactory.createApplicationDto(id = null), userId)
        auditLogRepository.deleteAll()
        assertThat(auditLogRepository.findAll()).isEmpty()

        applicationService.updateApplicationData(
            application.id!!,
            (application.applicationData as ObjectNode).put("name", "Modified application"),
            userId
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
        assertThat(event.actor::userId).isEqualTo(userId)
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
            applicationService.create(AlluDataFactory.createApplicationDto(id = null), userId)
        auditLogRepository.deleteAll()
        assertThat(auditLogRepository.findAll()).isEmpty()

        applicationService.updateApplicationData(
            application.id!!,
            application.applicationData,
            userId
        )

        val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
        assertThat(applicationLogs).isEmpty()
        verify { cableReportServiceAllu.create(any()) }
        verify { cableReportServiceAllu.getCurrentStatus(2) }
        verify { cableReportServiceAllu.update(2, any()) }
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
