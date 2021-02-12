package fi.hel.haitaton.hanke.tormaystarkastelu

import org.springframework.jdbc.core.JdbcOperations

class TormaystarkasteluDaoImpl(private val jdbcOperations: JdbcOperations) : TormaystarkasteluDao {

    override fun yleisetKatualueet(hankegeometriatId: Int): List<YleinenKatualueTormaystarkastelu> {
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
                    YleinenKatualueTormaystarkastelu(
                        rs.getInt(2) == 1,
                        rs.getInt(3)
                    )
                }, hankegeometriatId
            )
        }
    }

    override fun pyorailyreitit(hankegeometriatId: Int): List<PyorailyTormaystarkastelu> {
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
                    PyorailyTormaystarkastelu(
                        TormaystarkasteluPyorailyreittiluokitus.valueOfCycleway(rs.getString(2))
                            ?: TormaystarkasteluPyorailyreittiluokitus.EI_PYORAILYREITTI,
                        rs.getInt(3)
                    )
                }, hankegeometriatId, hankegeometriatId
            )
        }
    }
}