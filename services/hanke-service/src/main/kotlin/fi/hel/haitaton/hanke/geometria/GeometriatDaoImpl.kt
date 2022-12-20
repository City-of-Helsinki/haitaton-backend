package fi.hel.haitaton.hanke.geometria

import com.fasterxml.jackson.module.kotlin.readValue
import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.toJsonString
import java.sql.Timestamp
import java.sql.Types
import org.geojson.Crs
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.springframework.jdbc.core.JdbcOperations

class GeometriatDaoImpl(private val jdbcOperations: JdbcOperations) : GeometriatDao {

    companion object {

        private fun saveHankeGeometriaRows(geometriat: Geometriat, jdbcOperations: JdbcOperations) {
            val arguments: List<Array<Any>>? =
                geometriat.featureCollection?.features?.map { feature ->
                    arrayOf(
                        geometriat.id!!,
                        feature.geometry.toJsonString(),
                        feature.properties?.toJsonString() ?: "null"
                    )
                }
            val argumentTypes = intArrayOf(Types.INTEGER, Types.VARCHAR, Types.OTHER)
            if (arguments != null) {
                val originalSrid = geometriat.featureCollection!!.srid()
                jdbcOperations.batchUpdate(
                    """
                    INSERT INTO HankeGeometria (
                        hankeGeometriatId,
                        geometria,
                        parametrit
                    ) VALUES (
                        ?,
                        ${
                        if (originalSrid == SRID) {
                            "ST_SetSRID(ST_GeomFromGeoJSON(?), $SRID)"
                        } else {
                            "ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(?), $originalSrid), $SRID)"
                        }
                    },
                        ?
                    )
                    """.trimIndent(),
                    arguments,
                    argumentTypes
                )
            }
        }

        private fun FeatureCollection.srid(): Int {
            return this.crs?.properties?.get("name")?.toString()?.split("::")?.get(1)?.toInt()
                ?: SRID
        }

        private fun updateGeometriat(geometriat: Geometriat, jdbcOperations: JdbcOperations) {
            jdbcOperations.update(
                """
                UPDATE Geometriat
                SET
                    version = ?,
                    modifiedByUserId = ?,
                    modifiedAt = ?
                WHERE
                    id = ?
            """.trimIndent()
            ) { ps ->
                ps.setInt(1, geometriat.version!!)
                if (geometriat.modifiedByUserId != null) {
                    ps.setString(2, geometriat.modifiedByUserId!!)
                } else {
                    ps.setNull(2, Types.INTEGER)
                }
                if (geometriat.modifiedAt != null) {
                    ps.setTimestamp(
                        3,
                        Timestamp(geometriat.modifiedAt!!.toInstant().toEpochMilli())
                    )
                } else {
                    ps.setNull(3, Types.TIMESTAMP)
                }
                ps.setInt(4, geometriat.id!!)
            }
        }

        private fun deleteHankeGeometriaRows(
            geometriat: Geometriat,
            jdbcOperations: JdbcOperations
        ) {
            jdbcOperations.execute(
                "DELETE FROM HankeGeometria WHERE hankeGeometriatId = ${geometriat.id}"
            )
        }
    }

    override fun createGeometriat(geometriat: Geometriat): Geometriat {
        with(jdbcOperations) {
            val id =
                queryForObject(
                    """
                    INSERT INTO Geometriat (
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
                        ?
                    )
                    RETURNING id
                    """.trimIndent(),
                    { rs, _ -> rs.getInt(1) },
                    geometriat.version ?: 0,
                    geometriat.createdByUserId,
                    if (geometriat.createdAt != null) {
                        Timestamp(geometriat.createdAt!!.toInstant().toEpochMilli())
                    } else {
                        null
                    },
                    geometriat.modifiedByUserId,
                    if (geometriat.modifiedAt != null) {
                        Timestamp(geometriat.modifiedAt!!.toInstant().toEpochMilli())
                    } else {
                        null
                    }
                )
            geometriat.id = id
            saveHankeGeometriaRows(geometriat, this)
            return geometriat
        }
    }

    override fun retrieveGeometriat(id: Int): Geometriat? {
        with(jdbcOperations) {
            val geometriat =
                query(
                        """
                        SELECT
                            id,
                            version,
                            createdByUserId,
                            createdAt,
                            modifiedByUserId,
                            modifiedAt
                        FROM Geometriat WHERE id = ?""".trimIndent(),
                        { rs, _ ->
                            Geometriat(
                                id = rs.getInt(1),
                                featureCollection = null,
                                version = rs.getInt(2),
                                createdByUserId = rs.getString(3),
                                createdAt = rs.getTimestamp(4).toInstant().atZone(TZ_UTC),
                                modifiedByUserId = rs.getString(5),
                                modifiedAt = rs.getTimestamp(6)?.toInstant()?.atZone(TZ_UTC)
                            )
                        },
                        id
                    )
                    .getOrNull(0)
            return geometriat?.withFeatureCollection(
                FeatureCollection().apply {
                    features = retrieveHankeGeometriaRows(geometriat.id!!, this@with)
                    crs = Crs().apply { properties = mapOf(Pair("name", COORDINATE_SYSTEM_URN)) }
                }
            )
        }
    }

    private fun retrieveHankeGeometriaRows(
        geometriatId: Int,
        jdbcOperations: JdbcOperations
    ): List<Feature> {
        return jdbcOperations.query(
            """
            SELECT
                ST_AsGeoJSON(geometria),
                parametrit
            FROM
                HankeGeometria
            WHERE
                hankeGeometriatId = ?
            """.trimIndent(),
            { rs, _ ->
                val geojson = rs.getString(1)
                val paramjson = rs.getString(2)
                Feature().apply {
                    geometry = OBJECT_MAPPER.readValue(geojson)
                    paramjson?.let { properties = OBJECT_MAPPER.readValue(paramjson) }
                }
            },
            geometriatId
        )
    }

    override fun updateGeometriat(geometriat: Geometriat) {
        with(jdbcOperations) {
            // update master row
            updateGeometriat(geometriat, this)
            // delete old geometry rows
            deleteHankeGeometriaRows(geometriat, this)
            // save new geometry rows
            saveHankeGeometriaRows(geometriat, this)
        }
    }

    override fun deleteGeometriat(geometriat: Geometriat) {
        with(jdbcOperations) {
            // update master row
            updateGeometriat(geometriat, this)
            // delete old geometry rows
            deleteHankeGeometriaRows(geometriat, this)
        }
    }
}
