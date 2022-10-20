package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.factory.AuditLogEntryFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.mutate
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedArvioija
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedToteuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class YhteystietoLoggingServiceTest {

    private val userId = "test"

    private val auditLogRepository: AuditLogRepository = mockk()
    private val yhteystietoLoggingService = YhteystietoLoggingService(auditLogRepository)

    @BeforeEach
    fun clearMockks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkStubs() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified()
    }

    @Test
    fun `saveDisclosureLogForUser with hanke with no yhteystiedot doesn't do anything`() {
        val hanke = HankeFactory.create()

        yhteystietoLoggingService.saveDisclosureLogForUser(hanke, userId)

        verify { auditLogRepository wasNot Called }
    }

    @Test
    fun `saveDisclosureLogForUser saves audit logs for all yhteystiedot`() {
        val hanke = HankeFactory.create().withYhteystiedot()
        val expectedLogs = AuditLogEntryFactory.createReadEntriesForHanke(hanke)
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        yhteystietoLoggingService.saveDisclosureLogForUser(hanke, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogForUser saves identical audit logs only once`() {
        val yhteystieto = HankeYhteystietoFactory.createDifferentiated(1)
        val hanke =
            HankeFactory.create().mutate {
                it.omistajat = mutableListOf(yhteystieto)
                it.arvioijat = mutableListOf(yhteystieto)
                it.toteuttajat = mutableListOf(yhteystieto)
            }
        val expectedLogs =
            listOf(AuditLogEntryFactory.createReadEntry(objectBefore = yhteystieto.toJsonString()))
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        yhteystietoLoggingService.saveDisclosureLogForUser(hanke, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForUser without hankkeet does nothing`() {
        yhteystietoLoggingService.saveDisclosureLogsForUser(listOf(), userId)

        verify { auditLogRepository wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForUser with hankkeet without yhteystiedot does nothing`() {
        val hankkeet = listOf(HankeFactory.create(), HankeFactory.create())

        yhteystietoLoggingService.saveDisclosureLogsForUser(hankkeet, userId)

        verify { auditLogRepository wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForUser saves audit logs for all yhteystiedot in all hankkeet`() {
        val hankkeet =
            listOf(
                HankeFactory.create().withYhteystiedot(),
                HankeFactory.create()
                    .withGeneratedOmistaja(4)
                    .withGeneratedArvioija(5)
                    .withGeneratedToteuttaja(6)
            )
        val expectedLogs = hankkeet.flatMap { AuditLogEntryFactory.createReadEntriesForHanke(it) }
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        yhteystietoLoggingService.saveDisclosureLogsForUser(hankkeet, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForUser saves identical audit logs only once`() {
        val hankkeet =
            listOf(
                HankeFactory.create().withYhteystiedot(),
                HankeFactory.create().withYhteystiedot()
            )
        val expectedLogs = AuditLogEntryFactory.createReadEntriesForHanke(hankkeet[0])
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        yhteystietoLoggingService.saveDisclosureLogsForUser(hankkeet, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    private fun containsAllWithoutGeneratedFields(
        expectedLogs: List<AuditLogEntry>
    ): (List<AuditLogEntry>) -> Boolean = { entries ->
        withoutGeneratedFields(entries) == withoutGeneratedFields(expectedLogs)
    }

    private fun withoutGeneratedFields(entries: List<AuditLogEntry>): List<AuditLogEntry> {
        return entries.map { it.copy(eventTime = null, id = null) }
    }
}
