package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.ModifyHankealueRequest
import fi.hel.haitaton.hanke.domain.NewGeometriat
import fi.hel.haitaton.hanke.domain.NewHankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import java.time.ZonedDateTime
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.springframework.stereotype.Service

@Service
class HankealueService(
    private val geometriatService: GeometriatService,
    private val tormaystarkasteluService: TormaystarkasteluLaskentaService,
) {

    fun mergeAlueetToHanke(
        incoming: List<ModifyHankealueRequest>,
        existingHanke: HankeEntity,
        currentUserId: String,
    ) {
        mergeDataInto(incoming, existingHanke.alueet) { source, target ->
            copyNonNullHankealueFieldsToEntity(
                existingHanke.hankeTunnus,
                source,
                target,
                currentUserId,
            )
        }
        existingHanke.alueet.forEach { it.hanke = existingHanke }
    }

    /** Map by area geometry id to area geometry data. */
    fun geometryMapFrom(alueet: List<HankealueEntity>): Map<Int, Geometriat?> =
        alueet
            .mapNotNull { it.geometriat }
            .associateBy({ it }, { geometriatService.getGeometriat(it) })

    fun copyNonNullHankealueFieldsToEntity(
        hankeTunnus: String,
        source: Hankealue,
        target: HankealueEntity?,
        currentUserId: String,
    ): HankealueEntity {
        val result = target ?: HankealueEntity(nimi = source.nimi, tormaystarkasteluTulos = null)

        // Assuming the incoming date, while being zoned date and time, is in UTC and time value can
        // be simply dropped here.
        // Note, .toLocalDate() does not do any time zone conversion.
        source.haittaLoppuPvm?.let { result.haittaLoppuPvm = it.toLocalDate() }
        source.haittaAlkuPvm?.let { result.haittaAlkuPvm = it.toLocalDate() }

        source.kaistaHaitta?.let { result.kaistaHaitta = it }
        source.kaistaPituusHaitta?.let { result.kaistaPituusHaitta = it }
        source.meluHaitta?.let { result.meluHaitta = it }
        source.polyHaitta?.let { result.polyHaitta = it }
        source.tarinaHaitta?.let { result.tarinaHaitta = it }
        source.geometriat?.let {
            it.resetFeatureProperties(hankeTunnus)
            val saved = geometriatService.saveGeometriat(it, result.geometriat, currentUserId)
            result.geometriat = saved?.id
        }
        result.nimi = source.nimi
        source.haittojenhallintasuunnitelma?.let {
            result.haittojenhallintasuunnitelma.clear()
            result.haittojenhallintasuunnitelma.putAll(it)
        }

        return result
    }

    fun createAlueetFromCreateRequest(
        alueet: List<NewHankealue>,
        entity: HankeEntity,
        currentUserId: String,
    ): List<HankealueEntity> =
        alueet.map {
            createAlueFromCreateRequest(entity.hankeTunnus, it, currentUserId).apply {
                hanke = entity
            }
        }

    private fun createAlueFromCreateRequest(
        hanketunnus: String,
        source: NewHankealue,
        currentUserId: String,
    ): HankealueEntity {
        val result = HankealueEntity(nimi = source.nimi, tormaystarkasteluTulos = null)

        // Assuming the incoming date, while being zoned date and time, is in UTC and time value can
        // be simply dropped here.
        // Note, .toLocalDate() does not do any time zone conversion.
        result.haittaLoppuPvm = source.haittaLoppuPvm?.toLocalDate()
        result.haittaAlkuPvm = source.haittaAlkuPvm?.toLocalDate()

        result.kaistaHaitta = source.kaistaHaitta
        result.kaistaPituusHaitta = source.kaistaPituusHaitta
        result.meluHaitta = source.meluHaitta
        result.polyHaitta = source.polyHaitta
        result.tarinaHaitta = source.tarinaHaitta
        source.geometriat?.let {
            it.resetFeatureProperties(hanketunnus)
            val saved = geometriatService.createGeometriat(it, currentUserId)
            result.geometriat = saved.id
        }

        return result
    }

    fun updateTormaystarkastelu(alue: HankealueEntity) {
        alue.tormaystarkasteluTulos =
            tormaystarkasteluService.calculateTormaystarkastelu(alue)?.let {
                TormaystarkasteluTulosEntity(
                    autoliikenne = it.autoliikenne.indeksi,
                    haitanKesto = it.autoliikenne.haitanKesto,
                    katuluokka = it.autoliikenne.katuluokka,
                    autoliikennemaara = it.autoliikenne.liikennemaara,
                    kaistahaitta = it.autoliikenne.kaistahaitta,
                    kaistapituushaitta = it.autoliikenne.kaistapituushaitta,
                    pyoraliikenne = it.pyoraliikenneindeksi,
                    linjaautoliikenne = it.linjaautoliikenneindeksi,
                    raitioliikenne = it.raitioliikenneindeksi,
                    hankealue = alue,
                )
            }
    }

    companion object {

        fun createHankealueetFromApplicationAreas(
            areas: List<JohtoselvitysHakemusalue>?,
            startTime: ZonedDateTime?,
            endTime: ZonedDateTime?,
        ): List<NewHankealue> =
            areas?.let {
                it.map { Feature().apply { geometry = it.geometry } }
                    .map { feature -> FeatureCollection().add(feature) }
                    .map { featureCollection -> NewGeometriat(featureCollection) }
                    .mapIndexed { i, geometria ->
                        NewHankealue(
                            nimi = "$HANKEALUE_DEFAULT_NAME ${i + 1}",
                            geometriat = geometria,
                            haittaAlkuPvm = startTime,
                            haittaLoppuPvm = endTime,
                        )
                    }
            } ?: emptyList()
    }
}
