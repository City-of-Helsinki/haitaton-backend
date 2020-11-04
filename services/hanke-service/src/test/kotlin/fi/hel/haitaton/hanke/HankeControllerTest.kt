package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

internal class HankeControllerTest {

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun `test that the getHankebyId returns ok`() {
        //TODO: mock with data when implementation further
        // Dummy call:
        val response: ResponseEntity<Hanke> = HankeController().getHankeById("koira")

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        Assertions.assertThat(response.body?.name).isNotEmpty()
    }

    @Test
    fun `test that the putHanke can be called with partial hanke data`() {
        //TODO: mock with data when implementation further
        // Dummy call:

        var partialHanke = Hanke(hankeId = "id123", name="hankkeen nimi", isYKTHanke = false, startDate = null, endDate = null, owner="Tiina", phase = null)
        val response: ResponseEntity<Any> = HankeController().createPartialHanke(partialHanke,"id123")

        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull
        var responseHanke = response as? ResponseEntity<Hanke>
        Assertions.assertThat(responseHanke?.body).isNotNull
        Assertions.assertThat(responseHanke?.body?.name).isEqualTo("hankkeen nimi")
    }
}
