package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import java.util.UUID
import org.springframework.stereotype.Component

@Component
object HakemusyhteyshenkiloFactory {

    const val DEFAULT_ETUNIMI = "Tauno"
    const val DEFAULT_SUKUNIMI = "Testaaja"
    const val DEFAULT_SAHKOPOSTI = "tauno.testaaja@gmail.com"
    const val DEFAULT_PUHELIN = "0401234567"
    const val DEFAULT_TILAAJA = false
    val DEFAULT_ID = UUID.fromString("650fbf8b-e4f2-495b-9bc4-efd1ccf1c2a7")
    val DEFAULT_HANKEKAYTTAJA_ID = UUID.fromString("db92aa0e-5a32-4ffd-83b3-9bff28700cfc")

    fun create(
        etunimi: String = DEFAULT_ETUNIMI,
        sukunimi: String = DEFAULT_SUKUNIMI,
        sahkoposti: String = DEFAULT_SAHKOPOSTI,
        puhelin: String = DEFAULT_PUHELIN,
        tilaaja: Boolean = DEFAULT_TILAAJA,
    ) =
        Hakemusyhteyshenkilo(
            DEFAULT_ID,
            DEFAULT_HANKEKAYTTAJA_ID,
            etunimi,
            sukunimi,
            sahkoposti,
            puhelin,
            tilaaja
        )

    fun createEntity(
        id: UUID = UUID.randomUUID(),
        etunimi: String = DEFAULT_ETUNIMI,
        sukunimi: String = DEFAULT_SUKUNIMI,
        sahkoposti: String = DEFAULT_SAHKOPOSTI,
        puhelin: String = DEFAULT_PUHELIN,
        tilaaja: Boolean = DEFAULT_TILAAJA,
        permission: PermissionEntity = PermissionFactory.createEntity(),
        hakemusyhteystieto: HakemusyhteystietoEntity,
    ): HakemusyhteyshenkiloEntity =
        HakemusyhteyshenkiloEntity(
            id = id,
            hakemusyhteystieto = hakemusyhteystieto,
            hankekayttaja =
                HankekayttajaEntity(
                    id = id,
                    hankeId = hakemusyhteystieto.application.hanke.id,
                    etunimi = etunimi,
                    sukunimi = sukunimi,
                    sahkoposti = sahkoposti,
                    puhelin = puhelin,
                    permission = permission,
                ),
            tilaaja = tilaaja
        )

    fun createEntity(
        hakemusyhteystieto: HakemusyhteystietoEntity,
        hankekayttaja: HankekayttajaEntity,
        tilaaja: Boolean
    ): HakemusyhteyshenkiloEntity =
        HakemusyhteyshenkiloEntity(
            hakemusyhteystieto = hakemusyhteystieto,
            hankekayttaja = hankekayttaja,
            tilaaja = tilaaja
        )

    fun HakemusyhteystietoEntity.withYhteyshenkilo(
        permission: PermissionEntity = PermissionFactory.createEntity()
    ): HakemusyhteystietoEntity {
        yhteyshenkilot.add(createEntity(permission = permission, hakemusyhteystieto = this))
        return this
    }
}
