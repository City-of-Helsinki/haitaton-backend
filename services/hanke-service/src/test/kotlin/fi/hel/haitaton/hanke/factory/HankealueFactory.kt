package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.LocalDate
import java.time.ZonedDateTime

object HankealueFactory {

    const val DEFAULT_HANKEALUE_ID = 1

    fun create(
        id: Int? = DEFAULT_HANKEALUE_ID,
        hankeId: Int? = 2,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? = GeometriaFactory.create(),
        kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? =
            VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
        kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? =
            AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA,
        meluHaitta: Meluhaitta? = Meluhaitta.SATUNNAINEN_MELUHAITTA,
        polyHaitta: Polyhaitta? = Polyhaitta.TOISTUVA_POLYHAITTA,
        tarinaHaitta: Tarinahaitta? = Tarinahaitta.JATKUVA_TARINAHAITTA,
        nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
        tormaystarkasteluTulos: TormaystarkasteluTulos? = HaittaFactory.tormaystarkasteluTulos(),
        haittojenhallintasuunnitelma: Haittojenhallintasuunnitelma? =
            HaittaFactory.createHaittojenhallintasuunnitelma(),
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
            null,
        )
    }

    fun createHankeAlueEntity(
        mockId: Int = 1,
        hankeEntity: HankeEntity,
        haittaAlkuPvm: LocalDate? = DateFactory.getStartDatetime().toLocalDate(),
        haittaLoppuPvm: LocalDate? = DateFactory.getEndDatetime().toLocalDate(),
    ): HankealueEntity {
        val alue = create(id = mockId).apply { geometriat?.id = mockId }
        return HankealueEntity(
                id = alue.id!!,
                hanke = hankeEntity,
                geometriat = alue.geometriat?.id,
                haittaAlkuPvm = haittaAlkuPvm,
                haittaLoppuPvm = haittaLoppuPvm,
                kaistaHaitta = alue.kaistaHaitta,
                kaistaPituusHaitta = alue.kaistaPituusHaitta,
                meluHaitta = alue.meluHaitta,
                polyHaitta = alue.polyHaitta,
                tarinaHaitta = alue.tarinaHaitta,
                nimi = alue.nimi,
                tormaystarkasteluTulos = null,
            )
            .apply {
                tormaystarkasteluTulos = tormaystarkasteluTulosEntity(hankealueEntity = this)
                alue.haittojenhallintasuunnitelma?.let { haittojenhallintasuunnitelma.putAll(it) }
            }
    }

    private fun tormaystarkasteluTulosEntity(
        id: Int = 1,
        hankealueEntity: HankealueEntity,
    ): TormaystarkasteluTulosEntity =
        with(HaittaFactory.TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU) {
            TormaystarkasteluTulosEntity(
                id = id,
                autoliikenne = indeksi,
                haitanKesto = haitanKesto,
                katuluokka = katuluokka,
                autoliikennemaara = liikennemaara,
                kaistahaitta = kaistahaitta,
                kaistapituushaitta = kaistapituushaitta,
                pyoraliikenne = 2.5f,
                linjaautoliikenne = 3.75f,
                raitioliikenne = 3.75f,
                hankealue = hankealueEntity,
            )
        }
}
