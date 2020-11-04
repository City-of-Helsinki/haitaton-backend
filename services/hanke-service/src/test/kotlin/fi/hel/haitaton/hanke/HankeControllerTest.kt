package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.ZonedDateTime

internal class HankeControllerTest {

    val mockedHankeId = "AFC1234"

    val mockHankeService = Mockito.mock(HankeService::class.java)

    @BeforeEach
    fun setUp() {
        Mockito.`when`(mockHankeService.loadHanke(mockedHankeId))
                .thenReturn(Hanke(mockedHankeId, true, "Mannerheimintien remontti remonttinen", ZonedDateTime.now(), ZonedDateTime.now(), "Risto", 1))
    }

    @AfterEach
    fun tearDown() {
    }


    @Test
    fun `test that the getHankebyId returns ok`() {

        val response: ResponseEntity<Hanke> = HankeController(mockHankeService).getHankeById(mockedHankeId)

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        Assertions.assertThat(response.body?.name).isNotEmpty()
    }

    @Test
    fun `test that the putHanke can be called with partial hanke data`() {
        //TODO: mock with data when implementation further

        // Dummy call:
        var partialHanke = Hanke(hankeId = "id123", name = "hankkeen nimi", isYKTHanke = false, startDate = null, endDate = null, owner = "Tiina", phase = null)
        val response: ResponseEntity<Any> = HankeController(mockHankeService).createPartialHanke(partialHanke, "id123")

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        var responseHanke = response as? ResponseEntity<Hanke>
        Assertions.assertThat(responseHanke?.body).isNotNull
        Assertions.assertThat(responseHanke?.body?.name).isEqualTo("hankkeen nimi")
    }
}
