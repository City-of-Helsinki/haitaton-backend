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
import org.geojson.GeoJsonObject
import org.geojson.MultiPolygon
import org.geojson.Polygon
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component

@Component
class GeometriatDao(private val jdbcOperations: JdbcOperations) {

    fun createGeometriat(geometriat: Geometriat): Geometriat {
        val query =
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
               RETURNING id"""
                .trimIndent()
        val id =
            jdbcOperations.queryForObject(
                query,
                { rs, _ -> rs.getInt(1) },
                geometriat.version,
                geometriat.createdByUserId,
                geometriat.createdAt?.let { Timestamp(it.toInstant().toEpochMilli()) },
                geometriat.modifiedByUserId,
                geometriat.modifiedAt?.let { Timestamp(it.toInstant().toEpochMilli()) },
            )
        geometriat.id = id
        saveHankeGeometriaRows(geometriat)
        return geometriat
    }

    fun retrieveGeometriat(id: Int): Geometriat? {
        val query =
            """
               SELECT
                   id,
                   version,
                   createdByUserId,
                   createdAt,
                   modifiedByUserId,
                   modifiedAt
               FROM Geometriat WHERE id = ?"""
                .trimIndent()
        val rowMapper = RowMapper { rs, _ ->
            Geometriat(
                id = rs.getInt(1),
                featureCollection = null,
                version = rs.getInt(2),
                createdByUserId = rs.getString(3),
                createdAt = rs.getTimestamp(4).toInstant().atZone(TZ_UTC),
                modifiedByUserId = rs.getString(5),
                modifiedAt = rs.getTimestamp(6)?.toInstant()?.atZone(TZ_UTC),
            )
        }
        val geometriat = jdbcOperations.query(query, rowMapper, id).getOrNull(0)
        return geometriat?.withFeatureCollection(
            FeatureCollection().apply {
                features = retrieveHankeGeometriaRows(geometriat.id!!)
                crs = Crs().apply { properties = mapOf(Pair("name", COORDINATE_SYSTEM_URN)) }
            }
        )
    }

    fun validateGeometria(geometria: GeoJsonObject): InvalidDetail? {
        val detailQuery =
            "select valid, reason, ST_AsGeoJSON(location) as location from ST_IsValidDetail(ST_GeomFromGeoJSON(?))"
        val rowMapper = RowMapper { rs, _ ->
            if (!rs.getBoolean("valid")) {
                InvalidDetail(rs.getString("reason"), rs.getString("location"))
            } else {
                null
            }
        }
        return jdbcOperations.query(detailQuery, rowMapper, geometria.toJsonString())[0]
    }

    /**
     * Uses PostGIS to check if the geometries are valid.
     *
     * For e.g. polygons it checks - among other things - that the first and last coordinate are
     * identical and that the line doesn't intersect itself.
     *
     * Note that some empty geometries are valid, like empty GeometryCollections and
     * FeatureCollections.
     *
     * @return List of indexes in the feature collection where the validation failed.
     */
    fun validateGeometriat(geometriat: List<GeoJsonObject>): InvalidDetail? =
        geometriat.firstNotNullOfOrNull { validateGeometria(it) }

    /**
     * Returns all the hankealueet that cover the given geometry. I.e. every hankealue where the
     * given geometry is inside the hankealue.
     */
    fun matchingHankealueet(hankeId: Int, geometria: GeoJsonObject): List<Int> {
        val query =
            """
            SELECT ha.id
            FROM hankealue ha
            INNER JOIN hankegeometria hg ON hg.hankegeometriatid = ha.geometriat
            WHERE ha.hankeid = ?
            AND ST_Covers(hg.geometria, ST_GeomFromGeoJSON(?))
            """
                .trimIndent()

        return jdbcOperations.queryForList(
            query,
            Int::class.java,
            hankeId,
            geometria.toJsonString(),
        )
    }

    /** Check if the given geometry is inside the given hankealue. */
    fun isInsideHankeAlue(hankealueId: Int, geometria: GeoJsonObject): Boolean {
        val query =
            """
            SELECT ST_Covers(hg.geometria, ST_GeomFromGeoJSON(?))
            FROM hankealue ha
            INNER JOIN hankegeometria hg ON hg.hankegeometriatid = ha.geometriat
            WHERE ha.id = ?
            """
                .trimIndent()

        return jdbcOperations
            .queryForList(query, Boolean::class.java, geometria.toJsonString(), hankealueId)
            .any { it }
    }

    fun calculateArea(geometria: GeoJsonObject): Float? {
        val areaQuery = "select ST_Area(ST_SetSRID(ST_GeomFromGeoJSON(?), $SRID))"

        return jdbcOperations.queryForObject(areaQuery, Float::class.java, geometria.toJsonString())
    }

    fun calculateCombinedArea(geometriat: List<Polygon>): Float? {
        val geometryCollection = MultiPolygon()
        geometriat.forEach { geometryCollection.add(it) }

        val areaQuery = "select ST_Area(ST_UnaryUnion(ST_SetSRID(ST_GeomFromGeoJSON(?), $SRID)))"
        return jdbcOperations.queryForObject(
            areaQuery,
            Float::class.java,
            geometryCollection.toJsonString(),
        )
    }

    private fun retrieveHankeGeometriaRows(geometriatId: Int): List<Feature> {
        val query =
            """
            SELECT
                ST_AsGeoJSON(geometria),
                parametrit
            FROM
                HankeGeometria
            WHERE
                hankeGeometriatId = ?
            """
                .trimIndent()
        val rowMapper = RowMapper { rs, _ ->
            val geojson = rs.getString(1)
            val paramjson = rs.getString(2)
            Feature().apply {
                geometry = OBJECT_MAPPER.readValue(geojson)
                paramjson?.let { properties = OBJECT_MAPPER.readValue(paramjson) }
            }
        }
        return jdbcOperations.query(query, rowMapper, geometriatId)
    }

    /** Updates geometry rows by FIRST DELETING ALL OF THEM AND THEN CREATING NEW ROWS */
    fun updateGeometriat(geometriat: Geometriat) {
        // update master row
        updateGeometriatRows(geometriat)
        // delete old geometry rows
        deleteHankeGeometriaRows(geometriat)
        // save new geometry rows
        saveHankeGeometriaRows(geometriat)
    }

    /** Deletes geometry rows BUT DOES NOT DELETE THE MASTER ROW (Geometriat row) */
    fun deleteGeometriat(geometriat: Geometriat) {
        jdbcOperations.update("DELETE FROM Geometriat WHERE id = ?") { ps ->
            ps.setInt(1, geometriat.id!!)
        }
    }

    private fun saveHankeGeometriaRows(geometriat: Geometriat) {
        val arguments: List<Array<Any>>? =
            geometriat.featureCollection?.features?.map { feature ->
                arrayOf(
                    geometriat.id!!,
                    feature.geometry.toJsonString(),
                    feature.properties?.toJsonString() ?: "null",
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
                    """
                    .trimIndent(),
                arguments,
                argumentTypes,
            )
        }
    }

    private fun updateGeometriatRows(geometriat: Geometriat) {
        jdbcOperations.update(
            """
                UPDATE Geometriat
                SET
                    version = ?,
                    modifiedByUserId = ?,
                    modifiedAt = ?
                WHERE
                    id = ?
            """
                .trimIndent()
        ) { ps ->
            ps.setInt(1, geometriat.version)
            if (geometriat.modifiedByUserId != null) {
                ps.setString(2, geometriat.modifiedByUserId!!)
            } else {
                ps.setNull(2, Types.INTEGER)
            }
            if (geometriat.modifiedAt != null) {
                ps.setTimestamp(3, Timestamp(geometriat.modifiedAt!!.toInstant().toEpochMilli()))
            } else {
                ps.setNull(3, Types.TIMESTAMP)
            }
            ps.setInt(4, geometriat.id!!)
        }
    }

    private fun deleteHankeGeometriaRows(geometriat: Geometriat) {
        jdbcOperations.execute(
            "DELETE FROM HankeGeometria WHERE hankeGeometriatId = ${geometriat.id}"
        )
    }

    data class InvalidDetail(val reason: String, val location: String)

    companion object {
        private fun FeatureCollection.srid(): Int {
            return this.crs?.properties?.get("name")?.toString()?.split("::")?.get(1)?.toInt()
                ?: SRID
        }
    }
}
