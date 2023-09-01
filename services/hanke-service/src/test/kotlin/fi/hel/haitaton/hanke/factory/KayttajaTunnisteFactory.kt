package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.KayttajaTunniste
import fi.hel.haitaton.hanke.permissions.Role
import java.time.OffsetDateTime
import java.util.UUID

object KayttajaTunnisteFactory {
    val TUNNISTE_ID: UUID = UUID.fromString("b514795c-d982-430a-836b-91371829db51")
    const val TUNNISTE: String = "K6NqNdCJOrNRh45aCP08e9wc"
    val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-08-31T14:25:13Z")
    val SENT_AT: OffsetDateTime = OffsetDateTime.parse("2023-08-31T14:25:14Z")
    val ROLE: Role = Role.KATSELUOIKEUS
    val KAYTTAJA_ID: UUID = UUID.fromString("597431b3-3be1-4594-a07a-bef77c8167df")

    fun create(
        id: UUID = TUNNISTE_ID,
        tunniste: String = TUNNISTE,
        createdAt: OffsetDateTime = CREATED_AT,
        sentAt: OffsetDateTime? = SENT_AT,
        role: Role = ROLE,
        hankeKayttajaId: UUID? = KAYTTAJA_ID,
    ) = KayttajaTunniste(id, tunniste, createdAt, sentAt, role, hankeKayttajaId)
}
