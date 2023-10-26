package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.HankeKayttaja
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.permissions.HankeKayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankeKayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajaTunnisteEntity
import fi.hel.haitaton.hanke.permissions.KayttajaTunnisteRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class HankeKayttajaFactory(
    private val hankeKayttajaRepository: HankeKayttajaRepository,
    private val permissionService: PermissionService,
    private val kayttajaTunnisteRepository: KayttajaTunnisteRepository
) {

    fun saveUserAndToken(
        hankeId: Int,
        nimi: String = "Kake Katselija",
        sahkoposti: String = "kake@katselu.test",
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        tunniste: String = "existing",
    ): HankeKayttajaEntity =
        addToken(saveUser(hankeId, nimi, sahkoposti, null), tunniste, kayttooikeustaso)

    fun saveUserAndPermission(
        hankeId: Int,
        nimi: String = "Kake Katselija",
        sahkoposti: String = "kake@katselu.test",
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        userId: String = "fake id",
    ): HankeKayttajaEntity {
        val permissionEntity = permissionService.create(hankeId, userId, kayttooikeustaso)

        return saveUser(hankeId, nimi, sahkoposti, permissionEntity)
    }

    fun saveUser(
        hankeId: Int,
        nimi: String = "Kake Katselija",
        sahkoposti: String = "kake@katselu.test",
        permissionEntity: PermissionEntity? = null,
        kayttajaTunniste: KayttajaTunnisteEntity? = null,
    ): HankeKayttajaEntity {
        return hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                hankeId = hankeId,
                nimi = nimi,
                sahkoposti = sahkoposti,
                permission = permissionEntity,
                kayttajaTunniste = kayttajaTunniste,
            )
        )
    }

    fun addToken(
        hankeKayttaja: HankeKayttajaEntity,
        tunniste: String = "existing",
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ): HankeKayttajaEntity {
        hankeKayttaja.kayttajaTunniste = hankeKayttaja.saveToken(tunniste, kayttooikeustaso)
        return hankeKayttajaRepository.save(hankeKayttaja)
    }

    private fun HankeKayttajaEntity.saveToken(
        tunniste: String = "existing",
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ) =
        kayttajaTunnisteRepository.save(
            KayttajaTunnisteEntity(
                tunniste = tunniste,
                createdAt = OffsetDateTime.parse("2023-03-31T15:41:21Z"),
                kayttooikeustaso = kayttooikeustaso,
                hankeKayttaja = this,
            )
        )

    companion object {
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
}
