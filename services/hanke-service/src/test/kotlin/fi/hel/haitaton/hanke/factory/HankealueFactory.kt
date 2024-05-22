package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.ZonedDateTime

object HankealueFactory {

    fun create(
        id: Int? = 1,
        hankeId: Int? = 2,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? = GeometriaFactory.create(),
        kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? =
            VaikutusAutoliikenteenKaistamaariin.VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA,
        kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? =
            AutoliikenteenKaistavaikutustenPituus.PITUUS_100_499_METRIA,
        meluHaitta: Meluhaitta? = Meluhaitta.SATUNNAINEN_MELUHAITTA,
        polyHaitta: Polyhaitta? = Polyhaitta.TOISTUVA_POLYHAITTA,
        tarinaHaitta: Tarinahaitta? = Tarinahaitta.JATKUVA_TARINAHAITTA,
        nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
        tormaystarkasteluTulos: TormaystarkasteluTulos? = tormaystarkasteluTulos(),
        haittojenhallintasuunnitelma: Map<Haittojenhallintatyyppi, String>? = null,
    ): SavedHankealue {
        return SavedHankealue(
            id,
            hankeId,
            haittaAlkuPvm,
            haittaLoppuPvm,
            geometriat,
            kaistaHaitta,
            kaistaPituusHaitta,
            meluHaitta,
            polyHaitta,
            tarinaHaitta,
            nimi,
            tormaystarkasteluTulos,
            haittojenhallintasuunnitelma,
        )
    }

    fun createHaittojenhallintasuunnitelma(): Map<Haittojenhallintatyyppi, String> {
        return mapOf(
            Haittojenhallintatyyppi.YLEINEN to "Yleisten haittojen hallintasuunnitelma",
            Haittojenhallintatyyppi.PYORALIIKENNE to
                "Pyöräliikenteelle koituvien haittojen hallintasuunnitelma",
            Haittojenhallintatyyppi.AUTOLIIKENNE to
                "Autoliikenteelle koituvien haittojen hallintasuunnitelma",
            Haittojenhallintatyyppi.LINJAAUTOLIIKENNE to
                "Linja-autoliikenteelle koituvien haittojen hallintasuunnitelma",
            Haittojenhallintatyyppi.RAITIOLIIKENNE to
                "Raitioliikenteelle koituvien haittojen hallintasuunnitelma",
            Haittojenhallintatyyppi.MUUT to "Muiden haittojen hallintasuunnitelma"
        )
    }

    fun createMinimal(
        id: Int? = null,
        hankeId: Int? = null,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? = null,
        kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? = null,
        kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? = null,
        meluHaitta: Meluhaitta? = null,
        polyHaitta: Polyhaitta? = null,
        tarinaHaitta: Tarinahaitta? = null,
        nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
    ): SavedHankealue {
        return SavedHankealue(
            id,
            hankeId,
            haittaAlkuPvm,
            haittaLoppuPvm,
            geometriat,
            kaistaHaitta,
            kaistaPituusHaitta,
            meluHaitta,
            polyHaitta,
            tarinaHaitta,
            nimi,
            null
        )
    }

    fun createHankeAlueEntity(mockId: Int = 1, hankeEntity: HankeEntity): HankealueEntity {
        val alue = create(id = mockId).apply { geometriat?.id = mockId }
        return HankealueEntity(
                id = alue.id!!,
                hanke = hankeEntity,
                geometriat = alue.geometriat?.id,
                haittaAlkuPvm = DateFactory.getStartDatetime().toLocalDate(),
                haittaLoppuPvm = DateFactory.getEndDatetime().toLocalDate(),
                kaistaHaitta = alue.kaistaHaitta,
                kaistaPituusHaitta = alue.kaistaPituusHaitta,
                meluHaitta = alue.meluHaitta,
                polyHaitta = alue.polyHaitta,
                tarinaHaitta = alue.tarinaHaitta,
                nimi = alue.nimi,
                tormaystarkasteluTulos = null,
                haittojenhallintasuunnitelma = alue.haittojenhallintasuunnitelma,
            )
            .apply { tormaystarkasteluTulos = tormaystarkasteluTulosEntity(hankealueEntity = this) }
    }

    private fun tormaystarkasteluTulos() =
        TormaystarkasteluTulos(
            autoliikenneindeksi = 1.25f,
            pyoraliikenneindeksi = 2.5f,
            linjaautoliikenneindeksi = 3.75f,
            raitioliikenneindeksi = 3.75f,
        )

    private fun tormaystarkasteluTulosEntity(
        id: Int = 1,
        hankealueEntity: HankealueEntity,
    ): TormaystarkasteluTulosEntity =
        TormaystarkasteluTulosEntity(
            id = id,
            autoliikenne = 1.25f,
            pyoraliikenne = 2.5f,
            linjaautoliikenne = 3.75f,
            raitioliikenne = 3.75f,
            hankealue = hankealueEntity,
        )
}
