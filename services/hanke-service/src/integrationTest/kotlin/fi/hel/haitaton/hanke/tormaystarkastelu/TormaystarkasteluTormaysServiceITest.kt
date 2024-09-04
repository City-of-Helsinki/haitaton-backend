package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import org.geojson.Crs
import org.geojson.GeometryCollection
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

internal class TormaystarkasteluTormaysServiceITest : IntegrationTest() {

    @Autowired private lateinit var geometriatDao: GeometriatDao

    @Autowired private lateinit var tormaysService: TormaystarkasteluTormaysService

    /** Test manually what street classes (street_classes) Hanke geometries are located on */
    @ParameterizedTest
    @CsvSource("Kaivokatu,4", "Mustikkamaa,")
    fun `street classes`(location: String, result: Int?) {
        val geometriat = createHankeGeometriat(location)
        assertThat(tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat))
            .isEqualTo(result)
    }

    /**
     * Test manually whether Hanke geometries are locaed on central businessa area ("kantakaupunki",
     * central_business_area)
     */
    @ParameterizedTest
    @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
    fun `central business area`(location: String, result: Boolean) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.anyIntersectsWithKantakaupunki(geometriaIds)).isEqualTo(result)
    }

    /**
     * Test manually what kinds of traffic counts there are on Hanke geometries with radius of 15m
     */
    @ParameterizedTest
    @CsvSource("Kaivokatu,17566", "Mustikkamaa,")
    fun `traffic counts with radius of 15m`(location: String, result: Int?) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(
                tormaysService.maxLiikennemaara(
                    geometriaIds, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15))
            .isEqualTo(result)
    }

    /**
     * Test manually what kinds of traffic counts there are on Hanke geometries with radius of 30m
     */
    @ParameterizedTest
    @CsvSource("Kaivokatu,17566", "Mustikkamaa,")
    fun `traffic counts with radius of 30m`(location: String, result: Int?) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(
                tormaysService.maxLiikennemaara(
                    geometriaIds, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30))
            .isEqualTo(result)
    }

    /** Test manually what Hanke geometries are located on critical area for buses */
    @ParameterizedTest
    @CsvSource("Kaivokatu,false", "Mustikkamaa,false")
    fun `critical bus routes`(location: String, result: Boolean) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.anyIntersectsCriticalBusRoutes(geometriaIds)).isEqualTo(result)
    }

    /** Test manually what bus routes each Hanke geometry is located on */
    @ParameterizedTest
    @CsvSource("Kaivokatu,3,1023N;1023N;1016", "Mustikkamaa,0,")
    fun `bus routes`(location: String, resultCount: Int, resultRoutes: String?) {
        val geometriaIds = createHankeGeometriat(location)

        val bussit = tormaysService.getIntersectingBusRoutes(geometriaIds)

        assertThat(bussit.size).isEqualTo(resultCount)
        if (resultRoutes != null) {
            assertThat(bussit.map { it.reittiId })
                .containsExactlyInAnyOrder(*resultRoutes.split(';').toTypedArray())
        } else {
            assertThat(bussit).isEmpty()
        }
    }

    /** Test manually what tram infra each Hanke geometry is located on */
    @ParameterizedTest
    @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
    fun `tram infra`(location: String, result: Boolean) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.anyIntersectsWithTramInfra(geometriaIds)).isEqualTo(result)
    }

    /** Test manually what tram lines each Hanke geometry is located on */
    @ParameterizedTest
    @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
    fun `tram lines`(location: String, result: Boolean) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.anyIntersectsWithTramLines(geometriaIds)).isEqualTo(result)
    }

    @ParameterizedTest
    @CsvSource("Kaivokatu,3", "Mustikkamaa,")
    fun `cycleways hierarkia`(location: String, result: Int?) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.maxIntersectingPyoraliikenneHierarkia(geometriaIds))
            .isEqualTo(result)
    }

    private fun createHankeGeometriat(location: String): Set<Int> {
        val geometriat =
            "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat-$location.json"
                .asJsonResource(Geometriat::class.java)
        return setOf(geometriatDao.createGeometriat(geometriat).id!!)
    }

    @Nested
    inner class WithGeometry {

        @ParameterizedTest
        @CsvSource("Kaivokatu,4", "Mustikkamaa,")
        fun `street classes`(location: String, result: Int?) {
            val geometry = createGeometryCollection(location)

            assertThat(tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry))
                .isEqualTo(result)
        }

        @ParameterizedTest
        @CsvSource("Kaivokatu,17566", "Mustikkamaa,")
        fun `traffic counts with radius of 15m`(location: String, result: Int?) {
            val geometry = createGeometryCollection(location)

            assertThat(
                    tormaysService.maxLiikennemaara(
                        geometry, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15))
                .isEqualTo(result)
        }

        @ParameterizedTest
        @CsvSource("Kaivokatu,17566", "Mustikkamaa,")
        fun `traffic counts with radius of 30m`(location: String, result: Int?) {
            val geometry = createGeometryCollection(location)

            assertThat(
                    tormaysService.maxLiikennemaara(
                        geometry, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30))
                .isEqualTo(result)
        }

        @ParameterizedTest
        @CsvSource("Kaivokatu,false", "Mustikkamaa,false")
        fun `critical bus routes`(location: String, result: Boolean) {
            val geometry = createGeometryCollection(location)

            assertThat(tormaysService.anyIntersectsCriticalBusRoutes(geometry)).isEqualTo(result)
        }

        @ParameterizedTest
        @CsvSource("Kaivokatu,3,1023N;1023N;1016", "Mustikkamaa,0,")
        fun `bus routes`(location: String, resultCount: Int, resultRoutes: String?) {
            val geometry = createGeometryCollection(location)

            val bussit = tormaysService.getIntersectingBusRoutes(geometry)

            assertThat(bussit.size).isEqualTo(resultCount)
            if (resultRoutes != null) {
                assertThat(bussit.map { it.reittiId })
                    .containsExactlyInAnyOrder(*resultRoutes.split(';').toTypedArray())
            } else {
                assertThat(bussit).isEmpty()
            }
        }

        @ParameterizedTest
        @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
        fun `tram infra`(location: String, result: Boolean) {
            val geometry = createGeometryCollection(location)

            assertThat(tormaysService.anyIntersectsWithTramInfra(geometry)).isEqualTo(result)
        }

        @ParameterizedTest
        @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
        fun `tram lines`(location: String, result: Boolean) {
            val geometry = createGeometryCollection(location)

            assertThat(tormaysService.anyIntersectsWithTramLines(geometry)).isEqualTo(result)
        }

        @ParameterizedTest
        @CsvSource("Kaivokatu,3", "Mustikkamaa,")
        fun `cycleways hierarkia`(location: String, result: Int?) {
            val geometry = createGeometryCollection(location)

            assertThat(tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry))
                .isEqualTo(result)
        }

        private fun createGeometryCollection(location: String): GeometryCollection {
            val geometriat =
                "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat-$location.json"
                    .asJsonResource(Geometriat::class.java)
            val geoColl = GeometryCollection()
            geoColl.crs = Crs()
            geoColl.crs.properties["name"] = "EPSG:$SRID"
            geoColl.geometries = geometriat.featureCollection!!.features.map { it.geometry }
            return geoColl
        }
    }
}
