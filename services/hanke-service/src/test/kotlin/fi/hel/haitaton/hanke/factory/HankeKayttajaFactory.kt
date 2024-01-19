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

@Component
class HankeKayttajaFactory(
    private val hankeKayttajaRepository: HankekayttajaRepository,
    private val permissionService: PermissionService,
    private val kayttajakutsuRepository: KayttajakutsuRepository
) {

    fun saveUnidentifiedUser(
        hankeId: Int,
        etunimi: String = KAKE,
        sukunimi: String = KATSELIJA,
        sahkoposti: String = KAKE_EMAIL,
        puhelin: String = KAKE_PUHELIN,
        kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
        tunniste: String = "existing",
    ): HankekayttajaEntity =
        addToken(
            hankeKayttaja =
                saveUser(
                    hankeId = hankeId,
                    etunimi = etunimi,
                    sukunimi = sukunimi,
                    sahkoposti = sahkoposti,
                    puhelin = puhelin,
                    permissionEntity = null,
                ),
            tunniste = tunniste,
            kayttooikeustaso = kayttooikeustaso,
        )

    fun saveIdentifiedUser(
        hankeId: Int,
        etunimi: String = KAKE,
        sukunimi: String = KATSELIJA,
        sahkoposti: String = KAKE_EMAIL,
        puhelin: String = KAKE_PUHELIN,
        kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
        userId: String = "fake id",
    ): HankekayttajaEntity =
        saveUser(
            hankeId = hankeId,
            etunimi = etunimi,
            sukunimi = sukunimi,
            sahkoposti = sahkoposti,
            puhelin = puhelin,
            permissionEntity = permissionService.create(hankeId, userId, kayttooikeustaso),
        )

    fun saveIdentifiedUser(
        hankeId: Int,
        input: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso,
    ): HankekayttajaEntity =
        saveIdentifiedUser(
            hankeId = hankeId,
            etunimi = input.etunimi,
            sukunimi = input.sukunimi,
            sahkoposti = input.email,
            puhelin = input.puhelin,
            kayttooikeustaso = kayttooikeustaso
        )

    fun saveUser(
        hankeId: Int,
        etunimi: String = KAKE,
        sukunimi: String = KATSELIJA,
        sahkoposti: String = KAKE_EMAIL,
        puhelin: String = KAKE_PUHELIN,
        permissionEntity: PermissionEntity? = null,
        kayttajakutsuEntity: KayttajakutsuEntity? = null,
    ): HankekayttajaEntity =
        hankeKayttajaRepository.save(
            HankekayttajaEntity(
                hankeId = hankeId,
                etunimi = etunimi,
                sukunimi = sukunimi,
                sahkoposti = sahkoposti,
                puhelin = puhelin,
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

        const val KAKE = "Kake"
        const val KATSELIJA = "Katselija"
        const val KAKE_EMAIL = "kake@katselu.test"
        const val KAKE_PUHELIN = "0501234567"

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
            etunimi: String = KAKE,
            sukunimi: String = KATSELIJA,
            sahkoposti: String = KAKE_EMAIL,
            puhelin: String = KAKE_PUHELIN,
            permission: PermissionEntity? = null,
            kutsu: KayttajakutsuEntity? = null,
        ): HankekayttajaEntity =
            HankekayttajaEntity(
                id = id,
                hankeId = hankeId,
                etunimi = etunimi,
                sukunimi = sukunimi,
                sahkoposti = sahkoposti,
                puhelin = puhelin,
                permission = permission,
                kayttajakutsu = kutsu
            )

        fun createDto(i: Int = 1, tunnistautunut: Boolean = false, id: UUID = UUID.randomUUID()) =
            HankeKayttajaDto(
                id = id,
                sahkoposti = "email.$i.address.com",
                etunimi = "test$i",
                sukunimi = "name$i",
                nimi = "test$i name$i",
                puhelinnumero = "040555$i$i$i$i",
                kayttooikeustaso = KATSELUOIKEUS,
                tunnistautunut = tunnistautunut
            )

        fun generateHankeKayttajat(amount: Int = 3): List<HankeKayttajaDto> =
            (1..amount).map { createDto(it, it % 2 == 0) }
    }
}
