package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import org.springframework.context.annotation.Configuration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.validation.ConstraintViolationException

@ExtendWith(SpringExtension::class)
@Import(HankeControllerTest.TestConfiguration::class)
class HankeControllerTest {

    @Configuration
    class TestConfiguration {
        // makes validation happen here in unit test as well
        @Bean
        fun bean(): MethodValidationPostProcessor = MethodValidationPostProcessor()

        @Bean
        fun hankeService(): HankeService = Mockito.mock(HankeService::class.java)

        @Bean
        fun hankeController(hankeService: HankeService): HankeController = HankeController(hankeService)
    }

    private val mockedHankeTunnus = "AFC1234"

    @Autowired
    private lateinit var hankeService: HankeService

    @Autowired
    private lateinit var hankeController: HankeController

    @Test
    fun `test that the getHankebyTunnus returns ok`() {
        Mockito.`when`(hankeService.loadHanke(mockedHankeTunnus))
                .thenReturn(Hanke(1234, mockedHankeTunnus, true,
                        "Mannerheimintien remontti remonttinen", "Lorem ipsum dolor sit amet...",
                        getDatetimeAlku(), getDatetimeLoppu(), Vaihe.OHJELMOINTI, null,
                        1, "Risto", getCurrentTimeUTC(), null, null, SaveType.DRAFT))

        val response: ResponseEntity<Any> = hankeController.getHankeByTunnus(mockedHankeTunnus)

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        Assertions.assertThat((response.body as Hanke).nimi).isNotEmpty
    }

    @Test
    fun `test that the updateHanke can be called with hanke data and response will be 200`() {
        val partialHanke = Hanke(id = 123, hankeTunnus = "id123",
                nimi = "hankkeen nimi", kuvaus = "lorem ipsum dolor sit amet...", onYKTHanke = false,
                alkuPvm = getDatetimeAlku(), loppuPvm = getDatetimeLoppu(), vaihe = Vaihe.SUUNNITTELU, suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                version = 1, createdBy = "Tiina", createdAt = getCurrentTimeUTC(), modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        val response: ResponseEntity<Any> = hankeController.updateHanke(partialHanke, "id123")

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        // If the status is ok, we expect ResponseEntity<Hanke>
        @Suppress("UNCHECKED_CAST")
        val responseHanke = response as ResponseEntity<Hanke>
        Assertions.assertThat(responseHanke.body).isNotNull
        Assertions.assertThat(responseHanke.body?.nimi).isEqualTo("hankkeen nimi")
    }


    @Test
    fun `test that the updateHanke will give validation errors from invalid hanke data for name`() {
        val partialHanke = Hanke(id = 0, hankeTunnus = "id123", nimi = "", kuvaus = "", onYKTHanke = false,
                alkuPvm = null, loppuPvm = null, vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
                version = 1, createdBy = "", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)
        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        Assertions.assertThatExceptionOfType(ConstraintViolationException::class.java).isThrownBy {
            hankeController.updateHanke(partialHanke, "id123")
        }.withMessageContaining("updateHanke.hanke.nimi: " + HankeError.HAI1002.toString())
    }

    // sending of sub types
    @Test
    fun `test that create with listOfOmistaja can be sent to controller and is responded with 200`() {
        val hanke = Hanke(id = null, hankeTunnus = null,
                nimi = "hankkeen nimi", kuvaus = "lorem ipsum dolor sit amet...", onYKTHanke = false,
                alkuPvm = getDatetimeAlku(), loppuPvm = getDatetimeLoppu(), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
                version = 1, createdBy = "Tiina", createdAt = getCurrentTimeUTC(), modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)


        hanke.omistajat = arrayListOf(
                HankeYhteystieto(null, "Pekkanen", "Pekka",
                        "pekka@pekka.fi", "3212312", null,
                        "Kaivuri ja mies", null, null, null,
                        null, null))

        val mockedHanke = hanke.copy()
        mockedHanke.omistajat = mutableListOf(hanke.omistajat[0])
        mockedHanke.id = 12
        mockedHanke.hankeTunnus= "JOKU12"
        mockedHanke.omistajat[0].id = 1

        // mock HankeService response
        Mockito.`when`(hankeService.createHanke(hanke)).thenReturn(mockedHanke)

        // Actual call
        val response: ResponseEntity<Any> = hankeController.createHanke(hanke)

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        // If the status is ok, we expect ResponseEntity<Hanke>
        @Suppress("UNCHECKED_CAST")
        val responseHanke = response as ResponseEntity<Hanke>
        Assertions.assertThat(responseHanke.body).isNotNull
        Assertions.assertThat(responseHanke.body?.id).isEqualTo(12)
        Assertions.assertThat(responseHanke.body?.hankeTunnus).isEqualTo("JOKU12")
        Assertions.assertThat(responseHanke.body?.nimi).isEqualTo("hankkeen nimi")
        Assertions.assertThat(responseHanke.body?.omistajat).isNotNull
        Assertions.assertThat(responseHanke.body?.omistajat).hasSize(1)
        Assertions.assertThat(responseHanke.body?.omistajat!![0].id).isEqualTo(1)
        Assertions.assertThat(responseHanke.body?.omistajat!![0].sukunimi).isEqualTo("Pekkanen")
    }

    private fun getDatetimeAlku(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
    }

    private fun getDatetimeLoppu(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 21, 0, 12, 34, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
    }

}
