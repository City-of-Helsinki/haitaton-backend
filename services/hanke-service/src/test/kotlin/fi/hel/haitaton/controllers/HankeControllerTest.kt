package fi.hel.haitaton.controllers

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
        var response : ResponseEntity<Any>
       //todo: mock with data when implementation further

        // Dummy call:
        response = HankeController().getHankeById("koira")
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Assertions.assertThat(response.body).isNotNull

    }
}