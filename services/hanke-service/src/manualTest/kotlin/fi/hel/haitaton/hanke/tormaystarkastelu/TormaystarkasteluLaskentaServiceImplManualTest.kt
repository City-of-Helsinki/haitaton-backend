package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.toJsonPrettyString
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/*
 A manual test for TormaystarkasteluLaskentaServiceImpl.
 NOTICE! You need db (in docker-compose.yml) running during the test and the "tormays" data loaded into it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class TormaystarkasteluLaskentaServiceImplManualTest {

    @Autowired
    private lateinit var hankeRepository: HankeRepository

    @Autowired
    private lateinit var hankeGeometriatDao: HankeGeometriatDao

    @Autowired
    private lateinit var tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService

    /*
     * In order to this to work you have to comment out if-clause in
     * TormaystarkasteluLaskentaServiceImpl.calculateTormaystarkstelu for checking hanke.tilat.onLiikenneHaittaIndeksi.
     * Otherwise the calculation works only once and after that it will throw
     * a TormaystarkasteluAlreadyCalculatedException.
     */
    @Test
    @Transactional
    fun calculateTormaystarkastelu() {
        val entity = hankeRepository.save(HankeEntity(hankeTunnus = "HAI21-1-testi").apply {
            tilaOnGeometrioita = true
            alkuPvm = LocalDate.now()
            loppuPvm = alkuPvm!!.plusDays(7)
            haittaAlkuPvm = alkuPvm
            haittaLoppuPvm = loppuPvm
            kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.YKSI
            kaistaPituusHaitta = KaistajarjestelynPituus.YKSI
        })
        val hankeGeometriat = "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = entity.id
        hankeGeometriatDao.createHankeGeometriat(hankeGeometriat)

        var json = ""
        for (i in 0 until 100) {
            val hanke = tormaystarkasteluLaskentaService.calculateTormaystarkastelu("HAI21-1-testi")
            if (i == 0) json = hanke.tormaystarkasteluTulos.toJsonPrettyString()
        }
        println(json)
    }
}
