package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.KayttajaTunniste
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import java.time.OffsetDateTime
import java.util.UUID

object KayttajaTunnisteFactory {
    val TUNNISTE_ID: UUID = UUID.fromString("b514795c-d982-430a-836b-91371829db51")
    const val TUNNISTE: String = "K6NqNdCJOrNRh45aCP08e9wc"
    val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-08-31T14:25:13Z")
    val KAYTTOOIKEUSTASO: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS
    val KAYTTAJA_ID: UUID = HankeKayttajaFactory.KAYTTAJA_ID

    fun create(
        id: UUID = TUNNISTE_ID,
        tunniste: String = TUNNISTE,
        createdAt: OffsetDateTime = CREATED_AT,
        kayttooikeustaso: Kayttooikeustaso = KAYTTOOIKEUSTASO,
        hankeKayttajaId: UUID? = KAYTTAJA_ID,
    ) = KayttajaTunniste(id, tunniste, createdAt, kayttooikeustaso, hankeKayttajaId)
}
