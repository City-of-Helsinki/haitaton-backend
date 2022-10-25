package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.logging.Action
import fi.hel.haitaton.hanke.logging.AuditLogEntry
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.toJsonString

object AuditLogEntryFactory : Factory<AuditLogEntry>() {

    fun createReadEntry(
        userId: String? = "test",
        action: Action = Action.READ,
        status: Status = Status.SUCCESS,
        objectType: ObjectType = ObjectType.YHTEYSTIETO,
        objectId: Int? = 1,
        objectBefore: String? = null,
        ipNear: String? = null,
        ipFar: String? = null,
    ) =
        AuditLogEntry(
            userId = userId,
            action = action,
            status = status,
            objectType = objectType,
            objectId = objectId,
            objectBefore = objectBefore,
            ipNear = ipNear,
            ipFar = ipFar,
        )

    fun createReadEntriesForHanke(hanke: Hanke): List<AuditLogEntry> =
        (hanke.omistajat + hanke.arvioijat + hanke.toteuttajat).map {
            createReadEntry(objectId = it.id, objectBefore = it.toJsonString())
        }
}
