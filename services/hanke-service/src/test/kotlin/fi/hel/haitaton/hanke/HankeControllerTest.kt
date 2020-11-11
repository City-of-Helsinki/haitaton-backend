package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.ZonedDateTime

@SpringBootTest
class HankeControllerTest {

    val mockedHankeId = "AFC1234"

    @MockBean
    lateinit var hankeService: HankeService

    lateinit var hankeController: HankeController

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        hankeController = HankeController(hankeService)
    }

    @AfterEach
    fun tearDown() {
    }


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
    fun `test that the putHanke can be called with partial hanke data`() {

        var partialHanke = Hanke(hankeId = "id123", name = "hankkeen nimi", isYKTHanke = false, startDate = null, endDate = null, owner = "Tiina", phase = null)
        //mock HankeService response
        Mockito.`when`(hankeService.save(partialHanke)).thenReturn(partialHanke)

        //Actual call
        val response: ResponseEntity<Any> = hankeController.updateHanke(partialHanke, "id123")

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        var responseHanke = response as? ResponseEntity<Hanke>
        Assertions.assertThat(responseHanke?.body).isNotNull
        Assertions.assertThat(responseHanke?.body?.name).isEqualTo("hankkeen nimi")


    }
}
