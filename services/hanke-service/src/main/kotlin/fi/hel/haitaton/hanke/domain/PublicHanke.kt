package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.LiikennehaittaIndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.time.ZonedDateTime
import org.geojson.FeatureCollection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PublicHankeYhteystieto(
    val organisaatioId: Int?,
    val organisaatioNimi: String?,
    val osasto: String?
)

fun hankeYhteystietoToPublic(yhteystieto: HankeYhteystieto) =
    PublicHankeYhteystieto(
        yhteystieto.organisaatioId,
        yhteystieto.organisaatioNimi,
        yhteystieto.osasto
    )

data class PublicGeometriat(
    val id: Int,
    val version: Int,
    val createdAt: ZonedDateTime,
    val modifiedAt: ZonedDateTime?,
    val featureCollection: FeatureCollection?
)

fun geometriatToPublic(geometriat: Geometriat) =
    PublicGeometriat(
        geometriat.id!!,
        geometriat.version!!,
        geometriat.createdAt!!,
        geometriat.modifiedAt,
        geometriat.featureCollection
    )

data class PublicHankealue(
        var id: Int?,
        var hankeId: Int?,
        var haittaAlkuPvm: ZonedDateTime? = null,
        var haittaLoppuPvm: ZonedDateTime? = null,
        var geometriat: Geometriat? = null,
        var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null,
        var kaistaPituusHaitta: KaistajarjestelynPituus? = null,
        var meluHaitta: Haitta13? = null,
        var polyHaitta: Haitta13? = null,
        var tarinaHaitta: Haitta13? = null,
)

data class PublicHanke(
    val id: Int,
    val hankeTunnus: String,
    val nimi: String,
    val kuvaus: String,
    val alkuPvm: ZonedDateTime?,
    val loppuPvm: ZonedDateTime?,
    val vaihe: Vaihe,
    val suunnitteluVaihe: SuunnitteluVaihe?,
    val tyomaaTyyppi: MutableSet<TyomaaTyyppi>,
    val liikennehaittaindeksi: LiikennehaittaIndeksiType,
    val tormaystarkasteluTulos: TormaystarkasteluTulos,
    val omistajat: List<PublicHankeYhteystieto>,
    val alueet: List<PublicHankealue>
)

fun hankealueToPublic(alue: Hankealue): PublicHankealue {
    return PublicHankealue(
        alue.id,
        alue.hankeId,
        alue.haittaAlkuPvm,
        alue.haittaLoppuPvm,
        alue.geometriat,
        alue.kaistaHaitta,
        alue.kaistaPituusHaitta,
        alue.meluHaitta,
        alue.polyHaitta,
        alue.tarinaHaitta
    )
}

fun hankeToPublic(hanke: Hanke): PublicHanke {
    val omistajat =
        hanke.omistajat
            .filter { it.organisaatioNimi != null || it.organisaatioId != null }
            .map { hankeYhteystietoToPublic(it) }

    val alueet = hanke.alueet.map { hankealueToPublic(it) }

    return PublicHanke(
        hanke.id!!,
        hanke.hankeTunnus!!,
        hanke.nimi!!,
        hanke.kuvaus!!,
        hanke.alkuPvm!!,
        hanke.loppuPvm!!,
        hanke.vaihe!!,
        hanke.suunnitteluVaihe,
        hanke.tyomaaTyyppi,
        hanke.liikennehaittaindeksi!!,
        hanke.tormaystarkasteluTulos!!,
        omistajat,
        alueet
    )
}

@RestController
@RequestMapping("/public-hankkeet")
class PublicHankeController(
    @Autowired private val hankeService: HankeService,
) {

    @GetMapping
    fun getAll(): List<PublicHanke> {
        val hankkeet = hankeService.loadAllHanke().filter { it.tormaystarkasteluTulos != null }
        return hankkeet.map { hankeToPublic(it) }
    }
}
