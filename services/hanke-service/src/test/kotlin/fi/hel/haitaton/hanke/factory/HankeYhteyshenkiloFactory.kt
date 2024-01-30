package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeYhteyshenkiloEntity
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.toUUID
import java.util.UUID

object HankeYhteyshenkiloFactory {

    fun create(i: Int) =
        Yhteyshenkilo(
            id = i.toUUID(),
            etunimi = "Etu$i",
            sukunimi = "Suku$i",
            email = "email$i",
            puhelinnumero = "010$i",
        )

    fun HankeYhteystieto.withYhteyshenkilo(i: Int) =
        copy(yhteyshenkilot = yhteyshenkilot + create(i))

    fun createEntity(id: Int, hankeYhteystieto: HankeYhteystietoEntity): HankeYhteyshenkiloEntity =
        with(create(id)) {
            HankeYhteyshenkiloEntity(
                id = this.id,
                hankeKayttaja =
                    HankekayttajaEntity(
                        id = this.id,
                        hankeId = hankeYhteystieto.hanke!!.id,
                        etunimi = etunimi,
                        sukunimi = sukunimi,
                        sahkoposti = email,
                        puhelin = puhelinnumero,
                        permission = null,
                    ),
                hankeYhteystieto = hankeYhteystieto,
            )
        }

    fun kake(id: UUID) =
        Yhteyshenkilo(
            id = id,
            etunimi = HankeKayttajaFactory.KAKE,
            sukunimi = HankeKayttajaFactory.KATSELIJA,
            email = HankeKayttajaFactory.KAKE_EMAIL,
            puhelinnumero = HankeKayttajaFactory.KAKE_PUHELIN
        )
}
