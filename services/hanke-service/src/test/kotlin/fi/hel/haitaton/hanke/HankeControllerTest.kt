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
        //todo: mock with data when implementation further

        // Dummy call:
        val response: ResponseEntity<Hanke> = HankeController().getHankeById("koira")
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull

    }
}