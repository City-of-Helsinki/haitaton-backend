package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.HaitatonPostgreSQLContainer
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.toJsonPrettyString
import javax.transaction.Transactional
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
internal class TormaystarkasteluDaoImplITest {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer()
            .withExposedPorts(5433) // use non-default port
            .withPassword("test")
            .withUsername("test")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource(
                    "/fi/hel/haitaton/hanke/tormaystarkastelu/HEL-GIS-data-test.sql"
                ),
                "/docker-entrypoint-initdb.d/HEL-GIS-data-test.sql"
            )

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

        @JvmStatic
        @BeforeAll
        fun setUp(@Autowired hankeRepository: HankeRepository, @Autowired hankeGeometriatDao: HankeGeometriatDao) {
            val entity = hankeRepository.save(HankeEntity(hankeTunnus = "HAI21-1-testi"))
            hankeGeometriat = "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat.json"
                .asJsonResource(HankeGeometriat::class.java)
            hankeGeometriat.hankeId = entity.id
            hankeGeometriatDao.createHankeGeometriat(hankeGeometriat)
        }

        private var hankeGeometriat: HankeGeometriat = HankeGeometriat()
    }

    @Autowired
    private lateinit var tormaystarkasteluDao: TormaystarkasteluDao

    /*
    Test manually whether Hanke geometries are located on general street area ("yleinen katualue", ylre_parts)
     */
    @Test
    fun yleisetKatualueet() {
        val yleisetKatualueet = tormaystarkasteluDao.yleisetKatualueet(hankeGeometriat)
        println(yleisetKatualueet.toJsonPrettyString())
        assertThat(yleisetKatualueet.size).isEqualTo(1)
        assertThat(yleisetKatualueet.values.first()).isTrue()
    }

    /*
    Test manually what general street classes (ylre_classes) Hanke geometries are located on
     */
    @Test
    fun yleisetKatuluokat() {
        val yleisetKatuluokat = tormaystarkasteluDao.yleisetKatuluokat(hankeGeometriat)
        println(yleisetKatuluokat.toJsonPrettyString())
        assertThat(yleisetKatuluokat.size).isEqualTo(1)
        assertThat(yleisetKatuluokat.values.first().size).isEqualTo(1)
        assertThat(yleisetKatuluokat.values.first().first())
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)
    }

    /*
    Test manually what street classes (street_classes) Hanke geometries are located on
     */
    @Test
    fun katuluokat() {
        val katuluokat = tormaystarkasteluDao.katuluokat(hankeGeometriat)
        println(katuluokat.toJsonPrettyString())
        assertThat(katuluokat.size).isEqualTo(1)
        assertThat(katuluokat.values.first().size).isEqualTo(1)
        assertThat(katuluokat.values.first().first())
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)
    }

    /*
    Test manually whether Hanke geometries are locaed on central businessa area ("kantakaupunki", central_business_area)
     */
    @Test
    fun kantakaupunki() {
        val kantakaupunki = tormaystarkasteluDao.kantakaupunki(hankeGeometriat)
        println(kantakaupunki.toJsonPrettyString())
        assertThat(kantakaupunki.size).isEqualTo(1)
        assertThat(kantakaupunki.values.first()).isTrue()
    }

    /*
    Test manually what kinds of traffic counts there are on Hanke geometries with radius of 15m
     */
    @Test
    fun liikennemaarat15() {
        val liikennemaarat =
            tormaystarkasteluDao.liikennemaarat(hankeGeometriat, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15)
        println(liikennemaarat.toJsonPrettyString())
        assertThat(liikennemaarat.size).isEqualTo(1)
        assertThat(liikennemaarat.values.first().size).isEqualTo(3)
        assertThat(liikennemaarat.values.first()).containsOnly(17566, 17286, 1478)
    }

    /*
    Test manually what kinds of traffic counts there are on Hanke geometries with radius of 30m
     */
    @Test
    fun liikennemaarat30() {
        val liikennemaarat =
            tormaystarkasteluDao.liikennemaarat(hankeGeometriat, TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30)
        println(liikennemaarat.toJsonPrettyString())
        assertThat(liikennemaarat.size).isEqualTo(1)
        assertThat(liikennemaarat.values.first().size).isEqualTo(3)
        assertThat(liikennemaarat.values.first()).containsOnly(17566, 17286, 1478)
    }

    /*
    Test manually what Hanke geometries are located on critical area for buses
     */
    @Test
    fun bussiliikenteenKannaltaKriittinenAlue() {
        val bussiliikenteenKannaltaKriittinenAlue =
            tormaystarkasteluDao.bussiliikenteenKannaltaKriittinenAlue(hankeGeometriat)
        println(bussiliikenteenKannaltaKriittinenAlue.toJsonPrettyString())
        assertThat(bussiliikenteenKannaltaKriittinenAlue.size).isEqualTo(0)
    }

    /*
    Test manually what bus routes each Hanke geometry is located on
     */
    @Test
    fun bussit() {
        val bussit = tormaystarkasteluDao.bussit(hankeGeometriat)
        println(bussit.toJsonPrettyString())
        assertThat(bussit.size).isEqualTo(1)
        assertThat(bussit.values.first().size).isEqualTo(3)
        assertThat(bussit.values.first().map { it.reittiId }).containsOnly("1016", "1023N")
    }

    /*
    Test manually what tram lane types each Hanke geometry is located on
     */
    @Test
    fun raitiotiet() {
        val raitiotiet = tormaystarkasteluDao.raitiotiet(hankeGeometriat)
        println(raitiotiet.toJsonPrettyString())
        assertThat(raitiotiet.size).isEqualTo(1)
        assertThat(raitiotiet.values.first().size).isEqualTo(1)
        assertThat(raitiotiet.values.first().first()).isEqualTo(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU)
    }

    /*
    Test manually what kind of cycleways Hanke geometries are located on
     */
    @Test
    fun pyorailyreitit() {
        val pyorailyreitit = tormaystarkasteluDao.pyorailyreitit(hankeGeometriat)
        println(pyorailyreitit.toJsonPrettyString())
        assertThat(pyorailyreitit.size).isEqualTo(1)
        assertThat(pyorailyreitit.values.first()).containsOnly(
            TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI,
            TormaystarkasteluPyorailyreittiluokka.PAAREITTI
        )
    }
}
