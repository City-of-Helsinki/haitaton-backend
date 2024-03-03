package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeYhteyshenkiloEntity
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.toUUID
import java.util.UUID

object HankeYhteyshenkiloFactory {

    fun create(
        id: UUID = UUID.fromString("ed1d0ea2-c5de-4298-859a-9d02eb828668"),
        etunimi: String = HankeKayttajaFactory.KAKE,
        sukunimi: String = HankeKayttajaFactory.KATSELIJA,
        sahkoposti: String = HankeKayttajaFactory.KAKE_EMAIL,
        puhelinnumero: String = HankeKayttajaFactory.KAKE_EMAIL
    ) = Yhteyshenkilo(id, etunimi, sukunimi, sahkoposti, puhelinnumero)

    fun create(i: Int) =
        Yhteyshenkilo(
            id = i.toUUID(),
            etunimi = "Etu$i",
            sukunimi = "Suku$i",
            sahkoposti = "email$i",
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
                        sahkoposti = sahkoposti,
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
            sahkoposti = HankeKayttajaFactory.KAKE_EMAIL,
            puhelinnumero = HankeKayttajaFactory.KAKE_PUHELIN
        )
}
