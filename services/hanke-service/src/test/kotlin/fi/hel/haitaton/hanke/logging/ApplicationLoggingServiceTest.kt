package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.factory.AlluDataFactory
import io.mockk.called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ApplicationLoggingServiceTest {
    private val userId = "test"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val applicationLoggingService = ApplicationLoggingService(auditLogService)

    @AfterEach
    fun cleanUp() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(auditLogService)
        clearAllMocks()
    }

    @Test
    fun `logCreate creates audit log entry for created application`() {
        val application = AlluDataFactory.createApplication()

        applicationLoggingService.logCreate(application, userId)

        verify {
            auditLogService.create(
                withArg { entry ->
                    Assertions.assertEquals(Operation.CREATE, entry.operation)
                    Assertions.assertEquals(Status.SUCCESS, entry.status)
                    Assertions.assertNull(entry.failureDescription)
                    Assertions.assertEquals(userId, entry.userId)
                    Assertions.assertEquals(UserRole.USER, entry.userRole)
                    Assertions.assertEquals(
                        AlluDataFactory.defaultApplicationId.toString(),
                        entry.objectId
                    )
                    Assertions.assertEquals(ObjectType.APPLICATION, entry.objectType)
                    Assertions.assertNotNull(entry.objectAfter)
                    Assertions.assertNull(entry.objectBefore)
                }
            )
        }
    }

    @Test
    fun `logUpdate creates audit log entry for updated hanke`() {
        val applicationBefore =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(name = "Johtoselvitys #1")
            )
        val applicationAfter =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(name = "Johtoselvitys #2")
            )

        applicationLoggingService.logUpdate(applicationBefore, applicationAfter, userId)

        verify {
            auditLogService.create(
                withArg { entry ->
                    Assertions.assertEquals(Operation.UPDATE, entry.operation)
                    Assertions.assertEquals(Status.SUCCESS, entry.status)
                    Assertions.assertNull(entry.failureDescription)
                    Assertions.assertEquals(userId, entry.userId)
                    Assertions.assertEquals(UserRole.USER, entry.userRole)
                    Assertions.assertEquals(
                        AlluDataFactory.defaultApplicationId.toString(),
                        entry.objectId
                    )
                    Assertions.assertEquals(ObjectType.APPLICATION, entry.objectType)
                    Assertions.assertNotNull(entry.objectBefore)
                    Assertions.assertNotNull(entry.objectAfter)
                }
            )
        }
    }

    @Test
    fun `logUpdate doesn't create audit log entry if hanke not changed`() {
        val applicationBefore =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(name = "Johtoselvitys #1")
            )
        val applicationAfter =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(name = "Johtoselvitys #1")
            )

        applicationLoggingService.logUpdate(applicationBefore, applicationAfter, userId)

        verify { auditLogService wasNot called }
    }
}
