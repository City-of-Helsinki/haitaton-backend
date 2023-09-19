package fi.hel.haitaton.hanke.logging

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.HankeMapper
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.defaultId
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.logging.ObjectType.HANKE
import fi.hel.haitaton.hanke.logging.ObjectType.YHTEYSTIETO
import fi.hel.haitaton.hanke.logging.Operation.DELETE
import fi.hel.haitaton.hanke.logging.Status.SUCCESS
import fi.hel.haitaton.hanke.logging.UserRole.USER
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

class HankeLoggingServiceTest {
    private val userId = "test"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val geometriatService: GeometriatService = mockk(relaxed = true)
    private val hankeMapper: HankeMapper = HankeMapper(geometriatService)
    private val hankeLoggingService = HankeLoggingService(auditLogService, hankeMapper)

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
                    assertEquals(DELETE, entry.operation)
                    assertEquals(SUCCESS, entry.status)
                    assertNull(entry.failureDescription)
                    assertEquals(userId, entry.userId)
                    assertEquals(USER, entry.userRole)
                    assertEquals(defaultId.toString(), entry.objectId)
                    assertEquals(HANKE, entry.objectType)
                    assertNull(entry.objectAfter)
                    assertNotNull(entry.objectBefore)
                }
            )
        }
    }

    @Test
    fun `logDelete creates audit log entry for deleted hanke entity`() {
        val hankeEntity = HankeFactory.createEntity()

        hankeLoggingService.logDelete(hankeEntity, userId)

        verify {
            auditLogService.createAll(
                withArg { entries -> assertThat(entries).isExpectedWithContacts() }
            )
        }
    }

    @Test
    fun `logDelete creates audit log entries for deleted yhteystiedot`() {
        val hanke = HankeFactory.create().withYhteystiedot()

        hankeLoggingService.logDelete(hanke, userId)

        verify {
            auditLogService.createAll(
                withArg { entries -> assertThat(entries).isExpectedWithContacts() }
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
                    assertEquals(SUCCESS, entry.status)
                    assertNull(entry.failureDescription)
                    assertEquals(userId, entry.userId)
                    assertEquals(USER, entry.userRole)
                    assertEquals(defaultId.toString(), entry.objectId)
                    assertEquals(HANKE, entry.objectType)
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
                    assertEquals(SUCCESS, entry.status)
                    assertNull(entry.failureDescription)
                    assertEquals(userId, entry.userId)
                    assertEquals(USER, entry.userRole)
                    assertEquals(defaultId.toString(), entry.objectId)
                    assertEquals(HANKE, entry.objectType)
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

    private fun Assert<Collection<AuditLogEntry>>.isExpectedWithContacts() {
        all {
            hasSize(5)
            each { entry ->
                entry.transform { it.operation }.isEqualTo(DELETE)
                entry.transform { it.status }.isEqualTo(SUCCESS)
                entry.transform { it.failureDescription }.isNull()
                entry.transform { it.userId }.isEqualTo(userId)
                entry.transform { it.userRole }.isEqualTo(USER)
                entry.transform { it.objectAfter }.isNull()
                entry.transform { it.objectBefore }.isNotNull()
            }
            extracting { it.objectType }
                .containsExactlyInAnyOrder(
                    YHTEYSTIETO,
                    YHTEYSTIETO,
                    YHTEYSTIETO,
                    YHTEYSTIETO,
                    HANKE
                )
        }
    }
}
