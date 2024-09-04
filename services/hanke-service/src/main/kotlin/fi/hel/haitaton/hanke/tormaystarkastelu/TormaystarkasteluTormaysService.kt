package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.toJsonString
import java.util.Collections
import org.geojson.GeoJsonObject
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service

@Service
class TormaystarkasteluTormaysService(private val jdbcOperations: JdbcOperations) {

    /** liikenteellinen katuluokka, street_classes */
    fun maxIntersectingLiikenteellinenKatuluokka(geometriaIds: Set<Int>): Int? =
        getDistinctValuesIntersectingRows(
                geometriaIds, "tormays_street_classes_polys", "street_class")
            .maxOfOrNull { TormaystarkasteluKatuluokka.valueOfKatuluokka(it).value }

    fun maxIntersectingLiikenteellinenKatuluokka(geometry: GeoJsonObject): Int? =
        getDistinctValuesIntersectingRows(geometry, "tormays_street_classes_polys", "street_class")
            .maxOfOrNull { TormaystarkasteluKatuluokka.valueOfKatuluokka(it).value }

    /** kantakaupunki, central_business_area */
    fun anyIntersectsWithKantakaupunki(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_central_business_area_polys")

    fun maxLiikennemaara(
        geometriaIds: Set<Int>,
        etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Int? {
        if (geometriaIds.isEmpty()) return null
        val placeholders = Collections.nCopies(geometriaIds.size, "?").joinToString(", ")
        val tableName = "tormays_volumes${etaisyys.radius}_polys"
        val sql =
            """
            SELECT max($tableName.volume)
            FROM $tableName, hankegeometria
            WHERE hankegeometria.hankegeometriatid in ($placeholders)
            AND ST_Intersects($tableName.geom, hankegeometria.geometria)
            """
                .trimIndent()
        return jdbcOperations
            .queryForObject(sql, Integer::class.java, *geometriaIds.toTypedArray())
            ?.toInt()
    }

    fun maxLiikennemaara(
        geometry: GeoJsonObject,
        etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Int? {
        val tableName = "tormays_volumes${etaisyys.radius}_polys"
        val sql =
            """
            SELECT max($tableName.volume)
            FROM $tableName
            WHERE ST_Intersects($tableName.geom, ST_GeomFromGeoJSON(?))
            """
                .trimIndent()
        return jdbcOperations.queryForObject(sql, Int::class.java, geometry.toJsonString())
    }

    fun anyIntersectsCriticalBusRoutes(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_critical_area_polys")

    fun anyIntersectsCriticalBusRoutes(geometria: GeoJsonObject) =
        anyIntersectsWith(geometria, "tormays_critical_area_polys")

    fun getIntersectingBusRoutes(geometriaIds: Set<Int>): Set<TormaystarkasteluBussireitti> {
        if (geometriaIds.isEmpty()) return setOf()
        val placeholders = Collections.nCopies(geometriaIds.size, "?").joinToString(", ")
        val sql =
            """
            SELECT DISTINCT ON (buses.route_id, buses.direction_id)
                buses.route_id,
                buses.direction_id,
                buses.rush_hour,
                buses.trunk
            FROM tormays_buses_polys buses, hankegeometria
            WHERE hankegeometria.hankegeometriatid in ($placeholders)
            AND ST_Intersects(buses.geom, hankegeometria.geometria)
            ORDER BY buses.route_id, buses.direction_id, buses.trunk DESC, buses.rush_hour DESC
            """
                .trimIndent()

        return jdbcOperations
            .query(sql, bussireittiRowMapper, *geometriaIds.toTypedArray())
            .toHashSet()
    }

    fun getIntersectingBusRoutes(geometria: GeoJsonObject): Set<TormaystarkasteluBussireitti> {
        val sql =
            """
            SELECT DISTINCT ON (route_id, direction_id)
                route_id,
                direction_id,
                rush_hour,
                trunk
            FROM tormays_buses_polys
            WHERE ST_Intersects(tormays_buses_polys.geom, ST_GeomFromGeoJSON(?))
            ORDER BY route_id, direction_id, trunk DESC, rush_hour DESC
            """
                .trimIndent()

        return jdbcOperations.query(sql, bussireittiRowMapper, geometria.toJsonString()).toHashSet()
    }

    fun anyIntersectsWithTramLines(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_tram_lines_polys")

    fun anyIntersectsWithTramLines(geometria: GeoJsonObject) =
        anyIntersectsWith(geometria, "tormays_tram_lines_polys")

    fun anyIntersectsWithTramInfra(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_tram_infra_polys")

    fun anyIntersectsWithTramInfra(geometria: GeoJsonObject) =
        anyIntersectsWith(geometria, "tormays_tram_infra_polys")

    fun maxIntersectingPyoraliikenneHierarkia(geometriaIds: Set<Int>): Int? =
        getDistinctValuesIntersectingRows(geometriaIds, "tormays_cycle_infra_polys", "hierarkia")
            .maxOfOrNull { PyoraliikenteenHierarkia.valueOfHierarkia(it).value }

    fun maxIntersectingPyoraliikenneHierarkia(geometry: GeoJsonObject): Int? =
        getDistinctValuesIntersectingRows(geometry, "tormays_cycle_infra_polys", "hierarkia")
            .maxOfOrNull { PyoraliikenteenHierarkia.valueOfHierarkia(it).value }

    private fun getDistinctValuesIntersectingRows(
        geometriaIds: Set<Int>,
        table: String,
        column: String
    ): List<String> {
        if (geometriaIds.isEmpty()) return listOf()
        val placeholders = Collections.nCopies(geometriaIds.size, "?").joinToString(", ")
        val sql =
            """
            SELECT DISTINCT $table.$column
            FROM $table, hankegeometria
            WHERE hankegeometria.hankegeometriatid in ($placeholders)
            AND ST_Intersects($table.geom, hankegeometria.geometria)
            """
                .trimIndent()
        return jdbcOperations.queryForList(sql, String::class.java, *geometriaIds.toTypedArray())
    }

    private fun getDistinctValuesIntersectingRows(
        geometria: GeoJsonObject,
        table: String,
        column: String
    ): List<String> {
        val sql =
            """
            SELECT DISTINCT $table.$column
            FROM $table
            WHERE ST_Intersects($table.geom, ST_GeomFromGeoJSON(?))
            """
                .trimIndent()
        return jdbcOperations.queryForList(sql, String::class.java, geometria.toJsonString())
    }

    private fun anyIntersectsWith(geometriat: Set<Int>, table: String): Boolean {
        if (geometriat.isEmpty()) return false
        val placeholders = Collections.nCopies(geometriat.size, "?").joinToString(", ")
        val sql =
            """
            SELECT count(*)
            FROM $table, hankegeometria
            WHERE hankegeometria.hankegeometriatid in ($placeholders)
            AND ST_Intersects($table.geom, hankegeometria.geometria)
            """
                .trimIndent()
        return jdbcOperations.queryForObject(sql, Int::class.java, *geometriat.toTypedArray())!! > 0
    }

    private fun anyIntersectsWith(geometria: GeoJsonObject, table: String): Boolean {
        val sql =
            """
            SELECT exists(
              SELECT
              FROM $table
              WHERE ST_Intersects($table.geom, ST_GeomFromGeoJSON(?))
            )
            """
                .trimIndent()
        return jdbcOperations.queryForObject(sql, Boolean::class.java, geometria.toJsonString())!!
    }

    companion object {
        private val bussireittiRowMapper = RowMapper { rs, _ ->
            TormaystarkasteluBussireitti(
                rs.getString(1),
                rs.getInt(2),
                rs.getInt(3),
                TormaystarkasteluBussiRunkolinja.valueOfRunkolinja(rs.getString(4)),
            )
        }
    }
}

// Enum classes that associate the strings used in this database dataset with integer classification
// values

enum class PyoraliikenteenHierarkia(val value: Int, val hierarkia: String) {
    MUU_YHTEYS(2, "Muu yhteys"),
    MUU_PYORAREITTI(3, "Muu pyöräreitti"),
    BAANA(5, "Baana"),
    PAAPYORAREITTI(5, "Pääpyöräreitti"),
    ;

    companion object {
        fun valueOfHierarkia(hierarkia: String?): PyoraliikenteenHierarkia =
            hierarkia?.let {
                return entries.first { it.hierarkia == hierarkia }
            } ?: MUU_PYORAREITTI
    }
}

enum class TormaystarkasteluKatuluokka(val value: Int, val katuluokka: String) {
    /**
     * @deprecated This will be replaced in data by the [TONTTIKATU_TAI_AJOYHTEYS] and
     *   [KANTAKAUPUNGIN_ASUNTOKATU_HUOLTAVAYLA_TAI_VAHALIIKENTEINEN_KATU] values. This can be
     *   removed when both street classes and ylre classes have been updated to use the new classes.
     *   As of writing (9.2.2024), the street classes just need deploying, but the ylre classes need
     *   updates to the material processing.
     */
    OLD_TONTTIKATU_TAI_AJOYHTEYS(1, "Tonttikatu tai ajoyhteys"),
    TONTTIKATU_TAI_AJOYHTEYS(1, "Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu"),
    KANTAKAUPUNGIN_ASUNTOKATU_HUOLTAVAYLA_TAI_VAHALIIKENTEINEN_KATU(
        2, "Kantakaupungin asuntokatu, huoltoväylä tai muu vähäliikenteinen katu"),
    PAIKALLINEN_KOKOOJAKATU(3, "Paikallinen kokoojakatu"),
    ALUEELLINEN_KOKOOJAKATU(4, "Alueellinen kokoojakatu"),
    PAAKATU_TAI_MOOTTORIVAYLA(5, "Pääkatu tai moottoriväylä"),
    ;

    companion object {
        fun valueOfKatuluokka(katuluokka: String): TormaystarkasteluKatuluokka {
            return entries.first { it.katuluokka == katuluokka }
        }
    }
}

enum class TormaystarkasteluBussiRunkolinja(val runkolinja: String) {
    EI("no"),
    ON("yes");

    companion object {
        fun valueOfRunkolinja(runkolinja: String): TormaystarkasteluBussiRunkolinja {
            return entries.find { it.runkolinja == runkolinja }
                ?: throw IllegalArgumentException(
                    "Unknown runkolinja value: $runkolinja. Only 'yes' and 'no' are allowed.")
        }
    }

    fun toLinjaautoliikenneluokittelu(): Linjaautoliikenneluokittelu =
        when (this) {
            ON -> Linjaautoliikenneluokittelu.RUNKOLINJA
            EI -> Linjaautoliikenneluokittelu.EI_VUOROJA_RUUHKAAIKANA
        }
}

/** There are two(2) separate traffic counts - one for radius of 15m and other for 30m */
enum class TormaystarkasteluLiikennemaaranEtaisyys(internal val radius: Int) {
    RADIUS_15(15),
    RADIUS_30(30)
}

/** Bus route */
class TormaystarkasteluBussireitti(
    val reittiId: String,
    val suunta: Int,
    val vuoromaaraRuuhkatunnissa: Int,
    val runkolinja: TormaystarkasteluBussiRunkolinja
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TormaystarkasteluBussireitti) return false

        if (reittiId != other.reittiId) return false
        if (suunta != other.suunta) return false

        return true
    }

    override fun hashCode(): Int {
        var result = reittiId.hashCode()
        result = 31 * result + suunta
        return result
    }
}
