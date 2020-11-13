package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
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

    private val mockedHankeId = "AFC1234"

    @Autowired
    private lateinit var hankeService: HankeService

    @Autowired
    private lateinit var hankeController: HankeController

    @Test
    fun `test that the getHankebyId returns ok`() {
        Mockito.`when`(hankeService.loadHanke(mockedHankeId))
                .thenReturn(fi.hel.haitaton.hanke.Hanke(mockedHankeId, true, "Mannerheimintien remontti remonttinen", java.time.ZonedDateTime.now(), java.time.ZonedDateTime.now(), "Risto", 1))

        val response: ResponseEntity<Any> = hankeController.getHankeById(mockedHankeId)

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        Assertions.assertThat((response.body as Hanke).name).isNotEmpty()
    }

    @Test
    fun `test that the updateHanke can be called with partial hanke data`() {

        var partialHanke = Hanke(hankeId = "id123", name = "hankkeen nimi", isYKTHanke = false, startDate = null, endDate = null, owner = "Tiina", phase = 0)
        //mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        //Actual call
        val response: ResponseEntity<Any> = hankeController.updateHanke(partialHanke, "id123")

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        var responseHanke = response as ResponseEntity<Hanke>
        Assertions.assertThat(responseHanke.body).isNotNull
        Assertions.assertThat(responseHanke.body?.name).isEqualTo("hankkeen nimi")
    }


    @Test
    fun `test that the updateHanke will give validation errors from invalid hanke data for owner and name`() {

        var partialHanke = Hanke(hankeId = "id123", name = "", isYKTHanke = false, startDate = null, endDate = null, owner = "", phase = 0)
        //mock HankeService response
        Mockito.`when`(hankeService.save(partialHanke)).thenReturn(partialHanke)

        //Actual call
        Assertions.assertThatExceptionOfType(ConstraintViolationException::class.java).isThrownBy {
            hankeController.updateHanke(partialHanke, "id123")
        }.withMessageContaining("updateHanke.hanke.name: "+HankeError.HAI1002.toString()).withMessageContaining("updateHanke.hanke.owner: "+HankeError.HAI1002.toString())

    }

}
