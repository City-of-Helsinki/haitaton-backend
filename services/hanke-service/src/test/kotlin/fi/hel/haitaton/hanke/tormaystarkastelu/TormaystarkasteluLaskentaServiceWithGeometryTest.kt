package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import io.mockk.verifySequence
import org.geojson.Crs
import org.geojson.FeatureCollection
import org.geojson.GeometryCollection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TormaystarkasteluLaskentaServiceWithGeometryTest {

    private val tormaysService: TormaystarkasteluTormaysService = mockk()
    private val laskentaService = TormaystarkasteluLaskentaService(tormaysService)

    // The parameter is only used to call mocks
    val geometry =
        GeometryCollection().apply {
            crs = Crs()
            crs.properties["name"] = "EPSG:$SRID"
        }

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(tormaysService)
    }

    @Nested
    inner class Katuluokkaluokittelu {
        @Test
        fun `returns 0 when there are no street classes`() {
            every { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry) } returns null

            val result = laskentaService.katuluokkaluokittelu(geometry)

            assertThat(result).isEqualTo(0)
            verify { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry) }
        }

        @ParameterizedTest
        @EnumSource(TormaystarkasteluKatuluokka::class)
        fun `returns street class value when it exists`(streetClass: TormaystarkasteluKatuluokka) {
            every { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry) } returns
                streetClass.value

            val result = laskentaService.katuluokkaluokittelu(geometry)

            assertThat(result).isEqualTo(streetClass.value)
            verify { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry) }
        }
    }

    @Nested
    inner class Liikennemaaraluokittelu {
        @Test
        fun `returns 0 when street class is 0`() {
            val result = laskentaService.liikennemaaraluokittelu(geometry, 0)

            assertThat(result).isEqualTo(0)
        }

        @Test
        fun `uses small radius for traffic amounts when street class is 3`() {
            every { tormaysService.maxLiikennemaara(geometry, RADIUS_15) } returns 1500

            val result = laskentaService.liikennemaaraluokittelu(geometry, 3)

            assertThat(result).isEqualTo(3)
            verify { tormaysService.maxLiikennemaara(geometry, RADIUS_15) }
        }

        @Test
        fun `uses large radius for traffic amounts when street class is 4`() {
            every { tormaysService.maxLiikennemaara(geometry, RADIUS_30) } returns 1500

            val result = laskentaService.liikennemaaraluokittelu(geometry, 4)

            assertThat(result).isEqualTo(3)
            verify { tormaysService.maxLiikennemaara(geometry, RADIUS_30) }
        }

        @Test
        fun `returns 0 if there is no traffic`() {
            every { tormaysService.maxLiikennemaara(geometry, RADIUS_15) } returns null

            val result = laskentaService.liikennemaaraluokittelu(geometry, 1)

            assertThat(result).isEqualTo(0)
            verify { tormaysService.maxLiikennemaara(geometry, RADIUS_15) }
        }

        @ParameterizedTest
        @CsvSource(
            "-140,0",
            "0,0",
            "1,1",
            "499,1",
            "500,2",
            "1499,2",
            "1500,3",
            "4999,3",
            "5000,4",
            "9999,4",
            "10000,5",
            "18000,5",
        )
        fun `returns matching classification when there is traffic`(
            volume: Int,
            expectedResult: Int,
        ) {
            every { tormaysService.maxLiikennemaara(geometry, RADIUS_15) } returns volume

            val result = laskentaService.liikennemaaraluokittelu(geometry, 1)

            assertThat(result).isEqualTo(expectedResult)
            verify { tormaysService.maxLiikennemaara(geometry, RADIUS_15) }
        }
    }

    @Nested
    inner class CalculatePyoraliikenneindeksi {
        @ParameterizedTest
        @ValueSource(ints = [1, 2, 3, 5])
        fun `returns the matching float when the geometries intersect with cycle routes`(
            hierarkiaValue: Int
        ) {
            every { tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry) } returns
                hierarkiaValue

            val result = laskentaService.calculatePyoraliikenneindeksi(geometry)

            assertThat(result).isEqualTo(hierarkiaValue.toFloat())
            verifySequence { tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry) }
        }

        @Test
        fun `returns 0 when the geometries don't intersect with any cycle routes`() {
            every { tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry) } returns null

            val result = laskentaService.calculatePyoraliikenneindeksi(geometry)

            assertThat(result).isEqualTo(0f)
            verifySequence { tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry) }
        }
    }

    @Nested
    inner class Raitioliikenneluokittelu {
        @Test
        fun `returns 5 when intersects with a tram line`() {
            every { tormaysService.anyIntersectsWithTramLines(geometry) } returns true

            val result = laskentaService.calculateRaitioliikenneindeksi(geometry)

            assertThat(result).isEqualTo(5f)
            verify { tormaysService.anyIntersectsWithTramLines(geometry) }
            verify(exactly = 0) { tormaysService.anyIntersectsWithTramInfra(geometry) }
        }

        @Test
        fun `returns 3 when intersects with tram infra`() {
            every { tormaysService.anyIntersectsWithTramLines(geometry) } returns false
            every { tormaysService.anyIntersectsWithTramInfra(geometry) } returns true

            val result = laskentaService.calculateRaitioliikenneindeksi(geometry)

            assertThat(result).isEqualTo(3f)
            verifyAll {
                tormaysService.anyIntersectsWithTramLines(geometry)
                tormaysService.anyIntersectsWithTramInfra(geometry)
            }
        }

        @Test
        fun `returns 0 when doesn't intersect with any tram line or infra`() {
            every { tormaysService.anyIntersectsWithTramLines(geometry) } returns false
            every { tormaysService.anyIntersectsWithTramInfra(geometry) } returns false

            val result = laskentaService.calculateRaitioliikenneindeksi(geometry)

            assertThat(result).isEqualTo(0f)
            verifyAll {
                tormaysService.anyIntersectsWithTramLines(geometry)
                tormaysService.anyIntersectsWithTramInfra(geometry)
            }
        }
    }

    @Nested
    inner class Linjaautoliikenneluokittelu {
        @Test
        fun `returns 5 when intersects with critical bus routes`() {
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometry) } returns true

            val result = laskentaService.calculateLinjaautoliikenneindeksi(geometry)

            assertThat(result).isEqualTo(5)
            verify { tormaysService.anyIntersectsCriticalBusRoutes(geometry) }
        }

        @Test
        fun `returns 0 when doesn't intersect with any bus lines`() {
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometry) } returns false
            every { tormaysService.getIntersectingBusRoutes(geometry) } returns setOf()

            val result = laskentaService.calculateLinjaautoliikenneindeksi(geometry)

            assertThat(result).isEqualTo(0)
            verifyAll {
                tormaysService.anyIntersectsCriticalBusRoutes(geometry)
                tormaysService.getIntersectingBusRoutes(geometry)
            }
        }

        @ParameterizedTest
        @CsvSource("0,2", "1,3", "10,3", "11,4", "20,4", "21,5", "100,5")
        fun `returns classification based on rush hour buses when there's no trunk line`(
            rushHourBuses: Int,
            expectedResult: Int,
        ) {
            val busLines =
                setOf(
                    TormaystarkasteluBussireitti(
                        "",
                        0,
                        rushHourBuses,
                        TormaystarkasteluBussiRunkolinja.EI,
                    )
                )
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometry) } returns false
            every { tormaysService.getIntersectingBusRoutes(geometry) } returns busLines

            val result = laskentaService.calculateLinjaautoliikenneindeksi(geometry)

            assertThat(result).isEqualTo(expectedResult)
            verifyAll {
                tormaysService.anyIntersectsCriticalBusRoutes(geometry)
                tormaysService.getIntersectingBusRoutes(geometry)
            }
        }

        @ParameterizedTest
        @CsvSource("ON,4", "EI,2")
        fun `returns classification based on trunk lines when there are zero rush hour buses`(
            runkolinja: TormaystarkasteluBussiRunkolinja,
            expectedResult: Int,
        ) {
            val busLines = setOf(TormaystarkasteluBussireitti("", 0, 0, runkolinja))
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometry) } returns false
            every { tormaysService.getIntersectingBusRoutes(geometry) } returns busLines

            val result = laskentaService.calculateLinjaautoliikenneindeksi(geometry)

            assertThat(result).isEqualTo(expectedResult)
            verifyAll {
                tormaysService.anyIntersectsCriticalBusRoutes(geometry)
                tormaysService.getIntersectingBusRoutes(geometry)
            }
        }
    }

    @Test
    fun `calculateTormaystarkastelu happy case`() {
        setupHappyCase()
        val featureCollection = FeatureCollection()

        val tulos =
            laskentaService.calculateTormaystarkastelu(
                featureCollection,
                5,
                VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
                AutoliikenteenKaistavaikutustenPituus.PITUUS_100_499_METRIA,
            )

        assertThat(tulos.liikennehaittaindeksi.indeksi).isEqualTo(5.0f)
        assertThat(tulos.liikennehaittaindeksi.tyyppi)
            .isEqualTo(IndeksiType.LINJAAUTOLIIKENNEINDEKSI)
        assertThat(tulos.autoliikenne.haitanKesto).isEqualTo(1)
        assertThat(tulos.autoliikenne.katuluokka).isEqualTo(4)
        assertThat(tulos.autoliikenne.liikennemaara).isEqualTo(2)
        assertThat(tulos.autoliikenne.kaistahaitta).isEqualTo(1)
        assertThat(tulos.autoliikenne.kaistapituushaitta).isEqualTo(4)
        assertThat(tulos.autoliikenne.indeksi).isEqualTo(2.5f)
        assertThat(tulos.linjaautoliikenneindeksi).isEqualTo(5.0f)
        assertThat(tulos.raitioliikenneindeksi).isEqualTo(3.0f)
        assertThat(tulos.pyoraliikenneindeksi).isEqualTo(3.0f)

        verifySequence {
            tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry)
            tormaysService.maxLiikennemaara(geometry, RADIUS_30)
            tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry)
            tormaysService.anyIntersectsCriticalBusRoutes(geometry)
            tormaysService.anyIntersectsWithTramLines(geometry)
            tormaysService.anyIntersectsWithTramInfra(geometry)
        }
    }

    private fun setupHappyCase() {
        every { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometry) } returns
            TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every { tormaysService.maxLiikennemaara(geometry, RADIUS_30) } returns 1000
        every { tormaysService.maxIntersectingPyoraliikenneHierarkia(geometry) } returns
            PyoraliikenteenHierarkia.MUU_PYORAREITTI.value
        every { tormaysService.anyIntersectsWithTramInfra(geometry) } returns true
        every { tormaysService.anyIntersectsWithTramLines(geometry) } returns false
        every { tormaysService.anyIntersectsCriticalBusRoutes(geometry) } returns true
    }
}
