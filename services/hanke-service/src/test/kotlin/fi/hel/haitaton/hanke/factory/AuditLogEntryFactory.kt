package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.logging.AuditLogEntry
import fi.hel.haitaton.hanke.logging.ContactWithRole
import fi.hel.haitaton.hanke.logging.CustomerWithRole
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.toJsonString

object AuditLogEntryFactory {

    fun createReadEntry(
        userId: String? = "test",
        userRole: UserRole = UserRole.USER,
        operation: Operation = Operation.READ,
        status: Status = Status.SUCCESS,
        objectType: ObjectType = ObjectType.YHTEYSTIETO,
        objectId: Any = 1,
        objectBefore: String? = null,
    ) =
        AuditLogEntry(
            userId = userId,
            userRole = userRole,
            operation = operation,
            status = status,
            objectType = objectType,
            objectId = objectId.toString(),
            objectBefore = objectBefore,
        )

    fun createReadEntriesForHanke(hanke: Hanke): List<AuditLogEntry> =
        hanke.extractYhteystiedot().map {
            createReadEntry(objectId = it.id!!, objectBefore = it.toJsonString())
        }

    fun createReadEntryForContact(
        applicationId: Long,
        contact: Contact,
        role: ApplicationContactType = ApplicationContactType.HAKIJA,
    ): AuditLogEntry =
        createReadEntry(
            objectId = applicationId,
            objectType = ObjectType.APPLICATION_CONTACT,
            objectBefore = ContactWithRole(role, contact).toJsonString()
        )

    fun createReadEntryForCustomer(
        applicationId: Long,
        customer: Customer,
        role: ApplicationContactType = ApplicationContactType.HAKIJA,
    ): AuditLogEntry =
        createReadEntry(
            objectId = applicationId,
            objectType = ObjectType.APPLICATION_CUSTOMER,
            objectBefore = CustomerWithRole(role, customer).toJsonString()
        )

    fun createReadEntryForHankeKayttaja(kayttaja: HankeKayttajaDto): AuditLogEntry =
        createReadEntry(
            objectId = kayttaja.id,
            objectType = ObjectType.HANKE_KAYTTAJA,
            objectBefore = kayttaja.toJsonString()
        )

    fun createReadEntryForHankeKayttajat(kayttajat: List<HankeKayttajaDto>): List<AuditLogEntry> =
        kayttajat.map { createReadEntryForHankeKayttaja(it) }
}
