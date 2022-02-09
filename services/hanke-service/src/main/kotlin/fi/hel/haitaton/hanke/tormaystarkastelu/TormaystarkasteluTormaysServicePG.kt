package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import org.springframework.jdbc.core.JdbcOperations

// PostGIS implementation
class TormaystarkasteluTormaysServicePG(private val jdbcOperations: JdbcOperations) : TormaystarkasteluTormaysService {

    override fun anyIntersectsYleinenKatuosa(hankegeometriat: HankeGeometriat) =
            anyIntersectsWith(hankegeometriat, "tormays_ylre_parts_polys")

    override fun maxIntersectingYleinenkatualueKatuluokka(hankegeometriat: HankeGeometriat) =
            getDistinctValuesIntersectingRows(hankegeometriat, "tormays_ylre_classes_polys", "ylre_class")
                    .maxOfOrNull { TormaystarkasteluKatuluokka.valueOfKatuluokka(it).value }

    override fun maxIntersectingLiikenteellinenKatuluokka(hankegeometriat: HankeGeometriat) =
            getDistinctValuesIntersectingRows(hankegeometriat, "tormays_street_classes_polys", "street_class")
                    .maxOfOrNull { TormaystarkasteluKatuluokka.valueOfKatuluokka(it).value }

    override fun anyIntersectsWithKantakaupunki(hankegeometriat: HankeGeometriat) =
            anyIntersectsWith(hankegeometriat, "tormays_central_business_area_polys")

    override fun maxLiikennemaara(
            hankegeometriat: HankeGeometriat,
            etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Int? {
        val tableName = "tormays_volumes${etaisyys.radius}_polys"
        with(jdbcOperations) {
            return queryForObject("""
            SELECT max($tableName.volume)
            FROM $tableName, hankegeometria
            WHERE hankegeometria.hankegeometriatid = ?
            AND ST_Intersects($tableName.geom, hankegeometria.geometria)
            """.trimIndent(), Integer::class.java, hankegeometriat.id)?.toInt()
        }
    }

    override fun anyIntersectsCriticalBusRoutes(hankegeometriat: HankeGeometriat) =
            anyIntersectsWith(hankegeometriat, "tormays_critical_area_polys")

    override fun getIntersectingBusRoutes(hankegeometriat: HankeGeometriat): Set<TormaystarkasteluBussireitti> {
        with(jdbcOperations) {
            // TODO: DISTINCT ON (route_id, direction_id) at the database level
            return query("""
                SELECT 
                    tormays_buses_polys.fid,
                    tormays_buses_polys.route_id,
                    tormays_buses_polys.direction_id,
                    tormays_buses_polys.rush_hour,
                    tormays_buses_polys.trunk,
                    hankegeometria.id
                FROM tormays_buses_polys, hankegeometria
                WHERE hankegeometria.hankegeometriatid = ?
                AND ST_Intersects(tormays_buses_polys.geom, hankegeometria.geometria)
                """.trimIndent(), { rs, _ -> TormaystarkasteluBussireitti(
                    rs.getString(2),
                    rs.getInt(3),
                    rs.getInt(4),
                    TormaystarkasteluBussiRunkolinja.valueOfRunkolinja(rs.getString(5))!!
                )}, hankegeometriat.id!!
            ).toHashSet()
        }
    }

    override fun maxIntersectingTramByLaneType(hankegeometriat: HankeGeometriat) =
            getDistinctValuesIntersectingRows(hankegeometriat, "tormays_trams_polys", "lane")
                    .maxOfOrNull { TormaystarkasteluRaitiotiekaistatyyppi.valueOfKaistatyyppi(it).value }

    override fun anyIntersectsWithCyclewaysPriority(hankegeometriat: HankeGeometriat) =
            anyIntersectsWith(hankegeometriat, "tormays_cycleways_priority_polys")

    override fun anyIntersectsWithCyclewaysMain(hankegeometriat: HankeGeometriat) =
            anyIntersectsWith(hankegeometriat, "tormays_cycleways_main_polys")

    private fun getDistinctValuesIntersectingRows(hankegeometriat: HankeGeometriat, table: String, column: String): List<String> {
        with(jdbcOperations) {
            return query("""
            SELECT DISTINCT $table.$column
            FROM $table, hankegeometria
            WHERE hankegeometria.hankegeometriatid = ?
            AND ST_Intersects($table.geom, hankegeometria.geometria)
            """.trimIndent(), { rs, _ ->
                rs.getString(1)
            }, hankegeometriat.id!!)
        }
    }

    private fun anyIntersectsWith(hankegeometriat: HankeGeometriat, table: String): Boolean {
        with(jdbcOperations) {
            return queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM $table, hankegeometria
                WHERE hankegeometria.hankegeometriatid = ?
                AND ST_Intersects($table.geom, hankegeometria.geometria)
            )
            """.trimIndent(), Boolean::class.java, hankegeometriat.id!!) ?: false
        }
    }

}

// Enum classes that associate the strings used in this database dataset with integer classification values

enum class TormaystarkasteluKatuluokka(val value: Int, val katuluokka: String) {
    TONTTIKATU_TAI_AJOYHTEYS(1, "Tonttikatu tai ajoyhteys"),

    PAIKALLINEN_KOKOOJAKATU(3, "Paikallinen kokoojakatu"),
    ALUEELLINEN_KOKOOJAKATU(4, "Alueellinen kokoojakatu"),
    PAAKATU_TAI_MOOTTORIVAYLA(5, "Pääkatu tai moottoriväylä");

    companion object {
        fun valueOfKatuluokka(katuluokka: String): TormaystarkasteluKatuluokka {
            return values().first { it.katuluokka == katuluokka }
        }
    }
}

enum class TormaystarkasteluBussiRunkolinja(val runkolinja: String) {
    EI("no"),
    LAHES("almost"),
    ON("yes");

    companion object {
        fun valueOfRunkolinja(runkolinja: String): TormaystarkasteluBussiRunkolinja? {
            return values().find { it.runkolinja == runkolinja }
        }
    }
}

enum class TormaystarkasteluRaitiotiekaistatyyppi(val value: Int, val kaistatyyppi: String) {
    OMA(4, "dedicated"),
    JAETTU(3, "mixed");

    companion object {
        fun valueOfKaistatyyppi(kaistatyyppi: String): TormaystarkasteluRaitiotiekaistatyyppi {
            return values().first { it.kaistatyyppi == kaistatyyppi }
        }
    }
}
