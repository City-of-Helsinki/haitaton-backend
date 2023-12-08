package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.factory.KayttajaTunnisteFactory.TUNNISTE_ID
import fi.hel.haitaton.hanke.factory.PermissionFactory.PERMISSION_ID
import fi.hel.haitaton.hanke.permissions.HankeKayttaja
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajakutsuEntity
import fi.hel.haitaton.hanke.permissions.KayttajakutsuRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso.KATSELUOIKEUS
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Component

private const val KAKE = "Kake"
private const val KATSELIJA = "Katselija"
private const val KAKE_EMAIL = "kake@katselu.test"
private const val KAKE_PUHELIN = "0501234567"

@Component
class HankeKayttajaFactory(
    private val hankeKayttajaRepository: HankekayttajaRepository,
    private val permissionService: PermissionService,
    private val kayttajakutsuRepository: KayttajakutsuRepository
) {

    fun saveUserAndToken(
        hankeId: Int,
        kayttajaInput: HankekayttajaInput = kayttajaInput(),
        kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
        tunniste: String = "existing",
    ): HankekayttajaEntity =
        addToken(
            hankeKayttaja =
                saveUser(
                    hankeId = hankeId,
                    kayttajaInput = kayttajaInput,
                    permissionEntity = null,
                ),
            tunniste = tunniste,
            kayttooikeustaso = kayttooikeustaso,
        )

    fun saveUserAndPermission(
        hankeId: Int,
        kayttaja: HankekayttajaInput = kayttajaInput(),
        kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
        userId: String = "fake id",
    ): HankekayttajaEntity =
        saveUser(
            hankeId = hankeId,
            kayttajaInput = kayttaja,
            permissionEntity = permissionService.create(hankeId, userId, kayttooikeustaso),
        )

    fun saveUser(
        hankeId: Int,
        kayttajaInput: HankekayttajaInput = kayttajaInput(),
        permissionEntity: PermissionEntity? = null,
        kayttajakutsuEntity: KayttajakutsuEntity? = null,
    ): HankekayttajaEntity =
        hankeKayttajaRepository.save(
            HankekayttajaEntity(
                hankeId = hankeId,
                etunimi = kayttajaInput.etunimi,
                sukunimi = kayttajaInput.sukunimi,
                sahkoposti = kayttajaInput.email,
                puhelin = kayttajaInput.puhelin,
                permission = permissionEntity,
                kayttajakutsu = kayttajakutsuEntity,
            )
        )

    fun addToken(
        hankeKayttaja: HankekayttajaEntity,
        tunniste: String = "existing",
        kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
    ): HankekayttajaEntity {
        hankeKayttaja.kayttajakutsu = hankeKayttaja.saveToken(tunniste, kayttooikeustaso)
        return hankeKayttajaRepository.save(hankeKayttaja)
    }

    private fun HankekayttajaEntity.saveToken(
        tunniste: String = "existing",
        kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
    ) =
        kayttajakutsuRepository.save(
            KayttajakutsuEntity(
                tunniste = tunniste,
                createdAt = OffsetDateTime.parse("2023-03-31T15:41:21Z"),
                kayttooikeustaso = kayttooikeustaso,
                hankekayttaja = this,
            )
        )

    companion object {
        val KAYTTAJA_ID = UUID.fromString("639870ab-533d-4172-8e97-e5b93a275514")

        private const val PEKKA = "Pekka Peruskäyttäjä"
        private const val PEKKA_EMAIL = "pekka@peruskäyttäjä.test"

        val KAYTTAJA_INPUT_HAKIJA =
            HankekayttajaInput(
                "Henri",
                "Hakija",
                "henri.hakija@mail.com",
                "0401234567",
            )

        val KAYTTAJA_INPUT_RAKENNUTTAJA =
            HankekayttajaInput(
                "Rane",
                "Rakennuttaja",
                "rane.rakennuttaja@mail.com",
                "0401234566",
            )

        val KAYTTAJA_INPUT_ASIANHOITAJA =
            HankekayttajaInput(
                "Anssi",
                "Asianhoitaja",
                "anssi.asianhoitaja@mail.com",
                "0401234565",
            )

        val KAYTTAJA_INPUT_SUORITTAJA =
            HankekayttajaInput(
                "Timo",
                "Työnsuorittaja",
                "timo.tyonsuorittaja@mail.com",
                "0401234564",
            )

        fun kayttajaInput(
            etunimi: String = KAKE,
            sukunimi: String = KATSELIJA,
            email: String = KAKE_EMAIL,
            puhelin: String = KAKE_PUHELIN,
        ) = HankekayttajaInput(etunimi, sukunimi, email, puhelin)

        fun create(
            id: UUID = KAYTTAJA_ID,
            hankeId: Int = HankeFactory.defaultId,
            nimi: String = PEKKA,
            sahkoposti: String = PEKKA_EMAIL,
            permissionId: Int? = PERMISSION_ID,
            kutsuId: UUID? = TUNNISTE_ID,
        ): HankeKayttaja = HankeKayttaja(id, hankeId, nimi, sahkoposti, permissionId, kutsuId)

        fun createEntity(
            id: UUID = KAYTTAJA_ID,
            hankeId: Int = HankeFactory.defaultId,
            kayttaja: HankekayttajaInput = kayttajaInput(),
            permission: PermissionEntity? = null,
            kutsu: KayttajakutsuEntity? = null,
        ): HankekayttajaEntity =
            HankekayttajaEntity(
                id = id,
                hankeId = hankeId,
                etunimi = kayttaja.etunimi,
                sukunimi = kayttaja.sukunimi,
                sahkoposti = kayttaja.email,
                puhelin = kayttaja.puhelin,
                permission = permission,
                kayttajakutsu = kutsu
            )

        fun generateHankeKayttajat(amount: Int = 3): List<HankeKayttajaDto> =
            (1..amount).map {
                HankeKayttajaDto(
                    id = UUID.randomUUID(),
                    sahkoposti = "email.$it.address.com",
                    nimi = "test name$it",
                    kayttooikeustaso = KATSELUOIKEUS,
                    tunnistautunut = it % 2 == 0
                )
            }
    }
}
