package fi.hel.haitaton.hanke.logging

import assertk.all
import assertk.assertThat
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.factory.PermissionFactory
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.hasObjectId
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.hasObjectType
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.isCreate
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.isUpdate
import io.mockk.called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PermissionLoggingServiceTest {
    private val userId = "test"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val loggingService = PermissionLoggingService(auditLogService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun cleanUp() {
        checkUnnecessaryStub()
        confirmVerified(auditLogService)
    }

    @Nested
    inner class LogCreate {
        @Test
        fun `Creates audit log entry for created permission`() {
            val permission =
                PermissionFactory.create(kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS)

            loggingService.logCreate(permission, userId)

            verify {
                auditLogService.create(
                    withArg { entry ->
                        assertThat(entry).all {
                            isCreate()
                            isSuccess()
                            hasUserActor(userId)
                            hasObjectId(PermissionFactory.PERMISSION_ID)
                            hasObjectType(ObjectType.PERMISSION)
                            prop(AuditLogEntry::objectBefore).isNull()
                            hasObjectAfter(permission)
                        }
                    }
                )
            }
        }
    }

    @Nested
    inner class LogUpdate {
        @Test
        fun `Creates audit log entry for updated permission`() {
            val kayttooikeustasoBefore = Kayttooikeustaso.KATSELUOIKEUS
            val permissionAfter =
                PermissionFactory.create(kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS)

            loggingService.logUpdate(kayttooikeustasoBefore, permissionAfter, userId)

            verify {
                auditLogService.create(
                    withArg { entry ->
                        assertThat(entry).all {
                            isUpdate()
                            isSuccess()
                            hasUserActor(userId)
                            hasObjectId(PermissionFactory.PERMISSION_ID)
                            hasObjectType(ObjectType.PERMISSION)
                            hasObjectBefore(
                                permissionAfter.copy(
                                    kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS
                                )
                            )
                            hasObjectAfter(permissionAfter)
                        }
                    }
                )
            }
        }

        @Test
        fun `Doesn't create audit log entry if permission not updated`() {
            val kayttooikeustasoBefore = PermissionFactory.KAYTTOOIKEUSTASO
            val permissionAfter = PermissionFactory.create()

            loggingService.logUpdate(kayttooikeustasoBefore, permissionAfter, userId)

            verify { auditLogService wasNot called }
        }
    }
}
