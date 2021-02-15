package fi.hel.haitaton.hanke.tormaystarkastelu

import org.springframework.jdbc.core.JdbcOperations

class TormaystarkasteluDaoImpl(private val jdbcOperations: JdbcOperations) : TormaystarkasteluDao {

    override fun yleisetKatualueet(hankegeometriatId: Int): Map<Int, Boolean> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_ylre_parts_polys.fid,
                tormays_ylre_parts_polys.ylre_street_area,
                hankegeometria.id
            FROM
                tormays_ylre_parts_polys,
                hankegeometria,
                hankegeometriat
            WHERE
                st_overlaps(tormays_ylre_parts_polys.geom, hankegeometria.geometria) AND
                hankegeometria.hankegeometriatid = hankegeometriat.id AND
                hankegeometriat.id = ?;
        """.trimIndent(), { rs, _ ->
                    Pair(
                        rs.getInt(2) == 1,
                        rs.getInt(3)
                    )
                }, hankegeometriatId
            ).associate { Pair(it.second, it.first) }
        }
    }

    override fun yleisetKatuluokat(hankegeometriatId: Int): Map<Int, TormaystarkasteluKatuluokka> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_ylre_classes_polys.fid,
                tormays_ylre_classes_polys.ylre_class,
                hankegeometria.id
            FROM
                tormays_ylre_classes_polys,
                hankegeometria,
                hankegeometriat
            WHERE
                st_overlaps(tormays_ylre_classes_polys.geom, hankegeometria.geometria) AND
                hankegeometria.hankegeometriatid = hankegeometriat.id AND
                hankegeometriat.id = ?;
        """.trimIndent(), { rs, _ ->
                    val ylreClass = rs.getString(2)
                    Pair(
                        TormaystarkasteluKatuluokka.valueOfKatuluokka(ylreClass) ?: throw IllegalKatuluokkaException(
                            ylreClass
                        ),
                        rs.getInt(3)
                    )
                }, hankegeometriatId
            ).associate { Pair(it.second, it.first) }
        }
    }

    override fun katuluokat(hankegeometriatId: Int): Map<Int, TormaystarkasteluKatuluokka> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_street_classes_polys.fid,
                tormays_street_classes_polys.street_class,
                hankegeometria.id
            FROM
                tormays_street_classes_polys,
                hankegeometria,
                hankegeometriat
            WHERE
                st_overlaps(tormays_street_classes_polys.geom, hankegeometria.geometria) AND
                hankegeometria.hankegeometriatid = hankegeometriat.id AND
                hankegeometriat.id = ?;
        """.trimIndent(), { rs, _ ->
                    val ylreClass = rs.getString(2)
                    Pair(
                        TormaystarkasteluKatuluokka.valueOfKatuluokka(ylreClass) ?: throw IllegalKatuluokkaException(
                            ylreClass
                        ),
                        rs.getInt(3)
                    )
                }, hankegeometriatId
            ).associate { Pair(it.second, it.first) }
        }
    }

    override fun kantakaupunki(hankegeometriatId: Int): Map<Int, Boolean> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_central_business_area_polys.fid,
                tormays_central_business_area_polys.central_business_area,
                hankegeometria.id
            FROM
                tormays_central_business_area_polys,
                hankegeometria,
                hankegeometriat
            WHERE
                st_overlaps(tormays_central_business_area_polys.geom, hankegeometria.geometria) AND
                hankegeometria.hankegeometriatid = hankegeometriat.id AND
                hankegeometriat.id = ?;
        """.trimIndent(), { rs, _ ->
                    Pair(
                        rs.getInt(2) == 1,
                        rs.getInt(3)
                    )
                }, hankegeometriatId
            ).associate { Pair(it.second, it.first) }
        }
    }

    override fun pyorailyreitit(hankegeometriatId: Int): Map<Int, TormaystarkasteluPyorailyreittiluokka> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_cycleways_priority_polys.fid,
                tormays_cycleways_priority_polys.cycleway,
                hankegeometria.id
            FROM 
                tormays_cycleways_priority_polys,
                hankegeometria,
                hankegeometriat
            WHERE
                st_overlaps(tormays_cycleways_priority_polys.geom, hankegeometria.geometria) AND 
                hankegeometria.hankegeometriatid = hankegeometriat.id AND 
                hankegeometriat.id = ?
            UNION
            SELECT 
                tormays_cycleways_main_polys.fid,
                tormays_cycleways_main_polys.cycleway,
                hankegeometria.id
            FROM
                tormays_cycleways_main_polys,
                hankegeometria,
                hankegeometriat
            WHERE
                st_overlaps(tormays_cycleways_main_polys.geom, hankegeometria.geometria) AND
                hankegeometria.hankegeometriatid = hankegeometriat.id AND
                hankegeometriat.id = ?;
        """.trimIndent(), { rs, _ ->
                    Pair(
                        TormaystarkasteluPyorailyreittiluokka.valueOfPyorailyvayla(rs.getString(2))
                            ?: TormaystarkasteluPyorailyreittiluokka.EI_PYORAILYREITTI,
                        rs.getInt(3)
                    )
                }, hankegeometriatId, hankegeometriatId
            ).associate { Pair(it.second, it.first) }
        }
    }
}