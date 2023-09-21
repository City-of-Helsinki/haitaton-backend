package fi.hel.haitaton.hanke.logging

import assertk.all
import assertk.assertThat
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.KayttajaTunnisteFactory
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
    inner class LogTunnisteCreate {
        @Test
        fun `Creates audit log entry for created kayttajatunniste`() {
            val tunniste =
                KayttajaTunnisteFactory.create(kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS)

            loggingService.logCreate(tunniste, userId)

            verify {
                auditLogService.create(
                    withArg { entry ->
                        assertThat(entry).all {
                            isCreate()
                            isSuccess()
                            hasUserActor(userId)
                            hasObjectId(tunniste.id)
                            hasObjectType(ObjectType.KAYTTAJA_TUNNISTE)
                            prop(AuditLogEntry::objectBefore).isNull()
                            hasObjectAfter(tunniste)
                        }
                    }
                )
            }
        }
    }

    @Nested
    inner class LogTunnisteUpdate {
        @Test
        fun `Creates audit log entry for updated kayttajatunniste`() {
            val kayttajaTunnisteBefore = KayttajaTunnisteFactory.create()
            val kayttajaTunnisteAfter =
                KayttajaTunnisteFactory.create(kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS)

            loggingService.logUpdate(kayttajaTunnisteBefore, kayttajaTunnisteAfter, userId)

            verify {
                auditLogService.create(
                    withArg { entry ->
                        assertThat(entry).all {
                            isUpdate()
                            isSuccess()
                            hasUserActor(userId)
                            hasObjectId(KayttajaTunnisteFactory.TUNNISTE_ID)
                            hasObjectType(ObjectType.KAYTTAJA_TUNNISTE)
                            hasObjectBefore(kayttajaTunnisteBefore)
                            hasObjectAfter(kayttajaTunnisteAfter)
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

    @Nested
    inner class LogKayttajaCreate {
        @Test
        fun `Creates audit log entry for created hanke kayttaja`() {
            val kayttaja = HankeKayttajaFactory.create()

            loggingService.logCreate(kayttaja, userId)

            verify {
                auditLogService.create(
                    withArg { entry ->
                        assertThat(entry).all {
                            isCreate()
                            isSuccess()
                            hasUserActor(userId)
                            hasObjectId(kayttaja.id)
                            hasObjectType(ObjectType.HANKE_KAYTTAJA)
                            prop(AuditLogEntry::objectBefore).isNull()
                            hasObjectAfter(kayttaja)
                        }
                    }
                )
            }
        }
    }
}
