package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.assertj.core.api.Condition
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class HankeLoggingServiceTest {
    private val userId = "test"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val hankeLoggingService = HankeLoggingService(auditLogService)

    @AfterEach
    fun cleanUp() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified()
        clearAllMocks()
    }

    @Test
    fun `logDelete creates audit log entry for deleted hanke`() {
        val hanke = HankeFactory.create()

        hankeLoggingService.logDelete(hanke, userId)

        verify {
            auditLogService.createAll(
                withArg { entries ->
                    assertEquals(1, entries.size)
                    val entry = entries.first()
                    assertEquals(Operation.DELETE, entry.operation)
                    assertEquals(Status.SUCCESS, entry.status)
                    assertNull(entry.failureDescription)
                    assertEquals(userId, entry.userId)
                    assertEquals(UserRole.USER, entry.userRole)
                    assertEquals(HankeFactory.defaultId.toString(), entry.objectId)
                    assertEquals(ObjectType.HANKE, entry.objectType)
                    assertNull(entry.objectAfter)
                    assertNotNull(entry.objectBefore)
                }
            )
        }
    }

    @Test
    fun `logDelete creates audit log entries for deleted yhteystiedot`() {
        val hanke = HankeFactory.create().withYhteystiedot()

        hankeLoggingService.logDelete(hanke, userId)

        verify {
            auditLogService.createAll(
                withArg { entries ->
                    Assertions.assertThat(entries)
                        .hasSize(4)
                        .allSatisfy { entry ->
                            assertEquals(Operation.DELETE, entry.operation)
                            assertEquals(Status.SUCCESS, entry.status)
                            assertNull(entry.failureDescription)
                            assertEquals(userId, entry.userId)
                            assertEquals(UserRole.USER, entry.userRole)
                            assertNull(entry.objectAfter)
                            assertNotNull(entry.objectBefore)
                        }
                        .areExactly(
                            3,
                            Condition({ it.objectType == ObjectType.YHTEYSTIETO }, "Yhteystieto")
                        )
                        .areExactly(1, Condition({ it.objectType == ObjectType.HANKE }, "Hanke"))
                }
            )
        }
    }

    @Test
    fun `logCreate creates audit log entry for created hanke`() {
        val hanke = HankeFactory.create()

        hankeLoggingService.logCreate(hanke, userId)

        verify {
            auditLogService.create(
                withArg { entry ->
                    assertEquals(Operation.CREATE, entry.operation)
                    assertEquals(Status.SUCCESS, entry.status)
                    assertNull(entry.failureDescription)
                    assertEquals(userId, entry.userId)
                    assertEquals(UserRole.USER, entry.userRole)
                    assertEquals(HankeFactory.defaultId.toString(), entry.objectId)
                    assertEquals(ObjectType.HANKE, entry.objectType)
                    assertNotNull(entry.objectAfter)
                    assertNull(entry.objectBefore)
                }
            )
        }
    }
}
