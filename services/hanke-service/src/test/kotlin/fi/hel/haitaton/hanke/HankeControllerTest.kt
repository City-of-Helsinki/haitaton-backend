package fi.hel.haitaton.hanke

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
import javax.validation.ConstraintViolationException

@ExtendWith(SpringExtension::class)
@Import(HankeControllerTest.TestConfiguration::class)
class HankeControllerTest {

    @Configuration
    class TestConfiguration {

        //makes validation happen here in unit test as well
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
                        getCurrentTimeUTC(), getCurrentTimeUTC(), "OHJELMOINTI",
                        1,"Risto", getCurrentTimeUTC(), null, null, SaveType.DRAFT))

        val response: ResponseEntity<Any> = hankeController.getHankeByTunnus(mockedHankeTunnus)

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        Assertions.assertThat((response.body as Hanke).nimi).isNotEmpty()
    }

    @Test
    fun `test that the updateHanke can be called with partial hanke data`() {
        var partialHanke = Hanke(id = 123, hankeTunnus = "id123",
                nimi = "hankkeen nimi", kuvaus = "lorem ipsum dolor sit amet...", onYKTHanke = false,
                alkuPvm = null, loppuPvm = null, vaihe = "OHJELMOINTI",
                version = 1, createdBy = "Tiina", createdAt = getCurrentTimeUTC(), modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)
        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        val response: ResponseEntity<Any> = hankeController.updateHanke(partialHanke, "id123")

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        var responseHanke = response as ResponseEntity<Hanke>
        Assertions.assertThat(responseHanke.body).isNotNull
        Assertions.assertThat(responseHanke.body?.nimi).isEqualTo("hankkeen nimi")
    }


    @Test
    fun `test that the updateHanke will give validation errors from invalid hanke data for creatorUserId and name`() {
        var partialHanke = Hanke(id = 0, hankeTunnus = "id123", nimi = "", kuvaus = "", onYKTHanke = false,
                alkuPvm = null, loppuPvm = null, vaihe = "",
                version = 1, createdBy = "", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)
        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        Assertions.assertThatExceptionOfType(ConstraintViolationException::class.java).isThrownBy {
            hankeController.updateHanke(partialHanke, "id123")
        }.withMessageContaining("updateHanke.hanke.nimi: "+HankeError.HAI1002.toString()).withMessageContaining("updateHanke.hanke.creatorUserId: "+HankeError.HAI1002.toString())

    }

}
