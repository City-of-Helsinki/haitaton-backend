package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

internal class TormaystarkasteluTormaysServiceITest : IntegrationTest() {

    @Autowired private lateinit var geometriatDao: GeometriatDao

    @Autowired private lateinit var tormaysService: TormaystarkasteluTormaysService

    /**
     * Test manually whether Hanke geometries are located on general street area ("yleinen katuosa",
     * ylre_parts)
     */
    @ParameterizedTest
    @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
    fun `general street area`(location: String, result: Boolean) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.anyIntersectsYleinenKatuosa(geometriaIds)).isEqualTo(result)
    }

    /** Test manually what general street classes (ylre_classes) Hanke geometries are located on */
    @ParameterizedTest
    @CsvSource("Kaivokatu,4", "Mustikkamaa,")
    fun `general street classes`(location: String, result: Int?) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriaIds))
            .isEqualTo(result)
    }

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
                    geometriaIds,
                    TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
                )
            )
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
                    geometriaIds,
                    TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
                )
            )
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

    /** Test manually what priority cycleways Hanke geometries are located on */
    @ParameterizedTest
    @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
    fun `priority cycleways`(location: String, result: Boolean) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.anyIntersectsWithCyclewaysPriority(geometriaIds))
            .isEqualTo(result)
    }

    /** Test manually what main cycleways Hanke geometries are located on */
    @ParameterizedTest
    @CsvSource("Kaivokatu,true", "Mustikkamaa,false")
    fun `main cycleways`(location: String, result: Boolean) {
        val geometriaIds = createHankeGeometriat(location)
        assertThat(tormaysService.anyIntersectsWithCyclewaysMain(geometriaIds)).isEqualTo(result)
    }

    private fun createHankeGeometriat(location: String): Set<Int> {
        val geometriat =
            "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat-$location.json"
                .asJsonResource(Geometriat::class.java)
        return setOf(geometriatDao.createGeometriat(geometriat).id!!)
    }
}
