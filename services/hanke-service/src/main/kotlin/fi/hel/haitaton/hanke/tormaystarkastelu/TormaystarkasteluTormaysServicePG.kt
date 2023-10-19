package fi.hel.haitaton.hanke.tormaystarkastelu

import java.util.Collections
import org.springframework.jdbc.core.JdbcOperations

// PostGIS implementation
class TormaystarkasteluTormaysServicePG(private val jdbcOperations: JdbcOperations) :
    TormaystarkasteluTormaysService {

    override fun anyIntersectsYleinenKatuosa(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_ylre_parts_polys")

    override fun maxIntersectingYleinenkatualueKatuluokka(geometriaIds: Set<Int>) =
        getDistinctValuesIntersectingRows(geometriaIds, "tormays_ylre_classes_polys", "ylre_class")
            .maxOfOrNull { TormaystarkasteluKatuluokka.valueOfKatuluokka(it).value }

    override fun maxIntersectingLiikenteellinenKatuluokka(geometriaIds: Set<Int>) =
        getDistinctValuesIntersectingRows(
                geometriaIds,
                "tormays_street_classes_polys",
                "street_class"
            )
            .maxOfOrNull { TormaystarkasteluKatuluokka.valueOfKatuluokka(it).value }

    override fun anyIntersectsWithKantakaupunki(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_central_business_area_polys")

    override fun maxLiikennemaara(
        geometriaIds: Set<Int>,
        etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Int? {
        if (geometriaIds.isEmpty()) return null
        val placeholders = Collections.nCopies(geometriaIds.size, "?").joinToString(", ")
        val tableName = "tormays_volumes${etaisyys.radius}_polys"
        with(jdbcOperations) {
            return queryForObject(
                    """
            SELECT max($tableName.volume)
            FROM $tableName, hankegeometria
            WHERE hankegeometria.hankegeometriatid in ($placeholders)
            AND ST_Intersects($tableName.geom, hankegeometria.geometria)
            """.trimIndent(),
                    Integer::class.java,
                    *geometriaIds.toTypedArray()
                )
                ?.toInt()
        }
    }

    override fun anyIntersectsCriticalBusRoutes(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_critical_area_polys")

    override fun getIntersectingBusRoutes(
        geometriaIds: Set<Int>
    ): Set<TormaystarkasteluBussireitti> {
        if (geometriaIds.isEmpty()) return setOf()
        val placeholders = Collections.nCopies(geometriaIds.size, "?").joinToString(", ")
        with(jdbcOperations) {
            // TODO: DISTINCT ON (route_id, direction_id) at the database level
            return query(
                    """
                SELECT 
                    tormays_buses_polys.fid,
                    tormays_buses_polys.route_id,
                    tormays_buses_polys.direction_id,
                    tormays_buses_polys.rush_hour,
                    tormays_buses_polys.trunk,
                    hankegeometria.id
                FROM tormays_buses_polys, hankegeometria
                WHERE hankegeometria.hankegeometriatid in ($placeholders)
                AND ST_Intersects(tormays_buses_polys.geom, hankegeometria.geometria)
                """.trimIndent(),
                    { rs, _ ->
                        TormaystarkasteluBussireitti(
                            rs.getString(2),
                            rs.getInt(3),
                            rs.getInt(4),
                            TormaystarkasteluBussiRunkolinja.valueOfRunkolinja(rs.getString(5))!!
                        )
                    },
                    *geometriaIds.toTypedArray()
                )
                .toHashSet()
        }
    }

    override fun maxIntersectingTramByLaneType(geometriaIds: Set<Int>) =
        getDistinctValuesIntersectingRows(geometriaIds, "tormays_trams_polys", "lane").maxOfOrNull {
            TormaystarkasteluRaitiotiekaistatyyppi.valueOfKaistatyyppi(it).value
        }

    override fun anyIntersectsWithCyclewaysPriority(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_cycleways_priority_polys")

    override fun anyIntersectsWithCyclewaysMain(geometriaIds: Set<Int>) =
        anyIntersectsWith(geometriaIds, "tormays_cycleways_main_polys")

    private fun getDistinctValuesIntersectingRows(
        geometriaIds: Set<Int>,
        table: String,
        column: String
    ): List<String> {
        if (geometriaIds.isEmpty()) return listOf()
        val placeholders = Collections.nCopies(geometriaIds.size, "?").joinToString(", ")
        with(jdbcOperations) {
            return query(
                """
            SELECT DISTINCT $table.$column
            FROM $table, hankegeometria
            WHERE hankegeometria.hankegeometriatid in ($placeholders)
            AND ST_Intersects($table.geom, hankegeometria.geometria)
            """.trimIndent(),
                { rs, _ -> rs.getString(1) },
                *geometriaIds.toTypedArray()
            )
        }
    }

    private fun anyIntersectsWith(geometriat: Set<Int>, table: String): Boolean {
        if (geometriat.isEmpty()) return false
        val placeholders = Collections.nCopies(geometriat.size, "?").joinToString(", ")
        return jdbcOperations.queryForObject(
            """
            SELECT count(*)
            FROM $table, hankegeometria
            WHERE hankegeometria.hankegeometriatid in ($placeholders)
            AND ST_Intersects($table.geom, hankegeometria.geometria)
            """.trimIndent(),
            Int::class.java,
            *geometriat.toTypedArray()
        )!! > 0
    }
}

// Enum classes that associate the strings used in this database dataset with integer classification
// values

enum class TormaystarkasteluKatuluokka(val value: Int, val katuluokka: String) {
    TONTTIKATU_TAI_AJOYHTEYS(1, "Tonttikatu tai ajoyhteys"),
    PAIKALLINEN_KOKOOJAKATU(3, "Paikallinen kokoojakatu"),
    ALUEELLINEN_KOKOOJAKATU(4, "Alueellinen kokoojakatu"),
    PAAKATU_TAI_MOOTTORIVAYLA(5, "Pääkatu tai moottoriväylä");

    companion object {
        fun valueOfKatuluokka(katuluokka: String): TormaystarkasteluKatuluokka {
            return entries.first { it.katuluokka == katuluokka }
        }
    }
}

enum class TormaystarkasteluBussiRunkolinja(val runkolinja: String) {
    EI("no"),
    LAHES("almost"),
    ON("yes");

    companion object {
        fun valueOfRunkolinja(runkolinja: String): TormaystarkasteluBussiRunkolinja? {
            return entries.find { it.runkolinja == runkolinja }
        }
    }

    fun toBussiLiikenneLuokittelu(): BussiLiikenneLuokittelu =
        when (this) {
            ON -> BussiLiikenneLuokittelu.RUNKOLINJA
            LAHES -> BussiLiikenneLuokittelu.RUNKOLINJAMAINEN
            EI -> BussiLiikenneLuokittelu.PERUS
        }
}

enum class TormaystarkasteluRaitiotiekaistatyyppi(val value: Int, val kaistatyyppi: String) {
    OMA(4, "dedicated"),
    JAETTU(3, "mixed");

    companion object {
        fun valueOfKaistatyyppi(kaistatyyppi: String): TormaystarkasteluRaitiotiekaistatyyppi {
            return entries.first { it.kaistatyyppi == kaistatyyppi }
        }
    }
}
