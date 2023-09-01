package fi.hel.haitaton.hanke.logging

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.factory.KayttajaTunnisteFactory
import fi.hel.haitaton.hanke.factory.PermissionFactory
import fi.hel.haitaton.hanke.permissions.Role
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

class HankeKayttajaLoggingServiceTest {
    private val userId = "test"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val loggingService = HankeKayttajaLoggingService(auditLogService)

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
    inner class LogPermissionUpdate {
        @Test
        fun `Creates audit log entry for updated permission`() {
            val roleBefore = Role.KATSELUOIKEUS
            val permissionAfter = PermissionFactory.create(role = Role.HANKEMUOKKAUS)

            loggingService.logUpdate(roleBefore, permissionAfter, userId)

            verify {
                auditLogService.create(
                    withArg { entry ->
                        assertThat(entry.operation).isEqualTo(Operation.UPDATE)
                        assertThat(entry.status).isEqualTo(Status.SUCCESS)
                        assertThat(entry.failureDescription).isNull()
                        assertThat(entry.userId).isEqualTo(userId)
                        assertThat(entry.userRole).isEqualTo(UserRole.USER)
                        assertThat(entry.objectId)
                            .isEqualTo(PermissionFactory.PERMISSION_ID.toString())
                        assertThat(entry.objectType).isEqualTo(ObjectType.PERMISSION)
                        assertThat(entry.objectBefore).isNotNull().all {
                            contains(PermissionFactory.PERMISSION_ID.toString())
                            contains(PermissionFactory.USER_ID)
                            contains(PermissionFactory.HANKE_ID.toString())
                            contains("KATSELUOIKEUS")
                        }
                        assertThat(entry.objectAfter).isNotNull().all {
                            contains(PermissionFactory.PERMISSION_ID.toString())
                            contains(PermissionFactory.USER_ID)
                            contains(PermissionFactory.HANKE_ID.toString())
                            contains("HANKEMUOKKAUS")
                        }
                    }
                )
            }
        }

        @Test
        fun `Doesn't create audit log entry if permission not updated`() {
            val roleBefore = PermissionFactory.ROLE
            val permissionAfter = PermissionFactory.create()

            loggingService.logUpdate(roleBefore, permissionAfter, userId)

            verify { auditLogService wasNot called }
        }
    }

    @Nested
    inner class LogTunnisteUpdate {
        @Test
        fun `Creates audit log entry for updated kayttajatunniste`() {
            val kayttajaTunnisteBefore = KayttajaTunnisteFactory.create(sentAt = null)
            val kayttajaTunnisteAfter = KayttajaTunnisteFactory.create(role = Role.HANKEMUOKKAUS)

            loggingService.logUpdate(kayttajaTunnisteBefore, kayttajaTunnisteAfter, userId)

            verify {
                auditLogService.create(
                    withArg { entry ->
                        assertThat(entry.operation).isEqualTo(Operation.UPDATE)
                        assertThat(entry.status).isEqualTo(Status.SUCCESS)
                        assertThat(entry.failureDescription).isNull()
                        assertThat(entry.userId).isEqualTo(userId)
                        assertThat(entry.userRole).isEqualTo(UserRole.USER)
                        assertThat(entry.objectId)
                            .isEqualTo(KayttajaTunnisteFactory.TUNNISTE_ID.toString())
                        assertThat(entry.objectType).isEqualTo(ObjectType.KAYTTAJA_TUNNISTE)
                        assertThat(entry.objectBefore).isNotNull().all {
                            contains(KayttajaTunnisteFactory.TUNNISTE_ID.toString())
                            contains(KayttajaTunnisteFactory.TUNNISTE)
                            contains(KayttajaTunnisteFactory.CREATED_AT.toString())
                            contains("null")
                            contains(KayttajaTunnisteFactory.KAYTTAJA_ID.toString())
                            contains("KATSELUOIKEUS")
                        }
                        assertThat(entry.objectAfter).isNotNull().all {
                            contains(KayttajaTunnisteFactory.TUNNISTE_ID.toString())
                            contains(KayttajaTunnisteFactory.TUNNISTE)
                            contains(KayttajaTunnisteFactory.CREATED_AT.toString())
                            contains(KayttajaTunnisteFactory.SENT_AT.toString())
                            contains(KayttajaTunnisteFactory.KAYTTAJA_ID.toString())
                            contains("HANKEMUOKKAUS")
                        }
                    }
                )
            }
        }

        @Test
        fun `Doesn't create audit log entry if permission not updated`() {
            val kayttajaTunnisteBefore = KayttajaTunnisteFactory.create()
            val kayttajaTunnisteAfter = KayttajaTunnisteFactory.create()

            loggingService.logUpdate(kayttajaTunnisteBefore, kayttajaTunnisteAfter, userId)

            verify { auditLogService wasNot called }
        }
    }
}
