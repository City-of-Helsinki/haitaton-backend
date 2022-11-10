package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.logging.Action
import fi.hel.haitaton.hanke.logging.AuditLogEntry
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.toJsonString

object AuditLogEntryFactory : Factory<AuditLogEntry>() {

    fun createReadEntry(
        userId: String? = "test",
        userRole: UserRole = UserRole.USER,
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
            userRole = userRole,
            action = action,
            status = status,
            objectType = objectType,
            objectId = objectId?.toString(),
            objectBefore = objectBefore,
            ipNear = ipNear,
            ipFar = ipFar,
        )

    fun createReadEntriesForHanke(hanke: Hanke): List<AuditLogEntry> =
        (hanke.omistajat + hanke.arvioijat + hanke.toteuttajat).map {
            createReadEntry(objectId = it.id, objectBefore = it.toJsonString())
        }

    fun createReadEntryForContact(contact: Contact): AuditLogEntry =
        createReadEntry(
            objectId = null,
            objectType = ObjectType.ALLU_CONTACT,
            objectBefore = contact.toJsonString()
        )

    fun createReadEntryForCustomer(customer: Customer): AuditLogEntry =
        createReadEntry(
            objectId = null,
            objectType = ObjectType.ALLU_CUSTOMER,
            objectBefore = customer.toJsonString()
        )
}
