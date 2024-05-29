package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.roundToOneDecimal
import kotlin.math.max
import org.geojson.Crs
import org.geojson.FeatureCollection
import org.geojson.GeoJsonObject
import org.geojson.GeometryCollection
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

    private fun hasAllRequiredInformation(alue: HankealueEntity): Boolean {
        return (alue.haittaAlkuPvm != null &&
            alue.haittaLoppuPvm != null &&
            alue.kaistaHaitta != null &&
            alue.kaistaPituusHaitta != null &&
            alue.geometriat != null)
    }

    private fun calculateAutoliikenneindeksi(alue: HankealueEntity): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] =
            RajaArvoLuokittelija.haittaajankestoluokka(alue.haittaAjanKestoDays()!!)
        luokittelu[LuokitteluType.VAIKUTUS_AUTOLIIKENTEEN_KAISTAMAARIIN] =
            alue.kaistaHaitta?.value!!
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_KAISTAVAIKUTUSTEN_PITUUS] =
            alue.kaistaPituusHaitta?.value!!

        val katuluokkaLuokittelu = katuluokkaluokittelu(setOf(alue.geometriat!!))
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_MAARA] =
            liikennemaaraluokittelu(setOf(alue.geometriat!!), katuluokkaLuokittelu)

        return calculateAutoliikenneindeksiFromLuokittelu(luokittelu)
    }

    private fun calculateAutoliikenneindeksi(
        geometria: GeoJsonObject,
        haittaajanKestoDays: Int,
        kaistahaitta: VaikutusAutoliikenteenKaistamaariin,
        kaistapituushaitta: AutoliikenteenKaistavaikutustenPituus,
    ): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] =
            RajaArvoLuokittelija.haittaajankestoluokka(haittaajanKestoDays)
        luokittelu[LuokitteluType.VAIKUTUS_AUTOLIIKENTEEN_KAISTAMAARIIN] = kaistahaitta.value
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_KAISTAVAIKUTUSTEN_PITUUS] =
            kaistapituushaitta.value

        val katuluokkaLuokittelu = katuluokkaluokittelu(geometria)
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_MAARA] =
            liikennemaaraluokittelu(geometria, katuluokkaLuokittelu)

        return calculateAutoliikenneindeksiFromLuokittelu(luokittelu)
    }

    internal fun katuluokkaluokittelu(geometriaIds: Set<Int>): Int =
        katuluokkaluokittelu(
            { tormaysService.anyIntersectsYleinenKatuosa(geometriaIds) },
            { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriaIds) },
            { tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriaIds) },
        )

    internal fun katuluokkaluokittelu(geometry: GeoJsonObject): Int =
        katuluokkaluokittelu(
            { tormaysService.anyIntersectsYleinenKatuosa(geometry) },
            { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry) },
            { tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometry) },
        )

    private fun katuluokkaluokittelu(
        ylreParts: () -> Boolean,
        streetClasses: () -> Int?,
        ylreClasses: () -> Int?,
    ): Int =
        if (ylreParts()) {
            // Use street classes if they are available.
            // Otherwise, default to ylre classes.
            streetClasses() ?: ylreClasses() ?: 0
        } else {
            val max = ylreClasses()
            if (max == null) {
                // If there are no ylre parts or classes, return 0
                0
            } else {
                // Use street classes if they are available.
                // Otherwise default to ylre classes.
                streetClasses() ?: max
            }
        }

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

    internal fun calculateAutoliikenneindeksiFromLuokittelu(
        luokitteluByType: Map<LuokitteluType, Int>
    ): Float =
        luokitteluByType
            .map { (type, index) -> autoliikenneindeksipainot(type).times(index) }
            .sum()
            .roundToOneDecimal()

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
        fun autoliikenneindeksipainot(luokittelu: LuokitteluType) =
            when (luokittelu) {
                LuokitteluType.HAITTA_AJAN_KESTO -> 0.1f
                LuokitteluType.VAIKUTUS_AUTOLIIKENTEEN_KAISTAMAARIIN -> 0.25f
                LuokitteluType.AUTOLIIKENTEEN_KAISTAVAIKUTUSTEN_PITUUS -> 0.2f
                LuokitteluType.KATULUOKKA -> 0.2f
                LuokitteluType.AUTOLIIKENTEEN_MAARA -> 0.25f
            }
    }
}
