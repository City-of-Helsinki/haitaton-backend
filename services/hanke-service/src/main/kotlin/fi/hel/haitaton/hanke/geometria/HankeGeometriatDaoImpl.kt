package fi.hel.haitaton.hanke.geometria

import com.fasterxml.jackson.module.kotlin.readValue
import fi.hel.haitaton.hanke.*
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.springframework.jdbc.core.JdbcOperations
import java.sql.Timestamp

class HankeGeometriatDaoImpl(private val jdbcOperations: JdbcOperations) : HankeGeometriatDao {

    override fun deleteHankeGeometriat(hankeId: Int) {
        jdbcOperations.execute("DELETE FROM HankeGeometriat WHERE hankeId = $hankeId") // CASCADE - will delete also HankeGeometria rows
    }

    override fun saveHankeGeometriat(hankeGeometriat: HankeGeometriat) {
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
                rs.getString(1)
            }
            hankeGeometriat.featureCollection?.features?.forEach { feature ->
                execute("""
                    INSERT INTO HankeGeometria (
                        hankeGeometriatId,
                        geometria,
                        parametrit
                    ) VALUES (
                        $id,
                        ST_SetSRID(ST_GeomFromGeoJSON('${feature.geometry.toJsonString()}'), $SRID),
                        '${feature.properties.toJsonString()}'
            )""".trimIndent())
            }
        }
    }

    override fun loadHankeGeometriat(hankeId: Int): HankeGeometriat? {
        with(jdbcOperations) {
            var id: String? = null
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
                id = rs.getString(1)
                HankeGeometriat(
                        rs.getInt(2),
                        null,
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getTimestamp(5).toInstant().atZone(TZ_UTC),
                        rs.getInt(6),
                        rs.getTimestamp(7).toInstant().atZone(TZ_UTC)
                )
            }.getOrNull(0)
            return if (hankeGeometriat != null && id != null) {
                hankeGeometriat.featureCollection = FeatureCollection()
                hankeGeometriat.featureCollection!!.features = mutableListOf()
                query("""
                    SELECT
                        ST_AsGeoJSON(geometria),
                        parametrit
                    FROM
                        HankeGeometria
                    WHERE
                        hankeGeometriatId = $id
                """.trimIndent()) { rs, _ ->
                    val geojson = rs.getString(1)
                    val paramjson = rs.getString(2)
                    hankeGeometriat.featureCollection!!.features.add(Feature().apply {
                        geometry = OBJECT_MAPPER.readValue(geojson)
                        properties = OBJECT_MAPPER.readValue(paramjson)
                    })
                }
                hankeGeometriat
            } else {
                null
            }
        }
    }
}
