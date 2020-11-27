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

class HankeGeometriatDaoImpl(private val jdbcOperations: JdbcOperations) : HankeGeometriatDao {

    companion object {
        private fun saveHankeGeometriaRows(hankeGeometriat: HankeGeometriat, jdbcOperations: JdbcOperations) {
            hankeGeometriat.featureCollection?.features?.forEach { feature ->
                jdbcOperations.execute("""
                    INSERT INTO HankeGeometria (
                        hankeGeometriatId,
                        geometria,
                        parametrit
                    ) VALUES (
                        ${hankeGeometriat.id},
                        ST_SetSRID(ST_GeomFromGeoJSON('${feature.geometry.toJsonString()}'), $SRID),
                        ${if (feature.properties != null) "'${feature.properties.toJsonString()}'" else "null"}
            )""".trimIndent())
            }
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
                    updatedByUserId,
                    updateddAt
                ) VALUES (
                    ${hankeGeometriat.hankeId},
                    ${hankeGeometriat.version ?: 0},
                    ${if (hankeGeometriat.createdByUserId != null) "'${hankeGeometriat.createdByUserId}'" else null},
                    ${if (hankeGeometriat.createdAt != null) "'${Timestamp(hankeGeometriat.createdAt!!.toInstant().toEpochMilli())}'" else null},
                    ${if (hankeGeometriat.updatedByUserId != null) "'${hankeGeometriat.updatedByUserId}'" else null},
                    ${if (hankeGeometriat.updatedAt != null) "'${Timestamp(hankeGeometriat.updatedAt!!.toInstant().toEpochMilli())}'" else null}               
                )
                RETURNING id
            """.trimIndent()) { rs, _ ->
                rs.getInt(1)
            }
            hankeGeometriat.id = id
            saveHankeGeometriaRows(hankeGeometriat, this)
        }
    }

    override fun updateHankeGeometriat(hankeGeometriat: HankeGeometriat) {
        with(jdbcOperations) {
            execute("""
                UPDATE HankeGeometriat
                SET
                    version = ${hankeGeometriat.version},
                    updatedByUserId = ${if (hankeGeometriat.updatedByUserId != null) "'${hankeGeometriat.updatedByUserId}'" else null},
                    updateddAt = ${if (hankeGeometriat.updatedAt != null) "'${Timestamp(hankeGeometriat.updatedAt!!.toInstant().toEpochMilli())}'" else null}
                WHERE
                    id = ${hankeGeometriat.id}
            """.trimIndent())
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
                updatedByUserId,
                updateddAt
            FROM HankeGeometriat WHERE hankeId = $hankeId            
        """.trimIndent()) { rs, _ ->
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
            }.getOrNull(0)
            return if (hankeGeometriat != null) {
                hankeGeometriat.featureCollection = FeatureCollection()
                hankeGeometriat.featureCollection!!.features = mutableListOf()
                query("""
                    SELECT
                        ST_AsGeoJSON(geometria),
                        parametrit
                    FROM
                        HankeGeometria
                    WHERE
                        hankeGeometriatId = ${hankeGeometriat.id}
                """.trimIndent()) { rs, _ ->
                    val geojson = rs.getString(1)
                    val paramjson = rs.getString(2)
                    hankeGeometriat.featureCollection!!.features.add(Feature().apply {
                        geometry = OBJECT_MAPPER.readValue(geojson)
                        paramjson?.let { properties = OBJECT_MAPPER.readValue(paramjson) }
                    })
                }
                hankeGeometriat
            } else {
                null
            }
        }
    }
}
