package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.HaitatonPostgreSQLContainer
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import io.mockk.every
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
@WithMockUser(username = "test", roles = ["haitaton-user"])
internal class TormaystarkasteluLaskentaServiceImplITest {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer
            .withExposedPorts(5433) // use non-default port
            .withPassword("test")
            .withUsername("test")

        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", container::getJdbcUrl)
            registry.add("spring.datasource.username", container::getUsername)
            registry.add("spring.datasource.password", container::getPassword)
            registry.add("spring.liquibase.url", container::getJdbcUrl)
            registry.add("spring.liquibase.user", container::getUsername)
            registry.add("spring.liquibase.password", container::getPassword)
        }
    }

    @MockkBean
    private lateinit var hankeGeometriatDao: HankeGeometriatDao

    @MockkBean
    private lateinit var tormaystarkasteluDao: TormaystarkasteluDao

    @Autowired
    private lateinit var hankeService: HankeService

    @Autowired
    private lateinit var tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService

    @Test
    fun `calculateTormaystarkastelu happy case`() {
        // setup
        val hankeGeometriat = HankeGeometriat(1, 1)
        every {
            hankeGeometriatDao.retrieveHankeGeometriat(1)
        } returns hankeGeometriat
        every {
            tormaystarkasteluDao.yleisetKatualueet(hankeGeometriat)
        } returns mapOf(Pair(1, true))
        every {
            tormaystarkasteluDao.yleisetKatuluokat(hankeGeometriat)
        } returns mapOf(Pair(1, setOf(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)))
        every {
            tormaystarkasteluDao.katuluokat(hankeGeometriat)
        } returns mapOf(Pair(1, setOf(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)))
        every {
            tormaystarkasteluDao.liikennemaarat(hankeGeometriat, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30)
        } returns mapOf(Pair(1, setOf(1000)))
        every {
            tormaystarkasteluDao.pyorailyreitit(hankeGeometriat)
        } returns mapOf(Pair(1, setOf(TormaystarkasteluPyorailyreittiluokka.PAAREITTI)))
        every {
            tormaystarkasteluDao.raitiotiet(hankeGeometriat)
        } returns mapOf(Pair(1, setOf(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU)))
        every {
            tormaystarkasteluDao.bussiliikenteenKannaltaKriittinenAlue(hankeGeometriat)
        } returns mapOf(Pair(1, true))
        val hanke = Hanke(1, "HAI21-1").apply {
            alkuPvm = ZonedDateTime.of(2021, 3, 4, 0, 0, 0, 0, TZ_UTC)
            loppuPvm = alkuPvm!!.plusDays(7)
            haittaAlkuPvm = alkuPvm
            haittaLoppuPvm = loppuPvm
            kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.EI_VAIKUTA
            kaistaPituusHaitta = KaistajarjestelynPituus.EI_TARVITA
            tilat.onGeometrioita = true
        }
        hankeService.createHanke(hanke)
        hankeService.updateHankeStateFlags(hanke)

        // tested functionality
        val tormaystarkasteluHanke = tormaystarkasteluLaskentaService.calculateTormaystarkastelu("HAI21-1")
        println(tormaystarkasteluHanke.tormaystarkasteluTulos)

        // assert results
        assertThat(tormaystarkasteluHanke.tormaystarkasteluTulos).isNotNull()
        assertThat(tormaystarkasteluHanke.tormaystarkasteluTulos!!.liikennehaittaIndeksi!!.type)
            .isEqualTo(IndeksiType.JOUKKOLIIKENNEINDEKSI)
    }
}
