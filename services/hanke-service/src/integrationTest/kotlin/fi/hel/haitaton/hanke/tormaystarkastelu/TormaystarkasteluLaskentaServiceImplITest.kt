package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
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
    private lateinit var tormaystarkasteluTulosRepository: TormaystarkasteluTulosRepository

    @Autowired
    private lateinit var hankeService: HankeService

    @Autowired
    private lateinit var tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService

    @Test
    fun `calculateTormaystarkastelu happy case`() {
        val hanke = setupHappyCase()
        val hankeTunnus = hanke.hankeTunnus!!

        // test the functionality
        val tormaystarkasteluHanke = tormaystarkasteluLaskentaService.calculateTormaystarkastelu(hankeTunnus)

        // assert the results and that the returned hanke-instance contains the tulos
        assertThat(tormaystarkasteluHanke.tormaystarkasteluTulos).isNotNull()
        assertThat(tormaystarkasteluHanke.tormaystarkasteluTulos!!.liikennehaittaIndeksi!!.tyyppi)
            .isEqualTo(IndeksiType.JOUKKOLIIKENNEINDEKSI)
    }

    @Test
    fun `calculate happy case persists the result correctly and can be read afterwards`() {
        val hanke = setupHappyCase()
        val hankeTunnus = hanke.hankeTunnus!!

        // Call calculate (which should save stuff...), ignore the returned hanke
        tormaystarkasteluLaskentaService.calculateTormaystarkastelu(hankeTunnus)

        // Load Hanke separately via its service, and check that it has the liikennehaittaindeksi
        val hankeViaService = hankeService.loadHanke(hankeTunnus)
        assertThat(hankeViaService).isNotNull()
        assertThat(hankeViaService!!.liikennehaittaindeksi).isNotNull()
        assertThat(hankeViaService.liikennehaittaindeksi!!.indeksi).isNotNull()
        assertThat(hankeViaService.liikennehaittaindeksi!!.indeksi).isEqualTo(4.0f)

        // Check that tormaystarkasteluTulos can be found via its Repository, and has some value
        val tormaystarkasteluTulosEntityList = tormaystarkasteluTulosRepository.findByHankeId(hankeViaService.id!!)
        assertThat(tormaystarkasteluTulosEntityList).isNotNull()
        assertThat(tormaystarkasteluTulosEntityList).isNotEmpty()
        assertThat(tormaystarkasteluTulosEntityList[0]).isNotNull()
        assertThat(tormaystarkasteluTulosEntityList[0].liikennehaitta).isNotNull()
        assertThat(tormaystarkasteluTulosEntityList[0].liikennehaitta!!.indeksi).isNotNull()
        assertThat(tormaystarkasteluTulosEntityList[0].liikennehaitta!!.indeksi).isEqualTo(4.0f)
    }

    @Test
    fun `calculate happy case result and can be read afterwards with getTormaystarkastelu`() {
        val hanke = setupHappyCase()
        val hankeTunnus = hanke.hankeTunnus!!

        // Call calculate (which should save stuff...), ignore the returned hanke
        tormaystarkasteluLaskentaService.calculateTormaystarkastelu(hankeTunnus)

        val tormaystarkasteluTulos = tormaystarkasteluLaskentaService.getTormaystarkastelu(hankeTunnus)
        assertThat(tormaystarkasteluTulos).isNotNull()
        assertThat(tormaystarkasteluTulos!!.liikennehaittaIndeksi).isNotNull()
        assertThat(tormaystarkasteluTulos.liikennehaittaIndeksi!!.indeksi).isNotNull()
        assertThat(tormaystarkasteluTulos.liikennehaittaIndeksi!!.indeksi).isEqualTo(4.0f)
    }

    private fun setupHappyCase(): Hanke {
        val hanke = hankeService.createHanke(Hanke())
        hanke.apply {
            alkuPvm = ZonedDateTime.of(2021, 3, 4, 0, 0, 0, 0, TZ_UTC)
            loppuPvm = alkuPvm!!.plusDays(7)
            haittaAlkuPvm = alkuPvm
            haittaLoppuPvm = loppuPvm
            kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.YKSI
            kaistaPituusHaitta = KaistajarjestelynPituus.YKSI
            tilat.onGeometrioita = true
        }
        hankeService.updateHanke(hanke)
        hankeService.updateHankeStateFlags(hanke)
        val hankeId = hanke.id!!
        val hankeGeometriatId = 1
        val hankeGeometriaId = 1
        val hankeGeometriat = HankeGeometriat(hankeGeometriatId, hankeId)

        every {
            hankeGeometriatDao.retrieveHankeGeometriat(hankeId)
        } returns hankeGeometriat
        every {
            tormaystarkasteluDao.yleisetKatualueet(hankeGeometriat)
        } returns mapOf(Pair(hankeGeometriaId, true))
        every {
            tormaystarkasteluDao.yleisetKatuluokat(hankeGeometriat)
        } returns mapOf(Pair(hankeGeometriaId, setOf(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)))
        every {
            tormaystarkasteluDao.katuluokat(hankeGeometriat)
        } returns mapOf(Pair(hankeGeometriaId, setOf(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)))
        every {
            tormaystarkasteluDao.liikennemaarat(hankeGeometriat, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30)
        } returns mapOf(Pair(hankeGeometriaId, setOf(1000)))
        every {
            tormaystarkasteluDao.pyorailyreitit(hankeGeometriat)
        } returns mapOf(Pair(hankeGeometriaId, setOf(TormaystarkasteluPyorailyreittiluokka.PAAREITTI)))
        every {
            tormaystarkasteluDao.raitiotiet(hankeGeometriat)
        } returns mapOf(Pair(hankeGeometriaId, setOf(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU)))
        every {
            tormaystarkasteluDao.bussiliikenteenKannaltaKriittinenAlue(hankeGeometriat)
        } returns mapOf(Pair(hankeGeometriaId, true))

        return hanke
    }


}
