package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import java.util.UUID
import org.springframework.stereotype.Component

@Component
object HakemusyhteyshenkiloFactory {

    private const val DEFAULT_ETUNIMI = "Tauno"
    private const val DEFAULT_SUKUNIMI = "Testaaja"
    private const val DEFAULT_SAHKOPOSTI = "tauno.testaaja@gmail.com"
    private const val DEFAULT_PUHELIN = "0401234567"
    private const val DEFAULT_TILAAJA = false

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
