package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
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
import org.springframework.transaction.annotation.Transactional

@Component
class HankeKayttajaFactory(
    private val hankeKayttajaRepository: HankekayttajaRepository,
    private val permissionService: PermissionService,
    private val kayttajakutsuRepository: KayttajakutsuRepository,
    private val applicationRepository: ApplicationRepository,
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
        kutsujaId: UUID? = null,
        userId: String = FAKE_USERID,
    ): HankekayttajaEntity =
        saveUser(
            hankeId = hankeId,
            etunimi = etunimi,
            sukunimi = sukunimi,
            sahkoposti = sahkoposti,
            puhelin = puhelin,
            kutsujaId = kutsujaId,
            permissionEntity = permissionService.create(hankeId, userId, kayttooikeustaso),
        )

    fun findOrSaveIdentifiedUser(
        hankeId: Int,
        input: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso,
    ): HankekayttajaEntity =
        hankeKayttajaRepository
            .findByHankeIdAndSahkopostiIn(hankeId, listOf(input.email))
            .firstOrNull()
            ?: saveIdentifiedUser(
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
        kutsujaId: UUID? = null,
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
                kutsujaId = kutsujaId,
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
                createdAt = INVITATION_DATE,
                kayttooikeustaso = kayttooikeustaso,
                hankekayttaja = this,
            )
        )

    @Transactional
    fun getFounderFromHakemus(applicationId: Long): HankekayttajaEntity {
        val application: ApplicationEntity = applicationRepository.getReferenceById(applicationId)
        val permission =
            permissionService.findPermission(application.hanke.id, application.userId!!)!!
        return hankeKayttajaRepository.findByPermissionId(permission.id)!!
    }

    @Transactional
    fun getFounderFromHanke(hanke: HankeEntity): HankekayttajaEntity {
        val permission = permissionService.findPermission(hanke.id, hanke.createdByUserId!!)!!
        return hankeKayttajaRepository.findByPermissionId(permission.id)!!
    }

    companion object {
        val KAYTTAJA_ID: UUID = UUID.fromString("639870ab-533d-4172-8e97-e5b93a275514")

        const val KAKE = "Kake"
        const val KATSELIJA = "Katselija"
        const val KAKE_EMAIL = "kake@katselu.test"
        const val KAKE_PUHELIN = "0501234567"

        const val FAKE_USERID = "fake id"

        val INVITATION_DATE: OffsetDateTime = OffsetDateTime.parse("2024-02-29T15:43:12Z")

        val KAYTTAJA_INPUT_HAKIJA =
            HankekayttajaInput(
                "Henri",
                "Hakija",
                "henri.hakija@mail.com",
                "0401234567",
            )

        val KAYTTAJA_INPUT_PERUSTAJA =
            HankekayttajaInput(
                "Piia",
                "Perustaja",
                "piia.perustaja@mail.com",
                "0401234566",
            )

        val KAYTTAJA_INPUT_OMISTAJA =
            HankekayttajaInput(
                "Olivia",
                "Omistaja",
                "olivia.omistaja@mail.com",
                "0401234565",
            )

        val KAYTTAJA_INPUT_RAKENNUTTAJA =
            HankekayttajaInput(
                "Rane",
                "Rakennuttaja",
                "rane.rakennuttaja@mail.com",
                "0401234564",
            )

        val KAYTTAJA_INPUT_ASIANHOITAJA =
            HankekayttajaInput(
                "Anssi",
                "Asianhoitaja",
                "anssi.asianhoitaja@mail.com",
                "0401234563",
            )

        val KAYTTAJA_INPUT_SUORITTAJA =
            HankekayttajaInput(
                "Timo",
                "Työnsuorittaja",
                "timo.tyonsuorittaja@mail.com",
                "0401234562",
            )

        val KAYTTAJA_INPUT_MUU =
            HankekayttajaInput(
                "Meeri",
                "Muukäyttäjä",
                "meeri.muukayttaja@mail.com",
                "0401234561",
            )

        fun create(
            id: UUID = KAYTTAJA_ID,
            hankeId: Int = HankeFactory.defaultId,
            etunimi: String = KAKE,
            sukunimi: String = KATSELIJA,
            sahkoposti: String = KAKE_EMAIL,
            puhelinnumero: String = KAKE_PUHELIN,
            kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
            roolit: List<ContactType> = emptyList(),
            permissionId: Int? = PERMISSION_ID,
            kayttajaTunnisteId: UUID? = TUNNISTE_ID,
        ): HankeKayttaja =
            HankeKayttaja(
                id = id,
                hankeId = hankeId,
                etunimi = etunimi,
                sukunimi = sukunimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                kayttooikeustaso = kayttooikeustaso,
                roolit = roolit,
                permissionId = permissionId,
                kayttajaTunnisteId = kayttajaTunnisteId,
                kutsuttu = if (permissionId != null) INVITATION_DATE else null,
            )

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

        fun createDto(
            id: UUID = KAYTTAJA_ID,
            etunimi: String = KAKE,
            sukunimi: String = KATSELIJA,
            sahkoposti: String = KAKE_EMAIL,
            puhelinnumero: String = KAKE_PUHELIN,
            kayttooikeustaso: Kayttooikeustaso = KATSELUOIKEUS,
            roolit: List<ContactType> = emptyList(),
            tunnistautunut: Boolean = true,
        ) =
            HankeKayttajaDto(
                id = id,
                etunimi = etunimi,
                sukunimi = sukunimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                kayttooikeustaso = kayttooikeustaso,
                roolit = roolit,
                tunnistautunut = tunnistautunut,
                kutsuttu = if (tunnistautunut) null else INVITATION_DATE,
            )

        fun createHankeKayttaja(i: Int = 1, vararg roolit: ContactType): HankeKayttajaDto =
            createDto(i, roolit = roolit.toList(), tunnistautunut = (i % 2 == 0))

        fun createHankeKayttajat(
            amount: Int = 3,
            roolit: List<ContactType> = emptyList()
        ): List<HankeKayttajaDto> =
            (1..amount).map { createDto(it, roolit = roolit, tunnistautunut = (it % 2 == 0)) }

        private fun createDto(
            i: Int,
            id: UUID = UUID.randomUUID(),
            roolit: List<ContactType> = emptyList(),
            tunnistautunut: Boolean = false
        ) =
            createDto(
                id = id,
                sahkoposti = "email.$i.address.com",
                etunimi = "test$i",
                sukunimi = "name$i",
                puhelinnumero = "040555$i$i$i$i",
                roolit = roolit,
                tunnistautunut = tunnistautunut,
            )
    }
}
