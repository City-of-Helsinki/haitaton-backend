package fi.hel.haitaton.hanke.logging

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import io.mockk.called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HankeLoggingServiceTest {
    private val userId = "test"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val hankeLoggingService = HankeLoggingService(auditLogService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun cleanUp() {
        checkUnnecessaryStub()
        confirmVerified(auditLogService)
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
                    assertThat(entries).all {
                        hasSize(5)
                        each { entry ->
                            entry.transform { it.operation }.isEqualTo(Operation.DELETE)
                            entry.transform { it.status }.isEqualTo(Status.SUCCESS)
                            entry.transform { it.failureDescription }.isNull()
                            entry.transform { it.userId }.isEqualTo(userId)
                            entry.transform { it.userRole }.isEqualTo(UserRole.USER)
                            entry.transform { it.objectAfter }.isNull()
                            entry.transform { it.objectBefore }.isNotNull()
                        }
                        extracting { it.objectType }
                            .containsExactlyInAnyOrder(
                                ObjectType.YHTEYSTIETO,
                                ObjectType.YHTEYSTIETO,
                                ObjectType.YHTEYSTIETO,
                                ObjectType.YHTEYSTIETO,
                                ObjectType.HANKE
                            )
                    }
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

    @Test
    fun `logUpdate creates audit log entry for updated hanke`() {
        val hankeBefore = HankeFactory.create(version = 1)
        val hankeAfter = HankeFactory.create(version = 2)

        hankeLoggingService.logUpdate(hankeBefore, hankeAfter, userId)

        verify {
            auditLogService.create(
                withArg { entry ->
                    assertEquals(Operation.UPDATE, entry.operation)
                    assertEquals(Status.SUCCESS, entry.status)
                    assertNull(entry.failureDescription)
                    assertEquals(userId, entry.userId)
                    assertEquals(UserRole.USER, entry.userRole)
                    assertEquals(HankeFactory.defaultId.toString(), entry.objectId)
                    assertEquals(ObjectType.HANKE, entry.objectType)
                    assertNotNull(entry.objectBefore)
                    assertNotNull(entry.objectAfter)
                }
            )
        }
    }
    @Test
    fun `logUpdate doesn't create audit log entry if hanke not changed`() {
        val hankeBefore = HankeFactory.create(version = 1)
        val hankeAfter = HankeFactory.create(version = 1)

        hankeLoggingService.logUpdate(hankeBefore, hankeAfter, userId)

        verify { auditLogService wasNot called }
    }
}
