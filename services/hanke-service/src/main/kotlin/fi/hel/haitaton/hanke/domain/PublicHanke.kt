package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.tormaystarkastelu.LiikennehaittaIndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import org.geojson.FeatureCollection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

data class PublicHankeYhteystieto(
        val organisaatioId: Int?,
        val organisaatioNimi: String?,
        val osasto: String?
)

fun hankeYhteystietoToPublic(yhteystieto: HankeYhteystieto) = PublicHankeYhteystieto(
        yhteystieto.organisaatioId,
        yhteystieto.organisaatioNimi,
        yhteystieto.osasto
)

data class PublicHankeGeometriat(
        val id: Int,
        val version: Int,
        val hankeId: Int,
        val createdAt: ZonedDateTime,
        val modifiedAt: ZonedDateTime?,
        val featureCollection: FeatureCollection?
)

fun hankeGeometriatToPublic(geometriat: HankeGeometriat) = PublicHankeGeometriat(
        geometriat.id!!,
        geometriat.version!!,
        geometriat.hankeId!!,
        geometriat.createdAt!!,
        geometriat.modifiedAt,
        geometriat.featureCollection
)

data class PublicHanke(
        val id: Int,
        val hankeTunnus: String,
        val nimi: String,
        val kuvaus: String,
        val alkuPvm: ZonedDateTime,
        val loppuPvm: ZonedDateTime,
        val haittaAlkuPvm: ZonedDateTime,
        val haittaLoppuPvm: ZonedDateTime,
        val vaihe: Vaihe,
        val suunnitteluVaihe: SuunnitteluVaihe?,
        val tyomaaTyyppi: MutableSet<TyomaaTyyppi>,
        val liikennehaittaindeksi: LiikennehaittaIndeksiType,
        val tormaystarkasteluTulos: TormaystarkasteluTulos,
        val omistajat: List<PublicHankeYhteystieto>,
        val geometriat: PublicHankeGeometriat?
)

fun hankeToPublic(hanke: Hanke): PublicHanke {
    val omistajat = hanke.omistajat
            .filter { it.organisaatioNimi != null || it.organisaatioId != null }
            .map { hankeYhteystietoToPublic(it) }

    val geometriat = hanke.geometriat
    val publicGeometriat = if (geometriat != null) hankeGeometriatToPublic(geometriat) else null

    return PublicHanke(
            hanke.id!!,
            hanke.hankeTunnus!!,
            hanke.nimi!!,
            hanke.kuvaus!!,
            hanke.alkuPvm!!,
            hanke.loppuPvm!!,
            hanke.haittaAlkuPvm!!,
            hanke.haittaLoppuPvm!!,
            hanke.vaihe!!,
            hanke.suunnitteluVaihe,
            hanke.tyomaaTyyppi,
            hanke.liikennehaittaindeksi!!,
            hanke.tormaystarkasteluTulos!!,
            omistajat,
            publicGeometriat
    )
}



@RestController
@RequestMapping("/public-hankkeet")
class PublicHankeController(
        @Autowired private val hankeService: HankeService,
        @Autowired private val hankeGeometriatService: HankeGeometriatService
) {

    @GetMapping
    fun getAll(): List<PublicHanke> {
        val hankkeet = hankeService.loadAllHanke()
                .filter { it.tormaystarkasteluTulos != null }
        hankkeet.forEach { it.geometriat = hankeGeometriatService.loadGeometriat(it) }
        return hankkeet.map { hankeToPublic(it) }
    }

}
