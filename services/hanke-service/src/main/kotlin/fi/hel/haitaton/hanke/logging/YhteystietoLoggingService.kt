package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.toJsonString
import java.time.OffsetDateTime
import org.springframework.stereotype.Service

@Service
class YhteystietoLoggingService(private val auditLogRepository: AuditLogRepository) {

    fun saveDisclosureLogForUser(hanke: Hanke, userId: String) {
        saveDisclosureLogs(extractYhteystiedot(hanke), userId)
    }

    fun saveDisclosureLogsForUser(hankkeet: List<Hanke>, userId: String) {
        saveDisclosureLogs(hankkeet.flatMap { extractYhteystiedot(it) }, userId)
    }

    private fun extractYhteystiedot(hanke: Hanke) =
        hanke.omistajat + hanke.arvioijat + hanke.toteuttajat

    private fun saveDisclosureLogs(yhteystiedot: List<HankeYhteystieto>, userId: String) {
        if (yhteystiedot.isEmpty()) {
            return
        }

        val eventTime = OffsetDateTime.now()
        val entries =
            yhteystiedot.toSet().map {
                AuditLogEntry(
                    userId = userId,
                    eventTime = eventTime,
                    action = Action.READ,
                    status = Status.SUCCESS,
                    objectType = ObjectType.YHTEYSTIETO,
                    objectId = it.id,
                    objectBefore = it.toJsonString()
                )
            }
        YhteystietoLoggingEntryHolder.applyIpAddresses(entries)
        auditLogRepository.saveAll(entries)
    }
}
