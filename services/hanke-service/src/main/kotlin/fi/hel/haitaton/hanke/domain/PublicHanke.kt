package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class PublicHankeYhteystieto(val organisaatioNimi: String?)

fun HankeYhteystieto.toPublic(): PublicHankeYhteystieto =
    when (tyyppi) {
        YhteystietoTyyppi.YKSITYISHENKILO -> PublicHankeYhteystieto(null)
        else -> PublicHankeYhteystieto(nimi)
    }

data class PublicGeometriat(val featureCollection: FeatureCollection?)

fun Geometriat.toPublic() = PublicGeometriat(featureCollection)

data class PublicHankealue(
    val id: Int?,
    val hankeId: Int?,
    val haittaAlkuPvm: ZonedDateTime?,
    val haittaLoppuPvm: ZonedDateTime?,
    val geometriat: PublicGeometriat?,
    val kaistaHaitta: VaikutusAutoliikenteenKaistamaariin?,
    val kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus?,
    val meluHaitta: Meluhaitta?,
    val polyHaitta: Polyhaitta?,
    val tarinaHaitta: Tarinahaitta?,
    val nimi: String,
    val tormaystarkastelu: TormaystarkasteluTulos?,
)

data class PublicHanke(
    val id: Int,
    val hankeTunnus: String,
    val nimi: String,
    val kuvaus: String,
    val alkuPvm: ZonedDateTime,
    val loppuPvm: ZonedDateTime,
    val vaihe: Hankevaihe,
    val tyomaaTyyppi: Set<TyomaaTyyppi>,
    val omistajat: List<PublicHankeYhteystieto>,
    val alueet: List<PublicHankealue>,
)

fun SavedHankealue.toPublic() =
    PublicHankealue(
        id,
        hankeId,
        haittaAlkuPvm,
        haittaLoppuPvm,
        geometriat?.toPublic(),
        kaistaHaitta,
        kaistaPituusHaitta,
        meluHaitta,
        polyHaitta,
        tarinaHaitta,
        nimi,
        tormaystarkasteluTulos,
    )

fun Hanke.toPublic() =
    PublicHanke(
        id,
        hankeTunnus,
        nimi,
        kuvaus!!,
        alkuPvm!!,
        loppuPvm!!,
        vaihe!!,
        tyomaaTyyppi,
        omistajat.map { it.toPublic() },
        alueet.map { it.toPublic() },
    )
