package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import org.geojson.FeatureCollection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

data class PublicHankeYhteystieto(
        var organisaatioId: Int?,
        var organisaatioNimi: String?,
        var osasto: String?
)

data class PublicHanke(
        val id: Int,
        val hankeTunnus: String,
        val alkuPvm: ZonedDateTime,
        val loppuPvm: ZonedDateTime,
        val haittaAlkuPvm: ZonedDateTime,
        val haittaLoppuPvm: ZonedDateTime,
        val vaihe: Vaihe,
        val suunnitteluVaihe: SuunnitteluVaihe?,
        val tyomaaTyyppi: MutableSet<TyomaaTyyppi>,
        val omistajat: List<PublicHankeYhteystieto>,
        val featureCollection: FeatureCollection?
)

fun hankeYhteystietoToPublic(yhteystieto: HankeYhteystieto) : PublicHankeYhteystieto {
    return PublicHankeYhteystieto(
            yhteystieto.organisaatioId,
            yhteystieto.organisaatioNimi,
            yhteystieto.osasto
    )
}

fun hankeToPublic(hanke: Hanke) : PublicHanke {
    val omistajat = hanke.omistajat
            .filter { it.organisaatioNimi != null || it.organisaatioId != null }
            .map { hankeYhteystietoToPublic(it) }

    return PublicHanke(
            hanke.id!!,
            hanke.hankeTunnus!!,
            hanke.alkuPvm!!,
            hanke.loppuPvm!!,
            hanke.haittaAlkuPvm!!,
            hanke.haittaLoppuPvm!!,
            hanke.vaihe!!,
            hanke.suunnitteluVaihe,
            hanke.tyomaaTyyppi,
            omistajat,
            hanke.geometriat?.featureCollection
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
                .filter { it.tilat.onLiikenneHaittaIndeksi }
        hankkeet.forEach { it.geometriat = hankeGeometriatService.loadGeometriat(it) }
        return hankkeet.map { hankeToPublic(it) }
    }

}
