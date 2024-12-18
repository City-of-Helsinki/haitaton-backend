package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.roundToOneDecimal
import kotlin.math.max
import org.geojson.Crs
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.geojson.GeoJsonObject
import org.geojson.GeometryCollection
import org.geojson.Polygon
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TormaystarkasteluLaskentaService(
    @Autowired private val tormaysService: TormaystarkasteluTormaysService
) {

    fun calculateTormaystarkastelu(alue: HankealueEntity): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(alue)) {
            return null
        }

        val autoliikenneindeksi = calculateAutoliikenneindeksi(alue)
        val pyoraliikenneindeksi = calculatePyoraliikenneindeksi(setOf(alue.geometriat!!))
        val linjaautoliikenneindeksi =
            calculateLinjaautoliikenneindeksi(setOf(alue.geometriat!!)).toFloat()
        val raitioliikenneindeksi = calculateRaitioliikenneindeksi(setOf(alue.geometriat!!))

        return TormaystarkasteluTulos(
            autoliikenneindeksi,
            pyoraliikenneindeksi,
            linjaautoliikenneindeksi,
            raitioliikenneindeksi,
        )
    }

    fun calculateTormaystarkastelu(
        geometriat: FeatureCollection,
        haittaajanKestoDays: Int,
        kaistahaitta: VaikutusAutoliikenteenKaistamaariin,
        kaistapituushaitta: AutoliikenteenKaistavaikutustenPituus,
    ): TormaystarkasteluTulos {

        val geometryCollection = GeometryCollection()
        geometryCollection.crs = Crs()
        geometryCollection.crs.properties["name"] = "EPSG:$SRID"
        geometryCollection.geometries = geometriat.features.map { it.geometry }

        return TormaystarkasteluTulos(
            calculateAutoliikenneindeksi(
                geometryCollection,
                haittaajanKestoDays,
                kaistahaitta,
                kaistapituushaitta,
            ),
            calculatePyoraliikenneindeksi(geometryCollection),
            calculateLinjaautoliikenneindeksi(geometryCollection).toFloat(),
            calculateRaitioliikenneindeksi(geometryCollection),
        )
    }

    fun calculateTormaystarkastelu(
        geometry: Polygon,
        haittaajanKestoDays: Int,
        kaistahaitta: VaikutusAutoliikenteenKaistamaariin,
        kaistapituushaitta: AutoliikenteenKaistavaikutustenPituus,
    ): TormaystarkasteluTulos =
        calculateTormaystarkastelu(
            FeatureCollection().apply { add(Feature().apply { this.geometry = geometry }) },
            haittaajanKestoDays,
            kaistahaitta,
            kaistapituushaitta,
        )

    private fun hasAllRequiredInformation(alue: HankealueEntity): Boolean {
        return (alue.haittaAlkuPvm != null &&
            alue.haittaLoppuPvm != null &&
            alue.kaistaHaitta != null &&
            alue.kaistaPituusHaitta != null &&
            alue.geometriat != null)
    }

    private fun calculateAutoliikenneindeksi(alue: HankealueEntity): Autoliikenneluokittelu {
        val haittaajankesto =
            RajaArvoLuokittelija.haittaajankestoluokka(alue.haittaAjanKestoDays()!!)
        val katuluokka = katuluokkaluokittelu(setOf(alue.geometriat!!))
        val liikennemaara = liikennemaaraluokittelu(setOf(alue.geometriat!!), katuluokka)
        val kaistahaitta = alue.kaistaHaitta ?: VaikutusAutoliikenteenKaistamaariin.EI_VAIKUTA
        val kaistapituushaitta =
            alue.kaistaPituusHaitta
                ?: AutoliikenteenKaistavaikutustenPituus.EI_VAIKUTA_KAISTAJARJESTELYIHIN
        return Autoliikenneluokittelu(
            haittaajankesto,
            katuluokka,
            liikennemaara,
            kaistahaitta.value,
            kaistapituushaitta.value,
        )
    }

    private fun calculateAutoliikenneindeksi(
        geometria: GeoJsonObject,
        haittaajanKestoDays: Int,
        kaistahaitta: VaikutusAutoliikenteenKaistamaariin,
        kaistapituushaitta: AutoliikenteenKaistavaikutustenPituus,
    ): Autoliikenneluokittelu {
        val haittaajankesto = RajaArvoLuokittelija.haittaajankestoluokka(haittaajanKestoDays)
        val katuluokka = katuluokkaluokittelu(geometria)
        val liikennemaara = liikennemaaraluokittelu(geometria, katuluokka)
        return Autoliikenneluokittelu(
            haittaajankesto,
            katuluokka,
            liikennemaara,
            kaistahaitta.value,
            kaistapituushaitta.value,
        )
    }

    internal fun katuluokkaluokittelu(geometriaIds: Set<Int>): Int =
        tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriaIds) ?: 0

    internal fun katuluokkaluokittelu(geometry: GeoJsonObject): Int =
        tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry) ?: 0

    internal fun liikennemaaraluokittelu(geometriaIds: Set<Int>, katuluokkaluokittelu: Int): Int =
        liikennemaaraluokittelu(katuluokkaluokittelu) { radius ->
            tormaysService.maxLiikennemaara(geometriaIds, radius)
        }

    internal fun liikennemaaraluokittelu(geometry: GeoJsonObject, katuluokkaluokittelu: Int): Int =
        liikennemaaraluokittelu(katuluokkaluokittelu) { radius ->
            tormaysService.maxLiikennemaara(geometry, radius)
        }

    private fun liikennemaaraluokittelu(
        katuluokkaluokittelu: Int,
        maxLiikennemaara: (TormaystarkasteluLiikennemaaranEtaisyys) -> Int?
    ): Int {
        if (katuluokkaluokittelu == 0) {
            return 0
        }
        // type of street (=street class) decides which volume data we use for traffic (buffering of
        // street width varies)
        val radius =
            if (katuluokkaluokittelu >= 4) {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            } else {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            }
        val maxVolume = maxLiikennemaara(radius) ?: 0
        return RajaArvoLuokittelija.liikennemaaraluokka(maxVolume)
    }

    internal fun calculatePyoraliikenneindeksi(geometriaIds: Set<Int>): Float =
        tormaysService.maxIntersectingPyoraliikenneHierarkia(geometriaIds)?.toFloat() ?: 0f

    internal fun calculatePyoraliikenneindeksi(geometry: GeoJsonObject): Float =
        tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry)?.toFloat() ?: 0f

    internal fun calculateLinjaautoliikenneindeksi(geometriaIds: Set<Int>): Int =
        calculateLinjaautoliikenneindeksi(
            { tormaysService.anyIntersectsCriticalBusRoutes(geometriaIds) },
            { tormaysService.getIntersectingBusRoutes(geometriaIds) },
        )

    internal fun calculateLinjaautoliikenneindeksi(geometria: GeoJsonObject): Int =
        calculateLinjaautoliikenneindeksi(
            { tormaysService.anyIntersectsCriticalBusRoutes(geometria) },
            { tormaysService.getIntersectingBusRoutes(geometria) },
        )

    private fun calculateLinjaautoliikenneindeksi(
        intersectsWithCriticalRoutes: () -> Boolean,
        intersectingRoutes: () -> Set<TormaystarkasteluBussireitti>
    ): Int {
        if (intersectsWithCriticalRoutes()) {
            return Linjaautoliikenneluokittelu.TARKEIMMAT_JOUKKOLIIKENNEKADUT.value
        }

        val bussireitit = intersectingRoutes()

        val valueByRunkolinja =
            bussireitit.maxOfOrNull { it.runkolinja.toLinjaautoliikenneluokittelu().value }
                // bussireitit is empty
                ?: return Linjaautoliikenneluokittelu.EI_VAIKUTA_LINJAAUTOLIIKENTEESEEN.value

        val countOfRushHourBuses = bussireitit.sumOf { it.vuoromaaraRuuhkatunnissa }
        val valueByRajaArvo =
            RajaArvoLuokittelija.linjaautoliikenteenRuuhkavuoroluokka(countOfRushHourBuses)

        return max(valueByRajaArvo, valueByRunkolinja)
    }

    internal fun calculateRaitioliikenneindeksi(geometriaIds: Set<Int>): Float =
        calculateRaitioliikenneindeksi(
            { tormaysService.anyIntersectsWithTramLines(geometriaIds) },
            { tormaysService.anyIntersectsWithTramInfra(geometriaIds) },
        )

    internal fun calculateRaitioliikenneindeksi(geometria: GeoJsonObject): Float =
        calculateRaitioliikenneindeksi(
            { tormaysService.anyIntersectsWithTramLines(geometria) },
            { tormaysService.anyIntersectsWithTramInfra(geometria) },
        )

    private fun calculateRaitioliikenneindeksi(
        intersectsWithLines: () -> Boolean,
        intersectsWithInfra: () -> Boolean,
    ): Float =
        when {
                intersectsWithLines() ->
                    Raitioliikenneluokittelu
                        .RAITIOTIEVERKON_RATAOSA_JOLLA_SAANNOLLISTA_LINJALIIKENNETTA
                intersectsWithInfra() ->
                    Raitioliikenneluokittelu
                        .RAITIOTIEVERKON_RATAOSA_JOLLA_EI_SAANNOLLISTA_LINJALIIKENNETTA
                else -> Raitioliikenneluokittelu.EI_TUNNISTETTUJA_RAITIOTIEKISKOJA
            }
            .value
            .toFloat()

    companion object {
        fun calculateAutoliikenneindeksi(
            haitanKesto: Int,
            katuluokka: Int,
            liikennemaara: Int,
            kaistahaitta: Int,
            kaistapituushaitta: Int,
        ): Float =
            if (katuluokka == 0 && liikennemaara == 0) {
                0.0f
            } else {
                (0.1f * haitanKesto +
                        0.2f * katuluokka +
                        0.25f * liikennemaara +
                        0.25f * kaistahaitta +
                        0.2f * kaistapituushaitta)
                    .roundToOneDecimal()
            }
    }
}
