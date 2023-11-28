package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.geometriaIds
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TormaystarkasteluLaskentaServiceTest {

    private val tormaysService: TormaystarkasteluTormaysService = mockk()
    private val laskentaService = TormaystarkasteluLaskentaService(tormaysService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(tormaysService)
    }

    @ParameterizedTest(name = "Perusindeksi with default weights should be {0}")
    @CsvFileSource(resources = ["perusindeksi-test.csv"], numLinesToSkip = 1)
    fun perusIndeksiCalculatorTest(
        indeksi: Float,
        haittaAjanKesto: Int,
        todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin: Int,
        kaistajarjestelynPituus: Int,
        katuluokka: Int,
        liikennemaara: Int
    ) {
        val luokittelu =
            mapOf(
                LuokitteluType.HAITTA_AJAN_KESTO to haittaAjanKesto,
                LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN to
                    todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin,
                LuokitteluType.KAISTAJARJESTELYN_PITUUS to kaistajarjestelynPituus,
                LuokitteluType.KATULUOKKA to katuluokka,
                LuokitteluType.LIIKENNEMAARA to liikennemaara
            )
        val perusindeksi = laskentaService.calculatePerusIndeksiFromLuokittelu(luokittelu)
        assertThat(perusindeksi).isEqualTo(indeksi)
    }

    @Nested
    inner class KatuluokkaLuokittelu {
        // The parameter is only used to call mocks
        val geometriat = setOf<Int>()

        @Nested
        inner class WithYlreParts {
            @BeforeEach
            fun mockYlreParts() {
                every { tormaysService.anyIntersectsYleinenKatuosa(geometriat) } returns true
            }

            @AfterEach
            fun verifyYlreParts() {
                verify { tormaysService.anyIntersectsYleinenKatuosa(geometriat) }
            }

            @Nested
            inner class WithoutStreetClasses {
                @BeforeEach
                fun mockStreetClasses() {
                    every {
                        tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
                    } returns null
                }

                @AfterEach
                fun verifyYlreParts() {
                    verify { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat) }
                }

                @Test
                fun `returns 0 when there are no ylre classes`() {
                    every {
                        tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                    } returns null

                    val result = laskentaService.katuluokkaLuokittelu(geometriat)

                    assertThat(result).isEqualTo(0)
                    verify { tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat) }
                }

                @ParameterizedTest
                @EnumSource(TormaystarkasteluKatuluokka::class)
                fun `returns ylre class value when it exists`(
                    ylreClass: TormaystarkasteluKatuluokka
                ) {
                    every {
                        tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                    } returns ylreClass.value

                    val result = laskentaService.katuluokkaLuokittelu(geometriat)

                    assertThat(result).isEqualTo(ylreClass.value)
                    verify { tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat) }
                }
            }

            @ParameterizedTest
            @EnumSource(TormaystarkasteluKatuluokka::class)
            fun `returns street class value when it exists`(
                streetClass: TormaystarkasteluKatuluokka
            ) {
                every {
                    tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
                } returns streetClass.value

                val result = laskentaService.katuluokkaLuokittelu(geometriat)

                assertThat(result).isEqualTo(streetClass.value)
                verify { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat) }
            }
        }

        @Nested
        inner class WithoutYlreParts {
            @BeforeEach
            fun mockYlreParts() {
                every { tormaysService.anyIntersectsYleinenKatuosa(geometriat) } returns false
            }

            @AfterEach
            fun verifyYlreParts() {
                verify { tormaysService.anyIntersectsYleinenKatuosa(geometriat) }
            }

            @Test
            fun `returns 0 without ylre classes`() {
                every {
                    tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                } returns null

                val result = laskentaService.katuluokkaLuokittelu(geometriat)

                assertThat(result).isEqualTo(0)
                verify { tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat) }
            }

            @ParameterizedTest
            @EnumSource(TormaystarkasteluKatuluokka::class)
            fun `returns ylre classes when it exists and street classes doesn't`(
                ylreClass: TormaystarkasteluKatuluokka
            ) {
                every {
                    tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                } returns ylreClass.value
                every {
                    tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
                } returns null

                val result = laskentaService.katuluokkaLuokittelu(geometriat)

                assertThat(result).isEqualTo(ylreClass.value)
                verify {
                    tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                    tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
                }
            }

            @ParameterizedTest
            @EnumSource(TormaystarkasteluKatuluokka::class)
            fun `returns street classes when both it and ylre classes exist`(
                streetClass: TormaystarkasteluKatuluokka
            ) {
                every {
                    tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                } returns 2
                every {
                    tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
                } returns streetClass.value

                val result = laskentaService.katuluokkaLuokittelu(geometriat)

                assertThat(result).isEqualTo(streetClass.value)
                verify {
                    tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                    tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
                }
            }
        }
    }

    @Nested
    inner class LiikennemaaraLuokittelu {
        // The parameter is only used to call mocks
        val geometriat = setOf<Int>()

        @Test
        fun `returns 0 when street class is 0`() {
            val result = laskentaService.liikennemaaraLuokittelu(geometriat, 0)

            assertThat(result).isEqualTo(0)
        }

        @Test
        fun `uses small radius for traffic amounts when street class is 3`() {
            every { tormaysService.maxLiikennemaara(geometriat, RADIUS_15) } returns 1500

            val result = laskentaService.liikennemaaraLuokittelu(geometriat, 3)

            assertThat(result).isEqualTo(3)
            verify { tormaysService.maxLiikennemaara(geometriat, RADIUS_15) }
        }

        @Test
        fun `uses large radius for traffic amounts when street class is 4`() {
            every { tormaysService.maxLiikennemaara(geometriat, RADIUS_30) } returns 1500

            val result = laskentaService.liikennemaaraLuokittelu(geometriat, 4)

            assertThat(result).isEqualTo(3)
            verify { tormaysService.maxLiikennemaara(geometriat, RADIUS_30) }
        }

        @Test
        fun `returns 0 if there is no traffic`() {
            every { tormaysService.maxLiikennemaara(geometriat, RADIUS_15) } returns null

            val result = laskentaService.liikennemaaraLuokittelu(geometriat, 1)

            assertThat(result).isEqualTo(0)
            verify { tormaysService.maxLiikennemaara(geometriat, RADIUS_15) }
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
            expectedResult: Int
        ) {
            every { tormaysService.maxLiikennemaara(geometriat, RADIUS_15) } returns volume

            val result = laskentaService.liikennemaaraLuokittelu(geometriat, 1)

            assertThat(result).isEqualTo(expectedResult)
            verify { tormaysService.maxLiikennemaara(geometriat, RADIUS_15) }
        }
    }

    @Nested
    inner class PyorailyLuokittelu {
        // The parameter is only used to call mocks
        val geometriat = setOf<Int>()

        @Test
        fun `returns 5 when intersect with priority route`() {
            every { tormaysService.anyIntersectsWithCyclewaysPriority(geometriat) } returns true

            val result = laskentaService.pyorailyLuokittelu(geometriat)

            assertThat(result).isEqualTo(5)
            verify { tormaysService.anyIntersectsWithCyclewaysPriority(geometriat) }
        }

        @Test
        fun `returns 4 when intersect with main route, but not priority route`() {
            every { tormaysService.anyIntersectsWithCyclewaysPriority(geometriat) } returns false
            every { tormaysService.anyIntersectsWithCyclewaysMain(geometriat) } returns true

            val result = laskentaService.pyorailyLuokittelu(geometriat)

            assertThat(result).isEqualTo(4)
            verifyAll {
                tormaysService.anyIntersectsWithCyclewaysPriority(geometriat)
                tormaysService.anyIntersectsWithCyclewaysMain(geometriat)
            }
        }

        @Test
        fun `returns 0 when doesn't intersect with either`() {
            every { tormaysService.anyIntersectsWithCyclewaysPriority(geometriat) } returns false
            every { tormaysService.anyIntersectsWithCyclewaysMain(geometriat) } returns false

            val result = laskentaService.pyorailyLuokittelu(geometriat)

            assertThat(result).isEqualTo(0)
            verifyAll {
                tormaysService.anyIntersectsWithCyclewaysPriority(geometriat)
                tormaysService.anyIntersectsWithCyclewaysMain(geometriat)
            }
        }
    }

    @Nested
    inner class RaitiotieLuokittelu {
        // The parameter is only used to call mocks
        val geometriat = setOf<Int>()

        @Test
        fun `returns 5 when intersects with a tram line`() {
            every { tormaysService.anyIntersectsWithTramLines(geometriat) } returns true

            val result = laskentaService.raitiotieLuokittelu(geometriat)

            assertThat(result).isEqualTo(5)
            verify { tormaysService.anyIntersectsWithTramLines(geometriat) }
            verify(exactly = 0) { tormaysService.anyIntersectsWithTramInfra(geometriat) }
        }

        @Test
        fun `returns 3 when intersects with tram infra`() {
            every { tormaysService.anyIntersectsWithTramLines(geometriat) } returns false
            every { tormaysService.anyIntersectsWithTramInfra(geometriat) } returns true

            val result = laskentaService.raitiotieLuokittelu(geometriat)

            assertThat(result).isEqualTo(3)
            verifyAll {
                tormaysService.anyIntersectsWithTramLines(geometriat)
                tormaysService.anyIntersectsWithTramInfra(geometriat)
            }
        }

        @Test
        fun `returns 0 when doesn't intersect with any tram line or infra`() {
            every { tormaysService.anyIntersectsWithTramLines(geometriat) } returns false
            every { tormaysService.anyIntersectsWithTramInfra(geometriat) } returns false

            val result = laskentaService.raitiotieLuokittelu(geometriat)

            assertThat(result).isEqualTo(0)
            verifyAll {
                tormaysService.anyIntersectsWithTramLines(geometriat)
                tormaysService.anyIntersectsWithTramInfra(geometriat)
            }
        }
    }

    @Nested
    inner class BussiLuokittelu {
        // The parameter is only used to call mocks
        val geometriat = setOf<Int>()

        @Test
        fun `returns 5 when intersects with critical bus routes`() {
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometriat) } returns true

            val result = laskentaService.bussiLuokittelu(geometriat)

            assertThat(result).isEqualTo(5)
            verify { tormaysService.anyIntersectsCriticalBusRoutes(geometriat) }
        }

        @Test
        fun `returns 0 when doesn't intersect with any bus lines`() {
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometriat) } returns false
            every { tormaysService.getIntersectingBusRoutes(geometriat) } returns setOf()

            val result = laskentaService.bussiLuokittelu(geometriat)

            assertThat(result).isEqualTo(0)
            verifyAll {
                tormaysService.anyIntersectsCriticalBusRoutes(geometriat)
                tormaysService.getIntersectingBusRoutes(geometriat)
            }
        }

        @ParameterizedTest
        @CsvSource(
            "0,2",
            "4,2",
            "5,3",
            "10,3",
            "11,4",
            "20,4",
            "21,5",
            "100,5",
        )
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
                        TormaystarkasteluBussiRunkolinja.EI
                    )
                )
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometriat) } returns false
            every { tormaysService.getIntersectingBusRoutes(geometriat) } returns busLines

            val result = laskentaService.bussiLuokittelu(geometriat)

            assertThat(result).isEqualTo(expectedResult)
            verifyAll {
                tormaysService.anyIntersectsCriticalBusRoutes(geometriat)
                tormaysService.getIntersectingBusRoutes(geometriat)
            }
        }

        @ParameterizedTest
        @CsvSource(
            "ON,4",
            "LAHES,3",
            "EI,2",
        )
        fun `returns classification based on trunk lines when there are just a few buses`(
            runkolinja: TormaystarkasteluBussiRunkolinja,
            expectedResult: Int
        ) {
            val busLines = setOf(TormaystarkasteluBussireitti("", 0, 2, runkolinja))
            every { tormaysService.anyIntersectsCriticalBusRoutes(geometriat) } returns false
            every { tormaysService.getIntersectingBusRoutes(geometriat) } returns busLines

            val result = laskentaService.bussiLuokittelu(geometriat)

            assertThat(result).isEqualTo(expectedResult)
            verifyAll {
                tormaysService.anyIntersectsCriticalBusRoutes(geometriat)
                tormaysService.getIntersectingBusRoutes(geometriat)
            }
        }
    }

    @Test
    fun `calculateTormaystarkastelu happy case`() {
        val alueet = setupHappyCase()
        val geometriaIds = alueet.geometriaIds()

        val tulos = laskentaService.calculateTormaystarkastelu(alueet, geometriaIds)

        assertThat(tulos).isNotNull()
        assertThat(tulos!!.liikennehaittaIndeksi).isNotNull()
        assertThat(tulos.liikennehaittaIndeksi.indeksi).isNotNull()
        assertThat(tulos.liikennehaittaIndeksi.indeksi).isEqualTo(4.0f)
        assertThat(tulos.raitiovaunuIndeksi).isEqualTo(3.0f)

        verifyAll {
            tormaysService.anyIntersectsYleinenKatuosa(geometriaIds)
            tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriaIds)
            tormaysService.maxLiikennemaara(geometriaIds, RADIUS_30)
            tormaysService.anyIntersectsWithCyclewaysPriority(geometriaIds)
            tormaysService.anyIntersectsWithCyclewaysMain(geometriaIds)
            tormaysService.anyIntersectsCriticalBusRoutes(geometriaIds)
            tormaysService.anyIntersectsWithTramLines(geometriaIds)
            tormaysService.anyIntersectsWithTramInfra(geometriaIds)
        }
    }

    private fun setupHappyCase(): List<SavedHankealue> {

        val alkuPvm = ZonedDateTime.of(2021, 3, 4, 0, 0, 0, 0, TZ_UTC)
        val alueet =
            listOf(
                SavedHankealue(
                    geometriat = GeometriaFactory.create(),
                    haittaAlkuPvm = alkuPvm,
                    haittaLoppuPvm = alkuPvm.plusDays(7),
                    kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.YKSI,
                    kaistaPituusHaitta = KaistajarjestelynPituus.YKSI,
                    nimi = "$HANKEALUE_DEFAULT_NAME 1"
                )
            )
        val geometriaIds = alueet.geometriaIds()

        every { tormaysService.anyIntersectsYleinenKatuosa(geometriaIds) } returns true
        every { tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriaIds) } returns
            TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every { tormaysService.maxLiikennemaara(geometriaIds, RADIUS_30) } returns 1000
        every { tormaysService.anyIntersectsWithCyclewaysPriority(geometriaIds) } returns false
        every { tormaysService.anyIntersectsWithCyclewaysMain(geometriaIds) } returns true
        every { tormaysService.anyIntersectsWithTramInfra(geometriaIds) } returns true
        every { tormaysService.anyIntersectsWithTramLines(geometriaIds) } returns false
        every { tormaysService.anyIntersectsCriticalBusRoutes(geometriaIds) } returns true

        return alueet
    }
}
