package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.HankeKayttaja
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.permissions.HankeKayttajaEntity
import fi.hel.haitaton.hanke.permissions.KayttajaTunnisteEntity
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import java.util.UUID

object HankeKayttajaFactory {

    val KAYTTAJA_ID = UUID.fromString("639870ab-533d-4172-8e97-e5b93a275514")
    const val HANKE_ID = 14
    const val NIMI = "Pekka Peruskäyttäjä"
    const val SAHKOPOSTI = "pekka@peruskäyttäjä.test"
    val PERMISSION_ID = PermissionFactory.PERMISSION_ID
    val TUNNISTE_ID = KayttajaTunnisteFactory.TUNNISTE_ID

    fun create(
        id: UUID = KAYTTAJA_ID,
        hankeId: Int = HANKE_ID,
        nimi: String = NIMI,
        sahkoposti: String = SAHKOPOSTI,
        permissionId: Int? = PERMISSION_ID,
        kayttajaTunnisteId: UUID? = TUNNISTE_ID,
    ): HankeKayttaja =
        HankeKayttaja(id, hankeId, nimi, sahkoposti, permissionId, kayttajaTunnisteId)

    fun createEntity(
        id: UUID = KAYTTAJA_ID,
        hankeId: Int = HANKE_ID,
        nimi: String = NIMI,
        sahkoposti: String = SAHKOPOSTI,
        permission: PermissionEntity? = null,
        kayttajaTunniste: KayttajaTunnisteEntity? = null,
    ): HankeKayttajaEntity =
        HankeKayttajaEntity(
            id,
            hankeId,
            nimi,
            sahkoposti,
            permission = permission,
            kayttajaTunniste = kayttajaTunniste
        )

    fun generateHankeKayttajat(amount: Int = 3): List<HankeKayttajaDto> =
        (1..amount).map {
            HankeKayttajaDto(
                id = UUID.randomUUID(),
                sahkoposti = "email.$it.address.com",
                nimi = "test name$it",
                kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                tunnistautunut = it % 2 == 0
            )
        }
}
