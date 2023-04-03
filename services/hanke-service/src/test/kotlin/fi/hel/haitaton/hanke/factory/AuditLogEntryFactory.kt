package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.logging.AuditLogEntry
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.toJsonString

object AuditLogEntryFactory {

    fun createReadEntry(
        userId: String? = "test",
        userRole: UserRole = UserRole.USER,
        operation: Operation = Operation.READ,
        status: Status = Status.SUCCESS,
        objectType: ObjectType = ObjectType.YHTEYSTIETO,
        objectId: Any? = 1,
        objectBefore: String? = null,
    ) =
        AuditLogEntry(
            userId = userId,
            userRole = userRole,
            operation = operation,
            status = status,
            objectType = objectType,
            objectId = objectId?.toString(),
            objectBefore = objectBefore,
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
