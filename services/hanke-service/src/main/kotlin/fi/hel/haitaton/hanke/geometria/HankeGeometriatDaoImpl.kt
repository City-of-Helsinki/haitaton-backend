package fi.hel.haitaton.hanke.geometria

import com.fasterxml.jackson.module.kotlin.readValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.toJsonString
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.springframework.jdbc.core.JdbcOperations
import java.sql.Timestamp
import java.sql.Types

class HankeGeometriatDaoImpl(private val jdbcOperations: JdbcOperations) : HankeGeometriatDao {

    companion object {

        private fun saveHankeGeometriaRows(hankeGeometriat: HankeGeometriat, jdbcOperations: JdbcOperations) {
            val arguments: List<Array<Any>>? = hankeGeometriat.featureCollection?.features?.map { feature ->
                arrayOf(hankeGeometriat.id!!, feature.geometry.toJsonString(), feature.properties?.toJsonString()
                        ?: "null")
            }
            val argumentTypes = intArrayOf(Types.INTEGER, Types.VARCHAR, Types.OTHER)
            if (arguments != null) {
                val originalSrid = hankeGeometriat.featureCollection!!.srid()
                jdbcOperations.batchUpdate("""
                    INSERT INTO HankeGeometria (
                        hankeGeometriatId,
                        geometria,
                        parametrit
                    ) VALUES (
                        ?,
                        ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(?), $originalSrid), $SRID),
                        ?
                    )""".trimIndent(), arguments, argumentTypes)
            }
        }

        private fun FeatureCollection.srid(): String {
            return this.crs?.properties?.get("name")?.toString()?.split("::")?.get(1) ?: SRID.toString()
        }

        private fun deleteHankeGeometriaRows(hankeGeometriat: HankeGeometriat, jdbcOperations: JdbcOperations) {
            jdbcOperations.execute("DELETE FROM HankeGeometria WHERE hankeGeometriatId = ${hankeGeometriat.id}")
        }
    }

    override fun createHankeGeometriat(hankeGeometriat: HankeGeometriat) {
        with(jdbcOperations) {
            val id = queryForObject("""
                INSERT INTO HankeGeometriat (
                    hankeId,
                    version,
                    createdByUserId,
                    createdAt,
                    modifiedByUserId,
                    modifiedAt
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?               
                )
                RETURNING id
                """.trimIndent(), { rs, _ -> rs.getInt(1) },
                    hankeGeometriat.hankeId,
                    hankeGeometriat.version ?: 0,
                    hankeGeometriat.createdByUserId,
                    if (hankeGeometriat.createdAt != null) Timestamp(hankeGeometriat.createdAt!!.toInstant().toEpochMilli()) else null,
                    hankeGeometriat.modifiedByUserId,
                    if (hankeGeometriat.modifiedAt != null) Timestamp(hankeGeometriat.modifiedAt!!.toInstant().toEpochMilli()) else null
            )
            hankeGeometriat.id = id
            saveHankeGeometriaRows(hankeGeometriat, this)
        }
    }

    override fun updateHankeGeometriat(hankeGeometriat: HankeGeometriat) {
        with(jdbcOperations) {
            update("""
                UPDATE HankeGeometriat
                SET
                    version = ?,
                    modifiedByUserId = ?,
                    modifiedAt = ?
                WHERE
                    id = ?
            """.trimIndent()) { ps ->
                ps.setInt(1, hankeGeometriat.version!!)
                if (hankeGeometriat.modifiedByUserId != null) {
                    ps.setInt(2, hankeGeometriat.modifiedByUserId!!)
                } else {
                    ps.setNull(2, Types.INTEGER)
                }
                if (hankeGeometriat.modifiedAt != null) {
                    ps.setTimestamp(3, Timestamp(hankeGeometriat.modifiedAt!!.toInstant().toEpochMilli()))
                } else {
                    ps.setNull(3, Types.TIMESTAMP)
                }
                ps.setInt(4, hankeGeometriat.id!!)
            }
            // delete old geometry rows
            deleteHankeGeometriaRows(hankeGeometriat, this)
            // save new geometry rows
            saveHankeGeometriaRows(hankeGeometriat, this)
        }
    }

    override fun retrieveHankeGeometriat(hankeId: Int): HankeGeometriat? {
        with(jdbcOperations) {
            val hankeGeometriat = query("""
            SELECT
                id,
                hankeId,
                version,
                createdByUserId,
                createdAt,
                modifiedByUserId,
                modifiedAt
            FROM HankeGeometriat WHERE hankeId = ?            
        """.trimIndent(), { rs, _ ->
                HankeGeometriat(
                        rs.getInt(1),
                        rs.getInt(2),
                        null,
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getTimestamp(5).toInstant().atZone(TZ_UTC),
                        rs.getInt(6),
                        rs.getTimestamp(7).toInstant().atZone(TZ_UTC)
                )
            }, hankeId).getOrNull(0)
            return hankeGeometriat?.withFeatureCollection(FeatureCollection().apply {
                features = retrieveHankeGeometriaRows(hankeGeometriat.id!!, this@with)
            })
        }
    }

    private fun retrieveHankeGeometriaRows(hankeGeometriatId: Int, jdbcOperations: JdbcOperations): List<Feature> {
        return jdbcOperations.query("""
                    SELECT
                        ST_AsGeoJSON(geometria),
                        parametrit
                    FROM
                        HankeGeometria
                    WHERE
                        hankeGeometriatId = ?
                """.trimIndent(), { rs, _ ->
            val geojson = rs.getString(1)
            val paramjson = rs.getString(2)
            Feature().apply {
                geometry = OBJECT_MAPPER.readValue(geojson)
                paramjson?.let { properties = OBJECT_MAPPER.readValue(paramjson) }
            }
        }, hankeGeometriatId)
    }
}
